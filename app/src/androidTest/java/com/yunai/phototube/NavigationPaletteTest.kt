package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeAccent
import com.yunai.phototube.ui.theme.ThemeMode
import org.junit.Test

/** Real navigation and theme settings; media are bundled landscapes via localhost. */
class NavigationPaletteTest : RemoteAppTest() {
    override val photographicFixtures = true
    @Test fun allAccentsRetainSelectionAcrossBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            for (accent in ThemeAccent.entries) {
                rule.onNodeWithContentDescription("相册标签").performClick()
                rule.onNodeWithContentDescription("设置").performClick()
                rule.onNodeWithText("液态玻璃").performClick()
                rule.onNodeWithText("恢复默认玻璃效果").performScrollTo().performClick()
                rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick().assertIsSelected()
                rule.onNodeWithTag("theme-accent-${accent.name}").performScrollTo().performClick().assertIsSelected()
                rule.onNodeWithContentDescription("返回设置").performClick()
                rule.onNodeWithContentDescription("返回相册").performClick()
                rule.onNodeWithContentDescription("图库标签").performClick().assertIsSelected()
                rule.onNodeWithContentDescription("相册标签").assertIsNotSelected()
                rule.onNode(hasText("图库") and hasAnyAncestor(hasTestTag("bottom-tabs"))).assertIsDisplayed()
                auditShot("navigation-palette-${mode.name}-${accent.name}")
            }
        }
    }
}
