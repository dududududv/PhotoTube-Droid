package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassOptionRow
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private class UnusedTagsState {
    var page by mutableStateOf<UnusedTagPage?>(null)
    var cursor by mutableStateOf<String?>(null)
    var selected by mutableStateOf<Map<Long, UnusedTag>>(emptyMap())
    var submitted by mutableStateOf<List<UnusedTag>>(emptyList())
    var result by mutableStateOf<UnusedTagResult?>(null)
    var uncertain by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
internal fun UnusedTagsScreen(server: PhotoTubeServer, onChanged: () -> Unit, onClose: () -> Unit) {
    val state = rememberConfigurationState(server) { UnusedTagsState() }
    val repository = remember(server) { UnusedTagsRepository(server) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<List<UnusedTag>?>(null) }
    suspend fun load(cursor: String?, reset: Boolean = false) {
        busy = true; state.error = null; state.cursor = cursor
        try {
            val page = repository.preview(cursor)
            state.page = page; state.cursor = cursor
            if (reset) { state.selected = emptyMap(); state.uncertain = false }
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { state.error = failure.message ?: "读取失败" }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { if (state.page == null && !state.uncertain) load(null) }
    ManagementPage("清理未使用标签", onClose, dismissEnabled = !busy, scrollable = false, overlay = { scene ->
        confirm?.let { snapshot -> com.yunai.phototube.ui.GlassConfirmation(scene,
            title = "清理 ${snapshot.size} 个标签？",
            message = "仅清理已选择且仍无引用的标签。期间名称、更新时间或引用发生变化的标签会被跳过。",
            confirmLabel = "确认清理", destructive = true, onDismiss = { confirm = null }, onConfirm = {
                confirm = null; busy = true; state.uncertain = true; state.error = null; state.result = null; state.submitted = snapshot
                scope.launch {
                    try {
                        state.result = repository.cleanup(snapshot)
                        state.selected = emptyMap(); state.uncertain = false; onChanged()
                        load(null)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (failure: Exception) { state.error = failure.message ?: "清理结果未确认" }
                    finally { busy = false }
                }
            })
        }
    }) {
        LazyColumn(Modifier.weight(1f).testTag("unused-tags-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("仅显示确认没有引用的标签。照片、文件和使用中的标签不受影响。", color = AppTheme.colors.secondary)
                    Text("当前可清理 ${state.page?.total ?: "—"} 个", color = AppTheme.colors.text)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = AppTheme.colors.text) }
                    if (state.uncertain) Text("清理结果尚未确认，请刷新候选后重新核对，不会自动重试。", color = AppTheme.colors.secondary)
                    if (state.error != null && !state.uncertain) {
                        GlassSettingsAction("重试当前页", { scope.launch { load(state.cursor) } }, enabled = !busy)
                    } else {
                        GlassSettingsAction("刷新候选", { scope.launch { load(null, reset = true) } }, enabled = !busy)
                    }
                    if (state.selected.isNotEmpty()) {
                        Text("已选 ${state.selected.size} 个 · 每次最多 500 个", color = AppTheme.colors.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { GlassSettingsAction("取消选择", { state.selected = emptyMap() }, enabled = !busy) }
                            Box(Modifier.weight(1f)) { GlassSettingsAction("清理已选 ${state.selected.size} 个", { confirm = state.selected.values.toList() }, enabled = !busy && !state.uncertain, destructive = true) }
                        }
                    }
                }
            }
            state.result?.let { result ->
                item { ManagementGroup("上次清理结果") {
                    Text("已清理 ${result.deleted.size} 个 · 跳过 ${result.skipped.size} 个", color = AppTheme.colors.text)
                    Text("跳过项可能已变化、已有引用或已不存在；请重新预览后决定。", color = AppTheme.colors.secondary)
                    GlassSettingsAction("收起结果", { state.result = null; state.submitted = emptyList() }, enabled = !busy)
                } }
                items(state.submitted, key = { "result-${it.id}" }) { tag ->
                    Text("${tag.name} · ${if (tag.id in result.deleted) "已清理" else "已跳过"}", color = AppTheme.colors.secondary)
                }
            }
            items(state.page?.items.orEmpty(), key = { it.id }) { tag ->
                GlassOptionRow(tag.name, tag.id in state.selected, { checked ->
                    state.selected = if (checked) state.selected + (tag.id to tag) else state.selected - tag.id
                }, enabled = !busy && !state.uncertain && (tag.id in state.selected || state.selected.size < 500),
                    modifier = Modifier.testTag("unused-tag-${tag.id}"))
            }
            item {
                if (state.page?.items?.isEmpty() == true) Text("没有可清理的标签", color = AppTheme.colors.secondary)
                state.page?.cursor?.let { next -> GlassSettingsAction("下一页", { scope.launch { load(next) } }, enabled = !busy && !state.uncertain) }
            }
        }
    }

}
