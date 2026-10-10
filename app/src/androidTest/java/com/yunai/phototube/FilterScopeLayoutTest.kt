package com.yunai.phototube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.*
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FilterScopeLayoutTest {
    @get:Rule val rule=createComposeRule()
    @Test fun shortWindowLargeTextKeepsApplyAndResetReachable() {
        var applied:WallFilters?=null
        rule.setContent {PhotoTubeTheme {
            val context=LocalContext.current
            val server=remember {PhotoTubeServer(context,LocalMockData.BASE,{})}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.size(340.dp,360.dp)) {
                    FilterMenu(rememberLayerBackdrop(),CornerInsets(16.dp,16.dp,16.dp,16.dp),.4f,
                        WallFilters(takenFrom="2026-09-03T00:00:00Z",takenTo="2026-09-01T00:00:00Z"),
                        onChange={applied=it},onDismiss={},server=server)
                }
            }
        }}
        rule.onNodeWithText("日期、目录与标签").performScrollTo().performClick()
        rule.onNodeWithText("应用").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("结束日期不能早于开始日期").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("重置高级条件").performScrollTo().performClick()
        rule.onNodeWithText("应用").assertIsEnabled()
        rule.onNodeWithText("开始").performScrollTo().performClick()
        rule.onNodeWithText("输入日期").performScrollTo().performClick()
        rule.onNodeWithContentDescription("输入日期").performScrollTo().performTextReplacement("2026-02-30")
        rule.onNodeWithText("选择日期").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription("输入日期").performScrollTo().performTextReplacement("2026-09-01")
        rule.onNodeWithText("选择日期").performScrollTo().performClick()
        try {rule.waitUntil(5000) {rule.onNodeWithText("应用").isDisplayed()}} catch(error:Throwable) {
            val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"filter-layout-failure.png"))
            rule.onRoot(useUnmergedTree=true).printToLog("FilterLayout")
            throw error
        }
        rule.onNodeWithText("应用").assertIsDisplayed().performClick()
        rule.runOnIdle {assertNotNull(applied?.takenFrom);assertNull(applied?.takenTo)}
    }
}
