package com.yunai.phototube.ui.theme

import androidx.compose.ui.graphics.Color

/** Semantic illustration colors: independent of the user's interaction accent. */
enum class SettingsSymbol(val top: Color, val bottom: Color) {
    FAVORITES(Color(0xFFFF6481), Color(0xFFEC3257)),
    CALENDAR(Color(0xFF58AFFF), Color(0xFF147BEA)),
    HAPTICS(Color(0xFFFF6E98), Color(0xFFE83C70)),
    APPEARANCE(Color(0xFF55B5FF), Color(0xFF2479EF)),
    VIDEO(Color(0xFFFFB653), Color(0xFFF38A1D)),
    ABOUT(Color(0xFFA5ACB5), Color(0xFF6B7582))
}
object SettingsIllustrationColors {
    val ink = Color.White
    val shade = Color.Black.copy(.12f)
    val warm = Color(0xFFFFE6A3)
    val cool = Color(0xFFE2F1FF)
    val rose = Color(0xFFFFE5ED)
}
