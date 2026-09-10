package com.yunai.phototube.ui.media

import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.yunai.phototube.R
import kotlinx.coroutines.delay

@Stable
internal class VideoPlayback(val player: ExoPlayer) {
    var playing by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var muted by mutableStateOf(true)
    var speed by mutableFloatStateOf(1f)
    var wantsPlay by mutableStateOf(true)
    fun toggle() {
        wantsPlay = !playing
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = wantsPlay
    }
    fun toggleSound() { muted = !muted; player.volume = if(muted) 0f else 1f }
    fun changeSpeed(value: Float) { speed = value; player.setPlaybackSpeed(value) }
    fun retry() { error = null; buffering = true; player.prepare(); player.playWhenReady = wantsPlay }
}
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberVideoPlayback(photo: Photo, active: Boolean): VideoPlayback {
    val context = LocalContext.current
    val view = LocalView.current
    var savedPosition by rememberSaveable(photo.id) { mutableLongStateOf(0L) }
    var savedPlay by rememberSaveable(photo.id) { mutableStateOf(true) }
    var savedMuted by rememberSaveable(photo.id) { mutableStateOf(true) }
    var savedSpeed by rememberSaveable(photo.id) { mutableFloatStateOf(1f) }
    val playback = remember(photo.id) { VideoPlayback(ExoPlayer.Builder(context).apply {
        photo.remote?.let { source -> setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(source.server.client))) }
    }.build().apply {
        setMediaItem(MediaItem.fromUri(photo.remote?.let {it.server.original(it.asset.id)} ?: "android.resource://${context.packageName}/${photo.id}"))
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
        volume = if(savedMuted) 0f else 1f
        seekTo(savedPosition)
        setPlaybackSpeed(savedSpeed)
    }).apply { wantsPlay=savedPlay; muted=savedMuted; speed=savedSpeed; position=savedPosition } }
    val isActive by rememberUpdatedState(active)
    DisposableEffect(playback, view) {
        val player = playback.player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                playback.position=player.currentPosition
                playback.playing=value
            }
            override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int) {
                playback.position=player.currentPosition
            }
            override fun onRenderedFirstFrame() { playback.ready = true }
            override fun onPlaybackStateChanged(state: Int) {
                playback.buffering = state == Player.STATE_BUFFERING
                if(player.duration > 0) playback.duration = player.duration
                if(state == Player.STATE_ENDED) playback.wantsPlay = false
            }
            override fun onPlayerError(error: PlaybackException) { playback.error = "暂时无法播放这段视频"; playback.buffering = false }
        }
        player.addListener(listener)
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
            if(event == Lifecycle.Event.ON_RESUME && isActive && playback.wantsPlay) player.play()
        }
        lifecycle?.addObserver(observer)
        player.prepare()
        onDispose { lifecycle?.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(active, playback) {
        val resumed = view.findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        playback.player.playWhenReady = active && resumed && playback.wantsPlay
    }
    // Coarse time labels and restoration only. Visible progress draws on each frame;
    // all live video pixels go through TextureView.
    LaunchedEffect(playback) { while(true) {
        playback.position = playback.player.currentPosition
        savedPosition=playback.position
        delay(200)
    } }
    LaunchedEffect(playback) {
        snapshotFlow { Triple(playback.wantsPlay,playback.muted,playback.speed) }.collect { (play,muted,speed) ->
            savedPlay=play; savedMuted=muted; savedSpeed=speed
        }
    }
    return playback
}
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoSurface(playback: VideoPlayback, modifier: Modifier) {
    AndroidView(modifier = modifier.graphicsLayer { alpha = .999f }, factory = { context ->
        (LayoutInflater.from(context).inflate(R.layout.video_glass_player, android.widget.FrameLayout(context), false) as PlayerView).apply {
            check(videoSurfaceView is TextureView)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player = playback.player
        }
    }, update = { it.player = playback.player }, onRelease = { it.player = null })
}
