package com.yunai.phototube.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.ui.theme.*

internal data class GlassMenuAction(val label: String, val enabled: Boolean = true,
    val destructive: Boolean = false, val section: String? = null,
    val icon: ImageVector? = null, val onClick: () -> Unit)

internal data class GlassMenuPlacement(val offset: IntOffset, val origin: TransformOrigin)

/** Keep the menu on screen and grow from the closest point to its trigger. */
internal fun glassMenuPlacement(anchor: IntRect, window: IntSize, direction: LayoutDirection, size: IntSize, margin: Int = 0): GlassMenuPlacement {
    val preferredX = if(direction == LayoutDirection.Ltr) anchor.right - size.width else anchor.left
    val x = preferredX.coerceIn(margin, (window.width - size.width - margin).coerceAtLeast(margin))
    val preferredY = when {
        anchor.bottom + size.height <= window.height - margin -> anchor.bottom
        anchor.top >= size.height + margin -> anchor.top - size.height
        else -> anchor.bottom
    }
    val y = preferredY.coerceIn(margin, (window.height - size.height - margin).coerceAtLeast(margin))
    val pivotX = ((anchor.center.x - x).toFloat() / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
    val pivotY = ((anchor.center.y - y).toFloat() / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
    return GlassMenuPlacement(IntOffset(x, y), TransformOrigin(pivotX, pivotY))
}

/** Anchored overlay with one transition owner; action callbacks run after dismissal. */
@Composable
internal fun GlassActionMenu(expanded: Boolean, onDismiss: () -> Unit, backdrop: Backdrop,
    actions: List<GlassMenuAction>, media: Boolean = false,
    controls: (@Composable (enabled: Boolean) -> Unit)? = null) {
    if (!expanded) return
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    val dismiss by rememberUpdatedState(onDismiss)
    val colors = AppTheme.colors
    val glass = LocalGlassAppearance.current
    val foreground = if (media) MediaColors.text else colors.text
    val maxHeight = (LocalConfiguration.current.screenHeightDp - 100).coerceAtLeast(120).dp
    LaunchedEffect(closing) {
        if (!closing) progress.animateTo(1f, spring(1f, 500f))
        else { progress.animateTo(0f, tween(140)); dismiss(); action?.invoke() }
    }
    val edgeMargin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val position = remember(edgeMargin) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            // The popup window itself may be clamped again by Android's visible frame.
            // Keep the glass inset inside that window, rather than relying on coordinates alone.
            val placement = glassMenuPlacement(anchorBounds, windowSize, layoutDirection, popupContentSize)
            origin = TransformOrigin(
                ((anchorBounds.center.x - placement.offset.x - edgeMargin).toFloat() / (popupContentSize.width - 2 * edgeMargin).coerceAtLeast(1)).coerceIn(0f, 1f),
                ((anchorBounds.center.y - placement.offset.y - edgeMargin).toFloat() / (popupContentSize.height - 2 * edgeMargin).coerceAtLeast(1)).coerceIn(0f, 1f))
            return placement.offset
        }
    } }
    Popup(popupPositionProvider = position, onDismissRequest = { closing = true },
        properties = PopupProperties(focusable = true)) {
      Box(Modifier.testTag("glass-menu-safe-frame").padding(8.dp)) {
        Column(Modifier.widthIn(max = 280.dp).width(280.dp).heightIn(max = maxHeight)
            .graphicsLayer { alpha = progress.value; scaleX = .86f + .14f * progress.value; scaleY = scaleX; transformOrigin = origin }
            .drawBackdrop(backdrop, shape = { RoundedCornerShape(26.dp) },
                effects = { vibrancy(); blur(maxOf(8f, glass.blur).dp.toPx()); lens(glass.refractionHeight.dp.toPx(), glass.refractionAmount.dp.toPx(), chromaticAberration = glass.dispersion) },
                onDrawSurface = { drawRect((if (media) Color(0xFF242426) else colors.group).copy(alpha = .82f)) })
            .testTag("glass-action-menu").verticalScroll(rememberScrollState()).padding(8.dp)) {
            controls?.invoke(!closing)
            actions.forEachIndexed { index, item ->
                if (item.section != null && (index == 0 || item.section != actions[index-1].section)) {
                    Text(item.section, color = if (media) MediaColors.secondary else colors.secondary,
                        fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = if(index == 0) 6.dp else 14.dp, bottom = 4.dp))
                }
                val tint = (if (item.destructive) if (media) MediaColors.destructive else colors.destructive else foreground)
                    .copy(alpha = if (item.enabled) 1f else .4f)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = item.enabled && !closing, role = Role.Button) {
                        action = item.onClick; closing = true
                    }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.label, color = tint, modifier = Modifier.weight(1f))
                    item.icon?.let { Icon(it, null, Modifier.size(21.dp), tint = tint) }
                }
            }
        }
      }
    }
}
