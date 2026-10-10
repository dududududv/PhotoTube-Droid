package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SmartAlbumDraftRestoreTest : RemoteAppTest() {
    override val albumKindFixture = "SMART"
    private val writes = CopyOnWriteArrayList<Pair<String, String>>()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes += request.requestUrl!!.encodedPath to request.body.readUtf8()
        return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("""{"error":{"code":"UNAVAILABLE","message":"测试服务暂不可用"}}""")
    }
    private fun openRules() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick()
        waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("智能相册规则").performClick()
        waitTag("smart-keyword")
    }
    @Test fun auditEditorWithoutSaving() {
        openRules(); auditShot("smart-top")
        rule.onNodeWithText("视频", substring=false).performClick()
        rule.onNodeWithText("未收藏").performClick()
        rule.onNodeWithText("所有已入库目录").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil(10000) {rule.onAllNodesWithText("测试媒体库").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("测试媒体库").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("旅行",substring=false).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("旅行",substring=false).performClick()
        auditShot("smart-directory")
        rule.onNodeWithText("选择此目录").performClick()
        rule.onNodeWithText("开始日期：不限").performScrollTo().performClick()
        auditShot("smart-date")
        rule.onNodeWithText("取消",substring=false).performClick()
        rule.onNodeWithText("全部包含 · 不限").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("风景").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("风景").performClick()
        rule.onNodeWithText("完成选择").performScrollTo().performClick()
        rule.onNodeWithText("保存规则").performScrollTo()
        auditShot("smart-bottom")
        rule.onAllNodesWithContentDescription("返回",substring=false).onLast().performClick()
        assertTrue(writes.isEmpty())
    }
    @Test fun nestedTagSelectionSurvivesRotationButCancellingDoesNotApplyIt() {
        openRules()
        rule.onNodeWithText("全部包含 · 不限").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("风景").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("风景").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("已选 1/100").assertExists()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("选择标签").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("全部包含 · 不限").assertExists().performScrollTo().performClick()
        rule.onNodeWithText("已选 0/100").assertExists()
        assertEquals(0, writes.size)
    }
    @Test fun ruleDraftAndDirectoryRouteSurviveRecreationAndFailedSave() {
        openRules()
        rule.onNodeWithText("视频", substring = false).performClick()
        rule.onNodeWithText("未收藏").performClick()
        rule.onNodeWithTag("smart-keyword").performScrollTo().performTextReplacement("旅行视频")
        rule.onNodeWithText("所有已入库目录").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil(10000) { rule.onAllNodesWithText("测试媒体库").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("测试媒体库").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("旅行", substring = false).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("旅行", substring = false).performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("旅行", substring = false).assertExists()
        rule.onNodeWithText("选择此目录").performClick()
        rule.onNodeWithTag("smart-keyword").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("旅行视频")))
        assertEquals(0, writes.size)
        rule.onNodeWithText("保存规则").performScrollTo().performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        assertEquals("/api/v1/albums/restore-album", writes.single().first)
        val filter = JSONObject(writes.single().second).getJSONObject("filter")
        assertEquals("VIDEO", filter.getString("kind"))
        assertFalse(filter.getBoolean("favorite"))
        assertEquals("旅行视频", filter.getString("keyword"))
        assertEquals("test-library", filter.getString("libraryId"))
        assertEquals("旅行", filter.getString("folderPath"))
        rule.waitUntil(10000) { rule.onAllNodesWithText("保存规则").fetchSemanticsNodes().any { it.config.contains(SemanticsProperties.Disabled).not() } }
        rule.activityRule.scenario.recreate()
        waitTag("smart-keyword")
        rule.onNodeWithTag("smart-keyword").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("旅行视频")))
        assertEquals("Recreation must not retry a write", 1, writes.size)
    }
}
