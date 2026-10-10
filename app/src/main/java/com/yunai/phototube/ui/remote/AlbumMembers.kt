package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.*
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.glass.LiquidSlider
import com.kyant.backdrop.backdrops.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun AlbumMemberPicker(repository:AlbumRepository,album:RemoteAlbum?,onChanged:()->Unit,onClose:()->Unit,remove:Boolean=false,
    sourceItems:LazyPagingItems<RemoteAsset>?=null, initialAssetId:String?=null, columns:Int=3,
    draftSelection:List<RemoteAsset>?=null,onPicked:((List<RemoteAsset>)->Unit)?=null) {
    val albumId=album?.id
    val drafting=onPicked!=null
    require(drafting || album!=null)
    require(!remove || album!=null)
    val items=sourceItems ?: run {
      val flow=rememberConfigurationPaging(Triple(repository.server,albumId,remove)) {Pager(PagingConfig(100,initialLoadSize=100,prefetchDistance=15,maxSize=500,enablePlaceholders=false)) {
        if(remove) AlbumPagingSource {cursor -> repository.members(requireNotNull(albumId),cursor).second} else AssetPagingSource(repository.server)
      }.flow}
      flow.collectAsLazyPagingItems()
    }
    var selected by rememberConfigurationState(Triple(repository.server,albumId,remove)) {mutableStateOf<Set<String>>(draftSelection.orEmpty().map {it.id}.toSet())}
    var draftAssets by rememberConfigurationState(repository.server to "album-draft-assets") {mutableStateOf(draftSelection.orEmpty().associateBy {it.id})}
    var picked by rememberConfigurationState(repository.server to albumId) {mutableStateOf<List<RemoteAsset>?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by rememberConfigurationState(Triple(repository.server,albumId,remove)) {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    var closeRequested by rememberSaveable(albumId, remove) { mutableStateOf(false) }
    ManagementPage(if (drafting) "选择照片" else if (remove) "移除照片" else "添加照片", onClose={picked?.let {onPicked?.invoke(it)} ?: onClose()}, dismissEnabled = !busy, scrollable = false,
        horizontalInset = PhotoWallMetrics.horizontalInset, compactHeader = true, closeRequested = closeRequested, overlay={scene ->
            ManagementRefreshAction(scene,!busy && !closeRequested,items::refresh)
        }) {
            BoxWithConstraints(Modifier.weight(1f)) {
              val width=maxWidth.value.coerceAtLeast(1f)
              val listState=rememberLazyListState()
              val snapshot=items.itemSnapshotList
              val rows=remember(snapshot,width,columns) {photoWallRows(snapshot.items.map {WallEntry.Asset(it)},width,width/columns,PhotoWallMetrics.gap.value).filterIsInstance<PhotoWallRow.Photos>()}
              var positioned by rememberSaveable(albumId,remove) {mutableStateOf(initialAssetId==null)}
              LaunchedEffect(rows,initialAssetId) {
                  if(!positioned) {
                      val row=rows.indexOfFirst {it.photos.any {photo -> photo.asset.id==initialAssetId}}
                      if(row>=0) {listState.scrollToItem(row + 1 + if(error!=null) 1 else 0);positioned=true}
                  }
              }
              LaunchedEffect(error) { if(error!=null) listState.scrollToItem(0) }
              val rowMap=remember(rows) {rows.associateBy {it.key}}
              LaunchedEffect(rowMap,listState) {
                  snapshotFlow {listState.layoutInfo.visibleItemsInfo.map {it.key}}.collect {keys ->
                      keys.forEach {key -> rowMap[key]?.photos?.forEach {photo ->
                          if(items.peek(photo.index)?.id==photo.asset.id) items[photo.index]
                      } }
                  }
              }
              LazyColumn(Modifier.fillMaxSize().testTag("album-member-grid"),state=listState,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                item(key="selection-guidance") {
                    Text(if(remove) "选择要移除的照片，原文件会保留" else if(selected.size==500) "已选 500 项，已达到单次上限" else "已选 ${selected.size} 项",color=AppTheme.colors.secondary)
                }
                if(error!=null) item(key="selection-error") { Text(error!!,color=AppTheme.colors.secondary) }
                items(rows,key={it.key}) {row ->
                  Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                   row.photos.forEach {photo -> key(photo.asset.id) {
                    val asset=photo.asset
                    Box(Modifier.semantics { this.selected = asset.id in selected }.testTag("member-select-${asset.id}").width((row.height*photo.ratio).dp).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(AppTheme.colors.subtle).clickable(enabled=!busy && !closeRequested && (selected.size<500 || asset.id in selected)) {selected=if(asset.id in selected) selected-asset.id else selected+asset.id; if(drafting) draftAssets=draftAssets+(asset.id to asset)}) {
                        AssetThumbnail(asset,repository.server,Modifier.fillMaxSize())
                        MediaSelectionMark(asset.id in selected, Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp))
                    }
                   } }
                  }
                }
                if(items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading) item {CircularProgressIndicator()}
                val failure=(items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
                if(failure!=null) item {GlassSettingsAction("加载失败，重试", items::retry, enabled = !busy && !closeRequested)}
                if(items.loadState.refresh is LoadState.NotLoading && items.itemCount==0) item {Text(if(remove) "相册中没有可移除的内容" else "暂无可添加内容",color=AppTheme.colors.secondary)}
              }
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { GlassSettingsAction("取消", {closeRequested=true}, enabled = !busy && !closeRequested) }
                Box(Modifier.weight(1f)) { GlassSettingsAction(if(drafting) "完成选择" else "${if(remove) "移除" else "添加"} ${selected.size} 项", {
                    if (busy || closeRequested) return@GlassSettingsAction
                    if(drafting) {picked=selected.mapNotNull(draftAssets::get);closeRequested=true;return@GlassSettingsAction}
                    val submitted = selected.toSet()
                    busy = true; error = null
                    scope.launch {
                        try {
                            val result = assetActionResult(repository.changeMembers(requireNotNull(albumId), submitted, remove = remove))
                            val failures = result.failed.filter { it.id in submitted }
                            val succeeded = (result.succeeded intersect submitted) - failures.map { it.id }.toSet()
                            val remaining = submitted - succeeded
                            val missing = remaining - failures.map { it.id }.toSet()
                            withContext(Dispatchers.Main.immediate) {
                                selected = remaining
                                onChanged()
                                if (remaining.isEmpty()) closeRequested = true else {
                                    val messages = failures.map { it.message } + if (missing.isNotEmpty()) listOf("${missing.size} 项未返回结果，请刷新后核对") else emptyList()
                                    error = "${succeeded.size} 项已${if (remove) "移除" else "添加"}；" + messages.joinToString("；")
                                }
                            }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (problem: Exception) { withContext(Dispatchers.Main.immediate) { error = problem.message } }
                        finally { withContext(NonCancellable + Dispatchers.Main.immediate) { busy = false } }
                    }
                }, enabled = !busy && !closeRequested && (drafting || selected.isNotEmpty())) }
            }

    }
}

@Composable
internal fun MediaSelectionMark(checked: Boolean, modifier: Modifier) {
    val accent = AppTheme.colors.accent
    Canvas(modifier) {
        val radius = size.minDimension / 2
        drawCircle(if (checked) accent else Color.Black.copy(alpha = .22f), radius)
        if (checked) {
            val width = size.width; val height = size.height
            drawLine(Color.White, Offset(width * .26f, height * .51f), Offset(width * .43f, height * .68f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, Offset(width * .43f, height * .68f), Offset(width * .75f, height * .33f), 2.dp.toPx(), StrokeCap.Round)
        } else drawCircle(Color.White.copy(alpha = .9f), radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}

@Composable
internal fun AlbumCoverEditor(repository:AlbumRepository,album:RemoteAlbum,asset:RemoteAsset,onSaved:(RemoteAlbum)->Unit,onClose:()->Unit) {
    val same=album.cover?.id==asset.id
    var x by rememberSaveable(album.id, asset.id) {mutableFloatStateOf(if(same) album.focusX else .5f)}
    var y by rememberSaveable(album.id, asset.id) {mutableFloatStateOf(if(same) album.focusY else .5f)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    var closing by rememberSaveable(album.id, asset.id) { mutableStateOf(false) }
    var saved by rememberConfigurationState(Pair(repository.server, asset.id)) { mutableStateOf<RemoteAlbum?>(null) }
    val enabled = !busy && !closing
    val backdrop = rememberLayerBackdrop()
    var imageRatio by remember(asset.id) { mutableFloatStateOf((asset.width ?: 1).toFloat() / (asset.height ?: 1).coerceAtLeast(1)) }
    ManagementPage("设置相册封面", onClose = { saved?.let(onSaved) ?: onClose() },
        dismissEnabled = !busy, closeRequested = closing, onCloseAttempt = { closing = true; true }) {
        Column(Modifier.fillMaxWidth().layerBackdrop(backdrop), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(repository.server.thumbnail(asset), asset.fileName, imageLoader = repository.server.imageLoader,
                contentScale = ContentScale.Crop, alignment = BiasAlignment(x * 2 - 1, y * 2 - 1),
                onSuccess = { result ->
                    val intrinsic = result.painter.intrinsicSize
                    if(intrinsic.width > 0 && intrinsic.height > 0) imageRatio = intrinsic.width / intrinsic.height
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))
                    .testTag("cover-preview").pointerInput(asset.id, enabled, imageRatio) {
                        if(!enabled) return@pointerInput
                        var startX = x; var startY = y
                        detectDragGestures(
                            onDragStart = { startX = x; startY = y },
                            onDragCancel = { x = startX; y = startY },
                            onDrag = { change, amount ->
                                change.consume()
                                // Match ContentScale.Crop: move only along overflowing image axes.
                                val overflowX = (size.height * imageRatio - size.width).coerceAtLeast(0f)
                                val overflowY = (size.width / imageRatio - size.height).coerceAtLeast(0f)
                                if(overflowX > 0f) x = (x - amount.x / overflowX).coerceIn(0f, 1f)
                                if(overflowY > 0f) y = (y - amount.y / overflowY).coerceIn(0f, 1f)
                            })
                    })
            Text("拖动照片调整取景，不会修改原图", color = AppTheme.colors.secondary)
        }
        Text("水平位置 · ${(x * 100).roundToInt()}%", color = AppTheme.colors.text)
        LiquidSlider({ x }, { x = it }, 0f..1f, .001f, backdrop,
            Modifier.heightIn(min = 48.dp).testTag("cover-focus-x"), enabled = enabled)
        Text("垂直位置 · ${(y * 100).roundToInt()}%", color = AppTheme.colors.text)
        LiquidSlider({ y }, { y = it }, 0f..1f, .001f, backdrop,
            Modifier.heightIn(min = 48.dp).testTag("cover-focus-y"), enabled = enabled)
        error?.let { Text(it, color = AppTheme.colors.secondary) }
        GlassSettingsAction(if (busy) "正在保存" else "保存封面", {
            if (!enabled) return@GlassSettingsAction
            val body = JSONObject().put("coverAssetId", asset.id)
                .put("coverFocalPoint", JSONObject().put("x", x).put("y", y))
            busy = true; error = null
            scope.launch {
                try { saved = repository.update(album.id, body); closing = true }
                catch (cancel: CancellationException) { throw cancel }
                catch (problem: Exception) { error = problem.message }
                finally { busy = false }
            }
        }, enabled = enabled)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                GlassSettingsAction("居中", { x = .5f; y = .5f }, enabled = enabled && (x != .5f || y != .5f))
            }
            Box(Modifier.weight(1f)) { GlassSettingsAction("取消", { closing = true }, enabled = enabled) }
        }
    }
}
