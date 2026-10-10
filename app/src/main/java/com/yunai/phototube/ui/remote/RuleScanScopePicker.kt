package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.paging.*
import androidx.paging.compose.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassOptionRow
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow

@Composable
internal fun RuleScanScopePicker(server: PhotoTubeServer, onChanged: () -> Unit, onClose: () -> Unit) {
    var directoryMode by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf(true) }
    var folder by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf<LibraryFolder?>(null) }
    var from by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf<String?>(null) }
    var to by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf<String?>(null) }
    var pickingFolder by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf(false) }
    var pickingDate by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf<Boolean?>(null) }
    var target by rememberConfigurationState(server, enabled = !server.privateMedia) { mutableStateOf<RuleScanTarget?>(null) }
    val chosen = if(directoryMode) folder?.let { value -> runCatching {RuleScanTarget.Directory(requireNotNull(value.libraryId),value.path,value.name)}.getOrNull() }
        else if(from!=null && to!=null) runCatching {RuleScanTarget.TimeRange(from!!,to!!)}.getOrNull() else null
    ManagementPage("选择规则扫描范围",onClose) {
        Text("目录与时间范围可跨相册。选中照片的规则预览在图库的批量操作中，相册范围在相册设置中。",color=AppTheme.colors.secondary)
        FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            GlassChoiceChip("目录",directoryMode,{directoryMode=true})
            GlassChoiceChip("拍摄时间",!directoryMode,{directoryMode=false})
        }
        ManagementGroup(if(directoryMode) "已入库目录" else "拍摄日期范围") {
            if(directoryMode) {
                SettingsNavigationRow(folder?.path?.ifEmpty {folder!!.name} ?: "选择目录",{pickingFolder=true})
                Text("包含所选目录下的子目录。这里只处理已入库内容，不触发文件扫描。",color=AppTheme.colors.secondary)
                if(folder!=null && chosen==null) Text("请选择媒体库中的具体子目录；若仍不可用，请更换目录。",color=AppTheme.colors.secondary)
            } else {
                SettingsNavigationRow(from?.let {"开始：${taskTimestamp(it)}"} ?: "选择开始日期",{pickingDate=false})
                SettingsNavigationRow(to?.let {"结束：${taskTimestamp(it)}"} ?: "选择结束日期",{pickingDate=true})
                Text("按本地时区选择日期，包含起止两天。",color=AppTheme.colors.secondary)
                if(from!=null && to!=null && chosen==null) Text("结束日期不能早于开始日期",color=AppTheme.colors.text)
            }
        }
        GlassSettingsAction("继续核对范围",{target=chosen},enabled=chosen!=null)
    }
    if(pickingFolder) IndexedFolderPicker(FilterCatalog(server),onClose={pickingFolder=false},onSelect={folder=it;pickingFolder=false})
    pickingDate?.let {end -> FilterDatePicker(if(end) to else from,end,onClose={pickingDate=null},onSelect={if(end) to=it else from=it;pickingDate=null})}
    target?.let {value -> key(value.bookmark) {RuleScanScreen(server,value,onChanged,onClose={target=null})} }
}

@Composable
internal fun ScanRulePicker(server: PhotoTubeServer, initial: Set<String>, onClose: () -> Unit, onSelect: (Set<String>) -> Unit) {
    var selected by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf(initial.toSet())}
    val repository=remember(server) {TagRuleRepository(server)}
    val flow=remember(repository) {Pager(PagingConfig(100,initialLoadSize=100,maxSize=500,enablePlaceholders=false)) {AlbumPagingSource(repository::list)}.flow}
    val rules=flow.collectAsLazyPagingItems()
    ManagementPage("选择扫描规则",onClose,scrollable=false) {
        Text("未指定规则时使用全部已启用规则；也可最多指定 100 条。停用的规则不能新加入选择。",color=AppTheme.colors.secondary)
        GlassSettingsAction("使用全部已启用规则",{onSelect(emptySet())})
        Text("已选 ${selected.size}/100 条",color=AppTheme.colors.secondary)
        LazyColumn(Modifier.weight(1f)) {
            items(rules.itemCount,key=rules.itemKey {it.id}) {index -> rules[index]?.let {rule ->
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    GlassOptionRow(rule.name,rule.id in selected,{checked -> selected=if(checked) selected+rule.id else selected-rule.id},
                        enabled=rule.id in selected || rule.enabled && selected.size<100,
                        modifier=Modifier.semantics {contentDescription=rule.name})
                    Text(if(rule.enabled) rule.pattern else "已停用",color=AppTheme.colors.secondary,modifier=Modifier.padding(horizontal=12.dp))
                }
            } }
            val error=(rules.loadState.refresh as? LoadState.Error) ?: (rules.loadState.append as? LoadState.Error)
            if(error!=null) item {Text("规则读取失败",color=AppTheme.colors.text);GlassSettingsAction("重试",rules::retry)}
            if(rules.loadState.refresh is LoadState.Loading || rules.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(rules.loadState.refresh is LoadState.NotLoading && rules.itemCount==0) item {Text("还没有标签规则",color=AppTheme.colors.secondary)}
        }
        GlassSettingsAction("使用所选规则",{onSelect(selected.toSet())},enabled=selected.isNotEmpty())
    }
}
