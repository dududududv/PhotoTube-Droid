package com.yunai.phototube.ui

import com.yunai.phototube.ui.theme.*
import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.R
import com.yunai.phototube.glass.LiquidButton
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoGlassPreview(surfaceOpacity: Float = .4f, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ThemeSystemBars(media = true)
        val context = LocalContext.current
        val view = LocalView.current
        val scene = rememberLayerBackdrop()
        var wantsPlay by remember { mutableStateOf(true) }
        var playing by remember { mutableStateOf(false) }
        var ready by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val player = remember(context) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.glass_video}"))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
            }
        }
        val playIntent by rememberUpdatedState(wantsPlay)
        DisposableEffect(player, view) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onRenderedFirstFrame() { ready = true }
                override fun onPlayerError(exception: PlaybackException) { error = "视频暂时无法播放" }
            }
            player.addListener(listener)
            val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) player.pause()
                if (event == Lifecycle.Event.ON_START && playIntent) player.play()
            }
            lifecycle?.addObserver(observer)
            player.prepare()
            player.playWhenReady = true
            onDispose {
                lifecycle?.removeObserver(observer)
                player.removeListener(listener)
                player.release()
            }
        }
        var lensOffset by remember { mutableStateOf(Offset(24f, 260f)) }
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        var lensSize by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.fillMaxSize().background(MediaColors.background).testTag("video-root")) {
            // Capture only the video container, with the issue #82 interop workaround.
            Box(Modifier.fillMaxSize().layerBackdrop(scene)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = .999f }.testTag("video-surface"),
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.video_glass_player, null) as PlayerView).apply {
                            check(videoSurfaceView is TextureView)
                            this.player = player
                        }
                    },
                    onRelease = { it.player = null }
                )
            }
            Row(Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LiquidButton(onClose, scene, Modifier.size(48.dp).semantics { contentDescription = "关闭视频" },
                    shape = CircleShape, horizontalPadding = 0.dp, surfaceColor = MediaColors.background.copy(surfaceOpacity * .625f)) {
                    Icon(Icons.Outlined.Close, null, tint = MediaColors.text)
                }
                Spacer(Modifier.width(12.dp))
                Text("城市片段", color = MediaColors.text, fontSize = 20.sp)
            }
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 72.dp, bottom = 96.dp)
                .onSizeChanged { areaSize = it }) {
                LiquidButton({}, scene,
                    modifier = Modifier.offset { IntOffset(lensOffset.x.roundToInt(), lensOffset.y.roundToInt()) }
                        .size(180.dp, 80.dp).onSizeChanged { lensSize = it }.testTag("video-glass")
                        .pointerInput(areaSize, lensSize) {
                            detectDragGestures { change, delta ->
                                change.consume()
                                lensOffset = Offset(
                                    (lensOffset.x + delta.x).coerceIn(0f, (areaSize.width - lensSize.width).coerceAtLeast(0).toFloat()),
                                    (lensOffset.y + delta.y).coerceIn(0f, (areaSize.height - lensSize.height).coerceAtLeast(0).toFloat())
                                )
                            }
                        },
                    isInteractive = false, surfaceColor = MediaColors.text.copy(surfaceOpacity * .2f)) {
                    Text("拖动看看", color = MediaColors.text, fontSize = 16.sp)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!ready || error != null) Text(error ?: "正在加载视频…", color = MediaColors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidButton({ wantsPlay = !wantsPlay; player.playWhenReady = wantsPlay }, scene,
                        Modifier.width(132.dp).height(64.dp).testTag("video-play"), surfaceColor = MediaColors.background.copy(surfaceOpacity * .625f)) {
                        Text(if (playing) "暂停" else "播放", color = MediaColors.text)
                    }
                    LiquidButton({ player.seekTo(0) }, scene, Modifier.width(132.dp).height(64.dp), surfaceColor = MediaColors.background.copy(surfaceOpacity * .625f)) {
                        Text("重新播放", color = MediaColors.text)
                    }
                }
            }
        }
    }
}
