package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TagDraftRestoreTest : RemoteAppTest() {
    private val writes = CopyOnWriteArrayList<Triple<String, String, JSONObject>>()
    private val tagQueries = CopyOnWriteArrayList<okhttp3.HttpUrl>()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes += Triple(request.method!!, request.requestUrl!!.encodedPath, JSONObject(request.body.readUtf8()))
        return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"保存暂不可用"}""")
    }
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if(request.requestUrl!!.queryParameter("tagsAll") != null) tagQueries += request.requestUrl!!
        if (request.requestUrl!!.encodedPath != "/api/v1/tag-rules") return null
        val row = JSONObject().put("id", "rule-7").put("name", "原规则").put("pattern", "holiday")
            .put("target", "FILE_NAME").put("tagIds", JSONArray().put(7)).put("enabled", true).put("generatedAssetCount", 0)
        return MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("items", JSONArray().put(row)).put("nextCursor", JSONObject.NULL).toString())
    }
    private fun tags() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithText("标签库").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("风景", substring = false).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun input(label: String) = rule.onNode(hasSetTextAction() and hasText(label))
    @Test fun tagBulkActionKeepsTagScopeAcrossRecreation() {
        tags()
        rule.onNodeWithTag("browse-tag-7").performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("tag-photos-select") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("tag-photos-select").performClick()
        waitTag("bulk-grid")
        rule.onNodeWithTag("bulk-scope").performClick()
        rule.onNodeWithText("选择全部匹配项").performClick()
        rule.activityRule.scenario.recreate()
        waitTag("bulk-grid")
        rule.onNodeWithText("已选择全部匹配项").assertExists()
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("收藏", substring = false).performClick()
        assertTrue(writes.isEmpty())
        rule.onNodeWithText("确认收藏").performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        assertEquals("/api/v1/assets/favorite", writes.single().second)
        val body = writes.single().third
        assertTrue(body.getBoolean("favorite"))
        assertFalse(body.has("assetIds"))
        val filter = body.getJSONObject("filter")
        assertEquals(7L, filter.getJSONArray("tagsAll").getLong(0))
        assertEquals(1, filter.getJSONArray("tagsAll").length())
        assertFalse(filter.getBoolean("archived"))
        waitTag("bulk-result")
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty() }
        waitTag("tag-photos")
    }
    @Test fun tagOpensScopedPhotosAndViewerRestoresWithoutLosingTag() {
        tags()
        rule.onNodeWithTag("browse-tag-7").performClick()
        val firstPhoto = hasTestTag("asset-restore-0") and hasAnyAncestor(hasTestTag("tag-photos"))
        rule.waitUntil(10000) { rule.onAllNodes(firstPhoto).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null), "tag-photo-gallery.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        rule.onNode(firstPhoto).performClick()
        waitTag("media-viewer")
        rule.activityRule.scenario.recreate()
        waitTag("media-viewer")
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        waitTag("tag-photos")
        assertTrue(tagQueries.any { it.encodedPath.endsWith("/assets") })
        assertTrue(tagQueries.any { it.encodedPath.endsWith("/timeline-summary") })
        tagQueries.forEach {
            assertEquals("7", it.queryParameter("tagsAll"))
            assertEquals("false", it.queryParameter("archived"))
            assertNull(it.queryParameter("private"))
        }
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("tag-photos").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("browse-tag-7").assertExists()
        assertTrue(writes.isEmpty())
    }
    private fun assertInput(label: String, value: String) = input(label).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
    @Test fun landscapeKeepsTagsAndActionsReachable() {
        tags()
        rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        waitTag("tag-list")
        try {
            rule.onNodeWithTag("tag-list").performScrollToNode(hasTestTag("tag-actions-7"))
            rule.onNodeWithText("风景", substring = false).assertIsDisplayed()
            rule.onNodeWithTag("tag-actions-7").performClick()
            rule.onNodeWithText("重命名", substring = false).assertIsDisplayed().performClick()
            input("标签名称").assertIsDisplayed()
            rule.onNodeWithText("取消").performClick()
            assertEquals(0, writes.size)
            rule.waitForIdle()
            android.os.SystemClock.sleep(450)
            val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(app.getExternalFilesDir(null), "tags-landscape.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            rule.onNodeWithTag("tag-list").performScrollToIndex(0)
            rule.onNodeWithText("标签规则").performClick()
            waitTag("tag-rule-list")
            rule.onNodeWithTag("tag-rule-list").performScrollToNode(hasText("编辑规则"))
            rule.onNodeWithText("原规则").assertIsDisplayed()
            rule.onNodeWithText("编辑规则").assertIsDisplayed().performClick()
            input("规则名称").assertExists()
            assertEquals(0, writes.size)
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }
    @Test fun renameRestoresTargetAndCreateCancelDoesNotWrite() {
        tags(); rule.onNodeWithTag("tag-actions-7").performClick(); rule.onNodeWithText("重命名", substring = false).performClick()
        input("标签名称").performTextReplacement("新的风景")
        rule.activityRule.scenario.recreate()
        assertInput("标签名称", "新的风景")
        assertEquals(0, writes.size)
        rule.onNodeWithText("保存标签").performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        assertEquals("PATCH", writes.single().first)
        assertEquals("/api/v1/tags/7", writes.single().second)
        assertEquals("新的风景", writes.single().third.getString("name"))
        rule.waitUntil(10000) { rule.onAllNodesWithText("保存暂不可用").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        auditShot("repair-tag-form-error")
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithText("新建标签").performClick()
        input("标签名称").performTextReplacement("不保存的标签")
        rule.activityRule.scenario.recreate(); assertInput("标签名称", "不保存的标签")
        rule.onNodeWithText("取消").performClick()
        assertEquals(1, writes.size)
    }
    @Test fun existingRuleDraftKeepsIdentityConditionsAndDisabledState() {
        tags(); rule.onNodeWithText("标签规则").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("编辑规则").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("编辑规则").performClick()
        input("规则名称").performTextReplacement("新规则名称")
        input("匹配表达式").performTextReplacement("(?i)travel")
        rule.onNodeWithText("相对路径", substring = false).performClick()
        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        assertInput("规则名称", "新规则名称")
        assertInput("匹配表达式", "(?i)travel")
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText("已选 1 个标签").assertExists()
        assertEquals(0, writes.size)
        rule.onNodeWithText("保存规则").performScrollTo().performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        val write = writes.single()
        assertEquals("PATCH", write.first); assertEquals("/api/v1/tag-rules/rule-7", write.second)
        assertEquals("RELATIVE_PATH", write.third.getString("target"))
        assertEquals("(?i)travel", write.third.getString("pattern"))
        assertFalse(write.third.getBoolean("enabled"))
        assertEquals(7L, write.third.getJSONArray("tagIds").getLong(0))
    }
    @Test fun ruleDateScopeRestoresAcrossRecreationAndCloseDiscardsDraft() {
        tags()
        rule.onNodeWithText("标签规则").performScrollTo().performClick(); waitTag("tag-rule-list")
        rule.onNodeWithText("按范围扫描").performScrollTo().performClick()
        rule.onNodeWithText("拍摄时间",substring=false).performClick()
        rule.onNodeWithText("选择开始日期").performClick()
        val day=java.time.YearMonth.now().atDay(1)
        rule.onNodeWithTag("filter-day-$day").performScrollTo().performClick()
        rule.onNodeWithText("选择",substring=false).performScrollTo().performClick()
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("选择规则扫描范围").assertExists()
        rule.onNodeWithText("选择开始日期").assertDoesNotExist()
        rule.onNodeWithText("选择结束日期").performScrollTo().performClick()
        rule.onNodeWithTag("filter-day-$day").performScrollTo().performClick()
        rule.onNodeWithText("选择",substring=false).performScrollTo().performClick()
        rule.onNodeWithText("继续核对范围").performScrollTo().assertIsEnabled()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("继续核对范围").performScrollTo().assertIsEnabled()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("选择规则扫描范围").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithText("按范围扫描").performScrollTo().performClick()
        rule.onNodeWithText("继续核对范围").assertIsNotEnabled()
        rule.onNodeWithText("拍摄时间",substring=false).performClick()
        rule.onNodeWithText("选择开始日期").assertExists()
        rule.onNodeWithText("选择结束日期").assertExists()
        assertTrue(writes.isEmpty())
    }

    @Test fun scanRuleSelectionRestoresAndCancelledChangesDoNotApply() {
        tags()
        rule.onNodeWithText("标签规则").performScrollTo().performClick(); waitTag("tag-rule-list")
        rule.onNodeWithText("按范围扫描").performScrollTo().performClick()
        rule.onNodeWithText("拍摄时间",substring=false).performClick()
        val day=java.time.YearMonth.now().atDay(1)
        listOf("选择开始日期","选择结束日期").forEach { label ->
            rule.onNodeWithText(label).performScrollTo().performClick()
            rule.onNodeWithTag("filter-day-$day").performScrollTo().performClick()
            rule.onNodeWithText("选择",substring=false).performScrollTo().performClick()
        }
        rule.onNodeWithText("继续核对范围").performScrollTo().performClick()
        rule.onNodeWithText("全部已启用规则").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("原规则").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("原规则").performClick().assertIsOn()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("选择扫描规则").assertExists()
        rule.onNodeWithContentDescription("原规则").assertIsOn()
        rule.onNodeWithText("使用所选规则").performClick()
        rule.onNodeWithText("已选 1 条规则").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("已选 1 条规则").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("原规则").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("原规则").assertIsOn().performClick().assertIsOff()
        rule.onNodeWithText("使用所选规则").assertIsNotEnabled()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("选择扫描规则").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithText("已选 1 条规则").assertExists()
        rule.onNodeWithText("开始规则预览").assertIsEnabled()
        assertTrue(writes.isEmpty())
    }

}
