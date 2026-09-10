package com.yunai.phototube.ui.media

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

internal data class ViewerRequest(val media: List<Photo>, val initialId: Int, val bounds: Map<Int, Rect>)
internal val ViewerRequestSaver = listSaver<ViewerRequest?,Int>(
    save={request -> request?.takeIf {it.media.none {photo->photo.remote!=null}}?.let {listOf(it.initialId)+it.media.map {photo->photo.id}} ?: emptyList()},
    restore={saved -> if(saved.isEmpty()) null else ViewerRequest(saved.drop(1).mapNotNull {id->photos.firstOrNull {it.id==id}},saved.first(),emptyMap())}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaViewer(request: ViewerRequest, favorites: Set<Int>, onFavorite: (Int) -> Unit, onDelete: (Photo) -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = request.media.indexOfFirst { it.id == request.initialId }.coerceAtLeast(0)) { request.media.size }
    val current = request.media[pager.currentPage]
    LaunchedEffect(current.remote) {current.remote?.load()}
    val metadata = rememberMediaMetadata(current)
    val progress = remember { Animatable(0f) }
    val mediaReady by remember { derivedStateOf { progress.value >= .999f } }
    val chromeReady by remember { derivedStateOf { progress.value > .9f } }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var chrome by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var info by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf(false) }
    var gestureSeek by remember { mutableStateOf<Long?>(null) }
    val viewerAccent = LocalThemeController.current.accent.dark
    val context = LocalContext.current
    val resources = LocalResources.current
    val scene = rememberLayerBackdrop()
    val playback = if(current.video) rememberVideoPlayback(current, mediaReady && !closing && !pager.isScrollInProgress && !info && !delete && !more && !sharing) else null
    fun close() {
        if(closing) return
        closing = true
        playback?.player?.pause()
        scope.launch { progress.animateTo(0f, spring(.92f, 550f)); onClose() }
    }
    fun share() {
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
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(.9f, 400f)) }
    LaunchedEffect(pager.currentPage) { zoomed = false; chrome = true; drag = 0f; more = false }
    LaunchedEffect(chrome, playback?.playing, pager.currentPage) {
        if(chrome && playback?.playing == true) { delay(4500); chrome = false }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ThemeSystemBars(media = true)
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f); setWindowAnimations(0) } }
        BackHandler { when { info -> info = false; more -> more = false; else -> close() } }
        BoxWithConstraints(Modifier.fillMaxSize().testTag("media-viewer").semantics { stateDescription = "${pager.currentPage + 1}/${request.media.size} ${current.name}" }) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            val landscapeVideo = current.video && width > height
            val dismissal = (abs(drag) / height).coerceIn(0f,.6f)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(MediaColors.background.copy(alpha=progress.value.coerceIn(0f,1f)*(1f-dismissal*1.4f))) })
            Box(Modifier.fillMaxSize().layerBackdrop(scene).graphicsLayer {
                translationY = drag * progress.value.coerceIn(0f,1f)
                // Release the dismissal scale together with the translation so the
                // closing image lands exactly on the original thumbnail bounds.
                scaleX = 1f-dismissal*.35f*progress.value.coerceIn(0f,1f); scaleY = scaleX
            }) {
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("media-pager").graphicsLayer { val p=progress.value.coerceIn(0f,1f)
                    // Warm the real photo layer during the final part of the transition.
                    // The video surface still takes over only when its transition ends.
                    alpha = if(current.video) { if(p >= .999f && !closing) 1f else 0f }
                        else ((p-.85f)/.15f).coerceIn(0f,1f) },
                    userScrollEnabled = !current.video && !zoomed && !closing && drag == 0f, pageSpacing = 16.dp, key = { request.media[it].id }) { index ->
                    val media = request.media[index]
                    if(media.video) {
                        Box(Modifier.fillMaxSize().testTag("viewer-video-surface").semantics {stateDescription=if(playback?.ready==true) "已显示视频画面" else "正在加载视频"}
                            .pointerInput(media.id) { detectTapGestures(onTap = { chrome = !chrome }) }
                            .pointerInput(media.id, playback) {
                                var origin=0L
                                var distance=0f
                                var resume=false
                                detectHorizontalDragGestures(
                                    onDragStart={
                                        val video=playback
                                        if(index==pager.currentPage && video!=null && video.duration>0) {
                                            origin=video.player.currentPosition;distance=0f;resume=video.wantsPlay
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
                                            video.player.playWhenReady=resume
                                        } }
                                        gestureSeek=null
                                    },
                                    onDragCancel={
                                        if(gestureSeek!=null) playback?.player?.playWhenReady=resume
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
                            MediaArtwork(media,Modifier.fillMaxSize(),ContentScale.Fit,description=null)
                            if(index == pager.currentPage && playback != null) VideoSurface(playback,Modifier.fillMaxSize())
                        }
                    } else PhotoGestures(media,index == pager.currentPage && !closing,
                        onTap = { chrome = !chrome }, onZoomed = { if(index == pager.currentPage) { zoomed = it; if(it) chrome = false } },
                        onDrag = { drag = it }, onRelease = { distance ->
                            if(distance > height*.15f) close() else {
                                if(distance < -height*.1f) info = true
                                scope.launch { animate(drag,0f,animationSpec=spring(.85f,450f)) { value,_ -> drag=value } }
                            }
                        })
                }
                if(!mediaReady || closing) {
                    MediaTransitionArtwork(current,request.bounds[current.id],{progress.value},exiting=closing)
                }
            }
            AnimatedVisibility(chrome && !closing && chromeReady && abs(drag) < 10f, enter=fadeIn(tween(180)), exit=fadeOut(tween(140))) {
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
                                MediaAction(if(current.id in favorites) "取消收藏" else "收藏",MediaSymbol.HEART,{onFavorite(current.id)},current.id in favorites,if(current.id in favorites) viewerAccent else MediaColors.text,enabled=current.remote==null)
                            }
                        } else MediaGlassButton(if(current.video) "关闭视频详情" else "关闭照片", MediaSymbol.BACK, scene, ::close)
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                            Column(Modifier.then(if(landscapeVideo) Modifier.mediaGlass(scene).padding(horizontal=24.dp,vertical=6.dp) else Modifier.padding(horizontal=8.dp)),horizontalAlignment=Alignment.CenterHorizontally) {
                                Text(current.date, color=MediaColors.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                                Text(current.name,color=MediaColors.secondary,fontSize=12.sp)
                            }
                        }
                        Row(Modifier.then(if(landscapeVideo) Modifier.mediaGlass(scene).padding(horizontal=4.dp) else Modifier),verticalAlignment=Alignment.CenterVertically) {
                            if(landscapeVideo) {
                                MediaAction("照片与视频信息",MediaSymbol.INFO,{info=true})
                                MediaAction("删除",MediaSymbol.TRASH,{delete=true},enabled=current.remote==null)
                            }
                        Box {
                            if(landscapeVideo) MediaAction("更多操作",MediaSymbol.MORE,{more=true})
                            else MediaGlassButton("更多操作",MediaSymbol.MORE,scene,{ more=true })
                            DropdownMenu(more,{more=false}) {
                                DropdownMenuItem(text={Text("${pager.currentPage+1} / ${request.media.size}")},onClick={},enabled=false)
                                DropdownMenuItem(text={Text("查看信息")},onClick={more=false;info=true})
                                if(playback != null) listOf(.5f,1f,1.5f,2f).forEach { speed ->
                                    DropdownMenuItem(text={Text("${if(playback.speed==speed) "✓  " else ""}${speed}× 播放速度")},onClick={playback.changeSpeed(speed);more=false})
                                }
                            }
                        }
                    }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        if(playback != null) VideoControls(playback,scene,landscapeVideo,gestureSeek)
                        if(!current.video || request.media.size > 1) Filmstrip(request.media,pager.currentPage) { index -> scope.launch { pager.animateScrollToPage(index) } }
                        if(!landscapeVideo) Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            MediaGlassButton(if(sharing) "正在准备分享" else "分享",MediaSymbol.SHARE,scene,::share)
                            val glass = LocalGlassAppearance.current
                            Row(Modifier.height(48.dp).drawBackdrop(backdrop=scene,shape={RoundedCornerShape(50)},
                                effects={vibrancy();blur(glass.blur.dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                                onDrawSurface={drawRect(MediaColors.scrim.copy(.24f))}).padding(horizontal=6.dp)) {
                                IconButton(enabled=current.remote==null,onClick={onFavorite(current.id)},modifier=Modifier.semantics {contentDescription=if(current.id in favorites) "取消收藏" else "收藏"}) {
                                    MediaIcon(MediaSymbol.HEART,if(current.id in favorites) viewerAccent else MediaColors.text,filled=current.id in favorites)
                                }
                                IconButton(onClick={info=true},modifier=Modifier.semantics {contentDescription="照片与视频信息"}) {MediaIcon(MediaSymbol.INFO)}
                            }
                            MediaGlassButton("删除",MediaSymbol.TRASH,scene,{delete=true},enabled=current.remote==null)
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
                Text(message,color=MediaColors.text); TextButton({playback.retry()}) { Text("重试") }
            } }
        }
        if(info) MediaInfo(current,metadata) { info=false }
        if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("删除${if(current.video) "视频" else "照片"}？")},
            text={Text("从当前演示图库移除，可通过撤销恢复。")},
            confirmButton={TextButton({delete=false;onDelete(current);close()}) {Text("删除",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton({delete=false}) {Text("取消")}})
        if(shareError) AlertDialog(onDismissRequest={shareError=false},text={Text("暂时无法打开分享，请稍后再试。")},confirmButton={TextButton({shareError=false}) {Text("好")}})
    }
}

