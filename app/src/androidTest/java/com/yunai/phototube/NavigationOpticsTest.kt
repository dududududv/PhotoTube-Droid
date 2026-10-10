package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.*
import org.junit.Test

/** Controlled optical comparison: bundled landscapes, identical layout, no remote media. */
class NavigationOpticsTest : RemoteAppTest() {
    override val photographicFixtures = true
    @Test fun compareRefractionWithoutChangingTintOrBlur() {
        for (amount in listOf(24f,12f,8f,0f)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-LIGHT").performScrollTo().performClick()
            rule.onNodeWithText("恢复默认玻璃效果").performScrollTo().performClick()
            rule.onNodeWithTag("appearance-amount").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(amount) }
            rule.waitForIdle()
            assertEquals(amount, rule.onNodeWithTag("appearance-amount").fetchSemanticsNode()
                .config[SemanticsProperties.ProgressBarRangeInfo].current, .01f)
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithContentDescription("图库标签").performClick()
            waitTag("asset-restore-0")
            rule.onNodeWithContentDescription("图库标签").assertIsSelected()
            auditShot("navigation-optics-${amount.toInt()}")
        }
    }
}
