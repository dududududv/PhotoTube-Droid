package com.yunai.phototube.ui.remote

import com.yunai.phototube.ui.media.mediaTransitionSource
import com.yunai.phototube.ui.media.mediaBounds
import androidx.compose.material.icons.rounded.Refresh
import com.kyant.backdrop.backdrops.layerBackdrop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.PagedViewerSession
import com.yunai.phototube.ui.media.BindPagedViewer
import com.yunai.phototube.ui.theme.AppTheme

@Composable
internal fun RemotePhotoWall(vm:PhotoWallViewModel,topInset:Dp,modifier:Modifier=Modifier,searchMessage:String?=null,showSearchMessage:Boolean=true,listState:androidx.compose.foundation.lazy.LazyListState=androidx.compose.foundation.lazy.rememberLazyListState(),onSelect:(()->Unit)?=null,onSelectAsset:((String,String,Int)->Unit)?=null,viewerSession:PagedViewerSession?=null,selectionActionVisible:Boolean=true,onOpen:((PagedViewerSession)->Unit)?=null) {
    val colors=AppTheme.colors
    val bounds=remember {mutableMapOf<String,androidx.compose.ui.geometry.Rect>()}
    if(vm.stage!=ConnectionStage.READY) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(top=topInset+56.dp,bottom=150.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("图库",fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=colors.text)
            ConnectionForm(vm)
        }
        return
    }
    if(searchMessage!=null) {
        Column(modifier.fillMaxSize().padding(horizontal=24.dp).padding(top=topInset+72.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(showSearchMessage) Text(searchMessage,color=colors.secondary,fontSize=16.sp)
        }
        return
    }
    val flow=vm.pageFlow
    if(flow==null) {
        Column(modifier.fillMaxSize().padding(top=topInset+64.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(vm.busy) CircularProgressIndicator() else Failure(vm.error,vm::refresh)
        }
        return
    }
    val items=flow.collectAsLazyPagingItems()
    val api=vm.server ?: return
    val viewerActive=viewerSession!=null
    BindPagedViewer(viewerSession,items) {(it as? WallEntry.Asset)?.value}
    BoxWithConstraints(modifier.fillMaxSize()) {
        val snapshot=items.itemSnapshotList
        val width=(maxWidth-PhotoWallMetrics.horizontalInset*2).value.coerceAtLeast(1f)
        val rows=remember(snapshot,width) {photoWallRows(snapshot.items,width)}
        val rowsByKey = remember(rows) { rows.associateBy { it.key } }
        LaunchedEffect(items, rowsByKey, listState, viewerActive) {
            if (viewerActive) return@LaunchedEffect
            // Layout may still be composing an older window when Paging drops a page.
            // Draw that immutable row snapshot, and send hints outside composition only
            // when the current window still maps the index to the same asset.
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }.collect { keys ->
                keys.forEach { key ->
                    (rowsByKey[key] as? PhotoWallRow.Photos)?.photos?.forEach { photo ->
                        val current = items.itemSnapshotList.items.getOrNull(photo.index) as? WallEntry.Asset
                        if (current?.value?.id == photo.asset.id) items[photo.index]
                    }
                }
            }
        }
        LazyColumn(state=listState,modifier=Modifier.fillMaxSize().testTag("remote-photo-wall"),
            contentPadding=PaddingValues(start=PhotoWallMetrics.horizontalInset,end=PhotoWallMetrics.horizontalInset,top=topInset,bottom=140.dp),verticalArrangement=Arrangement.spacedBy(PhotoWallMetrics.gap)) {
            item(key="title") {
                Column {
                    PhotoWallHeading(actionVisible=selectionActionVisible,onSelect=onSelect)
                    PhotoWallSummary("${vm.groups.sumOf {it.count}} 项内容"+(if(vm.filters.active) " · ${vm.filters.label}" else ""))
                    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.error?.let {Failure(it,vm::refresh)}
                }
            }
            if(items.loadState.refresh is LoadState.Loading) item {Loading("正在加载照片…")}
            if(items.loadState.refresh is LoadState.Error) item {Failure((items.loadState.refresh as LoadState.Error).error,items::retry)}
            if(items.loadState.refresh is LoadState.NotLoading && items.itemCount==0) item {
                Column(Modifier.padding(vertical=64.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(vm.filters.active || vm.filters.keyword.isNotEmpty()) "没有符合条件的照片或视频" else "还没有照片",color=colors.text,fontSize=20.sp)
                    if(vm.filters.keyword.isNotEmpty()) Text("试试文件名中的其他文字，或清除搜索重新浏览。",color=colors.secondary,modifier=Modifier.padding(top=12.dp))
                    if(vm.filters.active) com.yunai.phototube.ui.GlassSettingsAction("清除筛选",{vm.updateFilters(WallFilters(keyword=vm.filters.keyword))})
                    else if(vm.filters.keyword.isEmpty()) Text("照片加入图库后会显示在这里。",color=colors.secondary,modifier=Modifier.padding(top=12.dp))
                }
            }
            items(rows.size,key={rows[it].key}) {rowIndex ->
                when(val row=rows[rowIndex]) {
                    is PhotoWallRow.Month -> {
                        Text("${row.entry.key.replace('-','年')}月 · ${row.entry.count} 项",color=colors.text,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
                    }
                    is PhotoWallRow.Photos -> Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(PhotoWallMetrics.gap)) {
                        row.photos.forEach {photo ->
                            val asset=photo.asset
                            AssetThumbnail(asset,api,Modifier.width((row.height*photo.ratio).dp).fillMaxHeight()
                            .mediaBounds(asset.id, bounds)
                            .combinedClickable(enabled=onOpen!=null || onSelectAsset!=null,
                                onLongClick=onSelectAsset?.let {select -> {
                                    val offset=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key==row.key}?.offset ?: 0
                                    select(asset.id,row.key,-offset)
                                }},
                                onClick={onOpen?.invoke(PagedViewerSession.create(items,{(it as? WallEntry.Asset)?.value},api,asset,bounds))}))
                        }
                    }
                }
            }
            if(items.loadState.append is LoadState.Loading) item {Loading("正在加载更多…")}
            if(items.loadState.append is LoadState.Error) item {Failure((items.loadState.append as LoadState.Error).error,items::retry)}
        }
    }
}
@Composable
private fun ConnectionForm(vm:PhotoWallViewModel) {
    val colors=AppTheme.colors
    var address by rememberSaveable(vm.address) {mutableStateOf(vm.address)}
    var username by rememberSaveable(vm.address) {mutableStateOf("admin")}
    var password by remember(vm.address, vm.stage) {mutableStateOf("")}
    Text("连接图库",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=colors.text)
    when(vm.stage) {
        ConnectionStage.ADDRESS, ConnectionStage.CONNECTING -> {
            com.yunai.phototube.ui.GlassTextField(address,{address=it},"服务器地址",enabled=!vm.busy,
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType=androidx.compose.ui.text.input.KeyboardType.Uri,autoCorrectEnabled=false),
                modifier=Modifier.fillMaxWidth().testTag("server-address"))
            Text("输入服务器完整地址，例如 https://photos.example.com",color=colors.secondary)
            com.yunai.phototube.ui.GlassSettingsAction(if(vm.busy) "正在连接…" else "连接服务器",
                {vm.connect(address.trim())},enabled=!vm.busy && address.isNotBlank(),
                modifier=Modifier.testTag("server-connect"))
        }
        ConnectionStage.LOGIN -> {
            Text(vm.address,color=colors.secondary)
            com.yunai.phototube.ui.GlassTextField(username,{username=it},"账号",enabled=!vm.busy,
                modifier=Modifier.fillMaxWidth().testTag("local-login-username"))
            com.yunai.phototube.ui.GlassTextField(password,{password=it},"密码",enabled=!vm.busy,
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType=androidx.compose.ui.text.input.KeyboardType.Password,autoCorrectEnabled=false),
                modifier=Modifier.fillMaxWidth().testTag("local-login-password"))
            com.yunai.phototube.ui.GlassSettingsAction(if(vm.busy) "正在登录…" else "登录",{
                vm.login(username.trim(),password);password=""
            },enabled=!vm.busy && username.isNotBlank() && password.isNotEmpty(),
                modifier=Modifier.testTag("local-login-submit"))
            com.yunai.phototube.ui.GlassSettingsAction("更换服务器",vm::configure,enabled=!vm.busy)
        }
        ConnectionStage.INITIALIZE -> {
            Text(vm.address,color=colors.secondary)
            Text("请先在服务端初始化账号，再重新连接。",color=colors.secondary)
            com.yunai.phototube.ui.GlassSettingsAction("重新连接",{vm.connect(vm.address)},enabled=!vm.busy)
            com.yunai.phototube.ui.GlassSettingsAction("更换服务器",vm::configure,enabled=!vm.busy)
        }
        else -> Unit
    }
    vm.error?.let {Failure(it,null)}
}
@Composable
private fun Loading(label:String) {Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text(label,modifier=Modifier.padding(start=12.dp),color=AppTheme.colors.secondary)}}
@Composable
private fun Failure(error:Throwable?,retry:(()->Unit)?) {
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("server-error")) {
        Text(when(error) {is ServerError->error.message;is java.io.IOException->"无法连接服务，请检查服务地址和网络连接。";else->error?.message ?: "加载失败"},color=AppTheme.colors.text)
        (error as? ServerError)?.logId?.let {androidx.compose.foundation.text.selection.SelectionContainer {Text("日志编号：$it",color=AppTheme.colors.secondary,fontSize=12.sp)}}
        if(retry!=null) com.yunai.phototube.ui.GlassSettingsAction("重试",retry)
    }
}
@Composable
internal fun AssetThumbnail(asset:RemoteAsset,api:PhotoTubeServer,modifier:Modifier) {
    val colors=AppTheme.colors
    var failed by remember(asset.id,asset.contentHash) {mutableStateOf(false)}
    var attempt by remember(asset.id,asset.contentHash) {mutableIntStateOf(0)}
    var loaded by remember(asset.id,asset.contentHash) {mutableStateOf(false)}
    val url=api.thumbnail(asset)
    Box(modifier.mediaTransitionSource().clip(RoundedCornerShape(5.dp)).background(colors.subtle)
        .testTag("asset-${asset.id}").semantics {contentDescription=asset.fileName}) {
        if(url!=null) key(attempt) {
            AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).build(),null,imageLoader=api.imageLoader,
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onSuccess={loaded=true;failed=false},onError={failed=true})
        }
        if(!loaded) ThumbnailPlaceholder(
            if(asset.originalOnlyFormat!=null) "${asset.originalOnlyFormat!!.uppercase(java.util.Locale.ROOT)} 原文件" else if(url==null || asset.state=="PROCESSING") "缩略图处理中" else if(failed) "缩略图暂不可用" else "正在加载",
            asset.id, if(failed && url!=null) ({failed=false;attempt++}) else null
        )
        if(asset.kind=="VIDEO") Text("▶ ${asset.durationSec?.toInt()?.let {"${it/60}:${(it%60).toString().padStart(2,'0')}"} ?: "视频"}",color=Color.White,fontSize=12.sp,
            modifier=Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(.45f),RoundedCornerShape(topStart=10.dp)).padding(8.dp))
    }
}

