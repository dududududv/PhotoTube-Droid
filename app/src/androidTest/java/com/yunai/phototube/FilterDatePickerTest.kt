package com.yunai.phototube

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yunai.phototube.ui.remote.FilterDatePicker
import com.yunai.phototube.ui.remote.filterDayInstant
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*

/** Synthetic dates only. No API, media or session. */
class FilterDatePickerTest {
    @get:Rule val rule=createComposeRule()
    @Test fun invalidInputCannotConfirmAndLeapDayUsesEndOfLocalDay() {
        val visible=mutableStateOf(true)
        val results=mutableListOf<String>()
        rule.setContent {PhotoTubeTheme {if(visible.value) FilterDatePicker(null,true,{visible.value=false},{results+=it;visible.value=false})}}
        rule.onNodeWithText("输入日期").performScrollTo().performClick()
        rule.onNodeWithTag("filter-date-input").performTextReplacement("2026-02-29")
        rule.onNodeWithText("选择",substring=false).assertIsNotEnabled()
        rule.onNodeWithTag("filter-date-input").performTextReplacement("2024-02-29")
        rule.onNodeWithText("选择",substring=false).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf(filterDayInstant(LocalDate.of(2024,2,29),true,ZoneId.systemDefault())),results)
        rule.onNodeWithTag("filter-date-input").assertDoesNotExist()
    }
    @Test fun calendarMonthNavigationAndCancelNeverApplyDraft() {
        val visible=mutableStateOf(true)
        var calls=0
        val initial=LocalDate.of(2026,9,17).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        rule.setContent {PhotoTubeTheme {if(visible.value) FilterDatePicker(initial,false,{visible.value=false},{calls++})}}
        rule.onNodeWithTag("filter-day-2026-09-17").performScrollTo().assertIsSelected()
        rule.onNodeWithText("下个月").performScrollTo().performClick()
        rule.onNodeWithTag("filter-day-2026-10-01").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("取消",substring=false).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(0,calls)
        assertFalse(visible.value)
    }
}