@Composable
internal fun MediaGlassButton(label:String,symbol:MediaSymbol,backdrop:Backdrop,onClick:()->Unit,filled:Boolean=false,tint:Color=MediaColors.text,enabled:Boolean=true) {
    LiquidButton({if(enabled) onClick()},backdrop,Modifier.size(48.dp).graphicsLayer {alpha=if(enabled) 1f else .35f}.semantics { contentDescription=label;if(!enabled) disabled() },shape=CircleShape,horizontalPadding=0.dp,surfaceColor=MediaColors.scrim.copy(.24f)) {
        MediaIcon(symbol,tint,filled)
    }
}
@Composable
private fun Modifier.mediaGlass(scene:Backdrop):Modifier {
    val glass=LocalGlassAppearance.current
    return drawBackdrop(backdrop=scene,shape={RoundedCornerShape(50)},
        effects={vibrancy();blur(glass.blur.dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
        onDrawSurface={drawRect(MediaColors.scrim.copy(.24f))})
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
            MediaAction(if(playback.playing) "暂停视频" else "播放视频",if(playback.playing) MediaSymbol.PAUSE else MediaSymbol.PLAY,{playback.toggle()})
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
                            try {
                                do {
                                    val event=awaitPointerEvent()
                                    val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                    seekTarget(change.position.x)
                                    change.consume()
                                } while(event.changes.any {it.pressed})
                            } finally {
                                playback.player.seekTo(target.toLong());playback.position=target.toLong()
                                seeking=false;playback.player.playWhenReady=playback.wantsPlay
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaInfo(photo:Photo,metadata:MediaMetadata?,onClose:()->Unit) {
    ModalBottomSheet(onDismissRequest=onClose,containerColor=AppTheme.colors.group) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=32.dp).testTag("media-info")) {
            Text(photo.name,color=AppTheme.colors.text,fontSize=26.sp,fontWeight=FontWeight.SemiBold)
            Text(photo.date,color=AppTheme.colors.secondary,modifier=Modifier.padding(top=6.dp,bottom=24.dp))
            val info = listOf("类型" to (if(photo.remote!=null) photo.name.substringAfterLast('.',"未知格式").uppercase() else if(photo.video) "视频 · MP4" else "照片 · JPEG"),
                "尺寸" to (metadata?.let {"${it.width} × ${it.height}"} ?: "读取中…"),
                "文件大小" to (metadata?.let {"%.1f MB".format(it.bytes/1048576f)} ?: "读取中…"),
                "来源" to (photo.remote?.detail?.optString("relativePath") ?: if(photo.remote!=null) "NAS" else "内置演示素材")) + if(photo.video) listOf("时长" to mediaTime(metadata?.duration ?: 0L)) else emptyList()
            val remoteInfo=photo.remote?.detail?.let { detail ->
                listOf("状态" to detail.optString("state"),"拍摄时间" to detail.optString("takenAt",photo.date))+
                    (detail.optJSONArray("tags")?.let {tags -> listOf("标签" to (0 until tags.length()).mapNotNull {tags.optJSONObject(it)?.optString("name")?.takeIf {name->name.isNotBlank()}}.joinToString(" · ").ifBlank {"无"})} ?: emptyList())
            } ?: emptyList()
            (info+remoteInfo).forEach { (key,value) -> Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(key,color=AppTheme.colors.secondary);Text(value,color=AppTheme.colors.text,modifier=Modifier.weight(1f).padding(start=20.dp),textAlign=androidx.compose.ui.text.style.TextAlign.End)
            } }
        }
    }
}
