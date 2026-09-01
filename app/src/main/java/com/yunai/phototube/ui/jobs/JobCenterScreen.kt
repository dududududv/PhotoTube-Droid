package com.yunai.phototube.ui.jobs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.yunai.phototube.data.job.Job
import com.yunai.phototube.data.job.JobKind
import com.yunai.phototube.data.job.JobRepository
import com.yunai.phototube.data.job.JobState
import com.yunai.phototube.data.job.JobSummary
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors

@Composable
fun JobCenterRoute(
    repository: JobRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: JobCenterViewModel = viewModel(
        key = "job-center",
        factory = JobCenterViewModel.factory(repository),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val jobs = model.jobs.collectAsLazyPagingItems()
    DisposableEffect(model) {
        model.onRouteEntered()
        onDispose(model::onRouteLeft)
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(state.refreshRevision) {
        if (state.refreshRevision > 0) jobs.refresh()
    }
    JobCenterScreen(
        state = state,
        jobs = jobs,
        onBack = onBack,
        onSelectState = model::selectState,
        onSelectKind = model::selectKind,
        onSetQueuePaused = model::setQueuePaused,
        onCancel = model::cancel,
        onRetrySummary = model::refreshSummary,
        modifier = modifier,
    )
}

@Composable
private fun JobCenterScreen(
    state: JobCenterUiState,
    jobs: LazyPagingItems<Job>,
    onBack: () -> Unit,
    onSelectState: (JobState?) -> Unit,
    onSelectKind: (JobKind?) -> Unit,
    onSetQueuePaused: (JobSummary, Boolean) -> Unit,
    onCancel: (Job) -> Unit,
    onRetrySummary: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text("任务中心", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummarySection(state, onSelectKind, onSetQueuePaused, onRetrySummary)
            }
            item {
                StateFilters(state.selectedState, onSelectState)
            }
            state.actionError?.let { error ->
                item { DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth()) }
            }
            when (val refresh = jobs.loadState.refresh) {
                is LoadState.Loading -> item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LoadState.Error -> item {
                    val error = refresh.error.toTimelineError()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
                        Button(onClick = jobs::retry, modifier = Modifier.padding(top = 10.dp)) { Text("重试") }
                    }
                }
                is LoadState.NotLoading -> {
                    if (jobs.itemCount == 0) {
                        item {
                            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                                Text("当前筛选没有任务", color = PhotoTubeColors.Muted)
                            }
                        }
                    } else {
                        items(count = jobs.itemCount, key = { jobs[it]?.id ?: "placeholder-$it" }) { index ->
                            jobs[index]?.let { job ->
                                JobCard(
                                    job = job,
                                    isBusy = state.busyTarget == "job-${job.id}",
                                    actionsEnabled = state.busyTarget == null,
                                    onCancel = { onCancel(job) },
                                )
                            }
                        }
                    }
                }
            }
            when (val append = jobs.loadState.append) {
                is LoadState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                is LoadState.Error -> item {
                    val error = append.error.toTimelineError()
                    Column(Modifier.fillMaxWidth()) {
                        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
                        TextButton(onClick = jobs::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("重试加载更多")
                        }
                    }
                }
                is LoadState.NotLoading -> Unit
            }
        }
    }
}

