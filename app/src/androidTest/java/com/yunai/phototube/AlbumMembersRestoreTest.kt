package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class AlbumMembersRestoreTest : RemoteAppTest() {
    override val photographicFixtures = true
    private val writes = CopyOnWriteArrayList<Pair<String, Set<String>>>()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("/api/v1/albums/restore-album/assets", request.requestUrl!!.encodedPath)
        val body = JSONObject(request.body.readUtf8()).getJSONArray("assetIds")
        val ids = (0 until body.length()).map(body::getString).toSet()
        writes += request.method!! to ids
        val result = if (writes.size == 1) JSONObject().put("succeeded", JSONArray().put("restore-0"))
            .put("failed", JSONArray().put(JSONObject().put("target", "restore-99").put("code", "OFFLINE").put("message", "目录离线")))
        else JSONObject().put("succeeded", JSONArray(ids.toList())).put("failed", JSONArray())
        return MockResponse().setHeader("Content-Type", "application/json").setBody(result.toString())
    }
    private fun exercise(remove: Boolean) {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithText(if (remove) "选择" else "添加").performClick()
        waitTag("member-select-restore-0")
        val first=rule.onNodeWithTag("member-select-restore-0").fetchSemanticsNode().boundsInRoot
        val second=rule.onNodeWithTag("member-select-restore-1").fetchSemanticsNode().boundsInRoot
        assertEquals(1.5f,first.width/first.height,.02f)
        assertEquals(first.top,second.top,1f)
        assertEquals(first.bottom,second.bottom,1f)
        rule.onNodeWithTag("member-select-restore-0").performClick().assertIsSelected()
        assertEquals(first,rule.onNodeWithTag("member-select-restore-0").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("album-member-grid").performScrollToNode(hasTestTag("member-select-restore-99"))
        rule.onNodeWithTag("member-select-restore-99").performClick()
        rule.onNodeWithTag("album-member-grid").performScrollToNode(hasTestTag("member-select-restore-100"))
        waitTag("member-select-restore-100"); rule.onNodeWithTag("member-select-restore-100").performClick()
        val label = if (remove) "移除" else "添加"
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("$label 3 项").assertIsEnabled()
        rule.onNodeWithTag("member-select-restore-100").assertIsSelected()
        assertTrue(writes.isEmpty())
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(java.io.File(app.getExternalFilesDir(null), "member-picker-${if(remove) "remove" else "add"}.png"))
        rule.onNodeWithText("$label 3 项").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 项未返回结果，请刷新后核对", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("$label 2 项").assertIsEnabled()
        rule.onNodeWithText("目录离线", substring = true).assertExists()
        assertEquals(setOf("restore-0", "restore-99", "restore-100"), writes.single().second)
        assertEquals(if (remove) "DELETE" else "POST", writes.single().first)
        rule.onNodeWithContentDescription("刷新").performClick()
        rule.onNodeWithText("$label 2 项").assertIsEnabled()
        assertEquals(1, writes.size)
        rule.onNodeWithText("$label 2 项").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("album-member-grid").fetchSemanticsNodes().isEmpty() }
        assertEquals(2, writes.size)
        assertEquals(setOf("restore-99", "restore-100"), writes.last().second)
    }
    @Test fun addingKeepsCrossPageSelectionAndUnreportedResults() = exercise(false)
    @Test fun removingKeepsCrossPageSelectionAndUnreportedResults() = exercise(true)
}
