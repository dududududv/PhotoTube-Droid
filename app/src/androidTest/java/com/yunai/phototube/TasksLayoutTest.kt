package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class TasksLayoutTest : RemoteAppTest() {
    private val title = "AI 打标 · 周末旅行与家庭影像归档 — 山间、海边和城市夜景"
    @Volatile private var failed = false
    @Volatile private var empty = false
    private val longError = "服务暂时不可用，请核对供应商连接与任务状态。".repeat(35)
    @Volatile private var delayed = false
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (failed && request.requestUrl!!.encodedPath == "/api/v1/ai-tag-tasks") {
            return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
                .setBody(org.json.JSONObject().put("code", "UNAVAILABLE").put("message", longError).toString())
        }
        if (empty && request.requestUrl!!.encodedPath == "/api/v1/ai-tag-tasks") {
            return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[],"nextCursor":null}""")
        }
        val body = when (request.requestUrl!!.encodedPath) {
            "/api/v1/jobs/scan-batches" -> """{"items":[],"nextCursor":null}"""
            "/api/v1/tag-scans/layout-task" -> """{"id":"layout-task","state":"RUNNING","scanMode":"INCREMENTAL","dryRun":false,"processedAssets":37,"totalAssets":120,"cachedAssets":9,"providerAttemptCount":28,"skippedCount":0,"conflictCount":0,"batchSize":8,"createdAt":"2026-09-17T00:00:00Z","error":null,"report":[],"mediaScope":"ALL"}"""
            "/api/v1/jobs/summary" -> """{"items":[]}"""
            "/api/v1/ai-tag-tasks" -> """{"items":[{"albumId":"album","albumName":"${title.removePrefix("AI 打标 · ")}","run":{"id":"layout-task","state":"RUNNING","scanMode":"INCREMENTAL","dryRun":false,"processedAssets":37,"totalAssets":120,"cachedAssets":9,"providerAttemptCount":28,"skippedCount":0,"conflictCount":0,"batchSize":8,"createdAt":"2026-09-17T00:00:00Z","error":null,"report":[],"mediaScope":"ALL"}}],"nextCursor":null}"""
            else -> return null
        }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            .apply { if (delayed) setBodyDelay(2, TimeUnit.SECONDS) }
    }
    private fun isIndeterminateProgressBar() = SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
        androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)
    private fun capture(name: String) {
        println("Capturing $name")
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        println("Captured $name")
    }
    @Test fun refreshKeepsTaskRowsStationaryInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("theme-mode-${mode.name}") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithText("任务中心").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(10000) { rule.onAllNodes(isIndeterminateProgressBar()).fetchSemanticsNodes().isEmpty() }
            assertNoPagination()
            capture("tasks-${mode.name.lowercase()}")
            val before = rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
            delayed = true
            rule.onNodeWithContentDescription("刷新").performClick()
            rule.waitUntil(5000) { rule.onAllNodes(isIndeterminateProgressBar()).fetchSemanticsNodes().isNotEmpty() }
            val during = rule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
            assertEquals("Refresh must not push the task card down", before, during, 1f)
            delayed = false
            rule.waitUntil(10000) { rule.onAllNodes(isIndeterminateProgressBar()).fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("查看详情").performClick()
            rule.onNodeWithText("任务详情").assertIsDisplayed()
            capture("tasks-detail-${mode.name.lowercase()}")
            rule.onAllNodesWithContentDescription("返回").onLast().performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText("任务详情").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("task-list").fetchSemanticsNodes().isEmpty() }
        }
    }
    private fun assertNoPagination() {
        listOf("上一页相册扫描", "更多相册扫描", "上一页", "更多历史").forEach {
            rule.onNodeWithText(it, substring = false).assertDoesNotExist()
        }
    }
    @Test fun emptyTasksDoNotShowDisabledHistoryControls() {
        empty = true
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("任务中心").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("暂无任务").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("暂无任务").assertIsDisplayed()
        rule.onNodeWithContentDescription("刷新").assertIsDisplayed().assertIsEnabled()
        assertNoPagination()
        capture("tasks-empty-current")
    }
    @Test fun longFailureRemainsScrollableAndCachedTasksAccessibleInLandscape() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("任务中心").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10000) { rule.onAllNodes(isIndeterminateProgressBar()).fetchSemanticsNodes().isEmpty() }
        failed = true
        rule.onNodeWithContentDescription("刷新").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("列表不完整", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        waitTag("task-list")
        // On a short landscape viewport the error is a lazy off-screen item.
        rule.onNodeWithTag("task-list").performScrollToNode(hasText("列表不完整", substring = true))
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(title))
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithTag("task-list").performScrollToNode(hasText("查看详情"))
        rule.onNodeWithText("查看详情").assertIsDisplayed()
        capture("tasks-landscape-partial")
        assertNoPagination()
        rule.onNodeWithContentDescription("返回").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
    }

}
