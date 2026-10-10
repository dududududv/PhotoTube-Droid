package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AlbumCoverRestoreTest : RemoteAppTest() {
    override val photographicFixtures = true
    private val writes = CopyOnWriteArrayList<JSONObject>()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/albums/restore-album", request.requestUrl!!.encodedPath)
        writes += JSONObject(request.body.readUtf8())
        return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"封面暂时无法保存"}""")
    }
    private fun openCover() {
        rule.onNodeWithTag("album-media-restore-0").performTouchInput { longClick() }
        rule.onNodeWithTag("glass-action-menu").assertExists()
        auditShot("album-member-actions")
        rule.onNodeWithText("设为相册封面").performClick()
        waitTag("cover-focus-x")
    }
    private fun value(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    @Test fun focalPointAndTargetSurviveRecreationAndCancelResetsDraft() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-media-restore-0")
        openCover()
        rule.onNodeWithTag("cover-focus-x").performSemanticsAction(SemanticsActions.SetProgress) { it(.25f) }
        rule.onNodeWithTag("cover-focus-y").performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
        rule.activityRule.scenario.recreate(); waitTag("cover-focus-x")
        assertEquals(.25f, value("cover-focus-x"), .001f)
        assertEquals(.75f, value("cover-focus-y"), .001f)
        assertTrue(writes.isEmpty())
        // Window entry animation runs outside the Compose test clock.
        android.os.SystemClock.sleep(500)
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(java.io.File(app.getExternalFilesDir(null), "album-cover-preview.png"))
        rule.onNodeWithText("保存封面").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("封面暂时无法保存").fetchSemanticsNodes().isNotEmpty() }
        val body = writes.single()
        assertEquals("restore-0", body.getString("coverAssetId"))
        assertEquals(.25, body.getJSONObject("coverFocalPoint").getDouble("x"), .001)
        assertEquals(.75, body.getJSONObject("coverFocalPoint").getDouble("y"), .001)
        rule.onNodeWithText("取消").performScrollTo().performClick()
        openCover()
        assertEquals(.5f, value("cover-focus-x"), .001f)
        assertEquals(.5f, value("cover-focus-y"), .001f)
        assertEquals(1, writes.size)
    }
    @Test fun darkPreviewSupportsDragCancelAndCenterWithoutWriting() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithTag("album-restore-album").performScrollTo().performClick();waitTag("album-media-restore-0")
        openCover()
        rule.waitForIdle(); android.os.SystemClock.sleep(450)
        rule.onNodeWithTag("cover-preview").performTouchInput { swipe(center,center + androidx.compose.ui.geometry.Offset(0f,120f),durationMillis=400) }
        assertTrue("Dragging down exposes the top of the bundled portrait",value("cover-focus-y") < .5f)
        assertEquals("Portrait photo has no horizontal crop overflow",.5f,value("cover-focus-x"),.001f)
        val before=value("cover-focus-y")
        rule.onNodeWithTag("cover-preview").performTouchInput {
            down(center);moveBy(androidx.compose.ui.geometry.Offset(0f,-100f));cancel()
        }
        assertEquals("Cancelled drag restores its starting focus",before,value("cover-focus-y"),.001f)
        auditShot("album-cover-dark-drag")
        rule.onNodeWithText("居中").performScrollTo().performClick()
        assertEquals(.5f,value("cover-focus-x"),.001f)
        assertEquals(.5f,value("cover-focus-y"),.001f)
        rule.onNodeWithText("水平位置 · 50%").assertExists()
        rule.onNodeWithText("居中").assertIsNotEnabled()
        auditShot("album-cover-dark-controls")
        rule.onNodeWithText("取消").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("cover-focus-x").fetchSemanticsNodes().isEmpty()}
        assertTrue(writes.isEmpty())
    }

    @Test fun footerAndFailureRemainReadableInBothThemes() {
        for(mode in listOf("LIGHT","DARK")) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-$mode").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithTag("album-restore-album").performScrollTo().performClick();waitTag("album-media-restore-0")
            openCover()
            rule.onNodeWithTag("cover-focus-y").performSemanticsAction(SemanticsActions.SetProgress) {it(.65f)}
            rule.onNodeWithText("取消").performScrollTo()
            rule.onNodeWithText("保存封面").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("取消").assertIsDisplayed().assertIsEnabled()
            auditShot("album-cover-footer-${mode.lowercase()}")
            val count=writes.size
            rule.onNodeWithText("保存封面").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("封面暂时无法保存").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("取消").performScrollTo()
            rule.onNodeWithText("封面暂时无法保存").assertIsDisplayed()
            rule.onNodeWithText("保存封面").assertIsDisplayed().assertIsEnabled()
            assertEquals(.65f,value("cover-focus-y"),.001f)
            auditShot("album-cover-footer-error-${mode.lowercase()}")
            assertEquals(count+1,writes.size)
            rule.onNodeWithText("取消").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("cover-focus-x").fetchSemanticsNodes().isEmpty()}
            assertEquals(count+1,writes.size)
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
        }
    }

}
