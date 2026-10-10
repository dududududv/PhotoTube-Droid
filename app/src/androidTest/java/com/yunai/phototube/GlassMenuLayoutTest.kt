package com.yunai.phototube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.*
import com.yunai.phototube.ui.*
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Entirely synthetic UI. For large-font acceptance run with mock AVD system font_scale=2.0,
 * then restore its previous value; Popup obtains density from its own Android window. */
class GlassMenuLayoutTest {
    @get:Rule val rule=createComposeRule()
    @Test fun rtlLargeTextBottomMenuScrollsAndDeliversOneActionAfterExit() = exercise(false)

    @Test fun landscapeRtlLargeTextMenuRemainsScrollable() {
        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            exercise(true)
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    private fun exercise(landscape: Boolean) {
        var selected=0
        var dismissed=0
        val expanded=mutableStateOf(false)
        rule.setContent {
            val density=LocalDensity.current
            PhotoTubeTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f),LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val backdrop=rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFFABC6D3)))
                        Box(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text("菜单",Modifier.size(64.dp).clickable {expanded.value=true}.testTag("mock-menu-trigger"))
                            GlassActionMenu(expanded.value,{dismissed++;expanded.value=false},backdrop,
                                listOf(GlassMenuAction("不可用选项",enabled=false,onClick={error("Disabled action ran")}))+
                                    (1..30).map {i -> GlassMenuAction("合成菜单选项 $i",onClick={selected++})})
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("mock-menu-trigger").performClick()
        rule.onNodeWithTag("glass-action-menu").assertIsDisplayed()
        val outer=rule.onNodeWithTag("glass-menu-safe-frame").fetchSemanticsNode().boundsInRoot
        val inner=rule.onNodeWithTag("glass-action-menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Glass must retain space inside the popup's top boundary",inner.top>outer.top)
        assertTrue("Glass must retain space inside the popup's side boundaries",inner.left>outer.left && inner.right<outer.right)
        rule.onNodeWithText("不可用选项").assertIsNotEnabled()
        rule.onNodeWithText("合成菜单选项 30").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("合成菜单选项 1",substring=false).assertIsNotDisplayed()
        rule.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        if(landscape) assertTrue("Must exercise an actual landscape window",bitmap.width>bitmap.height)
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null),if(landscape) "mock-menu-rtl-large-landscape.png" else "mock-menu-rtl-large-bottom.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
        rule.mainClock.autoAdvance=false
        rule.onNodeWithText("合成菜单选项 30").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0,selected)
        rule.onNodeWithText("合成菜单选项 30").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(250)
        rule.mainClock.autoAdvance=true
        rule.waitForIdle()
        assertEquals(1,selected)
        assertEquals(1,dismissed)
        rule.onNodeWithTag("glass-action-menu").assertDoesNotExist()
    }
}
