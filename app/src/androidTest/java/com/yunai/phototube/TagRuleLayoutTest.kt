package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** App routes and generated rule/tag fixtures only; never starts a scan. */
class TagRuleLayoutTest : RemoteAppTest() {
    @Volatile private var populated = false
    private val writes = AtomicInteger()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet(); return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/tag-rules") return null
        val items = if (populated) """[{"id":"synthetic-rule","name":"合成假日规则","pattern":"(?i)holiday","target":"FILE_NAME","tagIds":[7],"enabled":true,"generatedAssetCount":10}]""" else "[]"
        return MockResponse().setHeader("Content-Type","application/json")
            .setBody("""{"items":$items,"nextCursor":null}""")
    }
    private fun closeRules() {
        rule.onAllNodesWithContentDescription("返回",substring=false).onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("tag-rule-list").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun emptyEditorAndDeleteConfirmationMatchBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
            populated=false
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithText("标签库").performClick();waitTag("tag-list")
            rule.onNodeWithText("标签规则").performClick();waitTag("tag-rule-list")
            rule.waitUntil(10000) {rule.onAllNodesWithText("还没有标签规则").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("按范围扫描").assertDoesNotExist()
            rule.onNodeWithText("新建规则").assertIsDisplayed().assertIsEnabled()
            auditShot("rules-empty-${mode.name.lowercase()}")
            rule.onNodeWithText("新建规则").performClick()
            rule.onNode(hasSetTextAction() and hasText("规则名称")).assertIsDisplayed()
            auditShot("rules-editor-${mode.name.lowercase()}")
            rule.onNodeWithText("保存规则").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
            auditShot("rules-editor-bottom-${mode.name.lowercase()}")
            rule.onAllNodesWithContentDescription("返回",substring=false).onLast().performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("保存规则").fetchSemanticsNodes().isEmpty()}
            closeRules()
            populated=true
            rule.onNodeWithText("标签规则").performClick();waitTag("tag-rule-list")
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成假日规则").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("按范围扫描").assertIsDisplayed()
            rule.onNodeWithTag("tag-rule-list").performScrollToNode(hasText("删除规则"))
            auditShot("rules-list-${mode.name.lowercase()}")
            rule.onNodeWithText("删除规则").performClick()
            rule.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
            auditShot("rules-delete-${mode.name.lowercase()}")
            rule.onNodeWithText("取消",substring=false).performClick()
            rule.onNodeWithText("合成假日规则").assertExists()
            closeRules()
            rule.onNodeWithContentDescription("返回",substring=false).performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("tag-list").fetchSemanticsNodes().isEmpty()}
            assertEquals(0,writes.get())
        }
    }
}
