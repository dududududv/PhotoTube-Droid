package com.yunai.phototube.ui.media

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import kotlin.math.abs

@Composable
internal fun PhotoGestures(
    photo: Photo, enabled: Boolean, onTap: () -> Unit,
    onZoomed: (Boolean) -> Unit, onDrag: (Float) -> Unit, onRelease: (Float) -> Unit
) {
    var zoom by remember(photo.id) { mutableFloatStateOf(1f) }
    var pan by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val animatedZoom by animateFloatAsState(zoom, spring(.9f, 600f), label = "photo-zoom")
    val animatedPan by animateOffsetAsState(pan,spring(.9f,600f),label="photo-pan")
    val renderedPan = if(touching) pan else animatedPan
    val scale = if(touching) zoom else animatedZoom
    val metadata = rememberMediaMetadata(photo)
    val currentTap by rememberUpdatedState(onTap)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    LaunchedEffect(zoom) { onZoomed(zoom > 1.01f) }
    LaunchedEffect(enabled) { if(!enabled) { zoom = 1f; pan = Offset.Zero } }
    fun clampPan(value: Offset, z: Float, width: Float, height: Float): Offset {
        val ratio = if(metadata != null) metadata.width.coerceAtLeast(1).toFloat() / metadata.height.coerceAtLeast(1) else width / height
        val imageWidth = minOf(width, height * ratio)
        val imageHeight = imageWidth / ratio
        return Offset(value.x.coerceIn(-maxOf(0f,(imageWidth*z-width)/2),maxOf(0f,(imageWidth*z-width)/2)),
            value.y.coerceIn(-maxOf(0f,(imageHeight*z-height)/2),maxOf(0f,(imageHeight*z-height)/2)))
    }
    Box(Modifier.fillMaxSize().testTag("photo-gesture-${photo.id}").semantics {
        stateDescription = if(zoom > 1.01f) "已放大" else "适合屏幕"
        onClick(label = "显示或隐藏工具栏") { currentTap(); true }
        customActions = listOf(CustomAccessibilityAction(if(zoom > 1f) "还原大小" else "放大照片") {
            zoom = if(zoom > 1f) 1f else 2.5f; pan = Offset.Zero; true
        })
    }.pointerInput(photo.id, enabled) {
        if(!enabled) return@pointerInput
        detectTapGestures(onTap = { currentTap() }, onDoubleTap = { point ->
            zoom = if(zoom > 1.01f) 1f else 2.5f
            pan = if(zoom == 1f) Offset.Zero else clampPan((Offset(size.width/2f,size.height/2f)-point)*(zoom-1f),zoom,size.width.toFloat(),size.height.toFloat())
        })
    }.pointerInput(photo.id, enabled, metadata) {
        if(!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var distance = Offset.Zero
            var dismissDrag = false
            var multi = false
            touching = true
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if(event.changes.any { it.isConsumed } && !multi && !dismissDrag) break
                val delta = event.calculatePan()
                if(fingers > 1) {
                    multi = true
                    if(dismissDrag) { currentDrag(0f); dismissDrag = false }
                    val old = zoom
                    zoom = (zoom * event.calculateZoom()).coerceIn(1f,5f)
                    val centroid = event.calculateCentroid(useCurrent = false) - Offset(size.width/2f,size.height/2f)
                    pan = clampPan((pan-centroid)*(zoom/old)+centroid+delta,zoom,size.width.toFloat(),size.height.toFloat())
                    event.changes.forEach { it.consume() }
                } else if(zoom > 1.01f) {
                    distance += delta
                    // Do not consume stationary down/up events: double-tap must
                    // still reach the tap recognizer while the image is zoomed.
                    if(distance.getDistance() > viewConfiguration.touchSlop) {
                        pan = clampPan(pan+delta,zoom,size.width.toFloat(),size.height.toFloat())
                        event.changes.forEach { it.consume() }
                    }
                } else if(!multi) {
                    distance += delta
                    if(!dismissDrag && abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) > abs(distance.x)*1.3f) dismissDrag = true
                    if(dismissDrag) { currentDrag(distance.y); event.changes.forEach { it.consume() } }
                }
            } while(event.changes.any { it.pressed })
            touching = false
            if(zoom <= 1.01f) { zoom = 1f; pan = Offset.Zero }
            if(dismissDrag) currentRelease(distance.y)
        }
    }) {
        MediaArtwork(photo, Modifier.fillMaxSize().graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = renderedPan.x; translationY = renderedPan.y
        }, ContentScale.Fit, description = null,original=true)
    }
}
