package com.yunai.phototube.ui.media

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

internal const val SourceHandoffFraction = .08f

/** Scoped to one themed UI tree; no bitmap or account data is retained. */
internal class MediaSourceHandoff {
    var source by mutableStateOf<Rect?>(null)
    var opacity by mutableStateOf<(() -> Float)?>(null)
}
internal val LocalMediaSourceHandoff = staticCompositionLocalOf<MediaSourceHandoff?> { null }

/** Keep layout/bounds alive while the dialog owns the source's pixels. */
@Composable
internal fun Modifier.mediaTransitionSource(): Modifier {
    val handoff = LocalMediaSourceHandoff.current ?: return this
    var frame by remember { mutableStateOf<Rect?>(null) }
    return onGloballyPositioned { frame = it.boundsInWindow() }.graphicsLayer {
        alpha = if (frame != null && frame == handoff.source) handoff.opacity?.invoke() ?: 1f else 1f
    }
}
