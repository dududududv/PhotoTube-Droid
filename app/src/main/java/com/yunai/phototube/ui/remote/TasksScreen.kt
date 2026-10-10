package com.yunai.phototube.ui.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import kotlinx.coroutines.*

private suspend fun <T> readTaskPart(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (cancel: CancellationException) { throw cancel } catch (problem: Exception) { Result.failure(problem) }

@Composable
internal fun TasksScreen(server: PhotoTubeServer, onClose: () -> Unit) {
    var closing by rememberSaveable {mutableStateOf(false)}
    val repository = remember(server) { TasksRepository(server) }
    var scans by rememberConfigurationState(server) { mutableStateOf<List<ScanBatch>>(emptyList()) }
    var scanCursor by rememberConfigurationState(server) { mutableStateOf<String?>(null) }
    var scanPrevious by rememberConfigurationState(server) { mutableStateOf<List<String?>>(emptyList()) }
    var scanNext by rememberConfigurationState(server) { mutableStateOf<String?>(null) }
    var scanFailure by rememberConfigurationState(server) { mutableStateOf<Pair<String, String>?>(null) }
    var jobs by rememberConfigurationState(server) { mutableStateOf<List<JobSummary>>(emptyList()) }
    var ai by rememberConfigurationState(server) { mutableStateOf<List<AiTask>>(emptyList()) }
    var cursor by rememberConfigurationState(server) { mutableStateOf<String?>(null) }
    var previous by rememberConfigurationState(server) { mutableStateOf<List<String?>>(emptyList()) }
    var next by rememberConfigurationState(server) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var detail by rememberConfigurationState(server) { mutableStateOf<TaskRow?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cursor, scanCursor, refresh, detail?.key, scanFailure) {
        if (detail != null || scanFailure != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var firstRead = true
            do {
                loading = true
                supervisorScope {
                    val scanRead = async { readTaskPart {
                        repository.scans(scanCursor).also { page ->
                            if (page.cursor != null && (page.cursor == scanCursor || page.cursor in scanPrevious)) throw ServerError("INVALID_CURSOR", "扫描批次返回重复游标")
                        }
                    } }
                    val jobRead = async { readTaskPart { repository.summary() } }
                    val aiRead = async { readTaskPart {
                        (if (firstRead || cursor == null) repository.ai(cursor) else AlbumPage(ai, next)).also { page ->
                            if (page.cursor != null && (page.cursor == cursor || page.cursor in previous)) throw ServerError("INVALID_CURSOR", "服务器返回重复游标")
                        }
                    } }
                    val summary = jobRead.await(); val page = aiRead.await(); val scanPage = scanRead.await()
                    scanPage.onSuccess { scans = it.items; scanNext = it.cursor }
                    summary.onSuccess { jobs = it }
                    page.onSuccess { ai = it.items; next = it.cursor }
                    val problems = listOfNotNull(scanPage.exceptionOrNull()?.let { "扫描批次：${it.message}" }, summary.exceptionOrNull()?.let { "普通任务：${it.message}" }, page.exceptionOrNull()?.let { "AI 任务：${it.message}" })
                    error = problems.takeIf { it.isNotEmpty() }?.joinToString("\n")
                }
                loading = false
                firstRead = false
                if (error != null) break
                val active = scans.any { it.active } || jobs.any { it.active } || (cursor == null && ai.any { it.run.active })
                delay(if (active) 3000 else 15000)
            } while (true)
        }
    }
    val rows = mergedTasks(jobs, ai, scans)
    // A fixed footer must not consume the task viewport on short screens or large text.
    val inlinePagination = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480 ||
        androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val pagination: @Composable () -> Unit = {
        // The two cursors address different histories. Keep their controls together,
        // and omit a history's controls when there is nowhere to navigate.
        if (scanPrevious.isNotEmpty() || scanNext != null || previous.isNotEmpty() || next != null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scanPrevious.isNotEmpty() || scanNext != null) {
                    Text("相册扫描记录", color = AppTheme.colors.secondary, style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {GlassSettingsAction("上一页相册扫描",{scanCursor=scanPrevious.last();scanPrevious=scanPrevious.dropLast(1);scans=emptyList();scanNext=null},enabled=!loading && scanPrevious.isNotEmpty())}
                        Box(Modifier.weight(1f)) {GlassSettingsAction("更多相册扫描",{scanPrevious=scanPrevious+listOf(scanCursor);scanCursor=scanNext;scans=emptyList();scanNext=null},enabled=!loading && scanNext!=null)}
                    }
                }
                if (previous.isNotEmpty() || next != null) {
                    Text("AI 任务历史", color = AppTheme.colors.secondary, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {GlassSettingsAction("上一页",{cursor=previous.last();previous=previous.dropLast(1);ai=emptyList();next=null},enabled=!loading && previous.isNotEmpty())}
                        Box(Modifier.weight(1f)) {GlassSettingsAction("更多历史",{previous=previous+listOf(cursor);cursor=next;ai=emptyList();next=null},enabled=!loading && next!=null && error==null)}
                    }
                }
            }
        }
    }
    ManagementPage("任务中心", onClose, closeRequested=closing, scrollable = false, showTitle = false, overlay={scene ->
        ManagementRefreshAction(scene,!loading) {refresh++}
    }) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("task-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("title") { ManagementTitle("任务中心") }
            item("introduction") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("查看后台处理进度。任务配置和控制仍在对应相册内。", color = AppTheme.colors.secondary)
                    if(cursor!=null) GlassSettingsAction("回到最新",{previous=emptyList();cursor=null;ai=emptyList();next=null},enabled=!loading)
                    // Polling must not move the list when loading changes.
                    Box(Modifier.fillMaxWidth().height(4.dp)) {
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            error?.let { message ->
                item("partial-error") {
                    SelectionContainer { Text("列表不完整\n$message", color = AppTheme.colors.text) }
                }
            }
            for (section in 0..2) {
                val group = rows.filter { it.section == section }
                if (group.isNotEmpty()) item("section-$section") { Text(listOf("进行中与等待", "失败", "最近处理结果")[section], color = AppTheme.colors.secondary) }
                items(group, key = { it.key }) { row ->
                    ManagementGroup(row.scan?.let { "相册扫描 · ${it.albumName ?: "未知相册"}" } ?: row.ai?.let { "AI 打标 · ${it.albumName}" } ?: taskKind(row.job!!.kind)) {
                        row.scan?.let { batch ->
                            Text(aiStateLabel(batch.state), color = AppTheme.colors.text)
                            batch.stages.forEach { stage ->
                                ScanStageContent(stage)
                                if (stage.stage != "SCAN" && stage.failed > 0) SettingsNavigationRow("${taskKind(stage.kind)}失败明细",{scanFailure=batch.id to stage.kind})
                            }
                        }
                        row.job?.let { job ->
                            Text("等待 ${job.pending} · 暂停 ${job.paused} · 进行中 ${job.running}", color = AppTheme.colors.secondary)
                            Text("成功 ${job.succeeded} · 失败 ${job.failed} · 已取消 ${job.cancelled}", color = AppTheme.colors.text)
                            if (job.total == null) Text("已发现 ${job.discovered} 项", color = AppTheme.colors.secondary)
                            else TaskProgress(job.succeeded + job.failed + job.cancelled, job.total)
                        }
                        row.ai?.run?.let { run ->
                            Text("${run.mediaScope.label} · ${aiStateLabel(run.state)}", color = AppTheme.colors.text)
                            if (run.total == 0L) Text(if (run.state == "SUCCEEDED") "无待处理内容" else if (run.polling) "等待范围统计" else "没有已处理内容", color = AppTheme.colors.secondary)
                            else TaskProgress(run.processed, run.total)
                            run.error?.let { SelectionContainer { Text(it, color = AppTheme.colors.text) } }
                        }
                        row.timestamp?.let { Text(taskTimestamp(it), color = AppTheme.colors.secondary, style = MaterialTheme.typography.bodySmall) }
                        if (row.scan == null) SettingsNavigationRow("查看详情",{detail=row})
                    }
                }
            }
            if (!loading && error == null && rows.isEmpty()) item {
                ManagementGroup("暂无任务") {
                    Text("相册扫描、媒体处理和 AI 打标任务会显示在这里。", color = AppTheme.colors.secondary)
                    GlassSettingsAction("返回相册",{closing=true})
                }
            }
            if (inlinePagination) item("history-pagination") { pagination() }
        }
        if (!inlinePagination) pagination()
    }
    scanFailure?.let { (batch, kind) -> JobHistory(server, kind, onClose = { scanFailure = null }, parentJobId = batch, state = "FAILED") }
    detail?.let { row -> if (row.ai != null) AiTaskDetail(server, row.ai, onClose = { detail = null }) else JobHistory(server, row.job!!.kind, onClose = { detail = null }) }
}
@Composable private fun TaskProgress(processed: Long, total: Long) {
    val progress by animateFloatAsState(if (total > 0) (processed.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f, tween(350), label = "task-progress")
    Text("$processed / $total", color = AppTheme.colors.secondary)
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
}
@Composable private fun AiTaskDetail(server: PhotoTubeServer, task: AiTask, onClose: () -> Unit) {
    val repository = remember(server) { AlbumAiRepository(server) }
    var run by remember { mutableStateOf(task.run) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(retry) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        try { do { run = repository.detail(task.run.id); error = null; if (!run.polling) break; delay(3000) } while (true) }
        catch (cancel: CancellationException) { throw cancel } catch (problem: Exception) { error = problem.message }
    } }
    ManagementPage("任务详情", onClose) {
        Text(task.albumName, color = AppTheme.colors.text)
        ManagementGroup("AI 打标 · ${run.mediaScope.label}") {
            Text(aiStateLabel(run.state), color = AppTheme.colors.text)
            TaskProgress(run.processed, run.total)
            Text("模型请求 ${run.attempts} 次 · 缓存 ${run.cached} 项 · 最大批量 ${run.batchSize}", color = AppTheme.colors.secondary)
            run.error?.let { SelectionContainer { Text(it, color = AppTheme.colors.text) } }
            run.report.forEach { report -> Text("${report.name} · ${report.count} 项", color = AppTheme.colors.text) }
        }
        error?.let { SelectionContainer { Text(it, color = AppTheme.colors.text) } }
        GlassSettingsAction("刷新状态",{retry++})
    }
}
@Composable private fun JobHistory(server: PhotoTubeServer, kind: String, onClose: () -> Unit, parentJobId: String? = null, state: String? = null) {
    var selectedAsset by rememberConfigurationState(listOf(server,kind,parentJobId,state),enabled=!server.privateMedia) { mutableStateOf<String?>(null) }
    val repository = remember(server) { TasksRepository(server) }
    val flow = rememberConfigurationPaging(listOf(server, kind, parentJobId, state)) { Pager(PagingConfig(100, initialLoadSize = 100, maxSize = 500, enablePlaceholders = false)) { AlbumPagingSource { cursor -> repository.jobs(kind, cursor, parentJobId, state) } }.flow }
    val jobs = flow.collectAsLazyPagingItems()
    ManagementPage(taskKind(kind), onClose, scrollable = false) {
        Text(if (parentJobId != null) "仅显示本次相册扫描的失败子任务。" else "按创建时间显示该类型的任务明细。", color = AppTheme.colors.secondary)
        GlassSettingsAction("刷新",jobs::refresh,enabled=jobs.loadState.refresh !is LoadState.Loading)
        LazyColumn(Modifier.weight(1f).testTag("job-history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(jobs.itemCount, key = jobs.itemKey { it.getString("id") }) { index -> jobs[index]?.let { job ->
                ManagementGroup(aiStateLabel(job.getString("state"))) {
                    Text(taskTimestamp(job.getString("createdAt")), color = AppTheme.colors.secondary)
                    Text("尝试 ${job.getInt("attempts")} / ${job.getInt("maxAttempts")}", color = AppTheme.colors.secondary)
                    job.nullString("error")?.let { message ->
                        val identity=jobAssetIdentity(message)
                        SelectionContainer { Text(identity.reason,color=AppTheme.colors.text) }
                        identity.assetId?.let { id -> SettingsNavigationRow("查看失败文件",{selectedAsset=id}) }
                        var technical by remember(job.getString("id")) { mutableStateOf(false) }
                        GlassSettingsAction(if(technical) "收起技术详情" else "技术详情",{technical=!technical})
                        if(technical) SelectionContainer { Text("任务：${job.getString("id")}\n$message",color=AppTheme.colors.secondary) }
                    }
                }
            } }
            if (jobs.loadState.refresh is LoadState.Loading || jobs.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val error = (jobs.loadState.refresh as? LoadState.Error) ?: (jobs.loadState.append as? LoadState.Error)
            if (error != null) item { Text(error.error.message ?: "读取失败"); GlassSettingsAction("重试",jobs::retry) }
            if (jobs.loadState.refresh is LoadState.NotLoading && jobs.itemCount == 0) item { Text("没有任务记录", color = AppTheme.colors.secondary) }
        }
    }
    selectedAsset?.let { JobAssetDetails(server,it) {selectedAsset=null} }
}
internal fun taskKind(kind: String) = when (kind) {
    "SCAN_LIBRARY" -> "扫描媒体库"; "EXTRACT_METADATA" -> "读取媒体信息"; "GENERATE_THUMBNAIL" -> "生成缩略图"
    "TRANSCODE_VIDEO" -> "视频转码"; "AI_INDEX" -> "AI 索引"; "TAG_SCAN" -> "标签处理"; "DERIVATIVE_PRUNE" -> "派生文件清理"
    "ALBUM_PATH_SYNC" -> "相册目录同步"; "XMP_EXPORT" -> "XMP 导出"; else -> kind
}

@Composable
private fun ScanStageContent(stage: ScanStage) {
    Text(taskKind(stage.kind), color = AppTheme.colors.text, style = MaterialTheme.typography.titleSmall)
    Text("等待 ${stage.pending} · 进行中 ${stage.running} · 暂停 ${stage.paused}", color = AppTheme.colors.secondary)
    Text("成功 ${stage.succeeded} · 失败 ${stage.failed} · 已取消 ${stage.cancelled}", color = AppTheme.colors.secondary)
    if (stage.queuePaused) Text("此类型的全局队列已暂停", color = AppTheme.colors.secondary)
    if (stage.upstreamOpen || stage.total == null) {
        Text(if (stage.stage == "THUMBNAIL") "等待扫描或照片信息处理，数量尚未确定" else "等待扫描，数量尚未确定", color = AppTheme.colors.secondary)
    } else if (stage.stage != "SCAN") {
        if (stage.total > 0) TaskProgress(stage.succeeded + stage.failed + stage.cancelled, stage.total)
        else Text("本阶段无待处理内容", color = AppTheme.colors.secondary)
    }
    stage.error?.let { SelectionContainer { Text(it, color = AppTheme.colors.text) } }
}
