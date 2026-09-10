package com.yunai.phototube.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Host supplied colors. Optical highlights remain independent of the app palette. */
@Immutable
data class GlassPalette(
    val isDark: Boolean,
    val accent: Color,
    val surface: Color,
    val track: Color,
    val selection: Color,
    val navigationContent: Color,
    val navigationAccent: Color,
)

val LocalGlassPalette = staticCompositionLocalOf<GlassPalette> {
    error("Provide GlassPalette at the application theme boundary")
}
