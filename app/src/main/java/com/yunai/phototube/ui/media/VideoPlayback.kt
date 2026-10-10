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
import androidx.media3.common.Timeline
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
internal class VideoPlayback(val player: ExoPlayer, private val preferences: com.yunai.phototube.data.PlaybackPreferences? = null) {
    var playing by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var muted by mutableStateOf(true)
    var volume by mutableFloatStateOf(preferences?.volume ?: 1f)
    var speed by mutableFloatStateOf(1f)
    var wantsPlay by mutableStateOf(true)
    internal var interactionActive=false
    internal var released=false
    fun resumeAfterSeek() {
        if(!released) player.playWhenReady=interactionActive && wantsPlay
    }
    fun toggle() {
        wantsPlay = !wantsPlay
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = wantsPlay
    }
    fun toggleSound() { muted = !muted; preferences?.muted = muted; player.volume = if(muted) 0f else volume }
    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        muted = volume == 0f
        preferences?.volume = volume
        preferences?.muted = muted
        player.volume = if(muted) 0f else volume
    }
    fun changeSpeed(value: Float) { speed = value; player.setPlaybackSpeed(value) }
    fun retry() { error = null; buffering = true; player.prepare(); player.playWhenReady = wantsPlay }
}
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun rememberVideoPlayback(photo: Photo, active: Boolean, mediaUri:String?=null): VideoPlayback {
    val context = LocalContext.current
    val view = LocalView.current
    val preferences = remember(context) { com.yunai.phototube.data.PlaybackPreferences(context) }
    var savedPosition by rememberSaveable(photo.id,mediaUri) { mutableLongStateOf(0L) }
    var savedPlay by rememberSaveable(photo.id,mediaUri) { mutableStateOf(mediaUri != null || preferences.autoPlay) }
    var savedMuted by rememberSaveable(photo.id,mediaUri) { mutableStateOf(preferences.muted) }
    var savedSpeed by rememberSaveable(photo.id,mediaUri) { mutableFloatStateOf(1f) }
    val playback = remember(photo.id,mediaUri) { VideoPlayback(ExoPlayer.Builder(context).apply {
        photo.remote?.let { source -> setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(source.server.client))) }
    }.build().apply {
        setMediaItem(MediaItem.fromUri(mediaUri ?: photo.remote?.let {it.server.original(it.asset.id)} ?: "android.resource://${context.packageName}/${photo.id}"))
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
        volume = if(savedMuted) 0f else preferences.volume
        seekTo(savedPosition)
        setPlaybackSpeed(savedSpeed)
    }, preferences).apply { wantsPlay=savedPlay; muted=savedMuted; speed=savedSpeed; position=savedPosition } }
    val isActive by rememberUpdatedState(active)
    DisposableEffect(playback, view) {
        val player = playback.player
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // Duration is available before STATE_READY and may precede the first frame.
                // Publish it now so a restored position is not clamped to a 1 ms UI range.
                if (player.duration > 0) playback.duration = player.duration
            }
            override fun onIsPlayingChanged(value: Boolean) {
                playback.position=player.currentPosition
                playback.playing=value
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // System stops must survive returning to the foreground. Our own
                // lifecycle/seek pauses use USER_REQUEST and retain the user's intent.
                if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
                    playback.wantsPlay = false
                }
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
        playback.interactionActive=isActive && lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)==true
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playback.interactionActive=false
                savedPosition=player.currentPosition
                savedPlay=playback.wantsPlay; savedMuted=playback.muted; savedSpeed=playback.speed
                player.pause()
            }
            if(event == Lifecycle.Event.ON_RESUME) {
                playback.interactionActive=isActive
                playback.resumeAfterSeek()
            }
        }
        lifecycle?.addObserver(observer)
        player.prepare()
        onDispose { playback.interactionActive=false;playback.released=true;lifecycle?.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(active, playback) {
        val resumed = view.findViewTreeLifecycleOwner()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        playback.interactionActive=active && resumed
        playback.resumeAfterSeek()
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
