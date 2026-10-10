package com.yunai.phototube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.ui.GlassConfirmation
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Entirely synthetic Compose scene; no API, account, or media access. */
class GlassConfirmationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun darkConfirmationRetainsReadableActionsOnStrongBackground() = checkMaterial(false)
    @Test fun mediaConfirmationRetainsReadableActionsInLightTheme() = checkMaterial(true)

    private fun checkMaterial(media:Boolean) {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        var confirmed=0
        var dismissed=0
        val visible=mutableStateOf(true)
        rule.setContent {PhotoTubeTheme {
            val colors=com.yunai.phototube.ui.theme.AppTheme.colors
            val palette=com.yunai.phototube.glass.LocalGlassPalette.current
            CompositionLocalProvider(
                com.yunai.phototube.ui.theme.LocalAppColors provides if(media) colors else colors.copy(group=Color(0xFF1C1C1E),text=Color(0xFFF3F3F0),destructive=com.yunai.phototube.ui.theme.MediaColors.destructive),
                com.yunai.phototube.glass.LocalGlassPalette provides palette.copy(isDark=!media)
            ) {
                val backdrop=rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFFFF00FF)))
                if(visible.value) GlassConfirmation(backdrop,"移入回收站？","本地合成内容，可以恢复。","确认移入回收站",
                    onConfirm={confirmed++;visible.value=false},onDismiss={dismissed++;visible.value=false},
                    destructive=true,media=media)
            }
        }}
            rule.onNodeWithText("确认移入回收站").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("取消").assertIsDisplayed().assertIsEnabled()
            rule.waitForIdle()
            val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            java.io.File(context.getExternalFilesDir(null),if(media) "mock-confirmation-media.png" else "mock-confirmation-dark.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
            rule.onNodeWithText("取消").performClick()
            rule.waitForIdle()
            assertEquals(0,confirmed);assertEquals(1,dismissed)

    }

    @Test fun largeTextKeepsActionsVisibleAndDismissDoesNotConfirm() = checkLargeText()

    @Test fun landscapeLargeTextKeepsActionsVisible() {
        val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        try {
            device.setOrientationLeft()
            device.waitForIdle()
            org.junit.Assert.assertTrue(device.displayWidth > device.displayHeight)
            checkLargeText()
        } finally { device.setOrientationNatural(); device.unfreezeRotation() }
    }

    private fun checkLargeText() {
        var confirmations = 0
        var dismissals = 0
        val visible = mutableStateOf(true)
        rule.setContent {
            PhotoTubeTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF698496)))
                    if (visible.value) GlassConfirmation(backdrop, "放弃未保存的修改？",
                        "本地合成说明。".repeat(80), "放弃修改", cancelLabel = "继续编辑", destructive = true,
                        onConfirm = { confirmations++; visible.value = false },
                        onDismiss = { dismissals++; visible.value = false })
                }
            }
        }
        rule.onNodeWithText("放弃修改").assertIsDisplayed()
        rule.onNodeWithText("继续编辑").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("glass-confirmation").assertDoesNotExist()
        assertEquals(0, confirmations)
        assertEquals(1, dismissals)
    }

    @Test fun sheetSmallDragReboundsAndLongDragDismissesOnce() {
        var dismissals = 0
        var confirmations = 0
        val visible = mutableStateOf(true)
        rule.setContent {
            PhotoTubeTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF698496)))
                if (visible.value) GlassConfirmation(backdrop, "合成信息", "仅本地数据", "关闭",
                    cancelLabel = null, alignment = androidx.compose.ui.Alignment.BottomCenter, draggable = true,
                    onConfirm = { confirmations++ }, onDismiss = { dismissals++; visible.value = false })
            }
        }
        val before = rule.onNodeWithTag("glass-confirmation").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("glass-sheet-handle").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 25f), 600)
        }
        rule.waitForIdle()
        assertEquals(0, dismissals)
        assertEquals(before, rule.onNodeWithTag("glass-confirmation").fetchSemanticsNode().boundsInRoot.top, 2f)
        rule.onNodeWithTag("glass-sheet-handle").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 220f), 600)
        }
        rule.waitForIdle()
        assertEquals(1, dismissals)
        assertEquals(0, confirmations)
        rule.onNodeWithTag("glass-confirmation").assertDoesNotExist()
    }

    @Test fun confirmationFreezesOptionsDuringExit() {
        val visible = mutableStateOf(true)
        val selected = mutableStateOf(false)
        var submitted: Boolean? = null
        rule.setContent {
            PhotoTubeTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF698496)))
                if (visible.value) GlassConfirmation(backdrop, "合成确认", "选项应在退出时锁定。", "确认",
                    onConfirm = { submitted = selected.value; visible.value = false },
                    onDismiss = { visible.value = false }, details = { enabled ->
                        androidx.compose.material3.Checkbox(selected.value, { selected.value = it }, enabled = enabled)
                    })
            }
        }
        rule.onNode(isToggleable()).performClick().assertIsOn()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("确认").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNode(isToggleable()).assertIsNotEnabled().performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(true, submitted)
    }

    @Test fun formWaitsForSuccessAndClosesWithoutSubmittingAgain() {
        val visible = mutableStateOf(true)
        val busy = mutableStateOf(false)
        val success = mutableStateOf(false)
        var submissions = 0
        var dismissals = 0
        rule.setContent {
            PhotoTubeTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF698496)))
                if(visible.value) GlassConfirmation(backdrop, "本地表单", "合成保存状态", "保存",
                    busy=busy.value, closeOnConfirm=false, closeRequested=success.value,
                    onConfirm={submissions++;busy.value=true}, onDismiss={dismissals++;visible.value=false})
            }
        }
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("保存").assertIsNotEnabled().performClick()
        rule.onNodeWithText("取消").assertIsNotEnabled().performClick()
        assertEquals(1,submissions)
        assertEquals(0,dismissals)
        rule.runOnIdle { success.value=true }
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        rule.mainClock.autoAdvance=false
        rule.runOnIdle { busy.value=false }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("保存").performClick()
        assertEquals(1,submissions)
        rule.mainClock.advanceTimeBy(300)
        rule.mainClock.autoAdvance=true
        rule.waitForIdle()
        assertEquals(1,dismissals)
        rule.onNodeWithTag("glass-confirmation").assertDoesNotExist()
    }

    @Test fun rapidConfirmIsDeliveredOnceAfterExit() {
        var confirmations = 0
        val visible = mutableStateOf(true)
        rule.setContent {
            PhotoTubeTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFF698496)))
                if (visible.value) GlassConfirmation(backdrop, "本地确认", "合成场景，无服务调用。", "确认",
                    onConfirm = { confirmations++; visible.value = false }, onDismiss = { visible.value = false })
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("确认").performClick()
        rule.onNodeWithText("确认").performClick()
        assertEquals(0, confirmations)
        rule.mainClock.advanceTimeBy(300)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1, confirmations)
        rule.onNodeWithTag("glass-confirmation").assertDoesNotExist()
    }
}
