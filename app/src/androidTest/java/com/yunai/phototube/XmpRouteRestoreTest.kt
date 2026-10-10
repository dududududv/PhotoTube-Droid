package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class XmpRouteRestoreTest : RemoteAppTest() {
    private fun captureAudit(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null), "audit-xmp-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun auditPreviewCancelAndHistory() {
        exports()
        rule.onNodeWithText("全部媒体库").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("测试媒体库").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("测试媒体库").performClick()
        captureAudit("scope")
        rule.onNodeWithText("完成选择").performClick()
        rule.onNodeWithTag("xmp-preview-normal").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("确认创建导出？").fetchSemanticsNodes().isNotEmpty() }
        captureAudit("confirm")
        rule.onNodeWithText("取消", substring = false).performClick()
        rule.onNodeWithTag("xmp-history").performScrollToNode(hasText("正在导出"))
        rule.onNodeWithText("正在导出").performClick()
        rule.waitUntil(10000) { reads.get() > 0 }
        captureAudit("detail")
        assertEquals(listOf("/api/v1/xmp-exports/preview"), writes.toList())
    }
    private val writes = CopyOnWriteArrayList<String>()
    private val reads = AtomicInteger()
    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    private fun run() = JSONObject().put("id", "run-one").put("state", "RUNNING").put("libraryIds", JSONArray().put("test-library"))
        .put("includePrivate", false).put("matchedCount", 7).put("succeededCount", 2).put("failedCount", 0).put("attemptCount", 1)
        .put("snapshotId", "snapshot-one").put("snapshotAt", "2026-09-17T00:00:00Z").put("logicalPath", "xmp/snapshot-one")
        .put("errorCode", JSONObject.NULL).put("createdAt", "2026-09-17T00:00:00Z").put("finishedAt", JSONObject.NULL)
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        "/api/v1/auth/session" -> json("""{"authenticated":true,"passwordSet":true,"user":{"username":"fixture"},"privateAccessUnlocked":false,"originalDeleteEnabled":false}""")
        "/api/v1/health" -> json("""{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":true}}""")
        "/api/v1/system/status" -> json("""{"generatedAt":"2026-09-17T00:00:00Z","alerts":[]}""")
        "/api/v1/xmp-exports" -> json(JSONObject().put("items", JSONArray().put(run())).put("nextCursor", JSONObject.NULL))
        "/api/v1/xmp-exports/run-one" -> { reads.incrementAndGet(); json(run()) }
        else -> null
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath; writes += path
        return if (request.method == "POST" && path == "/api/v1/xmp-exports/preview") json(JSONObject()
            .put("resolvedLibraryIds", JSONArray().put("test-library")).put("includePrivate", false)
            .put("estimatedAssetCount", 10).put("estimatedAt", "2026-09-17T00:00:00Z"))
        else MockResponse().setResponseCode(405)
    }
    private fun exports() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("服务状态与 AI 配置").performScrollTo().performClick()
        rule.onNodeWithText("XMP 导出与历史").performScrollTo().performClick()
        waitTag("xmp-preview-normal")
        rule.onNodeWithTag("xmp-history").performScrollToNode(hasText("正在导出"))
        rule.waitUntil(10000) { rule.onAllNodesWithText("正在导出").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("xmp-history").performScrollToIndex(0)
    }
    @Test fun runningDetailRestoresAndContinuesReadOnlyPolling() {
        exports(); rule.onNodeWithTag("xmp-history").performScrollToNode(hasText("正在导出"))
        rule.onNodeWithText("正在导出").performClick()
        rule.waitUntil(10000) { reads.get() > 0 }
        val before = reads.get()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("导出详情").assertExists()
        rule.waitUntil(10000) { reads.get() > before }
        rule.onNodeWithText("xmp/snapshot-one").assertExists()
        assertTrue(writes.isEmpty())
    }
    @Test fun librarySelectionRestoresButConfirmationDoesNotCreateOnRotation() {
        exports(); rule.onNodeWithText("全部媒体库").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("测试媒体库").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("测试媒体库").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("已选 1/100 个媒体库").assertExists()
        rule.onNodeWithText("完成选择").performClick()
        rule.onNodeWithText("已选 1 个媒体库").assertExists()
        rule.onNodeWithTag("xmp-preview-normal").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("确认创建导出？").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        waitTag("xmp-preview-normal")
        rule.onNodeWithText("确认创建导出？").assertDoesNotExist()
        rule.onNodeWithText("已选 1 个媒体库").assertExists()
        assertEquals(listOf("/api/v1/xmp-exports/preview"), writes.toList())
    }

    @Test fun landscapeHistoryCanOpenWithoutCreatingExport() {
        exports()
        rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        try {
            waitTag("xmp-history")
            assertTrue(rule.onNodeWithTag("xmp-history").fetchSemanticsNode().boundsInRoot.height > 0)
            rule.onNodeWithTag("xmp-history").performScrollToNode(hasText("正在导出"))
            rule.onNodeWithText("正在导出").assertIsDisplayed().performClick()
            rule.waitUntil(10000) { reads.get() > 0 }
            rule.onNodeWithText("导出详情").assertExists()
            assertTrue(writes.isEmpty())
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }
}
