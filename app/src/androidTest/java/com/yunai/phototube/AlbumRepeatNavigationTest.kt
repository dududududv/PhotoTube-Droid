package com.yunai.phototube

import android.os.SystemClock
import androidx.compose.ui.test.*
import org.junit.Test

/** Repeated navigation only. Compose test-clock output is not real-time motion evidence. */
class AlbumRepeatNavigationTest : RemoteAppTest() {
    @Test fun repeatedAlbumOpenAndCloseReturnsToVisibleCover() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("remote-albums").performScrollToIndex(1)
        rule.waitForIdle()
        rule.onNodeWithTag("remote-album-cover-restore-album",useUnmergedTree=true).assertIsDisplayed()
        SystemClock.sleep(100)
        repeat(3) {
            rule.onNodeWithTag("album-restore-album").performClick()
            waitTag("album-grid")
            rule.waitForIdle()
            SystemClock.sleep(100)
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithTag("remote-album-cover-restore-album",useUnmergedTree=true).assertIsDisplayed()
            SystemClock.sleep(100)
        }
    }
}
