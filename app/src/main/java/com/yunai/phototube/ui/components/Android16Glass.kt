package com.yunai.phototube.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState

private val LocalAndroid16HazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun rememberAndroid16HazeState(): HazeState = rememberHazeState()

@Composable
fun Android16HazeProvider(
    state: HazeState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAndroid16HazeState provides state, content = content)
}

/**
 * Android 16 专用背景毛玻璃。
 *
 * hazeEffect 对同一 HazeState 中 hazeSource 捕获的背景执行空间模糊，组件内容随后清晰绘制。
 * 项目最低版本为 API 36，因此这里有意不提供低版本色块降级。
 */
fun Modifier.android16Glass(
    cornerRadius: Dp,
    strength: Float = 0.68f,
    tint: Color = Color.White,
    showBorder: Boolean = true,
): Modifier = composed {
    val hazeState = checkNotNull(LocalAndroid16HazeState.current) {
        "android16Glass 必须位于 Android16HazeProvider 中"
    }
    val normalizedStrength = strength.coerceIn(0f, 1f)
    val tintAlpha = 0.18f + normalizedStrength * 0.38f
    val blurRadius = 22.dp + 18.dp * normalizedStrength
    val style = HazeStyle(
        backgroundColor = Color(0xFFF8F9FB),
        tints = listOf(HazeTint(tint.copy(alpha = tintAlpha))),
        blurRadius = blurRadius,
        noiseFactor = 0.06f,
    )

    val glass = clip(RoundedCornerShape(cornerRadius))
        .hazeEffect(state = hazeState, style = style)

    if (!showBorder) {
        glass
    } else {
        glass.drawWithCache {
            val radiusPx = cornerRadius.toPx()
            val borderWidth = 1.dp.toPx()
            onDrawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.94f),
                        0.50f to Color.White.copy(alpha = 0.48f),
                        1f to Color.White.copy(alpha = 0.20f),
                    ),
                    cornerRadius = CornerRadius(radiusPx),
                    style = Stroke(borderWidth),
                )
            }
        }
    }
}
