package com.yunai.phototube.glass

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

/** Shared optical rim: avoid additive white outlines over bright source pixels. */
fun glassHighlight(dark: Boolean, intensity: Float = 1f) = Highlight(
    width = .5.dp,
    alpha = (if (dark) .7f else .5f) * intensity.coerceIn(0f, 1f),
    style = HighlightStyle.Default(blendMode = BlendMode.SrcOver)
)
