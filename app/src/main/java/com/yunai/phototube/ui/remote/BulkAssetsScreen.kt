package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassActionMenu
import com.yunai.phototube.ui.GlassMenuAction
import com.yunai.phototube.ui.GlassConfirmation
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.saveable.rememberSaveable

private enum class BulkOperation(val label:String,val action:AssetAction,val value:Boolean?=null) {
    FAVORITE("收藏",AssetAction.FAVORITE,true),UNFAVORITE("取消收藏",AssetAction.FAVORITE,false),
    ARCHIVE("归档",AssetAction.ARCHIVE,true),UNARCHIVE("取消归档",AssetAction.ARCHIVE,false),TRASH("移入回收站",AssetAction.TRASH),
    PRIVATE("移入私密空间",AssetAction.PRIVATE,true),UNPRIVATE("移出私密空间",AssetAction.PRIVATE,false)
}

@Composable
internal fun BulkAssetsScreen(server:PhotoTubeServer,filters:WallFilters,estimatedCount:Long,onChanged:()->Unit,onClose:()->Unit,groups:List<MonthGroup> = emptyList(),sourceFlow:kotlinx.coroutines.flow.Flow<PagingData<WallEntry>>?=null,initialRowKey:String?=null,initialRowOffset:Int=0,initialSelectedId:String?=null) {
    // Freeze the scope while the underlying wall refreshes after mutations.
    val retain = !server.privateMedia
    val frozen=rememberConfigurationState(server, enabled=retain) {filters}
    val frozenGroups=rememberConfigurationState(server, enabled=retain) {groups}
    val estimate=rememberConfigurationState(server, enabled=retain) {estimatedCount}
    var privateOperation by remember {mutableStateOf<BulkOperation?>(null)}
    val flow=rememberConfigurationPaging(server to frozen, enabled=retain) {sourceFlow?.map {page -> page.filter {it is WallEntry.Asset}.map {(it as WallEntry.Asset).value}} ?: Pager(PagingConfig(100,initialLoadSize=100,prefetchDistance=16,maxSize=500,enablePlaceholders=false)) {AssetPagingSource(server,frozen)}.flow}
    val items=flow.collectAsLazyPagingItems()
    var selected by rememberConfigurationState(server, enabled=retain) {mutableStateOf<Set<String>>(setOfNotNull(initialSelectedId))}
    var allMatching by rememberConfigurationState(server, enabled=retain) {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var ruleTarget by remember {mutableStateOf<RuleScanTarget.Selection?>(null)}
    var actionsOpen by remember {mutableStateOf(false)}
    var scopeOpen by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf<BulkOperation?>(null)}
    var report by rememberConfigurationState(server, enabled=retain) {mutableStateOf<String?>(null)}
    var failures by rememberConfigurationState(server, enabled=retain) {mutableStateOf<List<String>>(emptyList())}
    val gridState = rememberLazyListState()
    var anchorApplied by rememberSaveable {mutableStateOf(initialRowKey==null)}
    var anchorUnavailable by rememberSaveable {mutableStateOf(false)}
    val resultRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(report) {
        if (report != null) {
            // Compose the header before asking its result region to enter the viewport.
            gridState.animateScrollToItem(0)
            withFrameNanos { }
            resultRequester.bringIntoView()
        }
    }
    val scope=rememberCoroutineScope()
    val colors=AppTheme.colors
    val topInset=com.yunai.phototube.ui.rememberCornerInsets().top
    var closeRequested by rememberSaveable {mutableStateOf(false)}
    fun perform(operation:BulkOperation,actionServer:PhotoTubeServer) {
        val targetIds=selected.toSet();val useFilter=allMatching
        val repository=AssetActions(actionServer)
        confirm=null;busy=true;report=null;failures=emptyList()
        scope.launch {
            try {
                if(useFilter) {
                    val result=repository.executeFilter(operation.action,frozen,operation.value)
                    withContext(Dispatchers.Main.immediate) {
                        report="匹配 ${result.matched} 项，成功 ${result.succeeded} 项，失败 ${result.failed} 项。"+
                            if(result.failed>0) "部分项目未完成，请核对刷新后的列表再操作。" else ""
                        allMatching=false;selected=emptySet()
                    }
                } else {
                    val result=repository.execute(operation.action,targetIds,operation.value)
                    withContext(Dispatchers.Main.immediate) {
                        val succeeded=targetIds.intersect(result.succeeded)-result.failed.map {it.id}.toSet()
                        selected=targetIds-succeeded
                        report="成功 ${succeeded.size} 项，${selected.size} 项未完成。"
                        failures=selected.map {id -> result.failed.firstOrNull {it.id==id}?.let {"${it.message}（${it.code}）"} ?: "服务器未确认 $id 的操作结果"}
                    }
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {report="操作未确认：${problem.message}。请核对刷新后的列表再重试。"}}
            finally {withContext(Dispatchers.Main.immediate) {busy=false;privateOperation=null;items.refresh();onChanged()}}
        }
    }
    val photoScene=rememberLayerBackdrop()
    val density=LocalDensity.current
    ManagementPage("选择照片",onClose,dismissEnabled=!busy,scrollable=false,showTitle=false, compactHeader=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<480, horizontalInset=PhotoWallMetrics.horizontalInset, contentTopInset=topInset, showBackButton=false, scaleTransition=false, closeRequested=closeRequested, overlay = { scene ->
        Box(Modifier.fillMaxWidth().padding(top=topInset,end=PhotoWallMetrics.horizontalInset+56.dp),contentAlignment=Alignment.TopEnd) {
            Box(Modifier.width(80.dp * density.fontScale.coerceAtLeast(1f))) {
                GlassSettingsAction("完成",{closeRequested=true},enabled=!busy && !closeRequested,sourceBackdrop=photoScene,
                    modifier=Modifier.testTag("bulk-done"))
            }
        }
        if (scopeOpen) {
            var choice by remember {mutableStateOf<Int?>(null)}
            val wasAllMatching=remember {allMatching}
            GlassConfirmation(scene, "选择范围",
                "全部匹配项包含未加载照片。打开时 $estimate 项；执行时按筛选条件逐批处理，数量可能变化。",
                "取消", cancelLabel=null, closeRequested=choice!=null,
                onConfirm={scopeOpen=false}, onDismiss={scopeOpen=false}, details={enabled ->
                    SettingsNavigationRow(if(wasAllMatching) "改为逐项选择" else "选择全部匹配项", {
                        allMatching=!wasAllMatching;selected=emptySet();choice=0
                    }, enabled)
                    if(!wasAllMatching) SettingsNavigationRow("选择已加载", {
                        allMatching=false;selected=(selected+items.itemSnapshotList.items.map {it.id}).take(500).toSet();choice=1
                    }, enabled)
                })
        }
        confirm?.let { operation ->
            val message = buildList {
                add(if (allMatching) "范围：当前筛选的全部匹配项，包含未加载的照片。打开页面时共 $estimate 项，执行时逐批求值，不是固定快照。" else "范围：选中的 ${selected.size} 项。")
                if (allMatching) add(scopeDescription(frozen))
                if (operation == BulkOperation.TRASH) add("可以在回收站恢复，不会删除原文件。")
            }.joinToString("\n\n")
            GlassConfirmation(scene, "${operation.label}？", message, "确认${operation.label}",
                destructive = operation == BulkOperation.TRASH, onDismiss = { confirm = null },
                onConfirm = {
                    if (operation == BulkOperation.PRIVATE && !server.privateMedia) { confirm = null; privateOperation = operation }
                    else perform(operation, server)
                })
        }
    }) {
        var footerHeight by remember {mutableIntStateOf(0)}
        Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
          val rowWidth=maxWidth.value.coerceAtLeast(1f)
          val snapshot=items.itemSnapshotList
          val rows=remember(snapshot,rowWidth) {selectionPhotoRows(snapshot.items,frozenGroups,rowWidth)}
          LaunchedEffect(rows,initialRowKey,items.loadState.refresh) {
              if(!anchorApplied && report==null) {
                  val index=selectionAnchorIndex(rows,initialRowKey)
                  if(index>=0) {gridState.scrollToItem(index+1,initialRowOffset);anchorApplied=true}
                  else if(items.loadState.refresh is LoadState.NotLoading &&
                      (rows.isNotEmpty() || items.loadState.refresh.endOfPaginationReached)) {
                      // A refreshed window can omit the old row leader. Prefer the
                      // selected photo, then settle once rather than jumping later.
                      val selectedIndex=initialSelectedId?.let {selectionAnchorIndex(rows,"row-$it")} ?: -1
                      if(selectedIndex>=0) gridState.scrollToItem(selectedIndex+1)
                      anchorUnavailable=true
                      anchorApplied=true
                  }
              }
          }
          val rowMap=remember(rows) {rows.associateBy {it.key}}
          LaunchedEffect(rowMap,gridState) {
              snapshotFlow {gridState.layoutInfo.visibleItemsInfo.map {it.key}}.collect {keys ->
                  keys.forEach {key -> (rowMap[key] as? PhotoWallRow.Photos)?.photos?.forEach {photo ->
                      if(items.peek(photo.index)?.id==photo.asset.id) items[photo.index]
                  } }
              }
          }
          LazyColumn(Modifier.fillMaxSize().layerBackdrop(photoScene).testTag("bulk-grid"),state=gridState,
              contentPadding=PaddingValues(bottom=with(density) {footerHeight.toDp()}+16.dp),
              verticalArrangement=Arrangement.spacedBy(PhotoWallMetrics.gap)) {
            item(key="heading") {
                Column {
                    PhotoWallHeading(actionLabel="完成",actionVisible=false,actionEnabled=!busy && !closeRequested) {closeRequested=true}
                    PhotoWallSummary(if(allMatching) "已选择全部匹配项" else "已选 ${selected.size} 项")
                    if(selected.size==500) Text("已达到单次选择上限，可在更多中更改选择范围",color=colors.secondary)
                    if(anchorUnavailable) Text("原浏览位置已变化，已保留选择结果",color=colors.secondary,
                        fontSize=13.sp,modifier=Modifier.padding(bottom=12.dp).testTag("bulk-anchor-unavailable"))
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (report != null || failures.isNotEmpty()) {
                        Column(Modifier.bringIntoViewRequester(resultRequester), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            report?.let {Text(it,color=colors.text,modifier=Modifier.testTag("bulk-result"))}
                            if(failures.isNotEmpty()) Text(failures.joinToString("\n"),color=colors.text,modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            items(rows,key={it.key}) {row ->
              if(row is PhotoWallRow.Month) Text("${row.entry.key.replace('-','年')}月 · ${row.entry.count} 项",color=colors.text,fontSize=16.sp,fontWeight=FontWeight.Medium,
                  modifier=Modifier.padding(top=12.dp,bottom=4.dp).testTag("bulk-month-${row.entry.key}"))
              else if(row is PhotoWallRow.Photos) Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(PhotoWallMetrics.gap)) {
               row.photos.forEach {photo -> key(photo.asset.id) {
                val asset=photo.asset
                Box(Modifier.width((row.height*photo.ratio).dp).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(colors.subtle)
                    .semantics {this.selected=allMatching || asset.id in selected}
                    .testTag("bulk-asset-${asset.id}").clickable(enabled=!busy && !allMatching && (asset.id in selected || selected.size<500)) {
                        selected=if(asset.id in selected) selected-asset.id else selected+asset.id
                    }) {
                    AssetThumbnail(asset,server,Modifier.fillMaxSize())
                    MediaSelectionMark(allMatching || asset.id in selected, Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp))
                }
               } }
              }
            }
            if(items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val error=(items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
            if(error!=null) item {Column {Text(error.error.message ?: "加载失败",color=colors.text);GlassSettingsAction("重试加载", items::retry, enabled = !busy)}}
            if(items.loadState.refresh is LoadState.NotLoading && items.itemCount==0) item {Text("没有符合条件的照片或视频",color=colors.secondary)}
        }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().testTag("bulk-footer")
            .onSizeChanged {footerHeight=it.height}, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val selectedItems = items.itemSnapshotList.items.filter { it.id in selected }
            val knownSelection = !allMatching && selectedItems.size == selected.size && selected.isNotEmpty()
            val favoriteAction = if(knownSelection && selectedItems.all {it.favorite}) BulkOperation.UNFAVORITE else BulkOperation.FAVORITE
            val canAct = !busy && !closeRequested && (allMatching || selected.isNotEmpty())
            Box(Modifier.weight(1f)) {GlassSettingsAction(favoriteAction.label, {confirm=favoriteAction}, enabled=canAct,
                modifier=Modifier.testTag("bulk-favorite"), sourceBackdrop=photoScene)}
            Box(Modifier.weight(1f)) {GlassSettingsAction("删除", {confirm=BulkOperation.TRASH}, enabled=canAct,
                modifier=Modifier.testTag("bulk-trash"), sourceBackdrop=photoScene, destructive=true)}
            Box(Modifier.weight(1f)) {
                GlassSettingsAction("更多", {actionsOpen=true}, enabled=!busy && !closeRequested,
                    modifier=Modifier.testTag("bulk-actions"), sourceBackdrop=photoScene)
                GlassActionMenu(actionsOpen, {actionsOpen=false}, photoScene, buildList {
                    add(GlassMenuAction("选择范围", section="选择", onClick={scopeOpen=true}))
                    add(GlassMenuAction("清除选择", enabled=canAct, section="选择", onClick={selected=emptySet();allMatching=false}))
                    if(!knownSelection) add(GlassMenuAction("取消收藏", enabled=canAct, section="整理", onClick={confirm=BulkOperation.UNFAVORITE}))
                    add(GlassMenuAction(if(frozen.archived) "取消归档" else "归档", enabled=canAct, section="整理",
                        onClick={confirm=if(frozen.archived) BulkOperation.UNARCHIVE else BulkOperation.ARCHIVE}))
                    if(frozen.privateOnly && !frozen.archived) add(GlassMenuAction("取消归档", enabled=canAct, section="整理", onClick={confirm=BulkOperation.UNARCHIVE}))
                    add(GlassMenuAction(if(frozen.privateOnly) "移出私密空间" else "移入私密空间", enabled=canAct, section="整理",
                        onClick={confirm=if(frozen.privateOnly) BulkOperation.UNPRIVATE else BulkOperation.PRIVATE}))
                    if(!server.privateMedia) add(GlassMenuAction("规则预览", enabled=canAct && !allMatching, section="高级",
                        onClick={ruleTarget=RuleScanTarget.Selection(selected.toSet())}))
                })
            }
        }
        }
    }

    ruleTarget?.let {target -> RuleScanScreen(server,target,onChanged={items.refresh();onChanged()},onClose={ruleTarget=null})}
    privateOperation?.let {operation -> PrivateMutationGate(server,onClose={privateOperation=null},onAuthorized={perform(operation,it)}) }

}

private fun scopeDescription(filters:WallFilters):String = buildList {
    add("${filters.kind?.label ?: "照片与视频"} · ${if(filters.privateOnly) "私密空间（包含归档）" else if(filters.archived) "已归档" else "未归档"}")
    filters.favorite?.let {add(if(it) "仅收藏" else "仅未收藏")}
    if(filters.keyword.isNotEmpty()) add("文件名包含：${filters.keyword}")
    filters.libraryId?.let {add("媒体库：$it\n目录：${filters.folderPath ?: "全部"}")}
    if(filters.takenFrom!=null || filters.takenTo!=null) add("拍摄时间：${filters.takenFrom?.let(::taskTimestamp) ?: "不限"} 至 ${filters.takenTo?.let(::taskTimestamp) ?: "不限"}")
    listOf("全部包含标签" to filters.tagsAll,"任一包含标签" to filters.tagsAny,"排除标签" to filters.tagsExclude).forEach {(label,ids) -> if(ids.isNotEmpty()) add("$label：${ids.joinToString()}")}
}.joinToString("\n")
