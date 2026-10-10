package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
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
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassSwitchRow
import com.yunai.phototube.ui.SettingsNavigationRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TagRulesScreen(server: PhotoTubeServer, onChanged: () -> Unit, onClose: () -> Unit) {
    val repository = remember(server) { TagRuleRepository(server) }
    val flow = remember(repository) { Pager(PagingConfig(100, initialLoadSize = 100, maxSize = 500, enablePlaceholders = false)) { AlbumPagingSource(repository::list) }.flow }
    val rules = flow.collectAsLazyPagingItems()
    var scanning by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf(false)}
    var editing by rememberSaveable(server) { mutableStateOf(false) }
    var target by rememberConfigurationState(server) { mutableStateOf<TagRule?>(null) }
    var deleting by remember { mutableStateOf<TagRule?>(null) }
    var reclaim by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ManagementPage("标签规则", onClose, dismissEnabled = !busy, scrollable = false, showTitle = false, overlay = { scene ->
        deleting?.let { rule -> com.yunai.phototube.ui.GlassConfirmation(scene,
            title="删除“${rule.name}”？", message="默认只删除规则，已有标签保留。",
            confirmLabel="确认删除规则", destructive=true, onDismiss={deleting=null},
            details={ enabled ->
                com.yunai.phototube.ui.GlassOptionRow("同时回收此规则生成的未确认标签",reclaim,{reclaim=it},enabled=enabled)
                Text("人工确认的标签不会被回收。",color=AppTheme.colors.secondary)
            }, onConfirm={
                val reclaimSnapshot=reclaim
                deleting=null;busy=true;error=null
                scope.launch {
                    try {repository.delete(rule.id,reclaimSnapshot);rules.refresh();onChanged()}
                    catch(cancel:CancellationException) {throw cancel}
                    catch(problem:Exception) {error=problem.message}
                    finally {busy=false}
                }
            })
        }
    }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("tag-rule-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("heading") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManagementTitle("标签规则")
                    Text("根据文件名或相对路径匹配标签。保存规则不会自动处理历史照片，请选择范围预览后应用。", color = AppTheme.colors.secondary)
                    GlassSettingsAction("新建规则",{ target = null; editing = true },enabled = !busy)
                    if (rules.itemCount > 0) SettingsNavigationRow("按范围扫描",{ scanning = true },enabled = !busy)
                }
            }
            error?.let { message -> item("mutation-error") { Text(message, color = AppTheme.colors.text) } }
            items(rules.itemCount, key = rules.itemKey { it.id }) { index -> rules[index]?.let { rule ->
                ManagementGroup(rule.name) {
                    Text("${if (rule.enabled) "已启用" else "已停用"} · ${if (rule.target == "FILE_NAME") "文件名" else "相对路径"}", color = AppTheme.colors.secondary)
                    Text(rule.pattern, color = AppTheme.colors.text)
                    Text("${rule.tags.size} 个标签 · 已关联 ${rule.generated} 项", color = AppTheme.colors.secondary)
                    SettingsNavigationRow("编辑规则",{ target = rule; editing = true },enabled = !busy)
                    GlassSettingsAction("删除规则",{ deleting = rule; reclaim = false; error = null },enabled = !busy,destructive=true)
                }
            } }
            if (rules.loadState.refresh is LoadState.Loading || rules.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (rules.loadState.refresh as? LoadState.Error) ?: (rules.loadState.append as? LoadState.Error)
            if (failure != null) item { Text(failure.error.message ?: "读取失败"); GlassSettingsAction("重试",rules::retry,enabled = !busy) }
            if (rules.loadState.refresh is LoadState.NotLoading && rules.itemCount == 0) item { Text("还没有标签规则", color = AppTheme.colors.secondary) }
        }
    }
    if(scanning) RuleScanScopePicker(server,onChanged,onClose={scanning=false})
    if (editing) TagRuleEditor(server, target, onClose = { editing = false }, onSaved = { editing = false; rules.refresh(); onChanged() })

}

@Composable
private fun TagRuleEditor(server: PhotoTubeServer, rule: TagRule?, onClose: () -> Unit, onSaved: () -> Unit) {
    var name by rememberSaveable(server) { mutableStateOf(rule?.name.orEmpty()) }
    var pattern by rememberSaveable(server) { mutableStateOf(rule?.pattern.orEmpty()) }
    var target by rememberConfigurationState(server) { mutableStateOf(rule?.target ?: "FILE_NAME") }
    var tags by rememberConfigurationState(server) { mutableStateOf(rule?.tags ?: emptySet()) }
    var enabled by rememberSaveable(server) { mutableStateOf(rule?.enabled ?: true) }
    var picking by rememberSaveable(server) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val repository = remember(server) { TagRuleRepository(server) }
    val catalog = remember(server) { FilterCatalog(server) }
    val scope = rememberCoroutineScope()
    ManagementPage(if (rule == null) "新建规则" else "编辑规则", onClose, dismissEnabled = !busy) {
        ManagementGroup("匹配条件") {
            GlassTextField(name,{name=it},"规则名称",enabled=!busy)
            FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("FILE_NAME" to "文件名", "RELATIVE_PATH" to "相对路径").forEach { (value, label) ->
                GlassChoiceChip(label,target == value,{target=value},enabled=!busy)
            } }
            GlassTextField(pattern,{pattern=it},"匹配表达式",singleLine=false,minLines=2,maxLines=6,supportingText={Text("例如 (?i)holiday：匹配包含 holiday 的名称，不区分大小写。支持 RE2 正则表达式。")},enabled=!busy)
        }
        ManagementGroup("添加的标签") {
            SettingsNavigationRow(if(tags.isEmpty()) "选择标签" else "已选 ${tags.size} 个标签",{picking=true},enabled=!busy)
            GlassSwitchRow("启用规则",enabled,{enabled=it},enabled=!busy)
        }
        Text("保存后不会自动重新打标。", color = AppTheme.colors.secondary)
        error?.let { Text(it, color = AppTheme.colors.text) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        GlassSettingsAction("保存规则",{ busy = true; error = null; scope.launch {
            try { repository.save(rule?.id, name, pattern, target, tags, enabled); onSaved() }
            catch (cancel: CancellationException) { throw cancel } catch (problem: Exception) { error = problem.message } finally { busy = false }
        } }, enabled = !busy && name.trim().length in 1..120 && pattern.length in 1..512 && tags.size in 1..100)
    }
    if (picking) TagConditionPicker(catalog, tags, onClose = { picking = false }, onSelect = { tags = it; picking = false })
}
