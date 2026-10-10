package com.yunai.phototube

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassSwitchRow
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pure local UI fixtures; no server, media, account or session. */
class GlassSettingsControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun disabledControlsBlockTouchAndResumeWhenEnabled() {
        val enabled = mutableStateOf(false)
        val checked = mutableStateOf(false)
        var changes = 0
        var saves = 0
        rule.setContent {
            PhotoTubeTheme {
                Column {
                    GlassSwitchRow("合成开关", checked.value, { checked.value = it; changes++ }, enabled.value)
                    GlassSettingsAction("合成保存", { saves++ }, enabled.value)
                }
            }
        }
        rule.onNode(isToggleable()).assertIsNotEnabled().performTouchInput { click() }
        rule.onNodeWithText("合成保存").assertIsNotEnabled().performTouchInput { click() }
        rule.runOnIdle { assertEquals(0, changes); assertEquals(0, saves); enabled.value = true }
        rule.onNode(isToggleable()).assertIsEnabled().performClick().assertIsOn()
        rule.onNodeWithText("合成保存").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, changes); assertEquals(1, saves) }
    }
    @Test fun cancelledSwitchPressAndDragDoNotChangeSetting() {
        val checked=mutableStateOf(false)
        var changes=0
        rule.setContent {PhotoTubeTheme {
            GlassSwitchRow("合成开关",checked.value,{checked.value=it;changes++})
        }}
        val toggle=rule.onNode(isToggleable())
        toggle.performTouchInput {down(center);cancel()}
        rule.waitForIdle()
        toggle.assertIsOff()
        rule.runOnIdle {assertEquals(0,changes)}
        toggle.performTouchInput {
            down(center)
            moveTo(center+androidx.compose.ui.geometry.Offset(100f,0f),200)
            cancel()
        }
        rule.waitForIdle()
        toggle.assertIsOff()
        rule.runOnIdle {assertEquals(0,changes)}
        toggle.performTouchInput {click()}
        toggle.assertIsOn()
        rule.runOnIdle {assertEquals(1,changes)}
    }
    @Test fun sliderCancelKeepsLiveValueWithoutAnotherCallback() {
        val value=mutableStateOf(.25f)
        var changes=0
        rule.setContent {PhotoTubeTheme {
            com.yunai.phototube.glass.LiquidSlider({value.value},{value.value=it;changes++},0f..1f,
                .001f,com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
                androidx.compose.ui.Modifier.width(280.dp).testTag("live-slider"))
        }}
        val slider=rule.onNodeWithTag("live-slider")
        slider.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width*.25f,centerY))
            moveTo(androidx.compose.ui.geometry.Offset(width*.65f,centerY),200)
        }
        rule.waitForIdle()
        var before=0f
        var calls=0
        rule.runOnIdle {before=value.value;calls=changes;org.junit.Assert.assertTrue(before>.25f)}
        slider.performTouchInput {cancel()}
        rule.waitForIdle()
        rule.runOnIdle {assertEquals(before,value.value,.001f);assertEquals(calls,changes)}
        slider.performTouchInput {down(androidx.compose.ui.geometry.Offset(width*before,centerY));cancel()}
        rule.waitForIdle()
        rule.runOnIdle {assertEquals(before,value.value,.001f);assertEquals(calls,changes)}
    }
    @Test fun disabledSliderRejectsTouchAndAccessibilityChanges() {
        val enabled = mutableStateOf(false)
        val value = mutableStateOf(.25f)
        rule.setContent {
            PhotoTubeTheme {
                com.yunai.phototube.glass.LiquidSlider({ value.value }, { value.value = it }, 0f..1f,
                    .001f, com.kyant.backdrop.backdrops.rememberLayerBackdrop(),
                    androidx.compose.ui.Modifier.width(280.dp).testTag("fixture-slider"), enabled.value)
            }
        }
        rule.onNodeWithTag("fixture-slider").assertIsNotEnabled().performTouchInput { click(centerRight) }
        rule.onNodeWithTag("fixture-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {
            org.junit.Assert.assertFalse(it(.8f))
        }
        rule.runOnIdle { assertEquals(.25f, value.value, .001f); enabled.value = true }
        rule.onNodeWithTag("fixture-slider").assertIsEnabled().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.8f) }
        rule.runOnIdle { assertEquals(.8f, value.value, .001f) }
    }
    @Test fun largeTextWrapsWithoutClippingAndKeepsOneAction() {
        val label = "合成范围确认与继续操作"
        var clicks = 0
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                PhotoTubeTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(180.dp)) {
                        GlassSettingsAction(label, { clicks++ }, modifier = androidx.compose.ui.Modifier.testTag("large-action"))
                    }
                }
            }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
        ) { it(layouts) }
        org.junit.Assert.assertTrue(layouts.single().lineCount > 1)
        org.junit.Assert.assertFalse(layouts.single().hasVisualOverflow)
        rule.onNodeWithTag("large-action").assertHeightIsAtLeast(72.dp).performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

}
