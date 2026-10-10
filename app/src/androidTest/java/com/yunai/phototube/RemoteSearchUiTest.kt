package com.yunai.phototube

import android.os.ParcelFileDescriptor
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real search route, keyboard insets, and paging restoration with an isolated API. */
class RemoteSearchUiTest : RemoteAppTest() {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun shell(command: String) = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun waitText(text: String) = rule.waitUntil(10000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun waitClosed() = rule.waitUntil(10000) { rule.onAllNodesWithTag("search-button").fetchSemanticsNodes().isNotEmpty() }

    @Test fun refreshAndFilterChangesInvalidateTheSearchOrigin() {
        val original = wall.pageFlow
        rule.runOnIdle { wall.beginSearch(); wall.updateFilters(wall.filters.copy(keyword = "restore-10")) }
        rule.waitUntil(10000) { !wall.busy && wall.pageFlow != null }
        rule.runOnIdle { wall.refresh() }
        rule.waitUntil(10000) { !wall.busy }
        rule.runOnIdle { wall.endSearch() }
        rule.waitUntil(10000) { !wall.busy && wall.filters.keyword.isEmpty() }
        org.junit.Assert.assertNotSame("A content refresh must discard the old window", original, wall.pageFlow)
        rule.runOnIdle { wall.beginSearch(); wall.updateFilters(wall.filters.copy(keyword = "restore-10", favorite = true)) }
        rule.waitUntil(10000) { !wall.busy }
        rule.runOnIdle { wall.endSearch() }
        rule.waitUntil(10000) { !wall.busy && wall.filters.keyword.isEmpty() }
        assertEquals(true, wall.filters.favorite)
    }
    @Test fun signOutCannotRestorePhotosFromTheSearchOrigin() {
        rule.runOnIdle {
            wall.beginSearch()
            wall.updateFilters(wall.filters.copy(keyword = "restore-10"))
            wall.signedOut()
            wall.endSearch()
        }
        rule.waitForIdle()
        assertEquals(com.yunai.phototube.data.ConnectionStage.LOGIN, wall.stage)
        org.junit.Assert.assertNull(wall.pageFlow)
        assertTrue(wall.groups.isEmpty())
    }
    @Test fun albumSearchUsesItsOwnNameQueryAndClearsOnCancel() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("不存在的相册")
        waitText("没有符合名称的相册")
        rule.onNodeWithTag("album-restore-album").assertDoesNotExist()
        rule.onNodeWithTag("search-input").performTextReplacement("恢复")
        waitTag("album-restore-album")
        assertEquals("", wall.filters.keyword)
        rule.onNodeWithText("取消").performClick()
        waitClosed()
        rule.onNodeWithContentDescription("图库标签").performClick()
        waitTag("asset-restore-0")
    }
    @Test fun shortQueryShowsOneHintNextToTheInputAndHidesOldPhotos() {
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("ab")
        rule.onAllNodesWithText("请输入至少 3 个字符，按文件名搜索").assertCountEquals(1)
        rule.onNodeWithTag("asset-restore-0").assertDoesNotExist()
        rule.onNodeWithTag("search-dock").assertIsDisplayed()
        assertEquals("", wall.filters.keyword)
    }
    @Test fun validFilenameSearchClearsAndBackDismisses() {
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("没有此文件")
        waitText("没有符合条件的照片或视频")
        rule.onNodeWithContentDescription("清除搜索").performClick()
        waitTag("asset-restore-0")
        rule.onNodeWithTag("search-input").performImeAction()
        rule.waitUntil(5000) { rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false }
        rule.onNodeWithTag("search-dock").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        waitClosed()
        rule.onNodeWithContentDescription("图库标签").assertIsDisplayed()
    }
    @Test fun cancelSearchRestoresPositionBeyondFirstPage() {
        rule.onNodeWithTag("remote-photo-wall").performScrollToNode(hasTestTag("asset-restore-160"))
        fun offset() = rule.onNodeWithTag("remote-photo-wall").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before = offset()
        assertTrue("Must exercise a later page", before > 50f)
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("restore-10")
        waitTag("asset-restore-10")
        rule.onNodeWithText("取消").performClick()
        waitClosed()
        rule.waitUntil(10000) { kotlin.math.abs(offset() - before) < 1f }
        assertEquals(before, offset(), 1f)
        rule.onNodeWithTag("asset-restore-160").assertIsDisplayed()
    }
    @Test fun keyboardLiftsSearchDockAndBackClosesInStages() {
        val original = shell("settings get secure show_ime_with_hard_keyboard")
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            rule.onNodeWithTag("search-button").performClick()
            rule.waitUntil(10000) { rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            rule.onNodeWithTag("search-input").performTextInput("restore")
            rule.onNodeWithTag("search-dock").assertIsDisplayed()
            // Wait for the platform IME and Compose expansion animations to settle.
            Thread.sleep(700)
            rule.waitForIdle()
            val dock = rule.onNodeWithTag("search-dock").fetchSemanticsNode().boundsInWindow
            var keyboardTop = 0
            rule.runOnUiThread {
                val view = rule.activity.window.decorView
                keyboardTop = view.height - view.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
            }
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File(app.getExternalFilesDir(null), "remote-search-keyboard.png"))
            assertTrue("Search dock must sit above the keyboard: dock=$dock keyboardTop=$keyboardTop decor=${rule.activity.window.decorView.height}", dock.bottom <= keyboardTop + 2f)
            val density = app.resources.displayMetrics.density
            assertTrue("Search dock must stay within 12dp of keyboard, gap=${keyboardTop-dock.bottom}",
                keyboardTop-dock.bottom <= 12f*density)
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressBack()
            rule.waitUntil(5000) { rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false }
            rule.onNodeWithTag("search-dock").assertIsDisplayed()
            device.pressBack()
            waitClosed()
        } finally {
            shell(if (original == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $original")
        }
    }
}
