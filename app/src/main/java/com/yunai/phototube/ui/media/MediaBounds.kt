package com.yunai.phototube.ui.media

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/** Only attached thumbnails own transition coordinates; lazy item reuse releases old entries. */
internal fun <T : Any> Modifier.mediaBounds(id: T, bounds: MutableMap<T, Rect>): Modifier =
    this then MediaBoundsElement(id, bounds)

private class MediaBoundsElement<T : Any>(val id: T, val bounds: MutableMap<T, Rect>) :
    ModifierNodeElement<MediaBoundsNode<T>>() {
    override fun create() = MediaBoundsNode(id, bounds)
    override fun update(node: MediaBoundsNode<T>) = node.update(id, bounds)
    override fun InspectorInfo.inspectableProperties() { name = "mediaBounds" }
    override fun equals(other: Any?) = other is MediaBoundsElement<*> && id == other.id && bounds === other.bounds
    override fun hashCode() = 31 * id.hashCode() + System.identityHashCode(bounds)
}

private class MediaBoundsNode<T : Any>(var id: T, var bounds: MutableMap<T, Rect>) :
    Modifier.Node(), GlobalPositionAwareModifierNode {
    private var last: Rect? = null
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val rect = coordinates.boundsInWindow()
        last = rect
        bounds[id] = rect
    }
    fun update(id: T, bounds: MutableMap<T, Rect>) {
        if (this.id != id || this.bounds !== bounds) {
            release()
            this.id = id
            this.bounds = bounds
        }
    }
    private fun release() {
        // A replacement node may already own this ID during a layout transition.
        if (last != null && bounds[id] === last) bounds.remove(id)
        last = null
    }
    override fun onReset() = release()
    override fun onDetach() = release()
}
