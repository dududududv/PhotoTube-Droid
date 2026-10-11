package com.yunai.phototube.ui.media

import com.yunai.phototube.ui.GlassConfirmation
import com.yunai.phototube.ui.GlassActionMenu
import com.yunai.phototube.ui.GlassMenuAction
import com.yunai.phototube.glass.LiquidSlider
import com.yunai.phototube.glass.LocalGlassPalette

import com.yunai.phototube.data.photoTags
import com.yunai.phototube.data.AssetActions
import com.yunai.phototube.data.AssetAction

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.yunai.phototube.glass.LocalGlassAppearance
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

internal data class ViewerRequest(val media: List<Photo>, val initialId: Int, val bounds: Map<Int, Rect>, val sourceCornerRadius: androidx.compose.ui.unit.Dp = 22.dp, val resolveSource: ((Int) -> Rect?)? = null)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaViewer(request: ViewerRequest, favorites: Set<Int>, onFavorite: (Int) -> Unit, onDelete: (Photo) -> Unit, onClose: () -> Unit, onRemoteChanged: () -> Unit = {}, onPageVisible: (Photo) -> Unit = {}, pagingStatus: @Composable () -> Unit = {}, paged: Boolean = false) {
    val media by rememberUpdatedState(request.media)
    val pager = rememberPagerState(initialPage = media.indexOfFirst { it.id == request.initialId }.coerceAtLeast(0)) { media.size }
    val current = media[pager.currentPage.coerceIn(media.indices)]
    LaunchedEffect(current.id, media) { onPageVisible(current) }
    LaunchedEffect(current.remote) {current.remote?.load()}
    val metadata = rememberMediaMetadata(current)
    var presented by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if(presented) 1f else 0f) }
    val mediaReady by remember { derivedStateOf { progress.value >= .999f } }
    val chromeReady by remember { derivedStateOf { progress.value > .9f } }
    val scope = rememberCoroutineScope()
    var closing by rememberSaveable { mutableStateOf(false) }
    var exitSource by remember { mutableStateOf<Rect?>(null) }
    val transitionSource = if (closing) exitSource else request.bounds[current.id]
    var sourceRestored by remember { mutableStateOf(false) }
    val sourceHandoff = LocalMediaSourceHandoff.current
    SideEffect {
        sourceHandoff?.source = transitionSource
        sourceHandoff?.opacity = { if (sourceRestored) 1f else 0f }
    }
    DisposableEffect(sourceHandoff) {
        onDispose { sourceHandoff?.source = null; sourceHandoff?.opacity = null }
    }

    var chrome by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var info by rememberSaveable { mutableStateOf(false) }
    var detailBusy by remember { mutableStateOf(false) }
    val detailExtent = remember { Animatable(if (info) .56f else 0f) }
    LaunchedEffect(info) { detailExtent.animateTo(if (info) .56f else 0f, spring(1f, 450f)) }
    var more by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }
    var gestureSeek by remember { mutableStateOf<Long?>(null) }
    var remoteBusy by remember { mutableStateOf(false) }
    var privateConfirm by remember { mutableStateOf(false) }
    var privateAuthorization by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val remoteFavorites = remember { mutableStateMapOf<String, Boolean>() }
    val remote = current.remote
    val isFavorite = if (remote == null) current.id in favorites else remoteFavorites[remote.asset.id] ?: remote.detail?.optBoolean("favorite", remote.asset.favorite) ?: remote.asset.favorite
    val viewerAccent = LocalThemeController.current.accent.dark
    val context = LocalContext.current
    val resources = LocalResources.current
    val scene = rememberLayerBackdrop()
    val detailsScene = rememberLayerBackdrop()
    var motionPlaying by remember(current.remote) {mutableStateOf(false)}
    val motionUrl=remote?.motionUrl()
    val videoMode=current.video || (motionPlaying && motionUrl!=null)
    val playback = if(videoMode) rememberVideoPlayback(current, mediaReady && !closing && !pager.isScrollInProgress && !info && detailExtent.value < .001f && !delete && !more && !sharing,if(motionPlaying) motionUrl else null) else null
    LaunchedEffect(playback?.wantsPlay) {
        if(motionPlaying && playback?.player?.playbackState==androidx.media3.common.Player.STATE_ENDED) motionPlaying=false
    }
    fun close() {
        if(closing) return
        exitSource = request.resolveSource?.let { it(current.id) } ?: request.bounds[current.id].takeIf { request.resolveSource == null }
        closing = true
        playback?.player?.pause()
        scope.launch {
            if (exitSource != null) {
                progress.animateTo(SourceHandoffFraction, spring(.92f, 550f))
                // The source and artwork belong to different windows. Present the
                // landed artwork before allowing the underlying tile to reappear.
                withFrameNanos { }
                withFrameNanos { }
                sourceRestored = true
                progress.animateTo(0f, tween(90))
            } else progress.animateTo(0f, spring(.92f, 550f))
            onClose()
        }
    }
    fun remoteAction(action: AssetAction, value: Boolean? = null, authorizedServer: com.yunai.phototube.data.PhotoTubeServer? = null) {
        val source = current.remote ?: return
        if (remoteBusy) return
        remoteBusy = true; actionError = null
        scope.launch {
            try {
                AssetActions(authorizedServer ?: source.server).execute(action, setOf(source.asset.id), value).requireSuccess(source.asset.id)
                withContext(Dispatchers.Main.immediate) {
                    if (action == AssetAction.FAVORITE) remoteFavorites[source.asset.id] = value!!
                    action.flag?.let { flag -> source.detail?.let { source.detail = org.json.JSONObject(it.toString()).put(flag, value) } }
                    onRemoteChanged()
                    if (action == AssetAction.PRIVATE) onClose() else if (action == AssetAction.TRASH) close()
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (problem: Exception) { withContext(Dispatchers.Main.immediate) {
                if(action==AssetAction.PRIVATE && value==true) {
                    android.widget.Toast.makeText(context,"私密操作未确认，请刷新图库核对",android.widget.Toast.LENGTH_LONG).show()
                    onRemoteChanged();onClose()
                } else actionError = problem.message ?: "操作失败"
            } }
            finally { remoteBusy = false; privateAuthorization = false }
        }
    }
    fun toggleFavorite() { if (current.remote == null) onFavorite(current.id) else remoteAction(AssetAction.FAVORITE, !isFavorite) }
    fun share() {
        if (current.remote?.server?.privateMedia == true) {
            actionError = "请先移出私密空间，再分享给其他应用"
            return
        }
                                if(!sharing) { sharing=true; scope.launch {
                                    try {
                                        val uri = withContext(Dispatchers.IO) {
                                            val directory = File(context.cacheDir,"shared-media").apply { mkdirs() }
                                            val file = File(directory,if(current.remote!=null) "${current.remote.asset.id}.${current.name.substringAfterLast('.',"bin").filter {it.isLetterOrDigit()}}" else "${resources.getResourceEntryName(current.id)}.${if(current.video) "mp4" else "jpg"}")
                                            if(current.remote!=null) {
                                                val source=current.remote
                                                source.server.client.newCall(okhttp3.Request.Builder().url(source.server.original(source.asset.id)).build()).execute().use {response ->
                                                    check(response.isSuccessful) {"无法读取分享文件"}
                                                    response.body.byteStream().use {input -> file.outputStream().use {input.copyTo(it)}}
                                                }
                                            } else if(!file.exists()) resources.openRawResource(current.id).use { input -> file.outputStream().use { input.copyTo(it) } }
                                            FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type=if(current.remote!=null) android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(current.name.substringAfterLast('.').lowercase()) ?: "application/octet-stream" else if(current.video) "video/mp4" else "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM,uri); clipData=ClipData.newRawUri(current.name,uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent,"分享${if(current.video) "视频" else "照片"}"))
                                    } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { shareError=true } finally { sharing=false }
                                } }
    }
    LaunchedEffect(Unit) {
        if(closing) {onClose();return@LaunchedEffect}
        if(!presented) {presented=true;progress.animateTo(1f, spring(.9f, 400f))}
    }
    LaunchedEffect(pager.currentPage) { zoomed = false; chrome = true; drag = 0f; more = false }
    LaunchedEffect(chrome, playback?.playing, pager.currentPage) {
        if(chrome && playback?.playing == true) { delay(4500); chrome = false }
    }
    Dialog(onDismissRequest = { if (info) info = false else close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ThemeSystemBars(media = true)
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f); setWindowAnimations(0) } }
        BackHandler { when { info -> info = false; more -> more = false; else -> close() } }
        BoxWithConstraints(Modifier.fillMaxSize().testTag("media-viewer").semantics { stateDescription = "${pager.currentPage + 1}/${media.size} ${current.name}" }) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val detailTopInset = WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current).toFloat()
            val landscapeVideo = videoMode && width > height
            val dismissal = (abs(drag) / height).coerceIn(0f,.6f)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(MediaColors.background.copy(alpha=progress.value.coerceIn(0f,1f)*(1f-dismissal*1.4f))) })
            val detailDensity = androidx.compose.ui.platform.LocalDensity.current
            Box(Modifier.fillMaxWidth().height(with(detailDensity) { (height * (1f-detailExtent.value)).toDp() })
                .padding(top = with(detailDensity) { (detailTopInset * (detailExtent.value / .56f).coerceIn(0f,1f)).toDp() })
                .then(if (info || detailExtent.value > .001f) Modifier.clearAndSetSemantics {} else Modifier)
                .layerBackdrop(scene).graphicsLayer {
                    translationY = drag * progress.value.coerceIn(0f,1f)
                    scaleX = 1f-dismissal*.35f*progress.value.coerceIn(0f,1f); scaleY = scaleX
                }) {
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("media-pager").graphicsLayer { val p=progress.value.coerceIn(0f,1f)
                    // Warm the real photo layer during the final part of the transition.
                    // The video surface still takes over only when its transition ends.
                    alpha = if(videoMode) { if(p >= .999f && !closing) 1f else 0f }
                        else ((p-.85f)/.15f).coerceIn(0f,1f) },
                    userScrollEnabled = !info && !videoMode && !zoomed && !closing && drag == 0f, pageSpacing = 16.dp, key = { media[it].id }) { index ->
                    val media = media[index]
                    if(media.video || (motionPlaying && index==pager.currentPage)) {
                        Box(Modifier.fillMaxSize().testTag("viewer-video-surface").semantics {stateDescription=if(playback?.ready==true) "已显示视频画面" else "正在加载视频"}
                            .pointerInput(media.id) { detectTapGestures(onTap = { chrome = !chrome }) }
                            .pointerInput(media.id, playback) {
                                var origin=0L
                                var distance=0f
                                detectHorizontalDragGestures(
                                    onDragStart={
                                        val video=playback
                                        if(index==pager.currentPage && video!=null && video.duration>0) {
                                            origin=video.player.currentPosition;distance=0f
                                            video.player.pause();gestureSeek=origin;chrome=true
                                        }
                                    },
                                    onHorizontalDrag={change,delta ->
                                        change.consume()
                                        playback?.let { video -> if(gestureSeek!=null) {
                                            distance+=delta
                                            // Relative seeking: one screen spans at most 90 seconds.
                                            gestureSeek=(origin+distance/size.width*minOf(video.duration,90_000L)).toLong().coerceIn(0L,video.duration)
                                        } }
                                    },
                                    onDragEnd={
                                        playback?.let {video -> gestureSeek?.let {target ->
                                            video.player.seekTo(target);video.position=target
                                            video.resumeAfterSeek()
                                        } }
                                        gestureSeek=null
                                    },
                                    onDragCancel={
                                        if(gestureSeek!=null) playback?.resumeAfterSeek()
                                        gestureSeek=null
                                    }
                                )
                            }
                            .pointerInput(media.id) {
                                var distance = 0f
                                detectVerticalDragGestures(onDragStart = { distance = 0f }, onVerticalDrag = { change, delta ->
                                    change.consume(); distance += delta; drag = distance
                                }, onDragEnd = {
                                    if(distance > height*.15f) close() else {
                                        if(distance < -height*.1f) info = true
                                        scope.launch { animate(drag,0f,animationSpec=spring(.85f,450f)) { value,_ -> drag=value } }
                                    }
                                }, onDragCancel = { drag = 0f })
                            }) {
                            MediaArtwork(media,Modifier.fillMaxSize(),ContentScale.Fit,description=null,original=!media.video)
                            if(index == pager.currentPage && playback != null) {
                                val videoAlpha by animateFloatAsState(if(playback.ready) 1f else 0f,tween(180),label="video-first-frame")
                                // Fade the whole video plane, including its letterbox,
                                // so a differently shaped poster cannot leak around it.
                                VideoSurface(playback,Modifier.fillMaxSize().graphicsLayer {alpha=videoAlpha}.background(MediaColors.background))
                            }
                        }
                    } else PhotoGestures(media,index == pager.currentPage && !closing,
                        onLongPress=if(index==pager.currentPage && motionUrl!=null) {{motionPlaying=true;chrome=true}} else null,
                        onTap = { chrome = !chrome }, onZoomed = { if(index == pager.currentPage) { zoomed = it; if(it) chrome = false } },
                        onDrag = { drag = it }, onRelease = { distance ->
                            if(distance > height*.15f) close() else {
                                if(distance < -height*.1f) info = true
                                scope.launch { animate(drag,0f,animationSpec=spring(.85f,450f)) { value,_ -> drag=value } }
                            }
                        })
                }
                if(!mediaReady || closing) {
                    MediaTransitionArtwork(current,transitionSource,{progress.value},exiting=closing, sourceCornerRadius=request.sourceCornerRadius)
                }
            }
            AnimatedVisibility(chrome && !info && detailExtent.value < .01f && !closing && chromeReady && abs(drag) < 10f, enter=fadeIn(tween(180)), exit=fadeOut(tween(140))) {
                Box(Modifier.fillMaxSize()) {
                    if (!landscapeVideo) {
                    // Readable over bright media in landscape; remains visually
                    // absent over portrait letterboxing.
                    Box(Modifier.fillMaxWidth().height(144.dp).background(Brush.verticalGradient(
                        0f to MediaColors.scrim.copy(.62f), .7f to MediaColors.scrim.copy(.4f), 1f to Color.Transparent)))
                    }
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=8.dp), verticalAlignment=Alignment.CenterVertically) {
                        if (landscapeVideo) {
                            Row(Modifier.mediaGlass(scene).padding(horizontal=4.dp), verticalAlignment=Alignment.CenterVertically) {
                                MediaAction("关闭视频详情",MediaSymbol.BACK,::close)
                                MediaAction(if(sharing) "正在准备分享" else "分享",MediaSymbol.SHARE,::share)
                                MediaAction(if(isFavorite) "取消收藏" else "收藏",MediaSymbol.HEART,{toggleFavorite()},isFavorite,if(isFavorite) viewerAccent else MediaColors.text,enabled=!remoteBusy)
                            }
                        } else MediaGlassButton(if(current.video) "关闭视频详情" else "关闭照片", MediaSymbol.BACK, scene, ::close)
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                            MediaViewerHeading(current.date,
                                photoTags(current.remote?.detail).joinToString(" | ") {it.name}.ifEmpty {current.name},
                                scene,landscapeVideo)
                        }
                        Row(Modifier.then(if(landscapeVideo) Modifier.mediaGlass(scene).padding(horizontal=4.dp) else Modifier),verticalAlignment=Alignment.CenterVertically) {
                            if(landscapeVideo) {
                                MediaAction("照片与视频信息",MediaSymbol.INFO,{info=true})
                                MediaAction("删除",MediaSymbol.TRASH,{delete=true},enabled=!remoteBusy)
                            }
                        Box {
                            if(landscapeVideo) MediaAction("更多操作",MediaSymbol.MORE,{more=true})
                            else MediaGlassButton("更多操作",MediaSymbol.MORE,scene,{ more=true })
                            GlassActionMenu(more, {more=false}, scene, buildList {
                                if(current.remote!=null) {
                                    add(GlassMenuAction(if(current.remote.detail?.optBoolean("archived")==true) "取消归档" else "归档",
                                        enabled=!remoteBusy && current.remote.detail!=null,
                                        onClick={remoteAction(AssetAction.ARCHIVE, current.remote.detail?.optBoolean("archived")!=true)}))
                                    add(GlassMenuAction(if(current.remote.server.privateMedia) "移出私密空间" else "移入私密空间",
                                        enabled=!remoteBusy && !sharing && current.remote.detail!=null, onClick={privateConfirm=true}))
                                }
                                if(playback!=null) listOf(.5f,1f,1.5f,2f).forEach { speed ->
                                    add(GlassMenuAction("${if(playback.speed==speed) "✓  " else ""}${speed}× 播放速度", onClick={playback.changeSpeed(speed)}))
                                }
                            }, media=true, controls={enabled ->
                                if(playback!=null) Column(Modifier.fillMaxWidth().padding(horizontal=14.dp, vertical=8.dp)) {
                                    Text("音量 ${(playback.volume*100).toInt()}%", color=MediaColors.text)
                                    val palette=LocalGlassPalette.current
                                    CompositionLocalProvider(LocalGlassPalette provides palette.copy(isDark=true, accent=MediaColors.text, track=MediaColors.text.copy(alpha=.25f))) {
                                        LiquidSlider({playback.volume}, playback::changeVolume, 0f..1f, .001f, scene,
                                            Modifier.heightIn(min=48.dp).testTag("viewer-volume").semantics {contentDescription="音量"}, enabled=enabled)
                                    }
                                }
                            })
                        }
                    }
                    }
                    if(motionUrl!=null) LiquidButton({motionPlaying=!motionPlaying;chrome=true},scene,
                        enabled=mediaReady && !zoomed && !closing,
                        surfaceColor=MediaColors.scrim.copy(alpha=.24f), verticalPadding=10.dp,
                        modifier=Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start=24.dp,top=66.dp)
                            .heightIn(min=48.dp).testTag("live-photo-toggle").semantics {
                                stateDescription=if(motionPlaying) "实况照片播放中" else "实况照片静态画面"
                            }) {
                        Text(if(motionPlaying) "LIVE · 返回照片" else "LIVE · 播放",color=MediaColors.text,fontWeight=FontWeight.SemiBold)
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        pagingStatus()
                        if(playback != null) VideoControls(playback,scene,landscapeVideo,gestureSeek)
                        if(!current.video || media.size > 1) Filmstrip(media,pager.currentPage) { index -> scope.launch { pager.animateScrollToPage(index) } }
                        if(!landscapeVideo) Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            MediaGlassButton(if(sharing) "正在准备分享" else "分享",MediaSymbol.SHARE,scene,::share)
                            val glass = LocalGlassAppearance.current
                            Row(Modifier.height(48.dp).drawBackdrop(backdrop=scene,shape={RoundedCornerShape(50)},
                                effects={vibrancy();blur(glass.blur.dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                                onDrawSurface={drawRect(MediaColors.scrim.copy(.24f))}).padding(horizontal=6.dp)) {
                                IconButton(enabled=!remoteBusy,onClick={toggleFavorite()},modifier=Modifier.semantics {contentDescription=if(isFavorite) "取消收藏" else "收藏"}) {
                                    MediaIcon(MediaSymbol.HEART,if(isFavorite) viewerAccent else MediaColors.text,filled=isFavorite)
                                }
                                IconButton(onClick={info=true},modifier=Modifier.semantics {contentDescription="照片与视频信息"}) {MediaIcon(MediaSymbol.INFO)}
                            }
                            MediaGlassButton("删除",MediaSymbol.TRASH,scene,{delete=true},enabled=!remoteBusy)
                        }
                    }
                }
            }
            gestureSeek?.let { target ->
                Text("${mediaTime(target)} / ${mediaTime(playback?.duration ?: 0L)}",color=MediaColors.text,
                    fontSize=20.sp,fontWeight=FontWeight.SemiBold,
                    modifier=Modifier.align(Alignment.Center).mediaGlass(scene).padding(horizontal=24.dp,vertical=16.dp)
                        .testTag("video-seek-preview"))
            }
            if(playback?.buffering == true && !closing && gestureSeek==null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(30.dp),color=MediaColors.text,strokeWidth=2.dp)
            playback?.error?.let { message -> Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(message,color=MediaColors.text)
                Box(Modifier.width(160.dp)) {com.yunai.phototube.ui.GlassSettingsAction("重试",{playback.retry()},media=true)}
            } }
            if (info || detailExtent.value > .001f) {
                MediaDetailsPanel(current, metadata, height, detailExtent,
                    onClose = { info = false }, onChanged = onRemoteChanged, onBusyChanged = { detailBusy = it }, modifier = Modifier.layerBackdrop(detailsScene))
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp).testTag("detail-actions"),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    MediaGlassButton(if(sharing) "正在准备分享" else "分享", MediaSymbol.SHARE, detailsScene, ::share, enabled = !detailBusy, surfaceColor = MediaColors.detailPanel.copy(.88f))
                    Row(Modifier.mediaGlass(detailsScene, surfaceColor = MediaColors.detailPanel.copy(.88f)).padding(horizontal = 6.dp)) {
                        MediaAction(if(isFavorite) "取消收藏" else "收藏", MediaSymbol.HEART, ::toggleFavorite,
                            filled = isFavorite, tint = if(isFavorite) viewerAccent else MediaColors.text, enabled = !remoteBusy && !detailBusy)
                        IconButton(enabled = !detailBusy, onClick = { info = false }, modifier = Modifier.semantics {
                            contentDescription = "收起照片信息"; selected = true
                        }) { MediaIcon(MediaSymbol.INFO, filled = true) }
                    }
                    MediaGlassButton("删除", MediaSymbol.TRASH, detailsScene, { delete = true }, enabled = !remoteBusy && !detailBusy, surfaceColor = MediaColors.detailPanel.copy(.88f))
                }
            }
        }
        if(privateConfirm) GlassConfirmation(scene,
            title=if(current.remote?.server?.privateMedia==true) "移出私密空间？" else "移入私密空间？",
            message=if(current.remote?.server?.privateMedia==true) "移出后，此内容将恢复为普通可见内容。" else "移入后，需要验证账号密码才能查看。",
            confirmLabel="确认", media=true,
            onConfirm={privateConfirm=false;if(current.remote?.server?.privateMedia==true) remoteAction(AssetAction.PRIVATE,false) else privateAuthorization=true},
            onDismiss={privateConfirm=false})
        if(privateAuthorization) current.remote?.let {source -> com.yunai.phototube.ui.remote.PrivateMutationGate(source.server,onClose={privateAuthorization=false},onAuthorized={remoteAction(AssetAction.PRIVATE,true,it)})}
        if(delete) GlassConfirmation(scene, title="删除${if(current.video) "视频" else "照片"}？",
            message=if(current.remote!=null) "移入回收站后可以恢复，NAS 原文件会保留。" else "从当前演示图库移除，可通过撤销恢复。",
            confirmLabel="删除", destructive=true, media=true,
            onConfirm={delete=false;if(current.remote!=null) remoteAction(AssetAction.TRASH) else {onDelete(current);close()}},
            onDismiss={delete=false})
        actionError?.let {message -> GlassConfirmation(scene, title="操作未完成", message=message, confirmLabel="好", cancelLabel=null, media=true,
            onConfirm={actionError=null}, onDismiss={actionError=null})}
        if(shareError) GlassConfirmation(scene, title="分享未完成", message="暂时无法打开分享，请稍后再试。", confirmLabel="好", cancelLabel=null, media=true,
            onConfirm={shareError=false}, onDismiss={shareError=false})
    }
}