@Composable
private fun SummarySection(
    state: JobCenterUiState,
    onSelectKind: (JobKind?) -> Unit,
    onSetQueuePaused: (JobSummary, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("当前队列", style = MaterialTheme.typography.headlineSmall)
        Text(
            "暂停只影响尚未领取的任务；运行中的任务会完成。取消运行任务后，处理器会在下一次 heartbeat 协作退出。",
            color = PhotoTubeColors.Muted,
            fontSize = 12.sp,
        )
        when {
            state.isLoadingSummary -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            state.summaryError != null -> {
                DiagnosticErrorText(
                    state.summaryError.message,
                    state.summaryError.logId,
                    Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onRetry) { Text("重试摘要") }
            }
            state.summaries.isEmpty() -> Text("暂无活跃任务队列", color = PhotoTubeColors.Muted)
            else -> Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.summaries.forEach { summary ->
                    SummaryCard(
                        summary = summary,
                        selected = state.selectedKind == summary.kind,
                        busy = state.busyTarget == "queue-${summary.kind.name}",
                        actionsEnabled = state.busyTarget == null,
                        onClick = { onSelectKind(summary.kind) },
                        onSetPaused = { onSetQueuePaused(summary, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: JobSummary,
    selected: Boolean,
    busy: Boolean,
    actionsEnabled: Boolean,
    onClick: () -> Unit,
    onSetPaused: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE6EFFC) else Color.White,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(summary.kind.label(), style = MaterialTheme.typography.titleMedium)
            val progressLabel = summary.total?.let { total ->
                "${summary.finished.coerceAtMost(total)} / $total"
            } ?: "已发现 ${summary.discovered} 个"
            Text(progressLabel, color = PhotoTubeColors.Muted, fontSize = 13.sp)
            summary.total?.takeIf { it > 0 }?.let { total ->
                LinearProgressIndicator(
                    progress = { (summary.finished.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "等待 ${summary.pending} · 运行 ${summary.running} · 失败 ${summary.failed}",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
            )
            if (!summary.kind.canControl) {
                Text("AI 契约冻结，仅查看", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            } else {
                TextButton(
                    onClick = { onSetPaused(!summary.queuePaused) },
                    enabled = actionsEnabled,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(if (summary.queuePaused) "继续队列" else "暂停队列")
                }
            }
        }
    }
}

@Composable
private fun StateFilters(selected: JobState?, onSelect: (JobState?) -> Unit) {
    val filters = listOf(
        null to "全部",
        JobState.RUNNING to "运行中",
        JobState.PENDING to "等待中",
        JobState.PAUSED to "已暂停",
        JobState.FAILED to "失败",
        JobState.CANCELLED to "已取消",
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (state, label) ->
            FilterChip(
                selected = selected == state,
                onClick = { onSelect(state) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun JobCard(
    job: Job,
    isBusy: Boolean,
    actionsEnabled: Boolean,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(job.kind.label(), style = MaterialTheme.typography.titleMedium)
                    Text(job.state.label(), color = job.state.color(), fontSize = 13.sp)
                }
                if (job.canCancel) {
                    TextButton(onClick = onCancel, enabled = actionsEnabled) {
                        if (isBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("取消任务")
                    }
                }
            }
            Text("尝试 ${job.attempts} / ${job.maxAttempts} · 优先级 L${job.priority}", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            Text("创建于 ${job.createdAt}", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            job.error?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            if (!job.kind.canControl) {
                Text("AI 相关任务只读展示", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            }
        }
    }
}

private fun JobKind.label(): String = when (this) {
    JobKind.SCAN_LIBRARY -> "媒体库扫描"
    JobKind.EXTRACT_METADATA -> "提取元数据"
    JobKind.GENERATE_THUMBNAIL -> "生成缩略图"
    JobKind.TRANSCODE_VIDEO -> "视频转码"
    JobKind.AI_INDEX -> "AI 索引"
    JobKind.TAG_SCAN -> "AI 标签扫描"
    JobKind.DERIVATIVE_PRUNE -> "清理派生缓存"
    JobKind.ALBUM_PATH_SYNC -> "路径相册同步"
    JobKind.XMP_EXPORT -> "XMP 导出"
}

private fun JobState.label(): String = when (this) {
    JobState.PENDING -> "等待中"
    JobState.PAUSED -> "已暂停"
    JobState.RUNNING -> "运行中"
    JobState.SUCCEEDED -> "已完成"
    JobState.FAILED -> "失败"
    JobState.CANCELLED -> "已取消"
}

private fun JobState.color(): Color = when (this) {
    JobState.SUCCEEDED -> Color(0xFF2E7D32)
    JobState.FAILED -> Color(0xFFC62828)
    JobState.RUNNING -> Color(0xFF176CD8)
    else -> PhotoTubeColors.Muted
}
