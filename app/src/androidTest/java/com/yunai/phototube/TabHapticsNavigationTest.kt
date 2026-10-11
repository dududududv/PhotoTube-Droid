package com.yunai.phototube

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import org.junit.Test

/** Checks interaction and the opt-out only; emulator results cannot validate motor feel. */
class TabHapticsNavigationTest : LocalFixtureAppTest() {
    @Test fun clickDragAndDisabledFeedbackKeepNavigationWorking() {
        rule.waitUntil(10000) { rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("相册标签").performClick().assertIsSelected()
        rule.onNodeWithContentDescription("相册标签").performClick().assertIsSelected()
        rule.onNodeWithContentDescription("图库标签").performClick().assertIsSelected()
        val tabs = rule.onNodeWithTag("bottom-tabs")
        tabs.performTouchInput {
            down(Offset(width * .25f, centerY))
            moveTo(Offset(width * .9f, centerY), 300)
            cancel()
        }
        rule.onNodeWithContentDescription("图库标签").assertIsSelected()
        tabs.performTouchInput { swipe(Offset(width * .25f, centerY), Offset(width * .9f, centerY), 500) }
        rule.onNodeWithContentDescription("相册标签").assertIsSelected()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("触感反馈")))
            .performScrollTo().assertIsOn().performClick().assertIsOff()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick().assertIsSelected()
        rule.onNodeWithContentDescription("相册标签").performClick().assertIsSelected()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("触感反馈")))
            .performScrollTo().assertIsOff()
    }
}