@Composable
internal fun MediaGlassButton(label:String,symbol:MediaSymbol,backdrop:Backdrop,onClick:()->Unit,filled:Boolean=false,tint:Color=MediaColors.text,enabled:Boolean=true,surfaceColor:Color=MediaColors.scrim.copy(.24f)) {
    LiquidButton({if(enabled) onClick()},backdrop,Modifier.size(48.dp).graphicsLayer {alpha=if(enabled) 1f else .35f}.semantics { contentDescription=label;if(!enabled) disabled() },shape=CircleShape,horizontalPadding=0.dp,surfaceColor=surfaceColor) {
        MediaIcon(symbol,tint,filled)
    }
}
@Composable
internal fun Modifier.mediaGlass(scene:Backdrop,scrimAlpha:Float=.24f,surfaceColor:Color=MediaColors.scrim.copy(scrimAlpha)):Modifier {
    val glass=LocalGlassAppearance.current
    return drawBackdrop(backdrop=scene,shape={RoundedCornerShape(50)},
        effects={vibrancy();blur(glass.blur.dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
        onDrawSurface={drawRect(surfaceColor)})
}
@Composable
private fun MediaAction(label:String,symbol:MediaSymbol,onClick:()->Unit,filled:Boolean=false,tint:Color=MediaColors.text,enabled:Boolean=true) {
    IconButton(enabled=enabled,onClick=onClick,modifier=Modifier.size(48.dp).semantics {contentDescription=label}) {
        MediaIcon(symbol,tint,filled)
    }
}
@Composable
private fun Filmstrip(media:List<Photo>,current:Int,onSelect:(Int)->Unit) {
    val state=rememberLazyListState()
    LaunchedEffect(current) { state.animateScrollToItem((current-2).coerceAtLeast(0)) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val stripWidth = 52.dp + 30.dp*(media.size-1) + 4.dp*(media.size-1)
    val inset = maxOf(24.dp,(maxWidth-stripWidth)/2)
    LazyRow(Modifier.fillMaxWidth().height(58.dp).testTag("media-filmstrip"),state=state,contentPadding=PaddingValues(horizontal=inset),
        horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
        itemsIndexed(media,key={_,photo->photo.id}) { index,photo ->
            val width by animateDpAsState(if(index==current) 52.dp else 30.dp,spring(.85f,500f),label="filmstrip-width")
            Box(Modifier.width(width).height(if(index==current) 52.dp else 42.dp).clip(RoundedCornerShape(6.dp))
                .border(if(index==current) 2.dp else 0.dp,if(index==current) MediaColors.text else Color.Transparent,RoundedCornerShape(6.dp))
                .clickable {onSelect(index)}.semantics {contentDescription="缩略图：${photo.name}";selected=index==current}) {
                MediaArtwork(photo,Modifier.fillMaxSize(),description=null)
                if(photo.video) Box(Modifier.align(Alignment.Center).size(18.dp)) { MediaIcon(MediaSymbol.PLAY) }
            }
        }
    }
}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoControls(playback:VideoPlayback,scene:Backdrop,landscape:Boolean,gestureSeek:Long?) {
    var seeking by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(0f) }
    val duration=playback.duration.coerceAtLeast(1L)
    // Only the progress drawing observes this frame-rate state: no per-frame layout
    // or whole-viewer recomposition, and no sampling of video images.
    val framePosition=remember(playback) { mutableLongStateOf(playback.position) }
    LaunchedEffect(playback,playback.playing) {
        if(playback.playing) while(true) {
            withInfiniteAnimationFrameNanos { framePosition.longValue=playback.player.currentPosition }
        }
    }
    Column(Modifier.fillMaxWidth(if(landscape) .84f else 1f).padding(horizontal=if(landscape) 0.dp else 24.dp).testTag("viewer-video-controls")) {
        Row(Modifier.fillMaxWidth().mediaGlass(scene).padding(horizontal=4.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            MediaAction(if(playback.wantsPlay) "暂停视频" else "播放视频",if(playback.wantsPlay) MediaSymbol.PAUSE else MediaSymbol.PLAY,{playback.toggle()})
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(48.dp).testTag("video-timeline")
                    .semantics {
                        contentDescription="视频进度"
                        progressBarRangeInfo=ProgressBarRangeInfo(playback.position.toFloat().coerceIn(0f,duration.toFloat()),0f..duration.toFloat())
                        setProgress { value ->
                            val position=value.coerceIn(0f,duration.toFloat()).toLong()
                            playback.player.seekTo(position);playback.position=position;true
                        }
                    }
                    .pointerInput(playback,duration) {
                        awaitEachGesture {
                            val down=awaitFirstDown(requireUnconsumed=false)
                            down.consume()
                            playback.player.pause()
                            seeking=true
                            fun seekTarget(x:Float) { target=(x/size.width).coerceIn(0f,1f)*duration }
                            seekTarget(down.position.x)
                            var releasedNormally=false
                            try {
                                do {
                                    val event=awaitPointerEvent()
                                    val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                    // Compose represents ACTION_CANCEL as an already-consumed up.
                                    // A cancelled preview must never commit its target position.
                                    if(change.isConsumed) break
                                    seekTarget(change.position.x)
                                    releasedNormally=change.changedToUp()
                                    change.consume()
                                } while(!releasedNormally)
                            } finally {
                                if(releasedNormally && !playback.released) {
                                    playback.player.seekTo(target.toLong());playback.position=target.toLong()
                                }
                                seeking=false;playback.resumeAfterSeek()
                            }
                        }
                    },contentAlignment=Alignment.Center) {
                    Spacer(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).drawWithContent {
                        drawRect(MediaColors.text.copy(.32f))
                        val position=if(gestureSeek!=null) gestureSeek.toFloat() else if(seeking) target else if(playback.playing) framePosition.longValue.toFloat() else playback.position.toFloat()
                        drawRect(MediaColors.text,size=androidx.compose.ui.geometry.Size(size.width*(position/duration).coerceIn(0f,1f),size.height))
                    })
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { alpha=if(seeking) 1f else 0f },horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(mediaTime(if(seeking) target.toLong() else playback.position),color=MediaColors.secondary,fontSize=11.sp)
                    Text("−${mediaTime(duration-(if(seeking) target.toLong() else playback.position))}",color=MediaColors.secondary,fontSize=11.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            MediaAction(if(playback.muted) "开启声音" else "静音",if(playback.muted) MediaSymbol.MUTED else MediaSymbol.SOUND,{playback.toggleSound()})
        }
        Spacer(Modifier.height(18.dp))
    }
}
internal fun mediaStateLabel(state: String): String = when (state) {
    "BROWSABLE" -> "可查看"; "PROCESSING" -> "处理中"; "OFFLINE" -> "文件离线"
    "TRASHED" -> "已移入回收站"; "FAILED" -> "处理失败"; else -> "状态未知"
}
internal fun mediaSizeLabel(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1073741824 -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%.1f GB".format(bytes / 1073741824.0)
}


@Composable
internal fun MediaViewerHeading(date:String,subtitle:String,scene:Backdrop,glass:Boolean) {
    Column(Modifier.testTag("media-heading").then(
        if(glass) Modifier.mediaGlass(scene,scrimAlpha=MediaColors.readableScrimAlpha).padding(horizontal=24.dp,vertical=6.dp)
        else Modifier.padding(horizontal=8.dp)),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(date,color=MediaColors.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text(subtitle,color=MediaColors.secondary,fontSize=12.sp,maxLines=2,
            overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
