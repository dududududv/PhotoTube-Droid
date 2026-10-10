package com.yunai.phototube.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.glass.LocalGlassPalette
import com.yunai.phototube.glass.glassHighlight
import com.yunai.phototube.ui.theme.AppTheme

/** One selection surface shared by equal-width options, rather than adjacent chips. */
@Composable
internal fun <T> GlassSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(options.isNotEmpty())
    val colors = AppTheme.colors
    val palette = LocalGlassPalette.current
    val appearance = LocalGlassAppearance.current
    val backdrop = rememberLayerBackdrop()
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), spring(1f, 450f), label = "segment-selection")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f)) {
        Box(Modifier.matchParentSize().clip(CircleShape).layerBackdrop(backdrop).background(colors.subtle))
        Box(Modifier.fillMaxWidth().padding(4.dp)) {
            Box(Modifier.matchParentSize().graphicsLayer {
                val segmentWidth = size.width / options.size
                translationX = (if (rtl) -position else position) * segmentWidth
            }) {
                Box(Modifier.fillMaxWidth(1f / options.size).fillMaxHeight().drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        blur(appearance.blur.dp.toPx())
                        lens(appearance.refractionHeight.dp.toPx(), appearance.refractionAmount.dp.toPx(),
                            chromaticAberration = appearance.dispersion)
                    },
                    highlight = { glassHighlight(palette.isDark) },
                    onDrawSurface = { drawRect(colors.group.copy(alpha = .8f)) },
                ))
            }
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { (value, label) ->
                    Box(Modifier.weight(1f).clip(CircleShape)
                        .selectable(value == selected, enabled = enabled, role = Role.RadioButton,
                            onClick = { onSelect(value) })
                        .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, color = if (value == selected) colors.text else colors.secondary,
                            fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
