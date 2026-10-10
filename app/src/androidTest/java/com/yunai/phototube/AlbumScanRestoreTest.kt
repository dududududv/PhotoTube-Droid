package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AlbumScanRestoreTest : RemoteAppTest() {
    override val albumKindFixture = "PATH_SYNC"
    private val starts = AtomicInteger()
    @Volatile private var submittedMode = ""
    private val reads = AtomicInteger()
    @Volatile private var delayed = false
    private var historyCount = 1
    private val path = "/api/v1/albums/restore-album/syncs"
    private fun run() = JSONObject().put("id", "scan-one").put("state", "RUNNING")
        .put("createdAt", "2026-09-17T00:00:00Z").put("succeededPaths", 0).put("failedPaths", 0).put("offlinePaths", 0)
    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        path -> json(JSONObject().put("items", JSONArray().apply { repeat(if(starts.get()==0 && historyCount==1) 0 else historyCount) { put(run().put("id", if (it == 0) "scan-one" else "scan-$it")) } }).put("nextCursor", JSONObject.NULL))
        "$path/scan-one" -> {
            reads.incrementAndGet()
            json(JSONObject().put("run", run()).put("pathResults", JSONArray().put(JSONObject()
                .put("relativePath", "旅行").put("state", "RUNNING").put("discoveredCount", 12).put("addedCount", 3))))
        }
        else -> null
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("POST", request.method); assertEquals(path, request.requestUrl!!.encodedPath)
        submittedMode=JSONObject(request.body.readUtf8()).getString("mode")
        starts.incrementAndGet()
        return json(run()).setResponseCode(202).apply { if (delayed) setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS) }
    }
    private fun album() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
    }
    private fun menu(label: String) {
        if(rule.onAllNodesWithText("整理与 AI").fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithContentDescription("相册选项").performClick()
            rule.onNodeWithText("整理与 AI").performClick()
        }
        rule.onNodeWithText(label).performScrollTo().performClick()
    }
    @Test fun startedScanAndHistoricalDetailRestoreWithoutStartingAgain() {
        album(); menu("手动扫描文件夹")
        rule.onNodeWithText("强制重新扫描").performClick()
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("旅行 · 扫描中").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("FULL",submittedMode)
        val before = reads.get()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("扫描文件夹").assertExists()
        rule.onNodeWithText("开始扫描").assertDoesNotExist()
        rule.waitUntil(10000) { reads.get() > before }
        assertEquals(1, starts.get())
        rule.onNodeWithText("关闭", substring = false).performClick()
        menu("最近一次扫描")
        rule.waitUntil(10000) { rule.onAllNodesWithText("扫描中", substring = false).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("扫描中", substring = false).performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("扫描文件夹").assertExists()
        rule.onNodeWithText("旅行 · 扫描中").assertExists()
        rule.onNodeWithText("开始扫描").assertDoesNotExist()
        assertEquals(1, starts.get())
    }
    @Test fun latestScanSurvivesRecreationWithoutOldRows() {
        historyCount = 60
        album(); menu("最近一次扫描")
        waitTag("scan-history-scan-one")
        rule.onNodeWithTag("scan-history-scan-40").assertDoesNotExist()
        rule.activityRule.scenario.recreate()
        waitTag("scan-history-scan-one")
        rule.onNodeWithTag("scan-history-scan-40").assertDoesNotExist()
        rule.onAllNodesWithContentDescription("返回", substring = false).onLast().performClick()
        waitTag("album-detail")
        rule.waitUntil(10000) { rule.onAllNodesWithTag("scan-history-list").fetchSemanticsNodes().isEmpty() }
        assertEquals(0, starts.get())
    }

    @Test fun activeLatestScanPreventsAnotherSubmission() {
        historyCount=2
        album();menu("手动扫描文件夹")
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(10000) {reads.get()>0}
        rule.onNodeWithText("开始扫描").assertDoesNotExist()
        assertEquals(0,starts.get())
    }

    @Test fun interruptedStartRequiresHistoryCheckInsteadOfResubmission() {
        delayed = true
        album(); menu("手动扫描文件夹")
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(10000) { starts.get() == 1 }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("启动结果未确认，请关闭此窗口并查看最近一次扫描，避免重复启动。").assertExists()
        rule.onNodeWithText("开始扫描").assertIsNotEnabled()
        assertEquals(1, starts.get())
    }
}
