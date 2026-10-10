package com.yunai.phototube.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import com.yunai.phototube.ui.GlassSwitchRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassOptionRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.*

@Composable
internal fun SmartAlbumEditor(repository: AlbumRepository, album: RemoteAlbum, onSaved: (RemoteAlbum) -> Unit, onClose: () -> Unit) {
    var draft by rememberConfigurationState(repository.server to album.id) { mutableStateOf(SmartFilterDraft.from(album.raw.getJSONObject("filter"))) }
    val catalog = remember(repository) { FilterCatalog(repository.server) }
    var tags by rememberSaveable(album.id) { mutableStateOf<String?>(null) }
    var folderOpen by rememberSaveable(album.id) { mutableStateOf(false) }
    var dateField by rememberSaveable(album.id) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ManagementPage("智能相册规则", onClose, dismissEnabled = !busy) {
        Text(album.name, color = AppTheme.colors.secondary)
        ManagementGroup("媒体与状态") {
            FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(null, "PHOTO", "VIDEO").forEach { kind ->
                GlassChoiceChip(when (kind) { "PHOTO" -> "照片"; "VIDEO" -> "视频"; else -> "全部" },draft.kind == kind,{draft=draft.copy(kind=kind)},enabled=!busy)
            } }
            Text("收藏", color = AppTheme.colors.secondary)
            FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(null, true, false).forEach { favorite ->
                GlassChoiceChip(when(favorite) {true -> "已收藏";false -> "未收藏";else -> "不限"},draft.favorite==favorite,{draft=draft.copy(favorite=favorite)},enabled=!busy)
            } }
            GlassSwitchRow("仅归档内容",draft.archived,{draft=draft.copy(archived=it)},enabled=!busy)
        }
        ManagementGroup("文件名与目录") {
            GlassTextField(draft.keyword, { draft = draft.copy(keyword = it) }, "文件名关键词", supportingText = { Text("3–255 个字符，仅匹配文件名") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("smart-keyword"))
            SettingsNavigationRow(draft.folder ?: if (draft.library != null) "已选择媒体库" else "所有已入库目录",{folderOpen=true},enabled=!busy)
            if (draft.library != null) GlassSettingsAction("清除目录条件",{draft=draft.copy(library=null,folder=null)},enabled=!busy)
        }
        ManagementGroup("拍摄日期") {
            for ((key, label, value) in listOf(Triple("from", "开始日期", draft.takenFrom), Triple("to", "结束日期", draft.takenTo))) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f)) {SettingsNavigationRow("$label：${value?.let {taskTimestamp(it).substringBefore(' ')} ?: "不限"}",{dateField=key},enabled=!busy)}
                    if(value!=null) Box(Modifier.width(72.dp)) {GlassSettingsAction("清除",{draft=if(key=="from") draft.copy(takenFrom=null) else draft.copy(takenTo=null)},enabled=!busy)}
                }
            }
            Text("按本地时区选择，包含开始日和结束日。", color = AppTheme.colors.secondary)
        }
        ManagementGroup("标签组合") {
            listOf(Triple("all", "全部包含", draft.all), Triple("any", "任一包含", draft.any), Triple("exclude", "排除", draft.exclude)).forEach { (key, label, values) ->
                SettingsNavigationRow("$label · ${if(values.isEmpty()) "不限" else "${values.size} 个标签"}",{tags=key},enabled=!busy)
            }
            Text("三组条件同时生效，每组最多 100 个标签。", color = AppTheme.colors.secondary)
        }
        val validation = draft.validationError()
        (error ?: validation)?.let { Text(it, color = AppTheme.colors.text) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        GlassSettingsAction("保存规则",{ busy = true; error = null; scope.launch {
            try { onSaved(repository.update(album.id, org.json.JSONObject().put("filter", draft.json()))) }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { error = problem.message }
            finally { busy = false }
        } }, enabled = !busy && validation == null)
    }
    tags?.let { key -> TagConditionPicker(catalog, when (key) { "all" -> draft.all; "any" -> draft.any; else -> draft.exclude }, onClose = { tags = null }) { selected ->
        draft = when (key) { "all" -> draft.copy(all = selected); "any" -> draft.copy(any = selected); else -> draft.copy(exclude = selected) }; tags = null
    } }
    if (folderOpen) IndexedFolderPicker(catalog, onClose = { folderOpen = false }) { folder -> draft = draft.copy(library = folder.libraryId, folder = folder.path.ifEmpty { null }); folderOpen = false }
    dateField?.let { field -> FilterDatePicker(if (field == "from") draft.takenFrom else draft.takenTo, field == "to", onClose = { dateField = null }) { value ->
        draft = if (field == "from") draft.copy(takenFrom = value) else draft.copy(takenTo = value); dateField = null
    } }
}

