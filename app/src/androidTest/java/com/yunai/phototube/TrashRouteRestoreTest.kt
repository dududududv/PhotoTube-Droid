package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class TrashRouteRestoreTest : RemoteAppTest() {
    private val restored = AtomicBoolean(false)
    private val writes = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/trash") return null
        val offset = request.requestUrl!!.queryParameter("cursor")?.toInt() ?: 0
        val ids = (0 until 130).filter { it != 0 || !restored.get() }
        val rows = ids.drop(offset).take(100).map { i -> JSONObject().put("id", "trash-$i")
            .put("kind", "PHOTO").put("state", "TRASHED").put("fileName", "trash-$i.jpg")
            .put("favorite", false).put("contentHash", JSONObject.NULL).put("takenAt", "2026-09-01T00:00:00Z")
            .put("trashedAt", "2026-09-16T12:00:00Z").put("retentionDays", 30) }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(JSONObject()
            .put("items", JSONArray(rows)).put("nextCursor", if (offset + 100 < ids.size) "100" else JSONObject.NULL).toString())
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath
        writes += path to JSONObject(request.body.readUtf8())
        if (request.method != "POST" || path != "/api/v1/assets/restore") return MockResponse().setResponseCode(405)
        restored.set(true)
        return MockResponse().setHeader("Content-Type", "application/json").setBody(JSONObject()
            .put("succeeded", JSONArray().put("trash-0"))
            .put("failed", JSONArray().put(JSONObject().put("target", "trash-129").put("code", "OFFLINE").put("message", "目录离线"))).toString())
    }
    @Test fun auditAllTrashConfirmationWithoutWriting() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithText("回收站").performClick(); waitTag("trash-trash-0")
        rule.onNodeWithText("清理全部回收站记录…").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("本次已收集 130 项").fetchSemanticsNodes().isNotEmpty() }
        captureAudit("trash-all-ready")
        rule.onNodeWithText("清除这 130 项记录").performScrollTo().performClick()
        rule.onNodeWithText("清除 130 项记录？").assertIsDisplayed()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        captureAudit("repair-trash-all-confirm")
        rule.onNodeWithText("取消", substring = false).performClick()
        assertTrue(writes.isEmpty())
    }
    private fun captureAudit(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null), "audit-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun crossPageSelectionAndPartialFailureSurviveRecreationWithoutPurge() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithText("回收站").performClick(); waitTag("trash-trash-0")
        rule.onNodeWithTag("trash-trash-0").performClick()
        // Move through the cursor boundary so the second selection is on another page.
        rule.onNodeWithTag("trash-list").performScrollToIndex(95)
        rule.waitUntil(10000) {
            runCatching { rule.onNodeWithTag("trash-list").performScrollToIndex(129); true }.getOrDefault(false)
        }
        waitTag("trash-trash-129"); rule.onNodeWithTag("trash-trash-129").performClick()
        rule.onNodeWithText("清除记录", substring = false).performClick()
        rule.onNodeWithText("清除 2 项记录？").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("清除 2 项记录？").assertDoesNotExist()
        rule.onNodeWithText("恢复 2 项").assertIsEnabled()
        rule.onNodeWithTag("trash-trash-129").assertIsSelected()
        assertEquals(0, writes.size)
        rule.onNodeWithText("恢复 2 项").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 项已恢复").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, writes.size)
        val ids = writes.single().second.getJSONArray("assetIds")
        assertEquals(setOf("trash-0", "trash-129"), (0 until ids.length()).map(ids::getString).toSet())
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("1 项已恢复").assertExists()
        rule.onNodeWithText("目录离线（OFFLINE）").assertExists()
        rule.onNodeWithText("恢复 1 项").assertIsEnabled()
        assertEquals(1, writes.size)
    }

    @Test fun landscapeCanSelectAndCancelPurgeWithoutWriting() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithText("回收站").performClick(); waitTag("trash-trash-0")
        rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        try {
            waitTag("trash-list")
            assertTrue("Trash needs a visible scrolling viewport", rule.onNodeWithTag("trash-list").fetchSemanticsNode().boundsInRoot.height > 0)
            rule.onNodeWithTag("trash-list").performScrollToNode(hasTestTag("trash-trash-0"))
            rule.onNodeWithTag("trash-trash-0").assertIsDisplayed().performClick()
            rule.onNodeWithText("恢复 1 项").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("清除记录", substring = false).assertIsDisplayed().performClick()
            rule.onNodeWithText("清除 1 项记录？").assertIsDisplayed()
            rule.onNodeWithText("取消", substring = false).performClick()
            rule.onNodeWithTag("trash-trash-0").assertIsSelected()
            assertEquals(0, writes.size)
            rule.waitForIdle()
            android.os.SystemClock.sleep(450)
            val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(app.getExternalFilesDir(null), "trash-landscape.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }
}
