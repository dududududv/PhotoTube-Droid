package com.yunai.phototube.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class GlassAppearance(
    val blur: Float = 2f,
    val refractionHeight: Float = 12f,
    val refractionAmount: Float = 24f,
    val dispersion: Boolean = false
)

val LocalGlassAppearance = staticCompositionLocalOf { GlassAppearance() }