@Composable
internal fun TagConditionPicker(catalog: FilterCatalog, initial: Set<Long>, onClose: () -> Unit, onSelect: (Set<Long>) -> Unit) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var selected by rememberConfigurationState(catalog) { mutableStateOf(initial) }
    val names = rememberConfigurationState(catalog) { mutableStateMapOf<Long, String>() }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(keyword) { kotlinx.coroutines.delay(300); query = keyword.trim() }
    val flow = remember(query) { Pager(PagingConfig(100, initialLoadSize = 100, enablePlaceholders = false)) { AlbumPagingSource { cursor -> catalog.tags(query, cursor) } }.flow }
    val tags = flow.collectAsLazyPagingItems()
    LaunchedEffect(tags.itemSnapshotList.items) { tags.itemSnapshotList.items.forEach { names[it.id] = it.name } }
    ManagementPage("选择标签", onClose) {
        val selectedText=androidx.compose.ui.text.rememberTextMeasurer().measure(
            "标签 · 移除",style=LocalTextStyle.current.copy(fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold),maxLines=1)
        val selectedHeight=with(androidx.compose.ui.platform.LocalDensity.current) {selectedText.size.height.toDp()+20.dp}.coerceAtLeast(56.dp)
        GlassTextField(keyword, { if (it.length <= 120) keyword = it }, "搜索标签")
        Text("已选 ${selected.size}/100", color = AppTheme.colors.secondary)
        Box(Modifier.fillMaxWidth().height(selectedHeight)) {
            if(selected.isEmpty()) Text("尚未选择标签",color=AppTheme.colors.secondary)
            else LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(selected.toList(),key={it}) {id ->
                    GlassChoiceChip("${names[id] ?: "标签 #$id"} · 移除",true,{selected=selected-id})
                }
            }
        }
        GlassSettingsAction("完成选择", { onSelect(selected) })
        LazyColumn(Modifier.fillMaxWidth().height(340.dp).testTag("tag-condition-list")) {
            items(tags.itemCount, key = tags.itemKey { it.id }) { index -> tags[index]?.let { tag ->
                Column(Modifier.padding(bottom=8.dp)) {
                    GlassOptionRow(tag.name,tag.id in selected,{checked -> selected=if(checked) selected+tag.id else selected-tag.id},
                        enabled=tag.id in selected || selected.size<100)
                    Text("${tag.count} 项",color=AppTheme.colors.secondary,modifier=Modifier.padding(start=12.dp,top=4.dp))
                }
            } }
            if (tags.loadState.refresh is LoadState.Loading || tags.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val error = (tags.loadState.refresh as? LoadState.Error) ?: (tags.loadState.append as? LoadState.Error)
            if (error != null) item { Text(error.error.message ?: "加载失败",color=AppTheme.colors.text); GlassSettingsAction("重试",tags::retry) }
            if (tags.loadState.refresh is LoadState.NotLoading && tags.itemCount == 0) item { Text("没有匹配的标签", color = AppTheme.colors.secondary) }
        }
    }
}

@Composable
internal fun IndexedFolderPicker(catalog: FilterCatalog, onClose: () -> Unit, onSelect: (LibraryFolder) -> Unit) {
    var trail by rememberConfigurationState(catalog) { mutableStateOf<List<LibraryFolder>>(emptyList()) }
    val current = trail.lastOrNull()
    val flow = remember(current) { Pager(PagingConfig(100, initialLoadSize = 100, enablePlaceholders = false)) { AlbumPagingSource { cursor -> catalog.folders(current?.libraryId, current?.path.orEmpty(), cursor) } }.flow }
    val folders = flow.collectAsLazyPagingItems()
    ManagementPage("已入库目录", onClose) {
        Text(current?.path?.ifEmpty { current.name } ?: "媒体库", color = AppTheme.colors.secondary)
        if (current != null) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {GlassSettingsAction("上一级",{trail=trail.dropLast(1)})}
            Box(Modifier.weight(1f)) {GlassSettingsAction("选择此目录",{onSelect(current)},enabled=current.online)}
        }
        LazyColumn(Modifier.fillMaxWidth().height(440.dp)) {
            items(folders.itemCount, key = folders.itemKey { "${it.libraryId}/${it.path}" }) { index -> folders[index]?.let { folder ->
                SettingsNavigationRow(folder.name + if (!folder.online) " · 离线" else "",{trail=trail+folder},enabled=folder.online)
            } }
            if (folders.loadState.refresh is LoadState.Loading || folders.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val error = (folders.loadState.refresh as? LoadState.Error) ?: (folders.loadState.append as? LoadState.Error)
            if (error != null) item { Text(error.error.message ?: "加载失败"); GlassSettingsAction("重试",folders::retry) }
            if (folders.loadState.refresh is LoadState.NotLoading && folders.itemCount == 0) item { Text("没有子目录", color = AppTheme.colors.secondary) }
        }
    }
}
