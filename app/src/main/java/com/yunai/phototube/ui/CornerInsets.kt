package com.yunai.phototube.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CornerInsets(val left: Dp, val right: Dp, val top: Dp, val bottom: Dp)

/** Compact edge margins; system clearance only affects its own edge. */
@Composable
internal fun rememberCornerInsets(): CornerInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Screen-edge chrome must not inherit IME height. Search owns imePadding
    // separately; safeDrawing would lift tabs and count keyboard clearance twice.
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.waterfall)
    return with(density) {
        CornerInsets(
            left = maxOf(16.dp, safe.getLeft(density, direction).toDp() + 4.dp),
            right = maxOf(16.dp, safe.getRight(density, direction).toDp() + 4.dp),
            top = safe.getTop(density).toDp() + 4.dp,
            bottom = safe.getBottom(density).toDp() + 4.dp
        )
    }
}
