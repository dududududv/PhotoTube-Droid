package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ScanBatchesTest : RemoteAppTest() {
    @Volatile private var dualPages = false
    private val reads = CopyOnWriteArrayList<RecordedRequest>()
    private fun json(body: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun stage(name: String, kind: String, open: Boolean, failed: Int = 0) = JSONObject()
        .put("stage", name).put("kind", kind).put("pending", 0).put("paused", 0).put("running", 0)
        .put("succeeded", 0).put("failed", failed).put("cancelled", 0).put("upstreamOpen", open)
        .put("total", if (open) JSONObject.NULL else 1).put("queuePaused", false).put("error", JSONObject.NULL)
    override fun readFixture(request: RecordedRequest): MockResponse? {
        reads += request
        val path = request.requestUrl!!.encodedPath
        return when (path) {
            "/api/v1/jobs/scan-batches" -> {
                val second = request.requestUrl!!.queryParameter("cursor") == "scan-next"
                val batch = JSONObject().put("id", if (second) "batch-2" else "batch-1").put("albumId", "restore-album")
                    .put("albumName", if (second) "第二个相册" else "扫描相册").put("createdAt", "2026-10-08T00:00:00Z")
                    .put("finishedAt", JSONObject.NULL).put("state", "RUNNING").put("stages", JSONArray()
                        .put(stage("SCAN", "ALBUM_PATH_SYNC", false))
                        .put(stage("METADATA", "EXTRACT_METADATA", true, 1))
                        .put(stage("THUMBNAIL", "GENERATE_THUMBNAIL", true)))
                json(JSONObject().put("items", JSONArray().put(batch)).put("nextCursor", if (second) JSONObject.NULL else "scan-next"))
            }
            "/api/v1/jobs/summary" -> json("""{"items":[{"kind":"EXTRACT_METADATA","pending":0,"paused":0,"running":0,"succeeded":0,"failed":1,"cancelled":0,"discovered":1,"total":null,"lastFinishedAt":null}]}""")
            "/api/v1/ai-tag-tasks" -> if (!dualPages) json("""{"items":[],"nextCursor":null}""") else json("""{"items":[],"nextCursor":"ai-next"}""")
            "/api/v1/jobs" -> json("""{"items":[{"id":"failed-child","state":"FAILED","createdAt":"2026-10-08T00:00:00Z","attempts":1,"maxAttempts":3,"error":"子任务错误"}],"nextCursor":null}""")
            else -> null
        }
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse = error("Task center must remain read-only")
    private fun open() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithText("任务中心").performClick(); waitTag("task-list")
        rule.waitUntil(10000) { rule.onAllNodesWithText("相册扫描 · 扫描相册").fetchSemanticsNodes().isNotEmpty() }
    }
    /** Run on the mock AVD with system font_scale=2.0. No real task is started. */
    @Test fun landscapeLargeTextKeepsTasksAndBothHistoriesReachable() {
        dualPages = true
        org.junit.Assume.assumeTrue("Requires mock AVD system font_scale=2.0", rule.activity.resources.configuration.fontScale >= 1.9f)
        open()
        try {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            waitTag("task-list")
            rule.waitUntil(10000) {reads.any {it.requestUrl!!.encodedPath == "/api/v1/ai-tag-tasks"}}
            rule.waitForIdle()
            rule.onNodeWithTag("task-list").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(100f))
            rule.onNodeWithTag("task-list").performScrollToNode(hasText("读取媒体信息失败明细"))
            rule.onNodeWithText("读取媒体信息失败明细").assertIsDisplayed()
            for (label in listOf("更多相册扫描", "更多历史")) {
                rule.onNodeWithTag("task-list").performScrollToNode(hasText(label))
                rule.onNodeWithText(label).assertIsDisplayed()
            }
            auditShot("tasks-dual-pagination-large-landscape")
            rule.onNodeWithTag("task-list").performScrollToNode(hasText("更多相册扫描"))
            rule.waitUntil(10000) {rule.onAllNodes(hasText("更多相册扫描") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("更多相册扫描").performClick()
            rule.waitUntil(10000) {reads.any {it.requestUrl!!.queryParameter("cursor") == "scan-next"}}
            rule.onNodeWithTag("task-list").performScrollToNode(hasText("相册扫描 · 第二个相册"))
            rule.onNodeWithText("相册扫描 · 第二个相册").assertIsDisplayed()
            assertTrue(reads.all { it.method == "GET" })
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }
    @Test fun stagesStayTogetherAndFailureDetailIsBoundToBatch() {
        open()
        rule.onAllNodesWithText("读取媒体信息", substring = false).assertCountEquals(1)
        rule.onNodeWithText("0 / 1", substring = false).assertDoesNotExist()
        rule.onNodeWithTag("task-list").performScrollToNode(hasText("等待扫描或照片信息处理，数量尚未确定"))
        rule.onNodeWithText("等待扫描或照片信息处理，数量尚未确定").assertIsDisplayed()
        rule.onNodeWithTag("task-list").performScrollToNode(hasText("读取媒体信息失败明细"))
        rule.onNodeWithText("读取媒体信息失败明细").performClick(); waitTag("job-history")
        rule.waitUntil(10000) { rule.onAllNodesWithText("子任务错误").fetchSemanticsNodes().isNotEmpty() }
        val request = reads.last { it.requestUrl!!.encodedPath == "/api/v1/jobs" }.requestUrl!!
        assertEquals("batch-1", request.queryParameter("parentJobId"))
        assertEquals("EXTRACT_METADATA", request.queryParameter("kind"))
        assertEquals("FAILED", request.queryParameter("state"))
        rule.activityRule.scenario.recreate(); waitTag("job-history")
        rule.onNodeWithText("仅显示本次相册扫描的失败子任务。").assertExists()
    }
    @Test fun scanPaginationRestoresAndContinuesPollingActivePage() {
        open()
        rule.onNodeWithText("更多相册扫描").assertIsDisplayed().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("相册扫描 · 第二个相册").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate(); waitTag("task-list")
        rule.onNodeWithText("相册扫描 · 第二个相册").assertExists()
        rule.onAllNodesWithText("列表不完整", substring = true).assertCountEquals(0)
        val before = reads.count { it.requestUrl!!.queryParameter("cursor") == "scan-next" }
        rule.mainClock.advanceTimeBy(3500)
        rule.waitUntil(10000) { reads.count { it.requestUrl!!.queryParameter("cursor") == "scan-next" } > before }
        rule.onNodeWithText("上一页相册扫描").assertIsDisplayed().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("相册扫描 · 扫描相册").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(reads.all { it.method == "GET" })
    }
}
