package com.yunai.phototube.ui.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import com.yunai.phototube.ui.remote.justifiedRows
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.ui.GlassActionMenu
import com.yunai.phototube.ui.GlassMenuAction
import com.yunai.phototube.ui.theme.*

internal fun albumMedia(key:String):List<Photo> = when(key) {
    "nature" -> listOf(photos[0],photos[4],photos[5])
    "daily" -> listOf(photos[1],photos[3],photos[6])
    "videos" -> listOf(photos[6])
    else -> listOf(photos[2])
}
@Composable
internal fun AlbumDetail(title:String,media:List<Photo>,favorites:Set<Int>,onOpen:(Photo,List<Photo>,Map<Int,Rect>)->Unit,
    undoPhoto:Photo?,onUndo:()->Unit,onClose:()->Unit) {
    val transition=remember { Animatable(0f) }
    var exiting by remember {mutableStateOf(false)}
    val latestClose by rememberUpdatedState(onClose)
    fun close() { if(!exiting) exiting=true }
    LaunchedEffect(exiting) {
        if(exiting) { transition.animateTo(0f,tween(200));latestClose() }
        else transition.animateTo(1f,spring(.9f,430f))
    }
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        ThemeSystemBars()
        val view=LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f);setWindowAnimations(0) } }
        var descending by rememberSaveable {mutableStateOf(true)}
        var columns by rememberSaveable {mutableIntStateOf(3)}
        var menu by remember {mutableStateOf(false)}
        val sorted=if(descending) media.sortedByDescending {it.takenOn} else media.sortedBy {it.takenOn}
        val state=rememberLazyListState()
        val ratios=sorted.associate {photo ->
            val metadata=rememberMediaMetadata(photo)
            photo.id to (metadata?.let {it.width.toFloat()/it.height.coerceAtLeast(1)} ?: 1f)
        }
        val scene=rememberLayerBackdrop()
        val bounds=remember {mutableMapOf<Int,Rect>()}
        Box(Modifier.fillMaxSize().testTag("album-detail")
            .graphicsLayer {alpha=transition.value;scaleX=.94f+.06f*transition.value;scaleY=scaleX}
            .background(AppTheme.colors.background)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
              val width=(maxWidth-12.dp).value.coerceAtLeast(1f)
              val rows=remember(sorted,ratios,width,columns) {justifiedRows(sorted,width,width/columns,4f) {ratios.getValue(it.id)}}
              LazyColumn(Modifier.fillMaxSize().layerBackdrop(scene).testTag("album-grid"),state=state,
                contentPadding=PaddingValues(start=6.dp,end=6.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                item(key="album-title") {
                    Column(Modifier.statusBarsPadding().padding(top=72.dp,start=21.dp,end=21.dp,bottom=24.dp)) {
                        Text(title,color=AppTheme.colors.text,fontSize=34.sp,fontWeight=FontWeight.Bold)
                        Text("${media.count {!it.video}} 张照片${if(media.any {it.video}) " · ${media.count {it.video}} 段视频" else ""}",color=AppTheme.colors.secondary,modifier=Modifier.padding(top=6.dp))
                    }
                }
                items(rows,key={it.items.first().id}) { row ->
                  Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                   row.items.forEach { photo ->
                    Box(Modifier.width((row.height*ratios.getValue(photo.id)).dp).fillMaxHeight().mediaTransitionSource().clip(RoundedCornerShape(4.dp)).mediaBounds(photo.id,bounds)
                        .clickable(enabled=!exiting) {onOpen(photo,sorted,bounds)}.testTag("album-media-${photo.id}")
                        .semantics {contentDescription=photo.name}) {
                        MediaArtwork(photo,Modifier.fillMaxSize(),description=null)
                        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MediaColors.scrim.copy(.18f)).padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(photo.id in favorites) Box(Modifier.size(16.dp)) {MediaIcon(MediaSymbol.HEART,filled=true)}
                            Spacer(Modifier.weight(1f))
                            if(photo.video) Text(mediaTime(rememberMediaMetadata(photo)?.duration ?: 0L),color=MediaColors.text,fontSize=12.sp)
                        }
                    }
                }
                  }
                }
                if(media.isEmpty()) item {Text("相册中还没有内容",color=AppTheme.colors.secondary,modifier=Modifier.padding(24.dp))}
            }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                LiquidButton(::close,scene,Modifier.size(48.dp).semantics {contentDescription="返回相册列表"},shape=CircleShape,horizontalPadding=0.dp,surfaceColor=AppTheme.colors.glass.copy(.4f)) {MediaIcon(MediaSymbol.BACK,AppTheme.colors.text)}
                Spacer(Modifier.weight(1f))
                Box {
                    LiquidButton({menu=true},scene,Modifier.size(48.dp).semantics {contentDescription="相册选项"},shape=CircleShape,horizontalPadding=0.dp,surfaceColor=AppTheme.colors.glass.copy(.4f),enabled=!exiting) {MediaIcon(MediaSymbol.MORE,AppTheme.colors.text)}
                    GlassActionMenu(menu, {menu=false}, scene, listOf(
                        GlassMenuAction(if(descending) "最早在前" else "最新在前", onClick={descending=!descending}),
                        GlassMenuAction(if(columns==3) "放大缩略图" else "缩小缩略图", onClick={columns=if(columns==3) 2 else 3})
                    ))
                }
            }
            if(undoPhoto!=null) UndoDeletion(onUndo,scene,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),enabled=!exiting)
        }
    }
}
@Composable
internal fun UndoDeletion(onUndo:()->Unit,backdrop:com.kyant.backdrop.Backdrop,modifier:Modifier=Modifier,enabled:Boolean=true) {
    val colors=AppTheme.colors
    FlowRow(modifier.fillMaxWidth().drawBackdrop(backdrop,shape={RoundedCornerShape(22.dp)},effects={
        vibrancy();blur(8.dp.toPx());lens(12.dp.toPx(),20.dp.toPx())
    }).background(colors.glass.copy(alpha=.4f),RoundedCornerShape(22.dp)).padding(12.dp),
        horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("已从演示图库移除",color=colors.text,modifier=Modifier.padding(horizontal=8.dp,vertical=12.dp))
        LiquidButton(onUndo,backdrop,Modifier.testTag("undo-deletion"),enabled=enabled,
            surfaceColor=colors.glass.copy(alpha=.4f),horizontalPadding=20.dp,verticalPadding=12.dp) {
            Text("撤销",color=colors.accent)
        }
    }
}
