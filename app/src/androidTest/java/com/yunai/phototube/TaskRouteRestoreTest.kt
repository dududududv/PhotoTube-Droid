package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TaskRouteRestoreTest : RemoteAppTest() {
    private val writes = AtomicInteger()
    private val details = AtomicInteger()
    @Volatile private var running = false
    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    private fun run(id: String = "run-current") = JSONObject().put("id", id).put("state", if (running) "RUNNING" else "SUCCEEDED")
        .put("scanMode", "INCREMENTAL").put("dryRun", false).put("mediaScope", "VIDEO")
        .put("processedAssets", 2).put("totalAssets", 8).put("cachedAssets", 1).put("providerAttemptCount", 1)
        .put("skippedCount", 0).put("conflictCount", 0).put("batchSize", 8).put("createdAt", "2026-09-16T12:00:00Z")
        .put("error", JSONObject.NULL).put("report", JSONArray())
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet(); return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        "/api/v1/jobs/scan-batches" -> json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
        "/api/v1/jobs/summary" -> json(JSONObject().put("items", JSONArray()))
        "/api/v1/ai-tag-tasks" -> {
            val old = request.requestUrl!!.queryParameter("cursor") != null
            json(JSONObject().put("items", JSONArray().put(JSONObject().put("albumId", "restore-album")
                .put("albumName", if (old) "历史任务相册" else "最新任务相册").put("run", run(if (old) "run-old" else "run-current"))))
                .put("nextCursor", if (old) JSONObject.NULL else "history+/="))
        }
        "/api/v1/tag-scans" -> json(JSONArray().put(run()))
        "/api/v1/tag-scans/run-current", "/api/v1/tag-scans/run-old" -> {
            details.incrementAndGet(); json(run(request.requestUrl!!.encodedPath.substringAfterLast('/')))
        }
        else -> null
    }
    private fun albums() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
    }
    private fun auditCapture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null), "audit-ai-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun landscapeAlbumAiCanSelectScopeReviewConfirmationAndReadHistory() {
        albums(); rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("整理与 AI").performClick()
        rule.onNodeWithText("AI 打标与任务").performClick(); waitTag("album-ai")
        waitTag("ai-run-run-current")
        auditCapture("portrait")
        try {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            waitTag("ai-run-run-current")
            rule.onNodeWithTag("ai-scope-VIDEO").performScrollTo().performClick()
            rule.onNodeWithTag("ai-incremental").performScrollTo().assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("全部重新打标").performScrollTo().performClick()
            rule.onNodeWithText("开始打标").assertIsDisplayed()
            auditCapture("confirmation")
            rule.onNodeWithText("取消").performClick()
            rule.onNodeWithTag("ai-run-run-current").performScrollTo().performClick()
            rule.onNodeWithText("撤销标签").performScrollTo().assertIsDisplayed()
            rule.waitForIdle(); android.os.SystemClock.sleep(450)
            val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(app.getExternalFilesDir(null), "album-ai-landscape.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            rule.onNodeWithText("撤销标签").performClick()
            rule.onNodeWithText("撤销此批次标签？").assertIsDisplayed()
            auditCapture("undo-confirmation")
            rule.onNodeWithText("取消").performClick()
            assertEquals(0, writes.get())
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }
    @Test fun taskHistoryDetailAndCursorSurviveRecreation() {
        albums(); rule.onNodeWithText("任务中心").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("AI 打标 · 最新任务相册").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10000) {rule.onAllNodes(hasText("更多历史") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("更多历史").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("AI 打标 · 历史任务相册").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("查看详情").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("任务详情").fetchSemanticsNodes().isNotEmpty() }
        val before = details.get()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { details.get() > before }
        rule.onNodeWithText("任务详情").assertExists()
        rule.onNodeWithText("历史任务相册").assertExists()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("任务详情").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("AI 打标 · 历史任务相册").assertExists()
        rule.onNodeWithText("上一页").assertIsEnabled().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("AI 打标 · 最新任务相册").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, writes.get())
    }
    @Test fun albumAiExpandedTaskKeepsPollingAfterRecreationWithoutCreatingTask() {
        running = true
        albums(); rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("整理与 AI").performClick()
        rule.onNodeWithText("AI 打标与任务").performClick()
        waitTag("ai-run-run-current")
        rule.onNodeWithTag("ai-run-run-current").performScrollTo().performClick()
        rule.onNodeWithText("暂停").performScrollTo().assertIsDisplayed()
        val before = details.get()
        rule.activityRule.scenario.recreate()
        waitTag("album-ai")
        rule.onNodeWithText("暂停").assertExists()
        rule.waitUntil(12000) { details.get() > before }
        rule.onNodeWithTag("ai-incremental").assertIsNotEnabled()
        assertEquals(0, writes.get())
    }
}
