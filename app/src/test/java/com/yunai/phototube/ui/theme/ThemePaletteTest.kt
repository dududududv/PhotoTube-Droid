package com.yunai.phototube.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun allAppearanceCombinationsShareSemanticColors() {
        for (dark in listOf(false, true)) {
            for (accent in ThemeAccent.entries) {
                val palette = createThemePalette(dark, accent)
                val expectedAccent = if (dark) accent.dark else accent.light
                assertEquals(expectedAccent, palette.colors.accent)
                assertEquals(expectedAccent, palette.scheme.primary)
                assertEquals(expectedAccent, palette.glass.accent)
                assertEquals(palette.colors.group, palette.scheme.surface)
                assertEquals(palette.colors.text, palette.scheme.onSurface)
                assertEquals(palette.colors.text, palette.glass.navigationContent)
                assertEquals(palette.colors.glass, palette.glass.surface)
                assertEquals(dark, palette.glass.isDark)
            }
        }
    }

    @Test
    fun switchingAppearanceDoesNotMutatePreviousPalette() {
        val light = createThemePalette(false, ThemeAccent.BLUE)
        val dark = createThemePalette(true, ThemeAccent.ROSE)
        assertEquals(Color(0xFFF9F9FB), light.colors.background)
        assertEquals(Color(0xFF111312), dark.colors.background)
        assertEquals(ThemeAccent.BLUE.light, light.scheme.primary)
        assertEquals(ThemeAccent.ROSE.dark, dark.scheme.primary)
        assertEquals(Color.White, light.scheme.onPrimary)
        assertEquals(Color.Black, dark.scheme.onPrimary)
    }
}
