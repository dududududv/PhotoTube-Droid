package com.yunai.phototube.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import com.yunai.phototube.ui.GlassSelectionMark
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun TrashScreen(server: PhotoTubeServer, onChanged: () -> Unit, onClose: () -> Unit) {
    var closing by rememberSaveable {mutableStateOf(false)}
    val repository = remember(server) { TrashRepository(server) }
    val scope = rememberCoroutineScope()
    val flow = rememberConfigurationPaging(server) { Pager(PagingConfig(100, initialLoadSize = 100, prefetchDistance = 12, maxSize = 500, enablePlaceholders = false)) { AlbumPagingSource(repository::list) }.flow }
    val items = flow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    var originalDeleteEnabled by remember { mutableStateOf(false) }
    var originalDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var capabilityError by remember { mutableStateOf(false) }
    var capabilityRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(server,capabilityRetry) {
        capabilityError=false
        try {val enabled=OriginalDeleteRepository(server).enabled();withContext(Dispatchers.Main.immediate) {originalDeleteEnabled=enabled}}
        catch(cancel:CancellationException) {throw cancel}
        catch(_:Exception) {withContext(Dispatchers.Main.immediate) {originalDeleteEnabled=false;capabilityError=true}}
    }
    var clearingAll by remember {mutableStateOf(false)}
    var selected by rememberConfigurationState(server) { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<Set<String>?>(null) }
    var error by rememberConfigurationState(server) { mutableStateOf<String?>(null) }
    var outcome by rememberConfigurationState(server) { mutableStateOf<String?>(null) }
    LaunchedEffect(outcome, error) {
        // The heading can be taller than a landscape viewport; reveal the result itself.
        if (outcome != null || error != null) listState.animateScrollToItem(1)
    }
    fun apply(ids: Set<String>, purge: Boolean) {
        if (busy || ids.isEmpty()) return
        busy = true; error = null; outcome = null
        scope.launch {
            try {
                val result = if (purge) repository.purge(ids) else repository.restore(ids)
                // Retain failures and any unreported targets for an explicit retry.
                selected = ids - result.succeeded
                outcome = "${result.succeeded.size} 项已${if (purge) "清除记录" else "恢复"}"
                val missing = ids - result.succeeded - result.failed.map { it.id }.toSet()
                error = (result.failed.map { "${it.message}（${it.code}）" } + if (missing.isNotEmpty()) listOf("${missing.size} 项未返回结果，请刷新后核对") else emptyList()).takeIf { it.isNotEmpty() }?.joinToString("\n")
                if (purge && result.succeeded.isNotEmpty()) server.imageLoader.memoryCache?.clear()
                items.refresh(); onChanged()
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { error = problem.message ?: "操作失败，请刷新后核对" }
            finally { busy = false }
        }
    }
    ManagementPage("回收站", onClose, closeRequested=closing, dismissEnabled = !busy, scrollable = false, showTitle = false, overlay={scene ->
        ManagementRefreshAction(scene,!busy && items.loadState.refresh !is LoadState.Loading,items::refresh)
        confirmPurge?.let {ids -> com.yunai.phototube.ui.GlassConfirmation(scene,title="清除 ${ids.size} 项记录？",
            message="这些 PhotoTube 记录和派生缓存将无法恢复。NAS 原文件不会删除；以后扫描相同目录时，文件可能重新入库。",
            confirmLabel="确认清除记录",destructive=true,
            onConfirm={confirmPurge=null;apply(ids,true)},onDismiss={confirmPurge=null})}
    }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("trash-list"), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("heading") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManagementTitle("回收站")
                    Text("删除的照片和视频会暂存在这里，可以恢复或清除记录。", color = AppTheme.colors.secondary)
                    if (items.itemCount > 0 || selected.isNotEmpty()) GlassSettingsAction(if(selected.isEmpty()) "选择 ${items.itemSnapshotList.items.take(500).size} 项" else "取消选择 ${selected.size} 项",
                        {if(selected.isEmpty()) selected=items.itemSnapshotList.items.take(500).map {it.asset.id}.toSet() else selected=emptySet()},enabled=!busy)
                    if (items.itemCount > 0) SettingsNavigationRow("清理全部回收站记录…",{clearingAll=true},enabled=!busy)
                    if(capabilityError) GlassSettingsAction("原文件删除能力读取失败，重试",{capabilityRetry++},enabled=!busy)
                }
            }
            if (busy) item("mutation-progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            outcome?.let { message -> item("mutation-outcome") { Text(message, color = AppTheme.colors.text) } }
            error?.let { message -> item("mutation-error") { Text(message, color = AppTheme.colors.text) } }
            items(items.itemCount, key = items.itemKey { it.asset.id }) { index -> items[index]?.let { entry ->
                val id = entry.asset.id
                val checked = id in selected
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppTheme.colors.group)
                    .toggleable(checked,enabled=!busy && (checked || selected.size<500),role=Role.Checkbox) {selected=if(it) selected+id else selected-id}
                    .semantics { this.selected = checked }.testTag("trash-$id").padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.subtle), contentAlignment = Alignment.Center) {
                        Text(if (entry.asset.kind == "VIDEO") "视频" else "照片", color = AppTheme.colors.secondary)
                        AsyncImage(server.thumbnail(entry.asset), entry.asset.fileName, imageLoader = server.imageLoader, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.asset.fileName, maxLines = 2, color = AppTheme.colors.text)
                        Text("删除于 ${taskTimestamp(entry.trashedAt)}", color = AppTheme.colors.secondary, style = MaterialTheme.typography.bodySmall)
                        Text(if (entry.remainingDays() == 0) "保留期已到" else "剩余 ${entry.remainingDays()} 天", color = AppTheme.colors.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                    GlassSelectionMark(checked)
                }
            } }
            if (items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
            if (failure != null) item { Text(failure.error.message ?: "加载失败", color = AppTheme.colors.text); GlassSettingsAction("重试",items::retry,enabled=!busy) }
            if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) item {
                Column(Modifier.fillMaxWidth().padding(vertical=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("回收站是空的",color=AppTheme.colors.text,style=MaterialTheme.typography.titleLarge)
                    Text("这里的恢复与清除仅影响相册记录，不会删除原文件。",color=AppTheme.colors.secondary)
                    GlassSettingsAction("返回浏览",{closing=true})
                }
            }
        }
        if(originalDeleteEnabled && selected.isNotEmpty()) SettingsNavigationRow(if(selected.size>20) "原文件删除每批最多 20 项" else "删除所选 NAS 原文件…",{originalDeleteIds=selected.toSet()},enabled=!busy && selected.size in 1..20)
        if (items.itemCount > 0 || selected.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {GlassSettingsAction("恢复 ${selected.size} 项",{apply(selected.toSet(),false)},enabled=!busy && selected.isNotEmpty())}
            Box(Modifier.weight(1f)) {GlassSettingsAction("清除记录",{confirmPurge=selected.toSet()},enabled=!busy && selected.isNotEmpty())}
        }
    }
    if(clearingAll) TrashClearScreen(server,onChanged={selected=emptySet();items.refresh();onChanged()},onClose={clearingAll=false;items.refresh();onChanged()})
    originalDeleteIds?.let {ids -> OriginalDeleteScreen(server,ids,onResult={result ->
        selected=selected-(result.succeeded-result.failed.map {it.id}.toSet());items.refresh();onChanged()
    },onClose={originalDeleteIds=null;items.refresh();onChanged()})}
}
