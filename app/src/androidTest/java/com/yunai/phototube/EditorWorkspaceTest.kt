package com.yunai.phototube

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.remote.EditorWorkspace
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure synthetic layout, with deliberately more tools than a short window can fit. */
class EditorWorkspaceTest {
    @get:Rule val rule=createComposeRule()
    @Test fun largeTextToolsScrollWithoutCollapsingPreview() = exercise(false)
    @Test fun landscapeKeepsToolsBesidePreviewAtLargeFont() {
        val device=androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            exercise(true)
        } finally {device.setOrientationNatural();device.unfreezeRotation()}
    }
    private fun exercise(landscape:Boolean) {
        var saves=0
        rule.setContent {
            val density=LocalDensity.current
            PhotoTubeTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    Box(if(landscape) Modifier.fillMaxSize().safeDrawingPadding() else Modifier.size(320.dp,280.dp)) {
                        EditorWorkspace(canvas={Box(it.testTag("mock-preview"))},controls={
                            repeat(8) {Text("合成编辑工具与错误说明 $it")}
                            GlassSettingsAction("保存新版本",{saves++})
                        })
                    }
                }
            }
        }
        rule.onNodeWithTag("mock-preview").assertHeightIsAtLeast(120.dp)
        val before=rule.onNodeWithTag("mock-preview").fetchSemanticsNode().boundsInRoot
        if(landscape) {
            val tools=rule.onNodeWithTag("editor-tools").fetchSemanticsNode().boundsInRoot
            assertTrue("Landscape tools must sit beside preview",tools.left>before.right)
            assertEquals(before.top,tools.top,1f)
            assertEquals(before.bottom,tools.bottom,1f)
        }
        rule.onNodeWithText("保存新版本").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(before,rule.onNodeWithTag("mock-preview").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle {assertEquals(1,saves)}
    }
}
