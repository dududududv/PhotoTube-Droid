package com.yunai.phototube.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunai.phototube.data.folder.FolderKind
import com.yunai.phototube.data.folder.FolderNode
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.components.PhotoDatePickerDialog
import com.yunai.phototube.ui.components.DiagnosticErrorText
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFilterSheet(
    state: AdvancedFilterUiState,
    model: AdvancedFilterViewModel,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PhotoTubeColors.Background,
        contentColor = PhotoTubeColors.Ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("筛选照片", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = model::clearDraft) { Text("清除") }
                Button(
                    onClick = onApply,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PhotoTubeColors.Ink),
                ) { Text("应用") }
            }
            Text(
                "列表与时间摘要会使用完全相同的条件；更改后从第一页重新加载。",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            state.validationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    FilterSection("媒体类型") {
                        FilterRow {
                            FlatChoice(state.draft.kind == AssetKind.PHOTO, { model.setKind(AssetKind.PHOTO) }, "照片")
                            FlatChoice(state.draft.kind == AssetKind.VIDEO, { model.setKind(AssetKind.VIDEO) }, "视频")
                        }
                    }
                }
                item {
                    FilterSection("收藏") {
                        FilterRow {
                            FlatChoice(state.draft.favorite == true, { model.setFavorite(true) }, "已收藏")
                            FlatChoice(state.draft.favorite == false, { model.setFavorite(false) }, "未收藏")
                        }
                    }
                }
                item {
                    FilterSection("精确星级") {
                        FilterRow {
                            (1..5).forEach { rating ->
                                FlatChoice(state.draft.rating == rating, { model.setRating(rating) }, "$rating 星")
                            }
                        }
                    }
                }
                item {
                    FilterSection("拍摄日期（含首尾两天）") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateChoice(
                                label = "开始",
                                date = state.draft.takenFrom,
                                onClick = { dateTarget = DateTarget.FROM },
                                modifier = Modifier.weight(1f),
                            )
                            DateChoice(
                                label = "结束",
                                date = state.draft.takenTo,
                                onClick = { dateTarget = DateTarget.TO },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.draft.takenFrom != null || state.draft.takenTo != null) {
                            TextButton(onClick = model::clearDates) { Text("清除日期") }
                        }
                    }
                }
                item {
                    FolderBrowser(state = state, model = model)
                }
                item {
                    TagFilterHeader(state = state, model = model)
                }
                items(state.tags, key = Tag::id) { tag ->
                    TagFilterRow(tag, state.draft.tagModes[tag.id], model::setTagMode)
                }
                if (state.isLoadingTags) {
                    item { CenteredProgress() }
                } else if (state.tagNextCursor != null) {
                    item {
                        OutlinedButton(onClick = model::loadMoreTags, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多标签")
                        }
                    }
                }
                state.tagError?.let { error ->
                    item { InlineError(error, model::searchTags) }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    dateTarget?.let { target ->
        PhotoDatePickerDialog(
            initial = if (target == DateTarget.FROM) state.draft.takenFrom else state.draft.takenTo,
            onDismiss = { dateTarget = null },
            onSelected = { date ->
                if (target == DateTarget.FROM) model.setTakenFrom(date) else model.setTakenTo(date)
                dateTarget = null
            },
        )
    }
}

@Composable
private fun FolderBrowser(state: AdvancedFilterUiState, model: AdvancedFilterViewModel) {
    FilterSection("目录及全部后代") {
        state.draft.folder?.let { selected ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = PhotoTubeColors.Muted)
                Text(
                    selected.path,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = model::clearFolder, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "清除目录筛选")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.folderLibraryId != null) {
                IconButton(onClick = model::navigateFolderUp) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回上一级")
                }
            }
            Text(
                if (state.folderLibraryId == null) "选择媒体库" else state.folderPath.ifEmpty { "媒体库根目录" },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.folderLibraryId != null && state.folderPath.isNotEmpty()) {
                TextButton(onClick = model::selectCurrentFolder) { Text("使用当前目录") }
            }
        }
        state.folderItems.forEach { node -> FolderNodeRow(node, model) }
        when {
            state.isLoadingFolders -> CenteredProgress()
            state.folderError != null -> InlineError(state.folderError, model::retryFolders)
            state.folderNextCursor != null -> TextButton(onClick = model::loadMoreFolders) { Text("加载更多目录") }
            state.folderItems.isEmpty() -> Text("这一层没有子目录", color = PhotoTubeColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FolderNodeRow(node: FolderNode, model: AdvancedFilterViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = node.online) { model.openFolder(node) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, tint = if (node.online) PhotoTubeColors.Ink else PhotoTubeColors.Muted)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(node.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (node.online) "本层 ${node.directAssetCount} 项" else "媒体库离线",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
            )
        }
        if (node.kind == FolderKind.DIRECTORY && node.online) {
            TextButton(onClick = { model.selectFolder(node) }) { Text("选择") }
        }
        if (node.hasChildren || node.kind == FolderKind.LIBRARY) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "进入")
        }
    }
}

@Composable
private fun TagFilterHeader(state: AdvancedFilterUiState, model: AdvancedFilterViewModel) {
    FilterSection("标签条件") {
        Text(
            "每个标签只能处于一种条件：全部包含（AND）、任一包含（OR）或排除（NOT）。",
            color = PhotoTubeColors.Muted,
            fontSize = 12.sp,
        )
        state.draft.tagModes.toSortedMap().forEach { (tagId, mode) ->
            val modeLabel = when (mode) {
                TagFilterMode.ALL -> "全部包含"
                TagFilterMode.ANY -> "任一包含"
                TagFilterMode.EXCLUDE -> "排除"
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.knownTags[tagId]?.name ?: "标签 $tagId"} · $modeLabel",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { model.setTagMode(tagId, null) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "移除标签条件")
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = state.tagQuery,
                onValueChange = model::updateTagQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text("搜索标签") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            IconButton(onClick = model::searchTags, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = "搜索标签")
            }
        }
    }
}

@Composable
private fun TagFilterRow(tag: Tag, selected: TagFilterMode?, onMode: (Long, TagFilterMode?) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tag.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${tag.assetCount} 项", color = PhotoTubeColors.Muted, fontSize = 12.sp)
        }
        FilterRow {
            FlatChoice(selected == TagFilterMode.ALL, { onMode(tag.id, TagFilterMode.ALL) }, "全部包含")
            FlatChoice(selected == TagFilterMode.ANY, { onMode(tag.id, TagFilterMode.ANY) }, "任一包含")
            FlatChoice(selected == TagFilterMode.EXCLUDE, { onMode(tag.id, TagFilterMode.EXCLUDE) }, "排除")
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
        content()
    }
}

@Composable
private fun FilterRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun FlatChoice(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(18.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            selectedContainerColor = PhotoTubeColors.Ink,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
private fun DateChoice(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(18.dp)) {
        Text(if (date == null) label else "$label  $date", maxLines = 1)
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun InlineError(error: TimelineError, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DiagnosticErrorText(error.message, error.logId, Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private enum class DateTarget { FROM, TO }
