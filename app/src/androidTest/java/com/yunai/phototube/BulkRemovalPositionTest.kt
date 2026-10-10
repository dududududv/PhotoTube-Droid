package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

/** Removes one generated record from an in-process loopback fixture only. */
class BulkRemovalPositionTest : RemoteAppTest() {
    @Volatile private var removed=false
    private val writes=java.util.concurrent.atomic.AtomicInteger()
    override fun readFixture(request:RecordedRequest):MockResponse? {
        val url=request.requestUrl!!
        val body=when(url.encodedPath) {
            "/api/v1/assets" -> {
                val start=url.queryParameter("cursor")?.toInt() ?: 0
                val ids=(start until 220).filter {it!=120 || !removed}.take(100)
                val rows=JSONArray(ids.map {id -> JSONObject().put("id","restore-$id").put("kind","PHOTO").put("state","BROWSABLE")
                    .put("takenAt","2026-09-01T00:00:00Z").put("fileName","synthetic-$id.jpg").put("favorite",false).put("width",48).put("height",32)})
                JSONObject().put("items",rows).put("nextCursor",ids.lastOrNull()?.plus(1)?.takeIf {it<220} ?: JSONObject.NULL)
            }
            "/api/v1/assets/timeline-summary" -> JSONObject().put("granularity","MONTH").put("groups",JSONArray().put(
                JSONObject().put("key","2026-09").put("startsAt","2026-09-01T00:00:00Z").put("count",if(removed) 219 else 220)))
            else -> return null
        }
        return MockResponse().setHeader("Content-Type","application/json").setBody(body.toString())
    }
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        assertEquals("/api/v1/assets/trash",request.requestUrl!!.encodedPath)
        assertEquals(listOf("restore-120"),JSONObject(request.body.readUtf8()).getJSONArray("assetIds").let {a -> (0 until a.length()).map {a.getString(it)}})
        removed=true;writes.incrementAndGet()
        return MockResponse().setHeader("Content-Type","application/json").setBody("""{"succeeded":["restore-120"],"failed":[]}""")
    }
    @Test fun removingLaterPhotoReturnsNearSurvivingNeighbor() {
        rule.onNodeWithTag("remote-photo-wall").performScrollToNode(hasTestTag("asset-restore-120"))
        rule.onNodeWithTag("asset-restore-120").performTouchInput {longClick()}
        waitTag("bulk-asset-restore-120")
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("移入回收站",substring=false).performClick()
        rule.onNodeWithText("确认移入回收站",substring=false).performClick()
        rule.waitUntil(10000) {writes.get()==1 && !wall.busy && rule.onAllNodesWithTag("bulk-result").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("asset-restore-120").assertDoesNotExist()
        rule.onNodeWithTag("asset-restore-121").assertIsDisplayed()
        rule.onNodeWithTag("asset-restore-0").assertDoesNotExist()
        assertEquals(219L,wall.groups.sumOf {it.count})
        assertEquals(1,writes.get())
    }
}
