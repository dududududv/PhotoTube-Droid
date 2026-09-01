package com.yunai.phototube.ui.xmp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.xmp.XmpExportRepository
import com.yunai.phototube.data.xmp.XmpExportRun
import com.yunai.phototube.data.xmp.XmpExportRunState
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun XmpExportRoute(
    repository: XmpExportRepository,
    sessionRepository: SessionRepository,
    onPrivateScopeChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: XmpExportViewModel = viewModel(
        key = "xmp-export",
        factory = XmpExportViewModel.factory(repository, sessionRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        viewModel.refreshEnvironment()
    }
    LaunchedEffect(state.privateScopeVisible) {
        onPrivateScopeChanged(state.privateScopeVisible)
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onRouteLeft()
            onPrivateScopeChanged(false)
        }
    }

    when (state.capability) {
        XmpCapability.Loading -> XmpStatusScreen(
            title = "正在检查照片信息备份…",
            onBack = onBack,
            modifier = modifier,
            showProgress = true,
        )
        XmpCapability.Unavailable -> XmpStatusScreen(
            title = "服务器未配置 XMP 导出目录",
            body = "这是合法部署形态，照片浏览和其它功能不受影响。请在 PhotoTube 服务端配置独立 export volume 后重试。",
            onBack = onBack,
            onRetry = viewModel::refreshEnvironment,
            modifier = modifier,
        )
        XmpCapability.Error -> XmpStatusScreen(
            title = "无法读取 XMP 能力状态",
            error = state.error,
            onBack = onBack,
            onRetry = viewModel::refreshEnvironment,
            modifier = modifier,
        )
        XmpCapability.Available -> {
            val runs = viewModel.runs.collectAsLazyPagingItems()
            LaunchedEffect(state.refreshRevision) {
                if (state.refreshRevision > 0) runs.refresh()
            }
            val hasActiveRuns = runs.itemSnapshotList.items.any { !it.state.isTerminal }
            val historyPollingAllowed = shouldAutomaticallyPollXmp(
                hasActiveWork = hasActiveRuns,
                hasFailure = runs.loadState.refresh is LoadState.Error,
            )
            LaunchedEffect(historyPollingAllowed) {
                while (historyPollingAllowed) {
                    delay(RUN_POLL_INTERVAL_MS)
                    if (runs.loadState.refresh !is LoadState.Loading) runs.refresh()
                }
            }
            val selectedIsActive = state.selectedRun?.state?.isTerminal == false
            val selectedPollingAllowed = shouldAutomaticallyPollXmp(
                hasActiveWork = selectedIsActive,
                hasFailure = state.runError != null,
            )
            LaunchedEffect(state.selectedRun?.id, selectedPollingAllowed) {
                while (selectedPollingAllowed) {
                    delay(RUN_POLL_INTERVAL_MS)
                    viewModel.refreshSelectedRun()
                }
            }
            XmpExportScreen(
                state = state,
                runs = runs,
                onBack = onBack,
                onIncludePrivateChanged = viewModel::setIncludePrivate,
                onPreview = viewModel::previewAll,
                onCancelPreview = viewModel::cancelPreview,
                onCreate = viewModel::createExport,
                onOpenRun = viewModel::openRun,
                onCloseRun = viewModel::closeRun,
                onRetryRun = viewModel::refreshSelectedRun,
                onPreviewAgain = viewModel::previewAgain,
                onPasswordChanged = viewModel::updatePrivatePassword,
                onUnlockPrivate = viewModel::unlockPrivateAccess,
                onDismissPrivateUnlock = viewModel::dismissPrivateUnlock,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun XmpStatusScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    error: TimelineError? = null,
    onRetry: (() -> Unit)? = null,
    showProgress: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SimpleXmpTopBar(onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showProgress) CircularProgressIndicator()
            Text(
                title,
                modifier = Modifier.padding(top = if (showProgress) 18.dp else 0.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            body?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 10.dp),
                    color = PhotoTubeColors.Muted,
                )
            }
            error?.let {
                DiagnosticErrorText(
                    message = it.message,
                    logId = it.logId,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            onRetry?.let {
                FilledTonalButton(onClick = it, modifier = Modifier.padding(top = 18.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("重新检查", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun XmpExportScreen(
    state: XmpExportUiState,
    runs: LazyPagingItems<XmpExportRun>,
    onBack: () -> Unit,
    onIncludePrivateChanged: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onCancelPreview: () -> Unit,
    onCreate: () -> Unit,
    onOpenRun: (String) -> Unit,
    onCloseRun: () -> Unit,
    onRetryRun: () -> Unit,
    onPreviewAgain: (XmpExportRun) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onUnlockPrivate: () -> Unit,
    onDismissPrivateUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding(),
    ) {
        SimpleXmpTopBar(onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("照片信息备份", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "把标签、评分、拍摄时间和位置导出为不可变 XMP 快照。",
                    modifier = Modifier.padding(top = 4.dp),
                    color = PhotoTubeColors.Muted,
                )
            }
            item {
                XmpBoundaryCard()
            }
            item {
                CreateExportCard(
                    state = state,
                    onIncludePrivateChanged = onIncludePrivateChanged,
                    onPreview = onPreview,
                )
            }
            state.preview?.let { preview ->
                item {
                    PreviewCard(
                        preview = preview,
                        isCreating = state.isCreating,
                        onCreate = onCreate,
                        onCancel = onCancelPreview,
                    )
                }
            }
            state.message?.let { message ->
                item { Text(message, color = PhotoTubeColors.Accent, fontSize = 13.sp) }
            }
            state.error?.let { error ->
                item { DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth()) }
            }
            item {
                Text("备份记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            when (val refresh = runs.loadState.refresh) {
                LoadState.Loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在读取备份记录…", modifier = Modifier.padding(start = 10.dp), color = PhotoTubeColors.Muted)
                    }
                }
                is LoadState.Error -> item {
                    val error = refresh.error.toTimelineError()
                    Column(Modifier.fillMaxWidth()) {
                        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
                        TextButton(onClick = runs::retry) { Text("重试") }
                    }
                }
                is LoadState.NotLoading -> if (runs.itemCount == 0) {
                    item { Text("还没有照片信息备份。", color = PhotoTubeColors.Muted) }
                }
            }
            items(
                count = runs.itemCount,
                key = { index -> runs.peek(index)?.id ?: "xmp-placeholder-$index" },
            ) { index ->
                runs[index]?.let { run ->
                    XmpRunRow(
                        run = run,
                        onClick = { onOpenRun(run.id) },
                    )
                }
            }
            when (val append = runs.loadState.append) {
                LoadState.Loading -> item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                is LoadState.Error -> item {
                    val error = append.error.toTimelineError()
                    Column(Modifier.fillMaxWidth()) {
                        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
                        TextButton(onClick = runs::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("重试加载更早记录")
                        }
                    }
                }
                is LoadState.NotLoading -> Unit
            }
        }
    }
    state.selectedRun?.let { run ->
        XmpRunDetailSheet(
            run = run,
            isRefreshing = state.isLoadingRun,
            error = state.runError,
            privateUnlocked = state.privateUnlocked,
            onDismiss = onCloseRun,
            onRetry = onRetryRun,
            onPreviewAgain = {
                onCloseRun()
                onPreviewAgain(run)
            },
        )
    }
    if (state.showPrivateUnlock) {
        PrivateXmpUnlockDialog(
            state = state,
            onPasswordChanged = onPasswordChanged,
            onConfirm = onUnlockPrivate,
            onDismiss = onDismissPrivateUnlock,
        )
    }
}

@Composable
private fun SimpleXmpTopBar(onBack: () -> Unit) {
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
        Text("照片信息备份", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun XmpBoundaryCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("XMP 是迁移快照，不是完整数据库备份", fontWeight = FontWeight.SemiBold)
            Text("不会复制、移动或修改 NAS 原照片，也不会写到原文件旁边。", color = PhotoTubeColors.Muted, fontSize = 13.sp)
            Text("相册关系、收藏、私密状态、编辑配方和审计记录仍需数据库备份。", color = PhotoTubeColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CreateExportCard(
    state: XmpExportUiState,
    onIncludePrivateChanged: (Boolean) -> Unit,
    onPreview: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text("创建新快照", fontWeight = FontWeight.SemiBold)
                    Text("默认检查当前账号的全部已注册媒体库", color = PhotoTubeColors.Muted, fontSize = 12.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = PhotoTubeColors.Muted)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                ) {
                    Text("包含私密照片")
                    Text(
                        if (state.privateUnlocked) "当前会话已解锁" else "开启时需要当前账号口令",
                        color = PhotoTubeColors.Muted,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = state.includePrivate,
                    onCheckedChange = onIncludePrivateChanged,
                    enabled = !state.isPreviewing && !state.isCreating,
                )
            }
            Button(
                onClick = onPreview,
                enabled = !state.isPreviewing && !state.isCreating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isPreviewing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isPreviewing) "正在检查…" else "检查可备份内容")
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: com.yunai.phototube.data.xmp.XmpExportPreview,
    isCreating: Boolean,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "找到 ${formatCount(preview.estimatedAssetCount)} 张可备份照片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "媒体库 ${preview.resolvedLibraryIds.size} 个 · 检查时间 ${formatTime(preview.estimatedAt)}",
                fontSize = 12.sp,
            )
            Text("实际任务会在开始时重新确认数量并冻结一致快照。", fontSize = 12.sp)
            if (preview.resolvedLibraryIds.isEmpty()) {
                Text("当前没有已注册媒体库，无法创建快照。", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreate,
                    enabled = !isCreating && preview.resolvedLibraryIds.isNotEmpty(),
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isCreating) "正在开始…" else "开始备份")
                }
                TextButton(onClick = onCancel, enabled = !isCreating) { Text("取消") }
            }
        }
    }
}

@Composable
private fun XmpRunRow(run: XmpExportRun, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(run.id.take(8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                RunStateLabel(run.state)
            }
            Text("创建于 ${formatTime(run.createdAt)}", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            if (!run.state.isTerminal) {
                Text("正在后台生成不可变快照", color = PhotoTubeColors.Muted, fontSize = 12.sp)
                if (run.matchedCount > 0) {
                    LinearProgressIndicator(
                        progress = { (run.completedCount.toFloat() / run.matchedCount).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    "共 ${formatCount(run.matchedCount)} · 成功 ${formatCount(run.succeededCount)} · 失败 ${formatCount(run.failedCount)}",
                    color = PhotoTubeColors.Muted,
                    fontSize = 12.sp,
                )
            }
            if (run.includePrivate) Text("包含私密范围", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
            run.logicalPath?.let { Text(it, color = PhotoTubeColors.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun RunStateLabel(state: XmpExportRunState) {
    val label = when (state) {
        XmpExportRunState.PENDING -> "等待中"
        XmpExportRunState.RUNNING -> "运行中"
        XmpExportRunState.SUCCEEDED -> "已完成"
        XmpExportRunState.PARTIAL -> "部分完成"
        XmpExportRunState.FAILED -> "失败"
        XmpExportRunState.CANCELLED -> "已终止"
    }
    Text(
        label,
        color = when (state) {
            XmpExportRunState.SUCCEEDED -> MaterialTheme.colorScheme.primary
            XmpExportRunState.FAILED, XmpExportRunState.CANCELLED -> MaterialTheme.colorScheme.error
            else -> PhotoTubeColors.Muted
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XmpRunDetailSheet(
    run: XmpExportRun,
    isRefreshing: Boolean,
    error: TimelineError?,
    privateUnlocked: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPreviewAgain: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("备份详情", style = MaterialTheme.typography.headlineSmall)
                Text(run.id, color = PhotoTubeColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            item { DetailRow("状态") { RunStateLabel(run.state) } }
            item { DetailRow("媒体库") { Text("${run.libraryIds.size} 个") } }
            item { DetailRow("私密范围") { Text(if (run.includePrivate) "包含" else "不包含") } }
            item { DetailRow("匹配 / 成功 / 失败") { Text("${run.matchedCount} / ${run.succeededCount} / ${run.failedCount}") } }
            item { DetailRow("尝试次数") { Text(run.attemptCount.toString()) } }
            item { DetailRow("创建时间") { Text(formatTime(run.createdAt)) } }
            run.startedAt?.let { item { DetailRow("开始时间") { Text(formatTime(it)) } } }
            run.finishedAt?.let { item { DetailRow("完成时间") { Text(formatTime(it)) } } }
            run.snapshotId?.let { item { DetailRow("快照 ID") { MonospaceValue(it) } } }
            run.logicalPath?.let { item { DetailRow("逻辑目录") { MonospaceValue(it) } } }
            run.factsSha256?.let { item { DetailRow("事实摘要") { MonospaceValue(it) } } }
            run.manifestSha256?.let { item { DetailRow("清单摘要") { MonospaceValue(it) } } }
            run.errorCode?.let { item { DetailRow("错误码") { MonospaceValue(it) } } }
            if (isRefreshing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        DiagnosticErrorText(it.message, it.logId, Modifier.fillMaxWidth())
                        TextButton(onClick = onRetry, enabled = !isRefreshing) {
                            Text(if (it.code == "RATE_LIMITED") "稍后手动重试" else "重试刷新任务状态")
                        }
                    }
                }
            }
            if (run.state.isTerminal) {
                item {
                    FilledTonalButton(
                        onClick = onPreviewAgain,
                        enabled = !run.includePrivate || privateUnlocked,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (run.includePrivate && !privateUnlocked) "需先解锁私密空间" else "按同一范围再次估算")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = PhotoTubeColors.Muted, fontSize = 12.sp)
        value()
        HorizontalDivider()
    }
}

@Composable
private fun MonospaceValue(value: String) {
    Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
}

@Composable
private fun PrivateXmpUnlockDialog(
    state: XmpExportUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解锁私密照片范围") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("请输入当前登录账号口令。估算和创建都以服务端短时授权为准。")
                OutlinedTextField(
                    value = state.privatePassword,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("账号口令") },
                )
                state.message?.let { Text(it, color = PhotoTubeColors.Muted, fontSize = 12.sp) }
                state.error?.let { DiagnosticErrorText(it.message, it.logId, Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = state.privatePassword.isNotEmpty() && !state.isUnlockingPrivate,
            ) {
                if (state.isUnlockingPrivate) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (state.isUnlockingPrivate) "正在解锁…" else "解锁")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun formatTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(value)

private const val RUN_POLL_INTERVAL_MS = 5_000L

internal fun shouldAutomaticallyPollXmp(
    hasActiveWork: Boolean,
    hasFailure: Boolean,
): Boolean = hasActiveWork && !hasFailure