/** Keep recovery usable even when the source aspect ratio produces a narrow tile. */
@Composable
internal fun ThumbnailPlaceholder(label:String,assetId:String,onRetry:(()->Unit)?) {
    val colors=AppTheme.colors
    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        val compact=maxWidth<104.dp*fontScale || maxHeight<100.dp*fontScale
        if(compact && onRetry!=null) {
            val backdrop=com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(colors.subtle))
                com.yunai.phototube.glass.LiquidButton(onRetry,backdrop,
                    Modifier.size(48.dp).testTag("thumbnail-retry-$assetId").semantics {contentDescription="$label，重试"},
                    horizontalPadding=0.dp,surfaceColor=colors.glass.copy(alpha=.4f)) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Refresh,null,Modifier.size(22.dp),tint=colors.accent)
                }
            }
        } else Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(label,color=colors.secondary,fontSize=12.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if(onRetry!=null) com.yunai.phototube.ui.GlassSettingsAction("重试",onRetry,modifier=Modifier.testTag("thumbnail-retry-$assetId"))
        }
    }
}

/** Shared summary spacing keeps the source wall aligned when entering selection. */
@Composable
internal fun PhotoWallSummary(text: String) {
    Text(text, color=AppTheme.colors.secondary, fontSize=13.sp,
        modifier=Modifier.padding(vertical=4.dp))
}

