package com.yunai.phototube.ui

import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Only tab selection uses this profile; long-press feedback remains platform-owned. */
internal class TabHaptics(private val view: View) {
    private val vibrator = view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val attributes = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
    private val effect = vibrator?.let(::supportedTabEffect)
    private var lastTick: Long? = null

    fun tick() {
        val effect = effect ?: return
        if (!view.isAttachedToWindow || !view.hasWindowFocus() || !view.isHapticFeedbackEnabled) return
        val now = SystemClock.uptimeMillis()
        if (lastTick?.let { now - it < 80 } == true) return
        lastTick = now
        vibrator?.vibrate(effect, attributes)
    }
}

private fun supportedTabEffect(vibrator: Vibrator): VibrationEffect? {
    if (!vibrator.hasVibrator()) return null
    // Native primitives have device-tuned transients and no generic waveform fallback.
    val primitives = vibrator.arePrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
    if (primitives[0]) return VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.55f).compose()
    if (primitives[1]) return VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.35f).compose()
    // Unknown/unsupported predefined effects may become a buzzy on/off vibration.
    if (vibrator.areEffectsSupported(VibrationEffect.EFFECT_TICK).firstOrNull() == Vibrator.VIBRATION_EFFECT_SUPPORT_YES)
        return VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    return null
}

@Composable
internal fun rememberTabHaptics(): TabHaptics {
    val view = LocalView.current
    return remember(view) { TabHaptics(view) }
}
