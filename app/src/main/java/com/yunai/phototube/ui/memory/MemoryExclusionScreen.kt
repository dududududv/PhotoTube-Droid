package com.yunai.phototube.ui.memory

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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.yunai.phototube.data.memory.MemoryExclusion
import com.yunai.phototube.data.memory.MemoryExclusionKind
import com.yunai.phototube.data.memory.MemoryExclusionRepository
import com.yunai.phototube.ui.components.PhotoDatePickerDialog
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MemoryExclusionRoute(
    repository: MemoryExclusionRepository,
    onBack: () -> Unit,
    onHomeChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: MemoryExclusionViewModel = viewModel(
        key = "memory-exclusions",
        factory = MemoryExclusionViewModel.factory(repository),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val exclusions = model.exclusions.collectAsLazyPagingItems()
    BackHandler(onBack = onBack)
    LaunchedEffect(model) {
        model.changes.collect {
            exclusions.refresh()
            onHomeChanged()
        }
    }
    MemoryExclusionScreen(
        state = state,
        exclusions = exclusions,
        onBack = onBack,
        onSetDateFrom = model::setDateFrom,
        onSetDateTo = model::setDateTo,
        onCreate = model::createDateRange,
        onDelete = model::delete,
        onFeedbackShown = model::consumeFeedback,
        modifier = modifier,
    )
}

@Composable
private fun MemoryExclusionScreen(
    state: MemoryExclusionUiState,
    exclusions: androidx.paging.compose.LazyPagingItems<MemoryExclusion>,
    onBack: () -> Unit,
    onSetDateFrom: (LocalDate?) -> Unit,
    onSetDateTo: (LocalDate?) -> Unit,
    onCreate: () -> Unit,
    onDelete: (MemoryExclusion) -> Unit,
    onFeedbackShown: () -> Unit,
    modifier: Modifier,
) {
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<MemoryExclusion?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
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
                Text("回忆屏蔽", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = exclusions::refresh, enabled = state.busyTarget == null) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新回忆屏蔽")
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("仅从首页回忆中排除", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "屏蔽不会隐藏、归档或删除照片；普通时间线、搜索和图集仍能看到原内容。当前只能新增日期范围，人物能力尚未开放，地点屏蔽已取消。",
                        color = PhotoTubeColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    CreateDateRangeCard(
                        state = state,
                        onPickFrom = { dateTarget = DateTarget.FROM },
                        onPickTo = { dateTarget = DateTarget.TO },
                        onCreate = onCreate,
                    )
                }
                state.actionError?.let { error ->
                    item {
                        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
                    }
                }
                item {
                    Text("已有规则", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
                when (val refresh = exclusions.loadState.refresh) {
                    is LoadState.Loading -> item { LoadingBlock() }
                    is LoadState.Error -> item {
                        FailureBlock(refresh.error.toTimelineError(), exclusions::retry)
                    }
                    is LoadState.NotLoading -> {
                        if (exclusions.itemCount == 0) {
                            item {
                                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                    Text("没有回忆屏蔽规则", color = PhotoTubeColors.Muted)
                                }
                            }
                        } else {
                            items(
                                count = exclusions.itemCount,
                                key = { exclusions[it]?.id ?: "memory-placeholder-$it" },
                            ) { index ->
                                exclusions[index]?.let { rule ->
                                    MemoryRuleCard(
                                        rule = rule,
                                        busy = state.busyTarget == rule.id,
                                        actionsEnabled = state.busyTarget == null,
                                        onDelete = { deleteTarget = rule },
                                    )
                                }
                            }
                        }
                    }
                }
                when (val append = exclusions.loadState.append) {
                    is LoadState.Loading -> item { LoadingBlock(compact = true) }
                    is LoadState.Error -> item {
                        TextButton(onClick = exclusions::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多失败，点击重试")
                        }
                    }
                    is LoadState.NotLoading -> Unit
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    dateTarget?.let { target ->
        PhotoDatePickerDialog(
            initial = if (target == DateTarget.FROM) state.dateFrom else state.dateTo,
            onDismiss = { dateTarget = null },
            onSelected = { date ->
                if (target == DateTarget.FROM) onSetDateFrom(date) else onSetDateTo(date)
                dateTarget = null
            },
        )
    }
    deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (state.busyTarget == null) deleteTarget = null },
            title = { Text("删除这条回忆屏蔽？") },
            text = {
                Text("删除后，符合条件的照片可以重新进入首页“往年今日”。照片、时间线、搜索、图集和 NAS 原文件不会发生变化。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        onDelete(rule)
                    },
                    enabled = state.busyTarget == null,
                    colors = ButtonDefaults.buttonColors(containerColor = PhotoTubeColors.Ink),
                ) { Text("删除规则") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = state.busyTarget == null) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CreateDateRangeCard(
    state: MemoryExclusionUiState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onCreate: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = PhotoTubeColors.Muted)
                Text("添加日期范围", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("开始日期", state.dateFrom, onPickFrom, Modifier.weight(1f))
                DateButton("结束日期", state.dateTo, onPickTo, Modifier.weight(1f))
            }
            state.validationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(
                onClick = onCreate,
                enabled = state.busyTarget == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PhotoTubeColors.Ink),
            ) {
                if (state.busyTarget == "create") {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("添加屏蔽")
                }
            }
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(18.dp)) {
        Text(date?.toString() ?: label, maxLines = 1)
    }
}

@Composable
private fun MemoryRuleCard(
    rule: MemoryExclusion,
    busy: Boolean,
    actionsEnabled: Boolean,
    onDelete: () -> Unit,
) {
    val title = when (rule.kind) {
        MemoryExclusionKind.DATE_RANGE -> "${rule.dateFrom} 至 ${rule.dateTo}"
        MemoryExclusionKind.PERSON -> "人物规则（当前不能新建）"
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text("仅影响首页回忆 · 创建于 ${formatCreatedAt(rule.createdAt)}", color = PhotoTubeColors.Muted, fontSize = 12.sp)
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDelete, enabled = actionsEnabled) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除回忆屏蔽")
                }
            }
        }
    }
}

@Composable
private fun LoadingBlock(compact: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(if (compact) 56.dp else 140.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(if (compact) 22.dp else 40.dp), strokeWidth = if (compact) 2.dp else 4.dp)
    }
}

@Composable
private fun FailureBlock(error: TimelineError, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private fun formatCreatedAt(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
}.getOrDefault(value)

private enum class DateTarget { FROM, TO }
