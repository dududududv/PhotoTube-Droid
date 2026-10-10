package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Generated tag names and counts only, with the real app's settings and library routes. */
class TagLibraryLayoutTest : RemoteAppTest() {
    private val writes = AtomicInteger()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet()
        return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/tags") return null
        val rows = JSONArray().apply {
            repeat(12) { i -> put(JSONObject().put("id", 7+i)
                .put("name", if (i == 1) "合成标签：山间旅行与城市建筑的光影记录" else "示例标签 ${i+1}")
                .put("assetCount", 12+i)) }
        }
        return MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("items",rows).put("nextCursor",JSONObject.NULL).toString())
    }
    @Test fun compactRowsAndAnchoredMenuWorkInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithText("标签库").performClick(); waitTag("tag-row-7")
            for (id in 7..9) rule.onNodeWithTag("tag-row-$id").assertIsDisplayed()
            org.junit.Assert.assertTrue(rule.onNodeWithTag("tag-row-7").fetchSemanticsNode().boundsInRoot.height / rule.activity.resources.displayMetrics.density <= 90f)
            rule.onNodeWithText("重命名").assertDoesNotExist()
            rule.onNodeWithText("删除标签").assertDoesNotExist()
            auditShot("tags-compact-${mode.name.lowercase()}")
            rule.onNodeWithTag("tag-actions-8").performClick()
            rule.onNodeWithText("重命名").assertIsDisplayed()
            rule.onNodeWithText("删除标签").assertIsDisplayed()
            auditShot("tags-menu-${mode.name.lowercase()}")
            rule.onNodeWithText("重命名").performClick()
            rule.onNode(hasSetTextAction() and hasText("合成标签：山间旅行与城市建筑的光影记录")).assertExists()
            rule.onNodeWithText("取消",substring=false).performClick()
            rule.onNodeWithTag("tag-actions-7").performClick()
            waitTag("glass-action-menu")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithTag("tag-list").assertIsDisplayed()
            rule.onNodeWithTag("tag-list").performScrollToNode(hasTestTag("tag-row-7"))
            rule.onNodeWithTag("tag-row-7").assertIsDisplayed()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("tag-list").fetchSemanticsNodes().isEmpty()}
            assertEquals(0,writes.get())
        }
    }
}
