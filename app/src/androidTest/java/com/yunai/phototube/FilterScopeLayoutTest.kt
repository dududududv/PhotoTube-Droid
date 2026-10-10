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
    @Test fun shortWindowImmediateDatesRejectInvalidValues() {
        var applied=WallFilters(takenTo="2026-09-02T23:59:59Z")
        rule.setContent {PhotoTubeTheme {
            val context=LocalContext.current
            val server=remember {PhotoTubeServer(context,LocalMockData.BASE,{})}
            val density=LocalDensity.current
            var current by remember {mutableStateOf(applied)}
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                Box(Modifier.size(340.dp,360.dp)) {
                    FilterMenu(rememberLayerBackdrop(),CornerInsets(16.dp,16.dp,16.dp,16.dp),.4f,current,
                        onChange={applied=it;current=it},onDismiss={},server=server)
                }
            }
        }}
        rule.onNodeWithText("日期、目录与标签").performScrollTo().performClick()
        rule.onNodeWithText("应用").assertDoesNotExist()
        rule.onNodeWithText("开始").performScrollTo().performClick()
        rule.onNodeWithText("输入日期").performScrollTo().performClick()
        rule.onNodeWithContentDescription("输入日期").performScrollTo().performTextReplacement("2026-02-30")
        rule.onNodeWithContentDescription("输入日期").performImeAction()
        rule.runOnIdle {assertNull(applied.takenFrom)}
        rule.onNodeWithContentDescription("输入日期").performScrollTo().performTextReplacement("2026-09-04")
        rule.onNodeWithContentDescription("输入日期").performImeAction()
        rule.runOnIdle {assertNull(applied.takenFrom)}
        rule.onNodeWithContentDescription("输入日期").performScrollTo().performTextReplacement("2026-09-01")
        rule.onNodeWithContentDescription("输入日期").performImeAction()
        rule.runOnIdle {assertNotNull(applied.takenFrom);assertNotNull(applied.takenTo)}
        rule.onNodeWithText("重置高级条件").performScrollTo().performClick()
        rule.runOnIdle {assertFalse(applied.hasScope)}
        rule.onNodeWithText("完成").assertDoesNotExist()
    }
}
