package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class UiRepairNavigationTest : RemoteAppTest() {
    override val photographicFixtures=true
    private val writes=AtomicInteger()
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        writes.incrementAndGet();return MockResponse().setResponseCode(405)
    }
    @Test fun viewerHasNoContentEditingAndReturnsToSameAlbum() {
        rule.onNodeWithContentDescription("相册标签").performClick();waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick();waitTag("album-grid")
        val before=rule.onNodeWithTag("album-media-restore-0").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("album-media-restore-0").performClick();waitTag("media-viewer")
        rule.onNodeWithContentDescription("编辑照片").assertDoesNotExist()
        rule.onNodeWithContentDescription("编辑视频").assertDoesNotExist()
        rule.onNodeWithContentDescription("分享").assertIsDisplayed()
        rule.onNodeWithContentDescription("照片与视频信息").assertIsDisplayed()
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("编辑历史与原图").assertDoesNotExist()
        rule.onNodeWithText("查看信息").performClick()
        rule.onNodeWithText("编辑标签").assertIsDisplayed()
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithTag("media-viewer").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty()}
        assertEquals(before,rule.onNodeWithTag("album-media-restore-0").fetchSemanticsNode().boundsInRoot)
        assertEquals(0,writes.get())
    }
    @Test fun sortingShowsCurrentValueAndAdvancedPageReturnsOneLevelAtATime() {
        rule.onNodeWithContentDescription("相册标签").performClick();waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick();waitTag("album-grid")
        rule.onNodeWithText("添加").assertIsDisplayed()
        rule.onNodeWithText("选择").assertIsDisplayed()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("排序 · 最早在前").performClick()
        rule.onNodeWithText("最新在前").assertIsDisplayed()
        rule.onNodeWithText("最早在前").assertIsDisplayed()
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("整理与 AI").performClick()
        rule.onNodeWithText("AI 提示词").performClick();waitTag("photo-ai-prompt")
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("photo-ai-prompt").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithText("整理与 AI").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("整理与 AI").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("album-grid").assertIsDisplayed()
        assertEquals(0,writes.get())
    }
}
