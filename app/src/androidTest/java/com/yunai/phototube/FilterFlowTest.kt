package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import java.io.File

/** MainActivity is hard-wired to in-process LocalMockData and bundled media. */
class FilterFlowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun waitText(text:String) = rule.waitUntil(10000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun shot(name:String) {
        rule.waitForIdle();Thread.sleep(350)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"filter-fixed-$name.png"))
    }
    private fun open() {rule.onNodeWithContentDescription("筛选").performClick()}
    private fun advanced() {rule.onNodeWithText("日期、目录与标签").performClick()}
    private fun close() {rule.onNodeWithText("完成").performClick();rule.waitUntil(10000){rule.onAllNodesWithTag("filter-menu").fetchSemanticsNodes().isEmpty()}}
    @Test fun mockFilterFlowAndContinuousScope() {
        waitText("24 项内容")
        rule.onNodeWithTag("remote-photo-wall").performTouchInput {swipeUp()}
        open();shot("root")
        rule.onNodeWithText("媒体类型").performClick();shot("media")
        rule.onNodeWithText("仅视频").performClick();close()
        waitText("1 项内容 · 视频")
        rule.onNodeWithTag("asset-mock-5").assertExists()
        rule.onNodeWithTag("asset-mock-0").assertDoesNotExist()
        open();rule.onNodeWithText("清除筛选").performClick();close();waitText("24 项内容")
        open();rule.onNodeWithText("只看归档").performClick();close();waitText("2 项内容 · 归档")
        open();rule.onNodeWithText("清除筛选").performClick();advanced();shot("scope")
        rule.onNodeWithText("开始").performClick()
        rule.onNodeWithTag("filter-calendar",useUnmergedTree=true).assertExists();shot("calendar")
        rule.onNodeWithText("输入日期").performScrollTo().performClick()
        rule.onNodeWithContentDescription("输入日期").performTextReplacement("2026-09-02")
        rule.onNodeWithText("选择日期").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回筛选菜单").performClick()
        close();waitText("24 项内容") // Discard the scope draft.
        open();advanced();rule.onNodeWithText("排除").performClick()
        waitText("风景");rule.onNodeWithText("风景").performClick();shot("tags")
        rule.onNodeWithText("应用").performClick();waitText("0 项内容 · 标签组合")
        open();rule.onNodeWithText("清除筛选").performClick();advanced()
        rule.onNodeWithText("目录").performClick();waitText("本地示例媒体库")
        rule.onNodeWithText("本地示例媒体库").performClick();waitText("示例照片")
        rule.onNodeWithText("示例照片").performClick();waitText("使用：示例照片")
        rule.onNodeWithText("使用：示例照片").performClick();shot("folders")
        rule.onNodeWithText("应用").performClick();waitText("24 项内容 · 指定目录")
        open();rule.onNodeWithText("清除筛选").performClick();close();waitText("24 项内容")
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("液态玻璃"));rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick();rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick();open();shot("dark")
        close()
        rule.onNodeWithContentDescription("相册标签").performClick();rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("液态玻璃"));rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-LIGHT").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick();rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
    }
}
