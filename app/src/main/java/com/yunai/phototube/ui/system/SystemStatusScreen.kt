package com.yunai.phototube.ui.system

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunai.phototube.data.system.LocalCacheSnapshot
import com.yunai.phototube.data.system.SystemAlert
import com.yunai.phototube.data.system.SystemAlertCode
import com.yunai.phototube.data.system.SystemAlertUnit
import com.yunai.phototube.data.system.SystemRepository
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SystemStatusRoute(
    repository: SystemRepository,
    onBack: () -> Unit,
    onOpenJobs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: SystemStatusViewModel = viewModel(
        key = "system-status",
        factory = SystemStatusViewModel.factory(repository),
    )
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LaunchedEffect(model) { model.refreshAll() }
    SystemStatusScreen(
        state = state,
        onBack = onBack,
        onRefresh = model::refreshAll,
        onRetryStatus = model::refreshStatus,
        onRetryCache = model::refreshLocalCache,
        onClearLocalCache = model::clearLocalCache,
        onOpenJobs = onOpenJobs,
        onFeedbackShown = model::consumeFeedback,
        modifier = modifier,
    )
}

@Composable
private fun SystemStatusScreen(
    state: SystemStatusUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetryStatus: () -> Unit,
    onRetryCache: () -> Unit,
    onClearLocalCache: () -> Unit,
    onOpenJobs: () -> Unit,
    onFeedbackShown: () -> Unit,
    modifier: Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbarHostState.showSnackbar(it)
            onFeedbackShown()
        }
    }
    Box(modifier.fillMaxSize().background(PhotoTubeColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回照片")
                }
                Text("系统状态与缓存", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !state.isClearingCache) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新系统状态与缓存")
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text("服务端运行状态", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "这是当前时点的只读检查，不保存告警历史，也不会把告警标成已读。",
                        color = PhotoTubeColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                when {
                    state.isLoadingStatus && state.status == null -> item { LoadingCard() }
                    state.statusError != null && state.status == null -> item {
                        FailureCard(state.statusError, onRetryStatus)
                    }
                    state.status != null -> {
                        item {
                            StatusSummaryCard(
                                alertCount = state.status.alerts.size,
                                generatedAt = state.status.generatedAt,
                                refreshing = state.isLoadingStatus,
                            )
                        }
                        items(state.status.alerts, key = { "${it.code}-${it.subject}" }) { alert ->
                            SystemAlertCard(alert, onOpenJobs)
                        }
                        state.statusError?.let { error ->
                            item {
                                FailureCard(error.copy(message = "刷新失败：${error.message}"), onRetryStatus, compact = true)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Android 本机媒体缓存", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "这里只管理本机图片内存、图片磁盘和认证 HTTP 缓存，不会退出登录，也不会删除服务器派生缓存、PhotoTube 数据或 NAS 原文件。",
                        color = PhotoTubeColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                when {
                    state.isLoadingCache && state.cache == null -> item { LoadingCard() }
                    state.cacheError != null && state.cache == null -> item {
                        FailureCard(state.cacheError, onRetryCache)
                    }
                    state.cache != null -> item {
                        LocalCacheCard(
                            snapshot = state.cache,
                            refreshing = state.isLoadingCache,
                            clearing = state.isClearingCache,
                            onClear = { confirmClear = true },
                        )
                    }
                }
                state.cacheError?.takeIf { state.cache != null }?.let { error ->
                    item {
                        FailureCard(error.copy(message = "缓存读取失败：${error.message}"), onRetryCache, compact = true)
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { if (!state.isClearingCache) confirmClear = false },
            title = { Text("清除本机媒体缓存？") },
            text = {
                Text("将清除本机图片内存、图片磁盘和认证 HTTP 缓存。照片会在下次查看时重新下载；登录、服务器地址、相册和 NAS 原文件均不受影响。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        onClearLocalCache()
                    },
                    enabled = !state.isClearingCache,
                    colors = ButtonDefaults.buttonColors(containerColor = PhotoTubeColors.Ink),
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, enabled = !state.isClearingCache) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StatusSummaryCard(alertCount: Int, generatedAt: String, refreshing: Boolean) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (alertCount == 0) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = if (alertCount == 0) Color(0xFF2E8B57) else Color(0xFFE07A1F),
                modifier = Modifier.size(30.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(if (alertCount == 0) "当前没有运维告警" else "当前有 $alertCount 条告警", fontWeight = FontWeight.SemiBold)
                Text("求值时间 ${formatTimestamp(generatedAt)}", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            }
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SystemAlertCard(alert: SystemAlert, onOpenJobs: () -> Unit) {
    val title = when (alert.code) {
        SystemAlertCode.DERIVATIVE_DISK_FREE_LOW -> "派生目录可用空间不足"
        SystemAlertCode.LIBRARY_DISK_FREE_LOW -> "媒体库可用空间不足"
        SystemAlertCode.DERIVATIVE_CACHE_HIGH -> "服务端派生缓存接近上限"
        SystemAlertCode.CONSECUTIVE_JOB_FAILURES -> "任务连续失败"
    }
    val guidance = when (alert.code) {
        SystemAlertCode.DERIVATIVE_DISK_FREE_LOW,
        SystemAlertCode.LIBRARY_DISK_FREE_LOW,
        -> "需要在 PhotoTube 服务端或 NAS 检查容量与挂载；Android 本地缓存清理不会改变此告警。"
        SystemAlertCode.DERIVATIVE_CACHE_HIGH -> "这是服务端缓存。可查看任务中心的派生缓存清理任务；实际清理由服务端调度。"
        SystemAlertCode.CONSECUTIVE_JOB_FAILURES -> "可到任务中心查看同类任务的最近状态与错误。"
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7EE)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(alert.subject, fontWeight = FontWeight.Medium)
            Text(alert.message, color = PhotoTubeColors.Muted, fontSize = 13.sp)
            Text(
                "当前 ${formatAlertValue(alert.currentValue, alert.unit)} · 阈值 ${formatAlertValue(alert.thresholdValue, alert.unit)}",
                fontSize = 13.sp,
            )
            Text(guidance, color = PhotoTubeColors.Muted, fontSize = 12.sp)
            if (alert.code in setOf(SystemAlertCode.DERIVATIVE_CACHE_HIGH, SystemAlertCode.CONSECUTIVE_JOB_FAILURES)) {
                TextButton(onClick = onOpenJobs, contentPadding = PaddingValues(0.dp)) { Text("查看任务中心") }
            }
        }
    }
}

@Composable
private fun LocalCacheCard(
    snapshot: LocalCacheSnapshot,
    refreshing: Boolean,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("已存储 ${formatBytes(snapshot.storedBytes)}", style = MaterialTheme.typography.titleMedium)
                    Text("清除后按需重新下载", color = PhotoTubeColors.Muted, fontSize = 12.sp)
                }
                if (refreshing || clearing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            CacheMetric("图片内存", snapshot.memoryBytes, snapshot.memoryMaxBytes)
            CacheMetric("图片磁盘", snapshot.imageDiskBytes, null)
            CacheMetric("认证 HTTP 磁盘", snapshot.httpDiskBytes, snapshot.httpDiskMaxBytes)
            OutlinedButton(
                onClick = onClear,
                enabled = !clearing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(if (clearing) "正在清除…" else "清除本机媒体缓存", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun CacheMetric(label: String, used: Long, max: Long?) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = PhotoTubeColors.Muted, modifier = Modifier.weight(1f))
        Text(if (max != null && max > 0) "${formatBytes(used)} / ${formatBytes(max)}" else formatBytes(used))
    }
}

@Composable
private fun LoadingCard() {
    Box(
        Modifier.fillMaxWidth().height(120.dp).background(Color.White, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun FailureCard(error: TimelineError, onRetry: () -> Unit, compact: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(22.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

internal fun formatAlertValue(value: Long, unit: SystemAlertUnit): String = when (unit) {
    SystemAlertUnit.BYTES -> formatBytes(value)
    SystemAlertUnit.COUNT -> "$value 次"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return String.format(Locale.US, if (value >= 10) "%.1f %s" else "%.2f %s", value, units[index])
}

private fun formatTimestamp(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
}.getOrDefault(value)
