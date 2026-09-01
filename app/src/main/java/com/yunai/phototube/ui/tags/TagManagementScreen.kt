package com.yunai.phototube.ui.tags

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.data.takeUnicodeCodePoints
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TagManagementRoute(
    repository: TagRepository,
    refreshRevision: Int,
    onBack: () -> Unit,
    onChanged: (TagLibraryChange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: TagManagementViewModel = viewModel(
        key = "tag-management",
        factory = TagManagementViewModel.factory(repository),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val tags = model.tags.collectAsLazyPagingItems()
    BackHandler(onBack = onBack)
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) tags.refresh()
    }
    LaunchedEffect(model) {
        model.changes.collect { change ->
            onChanged(change)
        }
    }
    TagManagementScreen(
        state = state,
        tags = tags,
        onBack = onBack,
        onQueryChanged = model::updateQuery,
        onSearch = model::submitQuery,
        onClearQuery = model::clearQuery,
        onCreate = model::create,
        onRename = model::rename,
        onDelete = model::delete,
        onFeedbackShown = model::consumeFeedback,
        modifier = modifier,
    )
}

@Composable
private fun TagManagementScreen(
    state: TagManagementUiState,
    tags: LazyPagingItems<Tag>,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Tag, String) -> Unit,
    onDelete: (Tag) -> Unit,
    onFeedbackShown: () -> Unit,
    modifier: Modifier,
) {
    var editorTarget by remember { mutableStateOf<TagEditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<Tag?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
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
                Text("标签管理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { editorTarget = TagEditorTarget(null) },
                    enabled = state.busyTarget == null,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "新建标签")
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("人工标签库", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "重命名会保留标签 ID 和资产关系。删除会移除全部资产上的该标签，但不会修改照片或 NAS 原文件。",
                        color = PhotoTubeColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索标签") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            onSearch()
                        }),
                        trailingIcon = {
                            Row {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = onClearQuery) {
                                        Icon(Icons.Rounded.Close, contentDescription = "清除标签搜索")
                                    }
                                }
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    onSearch()
                                }) {
                                    Icon(Icons.Rounded.Search, contentDescription = "搜索标签")
                                }
                            }
                        },
                    )
                }
                state.actionError?.let { error ->
                    item { DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth()) }
                }
                when (val refresh = tags.loadState.refresh) {
                    is LoadState.Loading -> item { LoadingBlock() }
                    is LoadState.Error -> item {
                        FailureBlock(refresh.error.toTimelineError(), tags::retry)
                    }
                    is LoadState.NotLoading -> {
                        if (tags.itemCount == 0) {
                            item {
                                EmptyTags(
                                    hasQuery = state.submittedQuery.isNotEmpty(),
                                    onCreate = { editorTarget = TagEditorTarget(null) },
                                )
                            }
                        } else {
                            items(count = tags.itemCount, key = { tags[it]?.id ?: "tag-placeholder-$it" }) { index ->
                                tags[index]?.let { tag ->
                                    TagCard(
                                        tag = tag,
                                        busy = state.busyTarget == TagManagementViewModel.tagTarget(tag.id),
                                        actionsEnabled = state.busyTarget == null,
                                        onRename = { editorTarget = TagEditorTarget(tag) },
                                        onDelete = { deleteTarget = tag },
                                    )
                                }
                            }
                        }
                    }
                }
                when (val append = tags.loadState.append) {
                    is LoadState.Loading -> item { LoadingBlock(compact = true) }
                    is LoadState.Error -> item {
                        TextButton(onClick = tags::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多失败，点击重试")
                        }
                    }
                    is LoadState.NotLoading -> Unit
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    editorTarget?.let { target ->
        TagEditorDialog(
            tag = target.tag,
            isBusy = state.busyTarget != null,
            onDismiss = { editorTarget = null },
            onConfirm = { name ->
                editorTarget = null
                target.tag?.let { onRename(it, name) } ?: onCreate(name)
            },
        )
    }
    deleteTarget?.let { tag ->
        AlertDialog(
            onDismissRequest = { if (state.busyTarget == null) deleteTarget = null },
            title = { Text("删除标签“${tag.name}”？") },
            text = {
                Text("将从 ${tag.assetCount} 项资产移除这个标签，并影响标签筛选和智能图集结果。照片、其它标签和 NAS 原文件不会被删除。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        onDelete(tag)
                    },
                    enabled = state.busyTarget == null,
                ) { Text("删除标签") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = state.busyTarget == null) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TagCard(
    tag: Tag,
    busy: Boolean,
    actionsEnabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tag.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${tag.assetCount} 项资产 · 更新于 ${formatTagDate(tag.updatedAt)}",
                    color = PhotoTubeColors.Muted,
                    fontSize = 12.sp,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Box {
                    IconButton(onClick = { expanded = true }, enabled = actionsEnabled) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "${tag.name}操作")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                expanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                expanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagEditorDialog(
    tag: Tag?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    val normalized = name.trim()
    val valid = normalized.isNotEmpty() && normalized.codePointCount(0, normalized.length) <= 120
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag == null) "新建标签" else "重命名标签") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.takeUnicodeCodePoints(MAX_TAG_NAME_CODE_POINTS) },
                singleLine = true,
                label = { Text("标签名称") },
                supportingText = { Text("${name.codePointCount(0, name.length)} / $MAX_TAG_NAME_CODE_POINTS") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalized) },
                enabled = valid && !isBusy && normalized != tag?.name,
            ) { Text(if (tag == null) "创建" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EmptyTags(hasQuery: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (hasQuery) "没有匹配的标签" else "还没有标签", style = MaterialTheme.typography.titleMedium)
        if (!hasQuery) TextButton(onClick = onCreate) { Text("新建第一个标签") }
    }
}

@Composable
private fun LoadingBlock(compact: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(if (compact) 56.dp else 140.dp),
        contentAlignment = Alignment.Center,
    ) {
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

private fun formatTagDate(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
}.getOrDefault(value)

private data class TagEditorTarget(val tag: Tag?)
