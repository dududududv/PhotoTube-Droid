package com.yunai.phototube

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yunai.phototube.ui.remote.ManagementPage
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Client-only checks: a transition must complete dismissal exactly once. */
class ManagementMotionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun closeRequestedBeforeFirstFrameCompletesOnce() {
        var closed = 0
        rule.setContent {
            PhotoTubeTheme { ManagementPage("测试页面", { closed++ }, closeRequested = true) { Text("内容") } }
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun returningDuringEntranceDoesNotWaitForEntranceOrDismissTwice() {
        var closed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { PhotoTubeTheme { ManagementPage("测试页面", { closed++ }) { Text("内容") } } }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.runOnIdle { assertEquals(0, closed) }
        rule.mainClock.advanceTimeBy(300)
        rule.runOnIdle { assertEquals(1, closed) }
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(1, closed) }
        rule.mainClock.autoAdvance = true
    }
}
