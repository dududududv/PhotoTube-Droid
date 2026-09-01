package com.yunai.phototube.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PhotoTubeScheme = lightColorScheme(
    primary = PhotoTubeColors.Ink,
    onPrimary = PhotoTubeColors.Surface,
    background = PhotoTubeColors.Background,
    onBackground = PhotoTubeColors.Ink,
    surface = PhotoTubeColors.Surface,
    onSurface = PhotoTubeColors.Ink,
    secondary = PhotoTubeColors.Muted,
)

@Composable
fun PhotoTubeDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotoTubeScheme,
        typography = Typography,
        content = content,
    )
}
