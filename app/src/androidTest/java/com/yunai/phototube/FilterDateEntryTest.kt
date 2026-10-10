package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test

/** Synthetic dates through the real filter route, with the actual keyboard. */
class FilterDateEntryTest : RemoteAppTest() {
    @Test fun calendarAndEndDateKeyboardRespectDraftInBothThemes() {
        for(mode in listOf("LIGHT","DARK")) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-$mode").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithContentDescription("图库标签").performClick()
            rule.onNodeWithContentDescription("筛选").performClick()
            rule.onNodeWithText("日期、目录与标签").performClick()
            rule.onNodeWithText("开始日期").performScrollTo().performClick()
            rule.onNodeWithText("下个月").assertExists()
            rule.onNodeWithText("输入日期").assertExists()
            auditShot("filter-date-calendar-$mode")
            rule.onNodeWithText("取消",substring=false).performScrollTo().performClick()
            rule.onNodeWithText("结束日期").performScrollTo().performClick()
            rule.onNodeWithText("输入日期").performScrollTo().performClick()
            rule.onNodeWithTag("filter-date-input").performClick().performTextReplacement("2026-02-29")
            rule.onNode(hasText("选择",substring=false) and !hasTestTag("wall-select")).assertIsNotEnabled()
            rule.onNodeWithTag("filter-date-input").performTextReplacement("2024-02-29")
            rule.onNode(hasText("选择",substring=false) and !hasTestTag("wall-select")).performScrollTo().assertIsDisplayed().assertIsEnabled()
            auditShot("filter-date-keyboard-$mode")
            rule.onNode(hasText("选择",substring=false) and !hasTestTag("wall-select")).performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("filter-date-input").fetchSemanticsNodes().isEmpty()}
            assertNull(wall.filters.takenTo)
            rule.onNodeWithText("2024年2月29日",substring=true).assertExists()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("apply-wall-filters").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithContentDescription("关闭筛选菜单").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("filter-menu").fetchSemanticsNodes().isEmpty()}
            assertNull(wall.filters.takenTo);assertNull(wall.filters.takenFrom)
        }
    }
}
