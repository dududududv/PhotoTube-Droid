package com.yunai.phototube.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yunai.phototube.glass.GlassPalette

internal data class ThemePalette(
    val colors: AppColors,
    val scheme: ColorScheme,
    val glass: GlassPalette,
)

// Keep wide Color argument construction outside Compose-generated restart code.
// The previous combined method produced a startup VerifyError on Android.
internal fun createThemePalette(dark: Boolean, themeAccent: ThemeAccent): ThemePalette {
    val accent = if (dark) themeAccent.dark else themeAccent.light
    val colors = createAppColors(dark, accent)
    return ThemePalette(colors, createMaterialScheme(dark, colors), createGlassPalette(dark, colors))
}

private fun createAppColors(dark: Boolean, accent: Color): AppColors {
    return if (dark) AppColors(
        Color(0xFF111312), Color.Black, Color(0xFF1C1C1E),
        Color(0xFFF3F3F0), Color(0xFFACACB3), Color(0xFF121212), Color.White.copy(.1f),
        accent, accent.copy(.18f), Color.White.copy(.06f), MediaColors.destructive, Color(0xFFE3BC7A)
    ) else AppColors(
        Color(0xFFF9F9FB), Color(0xFFF2F2F7), Color.White,
        Color(0xFF1C1C1E), Color(0xFF626269), Color(0xFFFAFAFA), Color.Black.copy(.1f),
        accent, accent.copy(.12f), Color.Black.copy(.04f), Color(0xFFBA262B), Color(0xFF805513)
    )
}

private fun createGlassPalette(dark: Boolean, colors: AppColors): GlassPalette {
    val accent = colors.accent
    return GlassPalette(dark, accent, colors.glass,
            colors.secondary.copy(if(dark) .36f else .2f), colors.text.copy(.04f),
            navigationContent = colors.text,
            // Navigation sits over photographs, so its accent needs stronger tonal
            // separation than the same color on an opaque settings surface.
            navigationAccent = lerp(accent, if (dark) Color.White else Color.Black, if (dark) .25f else .45f))
}

private fun createMaterialScheme(dark: Boolean, colors: AppColors): ColorScheme {
    val accent = colors.accent
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
            primary = accent, onPrimary = if(dark) Color.Black else Color.White,
            secondary = accent, tertiary = accent,
            onSecondary = if(dark) Color.Black else Color.White,
            onTertiary = if(dark) Color.Black else Color.White,
            secondaryContainer = colors.selected, onSecondaryContainer = colors.text,
            tertiaryContainer = colors.selected, onTertiaryContainer = colors.text,
            background = colors.background,
            onBackground = colors.text, surface = colors.group, onSurface = colors.text,
            onSurfaceVariant = colors.secondary, surfaceVariant = colors.groupedBackground,
            primaryContainer = colors.selected, onPrimaryContainer = colors.text,
            surfaceContainerLowest = colors.background, surfaceContainerLow = colors.group,
            surfaceContainer = colors.group, surfaceContainerHigh = colors.group,
            surfaceContainerHighest = colors.group, surfaceBright = colors.group,
            surfaceDim = colors.groupedBackground, surfaceTint = accent,
            outline = colors.secondary, outlineVariant = colors.separator
        )
}
