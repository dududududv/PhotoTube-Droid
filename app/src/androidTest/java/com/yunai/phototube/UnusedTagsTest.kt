package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class UnusedTagsTest : RemoteAppTest() {
    private val writes = CopyOnWriteArrayList<JSONObject>()
    private val updated = "2026-10-08T00:00:00Z"
    private var reject = false
    @Volatile private var previewFailure = false
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/tags/unused") return null
        if (previewFailure) return MockResponse().setResponseCode(503).setHeader("Content-Type","application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"合成候选暂不可用，请重试。"}""")
        val second = request.requestUrl!!.queryParameter("cursor") == "next-unused"
        val id = if (second) 12 else 11
        return MockResponse().setHeader("Content-Type", "application/json").setBody(JSONObject()
            .put("items", JSONArray().put(JSONObject().put("id", id).put("name", "候选$id").put("updatedAt", updated).put("assetCount", 0)))
            .put("nextCursor", if (second) JSONObject.NULL else "next-unused").put("total", 2).toString())
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("POST", request.method)
        assertEquals("/api/v1/tags/cleanup-unused", request.requestUrl!!.encodedPath)
        writes += JSONObject(request.body.readUtf8())
        return if (reject) MockResponse().setResponseCode(503).setBody("""{"code":"UNAVAILABLE","message":"清理未确认"}""")
        else MockResponse().setHeader("Content-Type", "application/json").setBody("""{"deletedIds":[11],"skippedIds":[12]}""")
    }
    private fun open() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithText("标签库").performClick(); waitTag("tag-list")
        rule.onNodeWithText("清理未使用标签").performScrollTo().performClick(); waitTag("unused-tag-11")
    }
    @Test fun previewFailureHasOneRecoveryAndSelectedActionsMatchBothThemes() {
        for (mode in listOf("LIGHT","DARK")) {
            previewFailure = true
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-$mode").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithText("标签库").performClick(); waitTag("tag-list")
            rule.onNodeWithText("清理未使用标签").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成候选暂不可用，请重试。").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("重试当前页").assertIsDisplayed().assertIsEnabled()
            listOf("刷新候选","取消选择","清理已选 0 个").forEach {rule.onNodeWithText(it).assertDoesNotExist()}
            captureAudit("unused-error-${mode.lowercase()}")
            previewFailure = false
            rule.onNodeWithText("重试当前页").performClick(); waitTag("unused-tag-11")
            rule.onNodeWithText("重试当前页").assertDoesNotExist()
            rule.onNodeWithText("刷新候选").assertIsDisplayed()
            rule.onNodeWithTag("unused-tag-11").performScrollTo().performClick()
            rule.onNodeWithText("下一页").performScrollTo().performClick(); waitTag("unused-tag-12")
            rule.onNodeWithTag("unused-tag-12").performScrollTo().performClick()
            captureAudit("unused-selection-${mode.lowercase()}")
            rule.onNodeWithText("清理已选 2 个").performScrollTo().performClick()
            rule.onNodeWithText("清理 2 个标签？").assertIsDisplayed()
            captureAudit("unused-confirm-${mode.lowercase()}")
            rule.onNodeWithText("取消",substring=false).performClick()
            rule.onNodeWithTag("unused-tag-12").assertIsOn()
            assertTrue(writes.isEmpty())
            rule.onAllNodesWithContentDescription("返回",substring=false).onLast().performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("unused-tags-list").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithContentDescription("返回",substring=false).performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("tag-list").fetchSemanticsNodes().isEmpty()}
        }
    }
    @Test fun auditCleanupConfirmationWithoutWriting() {
        open()
        rule.onNodeWithTag("unused-tag-11").performScrollTo().performClick()
        rule.onNodeWithText("下一页").performScrollTo().performClick(); waitTag("unused-tag-12")
        rule.onNodeWithTag("unused-tag-12").performScrollTo().performClick()
        captureAudit("unused-selected")
        rule.onNodeWithText("清理已选 2 个").performScrollTo().performClick()
        rule.onNodeWithText("清理 2 个标签？").assertIsDisplayed()
        captureAudit("unused-confirm")
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
    @Test fun confirmsExactCrossPageSnapshotsAndShowsSkippedTags() {
        open()
        rule.onNodeWithTag("unused-tag-11").performScrollTo().performClick()
        rule.onNodeWithText("下一页").performScrollTo().performClick(); waitTag("unused-tag-12")
        rule.onNodeWithTag("unused-tag-12").performScrollTo().performClick()
        rule.activityRule.scenario.recreate(); waitTag("unused-tag-12")
        rule.onNodeWithText("清理已选 2 个").performScrollTo().performClick()
        assertTrue(writes.isEmpty())
        rule.onNodeWithText("取消", substring = false).performClick()
        assertTrue(writes.isEmpty())
        rule.onNodeWithText("清理已选 2 个").performClick()
        rule.onNodeWithText("确认清理").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("已清理 1 个 · 跳过 1 个").fetchSemanticsNodes().isNotEmpty() }
        val rows = writes.single().getJSONArray("tags")
        assertEquals(2, rows.length())
        assertEquals(listOf(11L, 12L), (0..1).map { rows.getJSONObject(it).getLong("id") })
        for (i in 0..1) {
            val row = rows.getJSONObject(i)
            assertEquals("候选${row.getLong("id")}", row.getString("name"))
            assertEquals(updated, row.getString("updatedAt"))
        }
        rule.onNodeWithTag("unused-tags-list").performScrollToNode(hasText("候选12 · 已跳过"))
        rule.onNodeWithText("候选12 · 已跳过").assertIsDisplayed()
    }
    @Test fun uncertainResultSurvivesRecreationWithoutAutomaticRetry() {
        reject = true; open()
        rule.onNodeWithTag("unused-tag-11").performScrollTo().performClick()
        rule.onNodeWithText("清理已选 1 个").performScrollTo().performClick()
        rule.onNodeWithText("确认清理").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("清理未确认").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate(); waitTag("unused-tag-11")
        rule.onNodeWithTag("unused-tag-11").assertIsNotEnabled()
        rule.onNodeWithText("清理已选 1 个").performScrollTo().assertIsNotEnabled()
        assertEquals(1, writes.size)
        rule.onNodeWithText("刷新候选").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("清理已选 1 个").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("清理已选 0 个").assertDoesNotExist()
        rule.onNodeWithTag("unused-tag-11").assertIsEnabled()
        assertEquals(1, writes.size)
    }
}
