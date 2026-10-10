package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Dialog entry/keyboard/dismissal using synthetic albums and bundled photos only. */
class AlbumDialogThemeTest : RemoteAppTest() {
    override val photographicFixtures = true
    private val writes=AtomicInteger()
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        writes.incrementAndGet();return MockResponse().setResponseCode(409)
    }
    private fun menu(label:String) {
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText(label).performScrollTo().performClick()
        waitTag("glass-confirmation")
    }
    private fun cancel() {
        rule.onNodeWithText("取消").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-confirmation").fetchSemanticsNodes().isEmpty()}
    }
    @Test fun renameKeyboardAndDeleteOptionsCancelWithoutWritesInBothThemes() {
        for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithTag("album-restore-album").performClick();waitTag("album-grid")
            menu("重命名")
            val field=rule.onNode(hasSetTextAction())
            field.assertTextContains("恢复测试相册")
            field.performClick().performTextReplacement("合成相册新名称")
            rule.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("取消").assertIsDisplayed()
            auditShot("album-rename-keyboard-${mode.name.lowercase()}")
            cancel()
            menu("重命名")
            rule.onNode(hasSetTextAction()).assertTextContains("恢复测试相册")
            cancel()
            menu("删除相册")
            val cleanup=rule.onNodeWithText("同时清理此相册独占的照片记录")
            cleanup.assertIsOff()
            auditShot("album-delete-default-${mode.name.lowercase()}")
            cleanup.performClick().assertIsOn()
            rule.onNodeWithText("独占照片记录的数量尚未预估。").assertIsDisplayed()
            rule.onNodeWithText("照片及视频的标签、编辑等附属记录也会清理",substring=true).assertIsDisplayed()
            auditShot("album-delete-exclusive-${mode.name.lowercase()}")
            cancel()
            menu("删除相册")
            cleanup.assertIsOff();cancel()
            assertEquals(0,writes.get())
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithText("恢复测试相册").assertIsDisplayed()
        }
    }
}
