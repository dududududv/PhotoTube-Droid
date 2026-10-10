package com.yunai.phototube

import androidx.compose.ui.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Bundled landscapes through the isolated localhost fixture. */
class PhotoWallDensityTest : RemoteAppTest() {
    override val photographicFixtures = true
    @Test fun headingScrollsWithWallWhileFilterStaysAnchored() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-LIGHT").performScrollTo().performClick()
        rule.onNodeWithTag("theme-accent-BLUE").performScrollTo().performClick()
        rule.onNodeWithText("恢复默认玻璃效果").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        waitTag("asset-restore-0")
        rule.onNodeWithTag("wall-title").assertIsDisplayed()
        val filter=rule.onNodeWithTag("filter-button").fetchSemanticsNode().boundsInRoot
        auditShot("wall-density-initial")
        rule.onNodeWithTag("remote-photo-wall").performTouchInput {swipeUp(durationMillis=600)}
        rule.onNodeWithTag("wall-title").assertIsNotDisplayed()
        rule.onNodeWithTag("filter-button").assertIsDisplayed()
        assertEquals(filter,rule.onNodeWithTag("filter-button").fetchSemanticsNode().boundsInRoot)
        auditShot("wall-density-scrolled")
    }
}
