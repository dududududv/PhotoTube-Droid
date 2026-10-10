package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Only the in-process LocalMockData and bundled media used by MainActivity. */
class AlbumShelvesTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag:String) = rule.waitUntil(15000) {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun shot(name:String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450) // Let the window compositor finish dialog dismissal.
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(File(context.getExternalFilesDir(null),"$name.png"))
    }
    @Test fun shelvesNavigationAndToolbar() {
        rule.onNodeWithContentDescription("相册标签").performClick();waitTag("album-mock-album")
        rule.onNodeWithText("我的相册").assertIsDisplayed()
        rule.onNodeWithText("更多工具").assertDoesNotExist()
        rule.onNodeWithText("文件夹").assertDoesNotExist()
        rule.onNodeWithText("智能相册").assertDoesNotExist()
        rule.onNodeWithText("新建相册").assertDoesNotExist()
        val create=rule.onNodeWithTag("create-album-button").fetchSemanticsNode().boundsInRoot
        val settings=rule.onNodeWithTag("settings-button").fetchSemanticsNode().boundsInRoot
        assertEquals(create.height,settings.height,.5f)
        assertEquals(create.width,settings.width,.5f)
        val a=rule.onNodeWithTag("remote-album-cover-mock-album",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(a.width,a.height,.5f)
        // Wait for the bundled image decoder before visual evidence.
        Thread.sleep(800);shot("album-shelves-home")
        rule.onNodeWithTag("album-mock-album").assertIsDisplayed().performClick();waitTag("album-grid")
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("album-mock-album").assertIsDisplayed()
        rule.onNodeWithContentDescription("相册排序").performClick()
        rule.onNodeWithText("按名称").performClick();waitTag("album-mock-album")
        rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty()}
        val favorite=rule.onNodeWithTag("album-utility-收藏").fetchSemanticsNode().boundsInRoot
        val tags=rule.onNodeWithTag("album-utility-标签").fetchSemanticsNode().boundsInRoot
        val locked=rule.onNodeWithTag("album-utility-私密空间").fetchSemanticsNode().boundsInRoot
        val trash=rule.onNodeWithTag("album-utility-回收站").fetchSemanticsNode().boundsInRoot
        assertEquals(favorite.top,tags.top,.5f);assertEquals(locked.top,trash.top,.5f)
        assertEquals(favorite.left,locked.left,.5f);assertEquals(tags.left,trash.left,.5f)
        rule.onNodeWithTag("album-utility-收藏").performClick();waitTag("favorite-photos")
        rule.onNodeWithText("6 项 · 不含归档与私密内容").assertExists()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick();waitTag("album-utilities")
        rule.onNodeWithTag("album-utility-标签").performClick();waitTag("tag-list")
        rule.onAllNodesWithContentDescription("返回").onLast().performClick();waitTag("album-utilities")
        rule.onNodeWithTag("album-utility-私密空间").performClick()
        waitTag("private-password")
        rule.onAllNodesWithContentDescription("返回").onLast().performClick();waitTag("album-utilities")
        rule.onNodeWithTag("album-utility-回收站").performClick()
        waitTag("trash-list")
        rule.onAllNodesWithContentDescription("返回").onLast().performClick();waitTag("album-utilities")
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("名称").assertExists()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("名称").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-screen").assertExists()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("液态玻璃"))
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        shot("album-shelves-dark")
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("液态玻璃"))
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-LIGHT").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("任务中心"))
        shot("album-management-settings")
        rule.onNodeWithText("任务中心").performClick()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.onNodeWithTag("settings-screen").assertExists()
        rule.onNodeWithText("任务中心").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("settings-screen").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithText("我的相册").assertIsDisplayed()
        rule.onNodeWithText("更多工具").assertDoesNotExist()
    }
}
