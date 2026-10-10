package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class JobHistoryRestoreTest : RemoteAppTest() {
    private var withAsset=false
    private val assetId="00000000-0000-0000-0000-000000000007"
    private val cursors = CopyOnWriteArrayList<String>()
    private fun json(body: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        "/api/v1/assets/$assetId" -> json(JSONObject().put("id",assetId).put("fileName","合成失败文件.jpg").put("state","OFFLINE").put("relativePath","fixture/offline.jpg").put("albumIds",JSONArray()))
        "/api/v1/jobs/summary" -> json("""{"items":[{"kind":"DERIVATIVE_PRUNE","pending":0,"paused":0,"running":0,"succeeded":0,"failed":140,"cancelled":0,"discovered":140,"total":140,"lastFinishedAt":null}]}""")
        "/api/v1/jobs/scan-batches" -> json("""{"items":[],"nextCursor":null}""")
        "/api/v1/ai-tag-tasks" -> json("""{"items":[],"nextCursor":null}""")
        "/api/v1/jobs" -> {
            assertEquals("DERIVATIVE_PRUNE", request.requestUrl!!.queryParameter("kind"))
            val cursor = request.requestUrl!!.queryParameter("cursor")
            cursors += cursor ?: "first"
            val items = JSONArray()
            for (i in if (cursor == null) 0..99 else 100..139) {
                items.put(JSONObject().put("id", "job-$i").put("state", "FAILED")
                    .put("createdAt", "2026-09-17T00:00:00Z").put("attempts", 3).put("maxAttempts", 3).put("error", if(withAsset && i==0) "合成离线原因（assetId=$assetId）" else "任务记录 $i"))
            }
            json(JSONObject().put("items", items).put("nextCursor", if (cursor == null) "next+/=" else JSONObject.NULL))
        }
        else -> null
    }
    @Test fun ordinaryJobHistoryKeepsLoadedCursorWindowAndPosition() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("任务中心").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("派生文件清理").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("查看详情").performClick()
        waitTag("job-history")
        rule.onNodeWithTag("job-history").performScrollToNode(hasText("任务记录 90"))
        rule.waitUntil(10000) { cursors.contains("next+/=") }
        rule.onNodeWithTag("job-history").performScrollToNode(hasText("任务记录 130"))
        rule.onNodeWithText("任务记录 130").assertIsDisplayed()
        val before = cursors.toList()
        rule.activityRule.scenario.recreate()
        waitTag("job-history")
        rule.onNodeWithText("任务记录 130").assertIsDisplayed()
        assertEquals("Recreation should reuse the cursor window", before, cursors.toList())
        rule.onNodeWithTag("job-history").performScrollToNode(hasText("任务记录 131"))
        rule.onNodeWithText("任务记录 131").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("job-history").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("查看详情").performClick()
        waitTag("job-history")
        rule.onNodeWithText("任务记录 0").assertIsDisplayed()
        assertEquals(2, cursors.count { it == "first" })
    }
    @Test fun failureFileRestoresWithoutOpeningOfflineOriginal() {
        withAsset=true
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("任务中心").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("查看详情").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("查看详情").performClick();waitTag("job-history")
        rule.onNodeWithText("查看失败文件").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("合成失败文件.jpg").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("打开原文件").performScrollTo().assertIsNotEnabled()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("失败文件",substring=false).assertExists()
        rule.waitUntil(10000) {rule.onAllNodesWithText("合成失败文件.jpg").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("打开原文件").performScrollTo().assertIsNotEnabled()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("失败文件",substring=false).fetchSemanticsNodes().isEmpty()}
        waitTag("job-history")
        rule.onNodeWithText("合成离线原因").assertExists()
    }

}
