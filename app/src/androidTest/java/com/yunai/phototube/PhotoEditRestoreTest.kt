package com.yunai.phototube

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import com.yunai.phototube.data.EditTransform
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

class PhotoEditRestoreTest : RemoteAppTest() {
    @Volatile private var delayResponse = false
    private var switchingVersion = false
    private val writes = CopyOnWriteArrayList<JSONObject>()
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (!request.requestUrl!!.encodedPath.endsWith("/edit-versions")) return null
        val items = JSONArray()
        if (switchingVersion) items.put(JSONObject().put("id", "v2").put("sourceContentHash", "a".repeat(64))
            .put("active", false).put("sourceState", "CURRENT").put("renderState", "PENDING")
            .put("outputWidth", 100).put("outputHeight", 100).put("createdAt", "2026-09-17T00:00:00Z").put("transform", JSONObject()))
        return MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("items", items).put("nextCursor", JSONObject.NULL).toString())
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals(if (switchingVersion) "PATCH" else "POST", request.method)
        assertEquals(if (switchingVersion) "/api/v1/assets/restore-0/active-edit" else "/api/v1/assets/restore-0/edit-versions", request.requestUrl!!.encodedPath)
        writes += JSONObject(request.body.readUtf8())
        return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"保存未确认"}""")
            .apply { if (delayResponse) setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS) }
    }
    private fun openEditor() {
        rule.onNodeWithTag("asset-restore-0").performTouchInput {click(Offset(12f,12f))}; waitTag("media-viewer")
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("编辑历史与原图").performClick()
        rule.onNodeWithText("裁切、旋转与镜像").performClick(); waitTag("crop-canvas")
    }
    @Test fun editorPreviewAndSaveRemainReachableAcrossOrientation() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        fun capture(name:String) {
            rule.waitForIdle()
            val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
        openEditor()
        rule.onNodeWithText("旋转 90°").performClick()
        rule.onNodeWithText("保存新版本").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("mock-editor-portrait.png")
        try {
            device.setOrientationLeft();device.waitForIdle();waitTag("crop-canvas")
            rule.onNodeWithText("保存新版本").performScrollTo().assertIsDisplayed().assertIsEnabled()
            val preview=rule.onNodeWithTag("crop-canvas").fetchSemanticsNode().boundsInRoot
            val tools=rule.onNodeWithTag("editor-tools").fetchSemanticsNode().boundsInRoot
            assertTrue(tools.left>preview.right)
            rule.onNodeWithTag("crop-canvas").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(200f))
            capture("mock-editor-landscape.png")
            assertTrue(writes.isEmpty())
        } finally {device.setOrientationNatural();device.unfreezeRotation()}
    }
    @Test fun unsavedEditRequiresExplicitDiscard() = verifyDiscard("LIGHT")
    @Test fun darkUnsavedEditRequiresExplicitDiscard() = verifyDiscard("DARK")
    private fun verifyDiscard(mode:String) {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-$mode").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("theme-mode-$mode") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        openEditor()
        rule.onNodeWithText("旋转 90°").performClick()
        rule.onAllNodesWithContentDescription("返回",substring=false).onLast().performClick()
        rule.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        rule.onNodeWithText("放弃修改",substring=false).assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("继续编辑").assertIsDisplayed().assertIsEnabled()
        rule.waitForIdle()
        val bitmap=requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            java.io.File(app.getExternalFilesDir(null),"editor-discard-${mode.lowercase()}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        } finally {bitmap.recycle()}
        rule.onNodeWithText("继续编辑").performClick()
        rule.onNodeWithText("保存新版本").assertIsEnabled()
        rule.activityRule.scenario.recreate();waitTag("crop-canvas")
        rule.onNodeWithText("保存新版本").assertIsEnabled()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        rule.waitUntil(5000) {rule.onAllNodesWithText("放弃未保存的修改？").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("放弃修改",substring=false).performClick()
        rule.waitUntil(5000) {rule.onAllNodesWithTag("crop-canvas").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithText("裁切、旋转与镜像").performClick();waitTag("crop-canvas")
        rule.onNodeWithText("保存新版本").assertIsNotEnabled()
        assertTrue(writes.isEmpty())
    }
    @Test fun recipePreviewAndUncertainSaveSurviveRecreation() {
        openEditor()
        rule.onNodeWithTag("crop-canvas").performTouchInput {
            val frameWidth = min(width.toFloat(), height * 1.5f)
            val frameHeight = frameWidth / 1.5f
            val corner = Offset((width-frameWidth)/2 + 3f, (height-frameHeight)/2 + 3f)
            swipe(corner, corner + Offset(frameWidth*.15f, frameHeight*.15f), 400)
        }
        rule.onNodeWithText("旋转 90°").performClick()
        rule.onNodeWithText("水平镜像").performClick()
        rule.onNodeWithText("预览效果").performClick()
        rule.activityRule.scenario.recreate(); waitTag("crop-canvas")
        rule.onNodeWithText("调整裁切").assertExists()
        rule.onNodeWithText("水平镜像").assertIsOn()
        rule.onNodeWithText("保存新版本").assertIsEnabled()
        assertEquals(0, writes.size)
        rule.onNodeWithText("保存新版本").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("保存未确认：", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, writes.size)
        val body = writes.single()
        assertEquals("a".repeat(64), body.getString("sourceContentHash"))
        assertTrue(body.isNull("expectedActiveEditVersionId"))
        val transform = EditTransform.from(body.getJSONObject("transform"))
        assertEquals(90, transform.rotation); assertTrue(transform.flipHorizontal)
        assertTrue(transform.x > 0 && transform.y > 0 && transform.width < 1000000)
        rule.activityRule.scenario.recreate(); waitTag("crop-canvas")
        rule.onNodeWithText("保存新版本").assertIsNotEnabled()
        rule.onNodeWithText("重新载入当前版本（重置草稿）").assertExists()
        assertEquals("No automatic resubmission after recreation", 1, writes.size)
    }
    @Test fun rotationDuringVersionSwitchRequiresRefreshBeforeAnotherMutation() {
        switchingVersion = true
        delayResponse = true
        rule.onNodeWithTag("asset-restore-0").performClick(); waitTag("media-viewer")
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("编辑历史与原图").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("使用此版本").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("使用此版本"))
        rule.onNodeWithText("使用此版本").performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        rule.activityRule.scenario.recreate()
        waitTag("edit-history-list")
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("切换结果未确认，请刷新当前版本后再操作。"))
        rule.onNodeWithText("切换结果未确认，请刷新当前版本后再操作。").assertIsDisplayed()
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("使用此版本"))
        rule.onNodeWithText("使用此版本").assertIsNotEnabled()
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("刷新当前版本"))
        rule.onNodeWithText("刷新当前版本").assertIsEnabled().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("切换结果未确认，请刷新当前版本后再操作。").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("使用此版本"))
        rule.onNodeWithText("使用此版本").assertIsEnabled()
        assertEquals(1, writes.size)
        assertEquals("v2", writes.single().getString("targetEditVersionId"))
    }

    @Test fun rotationDuringSaveDoesNotPermitDuplicateCreation() {
        delayResponse = true
        openEditor()
        rule.onNodeWithText("旋转 90°").performClick()
        rule.onNodeWithText("保存新版本").performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        rule.activityRule.scenario.recreate(); waitTag("crop-canvas")
        rule.onNodeWithText("保存新版本").assertIsNotEnabled()
        rule.onNodeWithText("重新载入当前版本（重置草稿）").assertExists()
        assertEquals(1, writes.size)
    }

}
