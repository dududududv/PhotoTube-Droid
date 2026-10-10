package com.yunai.phototube.ui.remote

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.state.rememberConfigurationState
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun RuleScanScreen(server: PhotoTubeServer, album: RemoteAlbum, onChanged: () -> Unit, onClose: () -> Unit) {
    RuleScanScreen(server, RuleScanTarget.Album(album.id,album.name), onChanged, onClose)
}

@Composable
internal fun RuleScanScreen(server: PhotoTubeServer, target: RuleScanTarget, onChanged: () -> Unit, onClose: () -> Unit) {
    var ruleIds by rememberConfigurationState(server to target.bookmark, enabled = !server.privateMedia) {mutableStateOf<Set<String>>(emptySet())}
    var choosing by rememberConfigurationState(server to target.bookmark, enabled = !server.privateMedia) {mutableStateOf(false)}
    key(target.bookmark,ruleIds) {RuleScanWorkflow(server,target,ruleIds,onRules={choosing=true},onChanged,onClose)}
    if(choosing) ScanRulePicker(server,ruleIds,onClose={choosing=false},onSelect={ruleIds=it;choosing=false})
}

@Composable
private fun RuleScanWorkflow(server: PhotoTubeServer, target: RuleScanTarget, ruleIds: Set<String>, onRules: () -> Unit, onChanged: () -> Unit, onClose: () -> Unit) {
    val repository = remember(server) { TagRuleRepository(server) }
    val context = LocalContext.current
    // Only a server run ID is saved; no media, credentials or transient result is persisted.
    val preferences = remember { context.getSharedPreferences("rule-scan-bookmarks", Context.MODE_PRIVATE) }
    val bookmark = remember(server.base, target.bookmark, ruleIds) { server.base + "/" + ruleScanBookmark(target,ruleIds) }
    var runId by remember { mutableStateOf(preferences.getString(bookmark, null)) }
    var scan by remember { mutableStateOf<RuleScan?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(runId != null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var undone by remember { mutableStateOf(false) }
    var missingRun by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    suspend fun adopt(result: RuleScan) {
        withContext(Dispatchers.IO) { preferences.edit().putString(bookmark, result.run.id).commit() }
        withContext(Dispatchers.Main.immediate) {scan = result; runId = result.run.id; undone = false; missingRun = false}
    }
    fun operate(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { operation() }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { withContext(Dispatchers.Main.immediate) {error = problem.message ?: "操作失败"} }
            finally { withContext(Dispatchers.Main.immediate) {busy = false; retry++} }
        }
    }
    LaunchedEffect(runId, busy, retry) {
        val id = runId ?: return@LaunchedEffect
        if (busy) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var attempt = 0
            try {
                loading = true
                while (true) {
                    val result=repository.scan(id); withContext(Dispatchers.Main.immediate) {scan=result;loading=false;missingRun=false}
                    if (scan?.run?.polling != true) { if (scan?.run?.dryRun == false) onChanged(); break }
                    delay(listOf(1000L, 2000L, 3000L, 5000L)[attempt.coerceAtMost(3)]); attempt++
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { withContext(Dispatchers.Main.immediate) {error = problem.message ?: "读取任务失败"; missingRun = problem is ServerError && problem.code in setOf("NOT_FOUND", "HTTP_404")} }
            finally { withContext(Dispatchers.Main.immediate) {loading = false} }
        }
    }
    ManagementPage("规则扫描", onClose, dismissEnabled = !busy, overlay={scene ->
        confirm?.let {action ->
            val result=scan
            com.yunai.phototube.ui.GlassConfirmation(scene,
                title=if(action=="apply") "应用规则预览？" else "撤销本批标签？",
                message=if(action=="apply") "将在此预览保存的范围内写入规则标签，不会修改照片原文件。" else "回收本批新增的自动标签，保留后续人工修改。",
                confirmLabel=if(action=="apply") "确认应用" else "确认撤销", destructive=action=="undo",
                onDismiss={confirm=null},onConfirm={confirm=null;if(result!=null) operate {
                    if(action=="apply") {adopt(repository.apply(result));onChanged()}
                    else {repository.undo(result);undone=true;onChanged()}
                }})
        }
    }) {
        Text(if(target is RuleScanTarget.TimeRange) "拍摄时间：${taskTimestamp(target.from)} 至 ${taskTimestamp(target.to)}" else target.label, color = AppTheme.colors.secondary)
        ManagementGroup("先预览，再应用") {
            Text("匹配此范围内的普通照片与视频。预览不会修改标签，确认后才应用。", color = AppTheme.colors.secondary)
            SettingsNavigationRow(if(ruleIds.isEmpty()) "全部已启用规则" else "已选 ${ruleIds.size} 条规则",onRules,enabled=!busy && !loading && scan?.run?.active!=true)
            GlassSettingsAction("开始规则预览",{ operate { adopt(repository.preview(target.json(),ruleIds)) } },
                enabled = !busy && !loading && scan?.run?.active != true && (error == null || runId == null))
        }
        if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = AppTheme.colors.text); GlassSettingsAction("重新读取状态",{ error = null; retry++ },enabled=!busy) }
        if (missingRun) GlassSettingsAction("清除失效记录",{
            preferences.edit().remove(bookmark).apply()
            runId = null; scan = null; error = null; missingRun = false
        },enabled=!busy)
        scan?.let { result ->
            val run = result.run
            ManagementGroup(if (run.dryRun) "预览结果" else "应用结果") {
                Text("${aiStateLabel(run.state)} · ${run.processed} / ${run.total}", color = AppTheme.colors.text)
                val progress by animateFloatAsState(if (run.total > 0) (run.processed.toDouble() / run.total).toFloat().coerceIn(0f, 1f) else 0f, tween(350), label = "rule-scan-progress")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("匹配 ${result.matched} 项 · ${if (run.dryRun) "拟添加" else "标签变更"} ${result.additions}\n跳过 ${run.skipped} · 冲突 ${run.conflicts}", color = AppTheme.colors.secondary)
                if (result.highMatch && run.dryRun) Text("本次命中范围较广（${"%.1f".format(result.percentage)}%），请核对标签与样本后再应用。", color = AppTheme.colors.text)
                run.error?.let { Text(it, color = AppTheme.colors.text) }
                if (!result.ruleOnly) Text("该记录不是规则扫描，不能在这里应用。", color = AppTheme.colors.secondary)
                result.run.report.forEach { report ->
                    Text("${report.name} · ${report.count} 项", color = AppTheme.colors.text)
                    if (report.samples.isNotEmpty()) Text(report.samples.joinToString("、"), color = AppTheme.colors.secondary)
                }
                if (result.canApply) GlassSettingsAction("应用预览结果",{confirm="apply"},enabled=!busy && error==null)
                if (result.canUndo && !undone) GlassSettingsAction("撤销本批标签",{confirm="undo"},enabled=!busy && error==null)
                if (undone) Text("已撤销本批标签，后续人工修改保留。", color = AppTheme.colors.secondary)
            }
        }
    }
}
