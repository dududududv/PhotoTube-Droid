package com.yunai.phototube.ui.media

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.ui.theme.*
import kotlinx.coroutines.launch

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
    val scope=rememberCoroutineScope()
    var exiting by remember {mutableStateOf(false)}
    fun close() { if(!exiting) {exiting=true;scope.launch {transition.animateTo(0f,tween(200));onClose()}} }
    LaunchedEffect(Unit) {transition.animateTo(1f,spring(.9f,430f))}
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        ThemeSystemBars()
        var descending by rememberSaveable {mutableStateOf(true)}
        var columns by rememberSaveable {mutableIntStateOf(3)}
        var menu by remember {mutableStateOf(false)}
        val sorted=if(descending) media.sortedByDescending {it.takenOn} else media.sortedBy {it.takenOn}
        val state=rememberLazyGridState()
        val scene=rememberLayerBackdrop()
        val bounds=remember {mutableMapOf<Int,Rect>()}
        Box(Modifier.fillMaxSize().background(AppTheme.colors.background).testTag("album-detail")
            .graphicsLayer {alpha=transition.value;translationX=(1f-transition.value)*70.dp.toPx()}) {
            LazyVerticalGrid(GridCells.Fixed(columns),Modifier.fillMaxSize().layerBackdrop(scene).testTag("album-grid"),state=state,
                contentPadding=PaddingValues(start=3.dp,end=3.dp,bottom=100.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                item(span={GridItemSpan(maxLineSpan)}) {
                    Column(Modifier.statusBarsPadding().padding(top=72.dp,start=21.dp,end=21.dp,bottom=24.dp)) {
                        Text(title,color=AppTheme.colors.text,fontSize=34.sp,fontWeight=FontWeight.Bold)
                        Text("${media.count {!it.video}} 张照片${if(media.any {it.video}) " · ${media.count {it.video}} 段视频" else ""}",color=AppTheme.colors.secondary,modifier=Modifier.padding(top=6.dp))
                    }
                }
                items(sorted,key={it.id}) { photo ->
                    var frame by remember {mutableStateOf(Rect.Zero)}
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned {frame=it.boundsInWindow();bounds[photo.id]=frame}
                        .clickable {bounds[photo.id]=frame;onOpen(photo,sorted,bounds.toMap())}.testTag("album-media-${photo.id}")
                        .semantics {contentDescription=photo.name}) {
                        MediaArtwork(photo,Modifier.fillMaxSize(),description=null)
                        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MediaColors.scrim.copy(.18f)).padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(photo.id in favorites) Box(Modifier.size(16.dp)) {MediaIcon(MediaSymbol.HEART,filled=true)}
                            Spacer(Modifier.weight(1f))
                            if(photo.video) Text(mediaTime(rememberMediaMetadata(photo)?.duration ?: 0L),color=MediaColors.text,fontSize=12.sp)
                        }
                    }
                }
                if(media.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) {Text("相册中还没有内容",color=AppTheme.colors.secondary,modifier=Modifier.padding(24.dp))}
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                LiquidButton(::close,scene,Modifier.size(48.dp).semantics {contentDescription="返回相册列表"},shape=CircleShape,horizontalPadding=0.dp,surfaceColor=AppTheme.colors.glass.copy(.4f)) {MediaIcon(MediaSymbol.BACK,AppTheme.colors.text)}
                Spacer(Modifier.weight(1f))
                Box {
                    LiquidButton({menu=true},scene,Modifier.size(48.dp).semantics {contentDescription="相册选项"},shape=CircleShape,horizontalPadding=0.dp,surfaceColor=AppTheme.colors.glass.copy(.4f)) {MediaIcon(MediaSymbol.MORE,AppTheme.colors.text)}
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem(text={Text(if(descending) "最早在前" else "最新在前")},onClick={descending=!descending;menu=false})
                        DropdownMenuItem(text={Text(if(columns==3) "放大缩略图" else "缩小缩略图")},onClick={columns=if(columns==3) 2 else 3;menu=false})
                    }
                }
            }
            if(undoPhoto!=null) UndoDeletion(onUndo,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
    }
}
@Composable
internal fun UndoDeletion(onUndo:()->Unit,modifier:Modifier=Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppTheme.colors.group).padding(start=20.dp,end=8.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("已从演示图库移除",color=AppTheme.colors.text,modifier=Modifier.weight(1f))
        TextButton(onUndo) {Text("撤销")}
    }
}
