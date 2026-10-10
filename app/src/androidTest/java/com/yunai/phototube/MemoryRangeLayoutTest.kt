package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicInteger

/** Dates and rules are generated locally; no personal memory feed is used. */
class MemoryRangeLayoutTest : RemoteAppTest() {
    private val writes = AtomicInteger()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet()
        return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? =
        if(request.requestUrl!!.encodedPath == "/api/v1/memory-exclusions") MockResponse()
            .setHeader("Content-Type","application/json")
            .setBody("""{"items":[{"id":"synthetic-range","kind":"DATE_RANGE","dateFrom":"2025-01-01","dateTo":"2025-01-02"}],"nextCursor":null}""") else null

    @Test fun labeledRangeCalendarAndKeepConfirmationInBothThemes() {
        for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithText("回忆与精选").performClick()
            rule.onNodeWithText("回忆日期屏蔽").performClick()
            waitTag("memory-range-summary")
            rule.onNodeWithText("屏蔽此日期范围").assertIsNotEnabled()
            fun select(field:String, date:String, capture:Boolean=false) {
                rule.onNodeWithText(field).performClick()
                rule.onNodeWithText("输入日期").performScrollTo().assertIsDisplayed()
                if(capture) auditShot("memory-calendar-${mode.name.lowercase()}")
                rule.onNodeWithText("输入日期").performClick()
                rule.onNodeWithTag("filter-date-input").performTextReplacement(date)
                if(capture) auditShot("memory-input-${mode.name.lowercase()}")
                rule.onNodeWithText("选择日期").performScrollTo().performClick()
                rule.waitUntil(5000) {rule.onAllNodesWithTag("filter-date-input").fetchSemanticsNodes().isEmpty()}
            }
            select("开始日期","2025-02-02",true)
            select("结束日期","2025-02-01")
            rule.onNodeWithText("结束日期不能早于开始日期").assertIsDisplayed()
            rule.onNodeWithText("屏蔽此日期范围").assertIsNotEnabled()
            select("结束日期","2025-02-03")
            rule.onNodeWithText("开始日期").assertIsDisplayed()
            rule.onNodeWithText("结束日期").assertIsDisplayed()
            rule.onNodeWithText("2025-02-02 至 2025-02-03\n共 2 天，包含开始及结束日期").assertIsDisplayed()
            rule.onNodeWithText("屏蔽此日期范围").assertIsEnabled()
            auditShot("memory-range-${mode.name.lowercase()}")
            rule.onNodeWithText("取消屏蔽").performClick()
            rule.onNodeWithText("保留屏蔽").assertIsDisplayed()
            auditShot("memory-confirm-${mode.name.lowercase()}")
            rule.onNodeWithText("保留屏蔽").performClick()
            rule.onNodeWithText("2025-01-01 至 2025-01-02").assertIsDisplayed()
            androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("memory-range-summary").fetchSemanticsNodes().isEmpty()}
            androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("memory-往年今日-restore-0").fetchSemanticsNodes().isEmpty()}
            assertEquals(0,writes.get())
        }
    }
}
