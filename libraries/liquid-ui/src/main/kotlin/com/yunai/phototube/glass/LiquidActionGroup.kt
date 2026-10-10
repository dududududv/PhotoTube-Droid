package com.yunai.phototube.glass

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/** One optical surface, with independently accessible actions inside it. */
@Composable
fun LiquidActionGroup(backdrop: Backdrop, surfaceColor: Color, modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit) {
    val appearance = LocalGlassAppearance.current
    val dark = LocalGlassPalette.current.isDark
    Row(modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { RoundedCornerShape(50) },
        effects = {
            vibrancy()
            blur(appearance.blur.dp.toPx())
            lens(appearance.refractionHeight.dp.toPx(), appearance.refractionAmount.dp.toPx(),
                chromaticAberration = appearance.dispersion)
        },
        highlight = { glassHighlight(dark) },
        shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = if (dark) .16f else .055f)) },
        onDrawSurface = { drawRect(surfaceColor) }
    ), verticalAlignment = Alignment.CenterVertically, content = content)
}