@Composable
internal fun PhotoWallHeading(actionLabel:String="选择", actionEnabled:Boolean=true, actionVisible:Boolean=true, onSelect: (() -> Unit)?) {
    val actionAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (actionVisible) 1f else 0f,
        androidx.compose.animation.core.tween(120), label = "wall-selection-visibility")
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    val colors = AppTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(end = 56.dp).testTag("wall-heading")) {
        val actionWidth = 80.dp * scale
        val stacked = onSelect != null && maxWidth < 76.dp * scale + actionWidth + 12.dp
        val title: @Composable () -> Unit = {
            Text("图库", fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                color = colors.text, maxLines = 1, modifier = Modifier.testTag("wall-title"))
        }
        val action: @Composable () -> Unit = {
            // Retain the measured slot while the filter owns this area, so the wall
            // does not reflow and the glass no longer samples a second text action.
            if (onSelect != null) Box(Modifier.width(actionWidth.coerceAtMost(maxWidth))
                .graphicsLayer { alpha = actionAlpha }
                .then(if (actionVisible) Modifier else Modifier.clearAndSetSemantics { })) {
                com.yunai.phototube.ui.GlassSettingsAction(actionLabel, onSelect, enabled=actionEnabled && actionVisible,
                    modifier = Modifier.testTag("wall-select"))
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title()
            action()
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { title() }
            action()
        }
    }
}
