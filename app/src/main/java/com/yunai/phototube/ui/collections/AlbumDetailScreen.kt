package com.yunai.phototube.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.album.AlbumKind
import com.yunai.phototube.data.album.AlbumPath
import com.yunai.phototube.data.album.AlbumPathInput
import com.yunai.phototube.data.album.AlbumPathSyncRunState
import com.yunai.phototube.data.album.AlbumPathSyncRun
import com.yunai.phototube.data.album.AlbumPathSyncTrigger
import com.yunai.phototube.data.album.AlbumRepository
import com.yunai.phototube.data.album.AlbumSortMode
import com.yunai.phototube.data.album.SourceFolder
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AlbumDetailRoute(
    albumId: String,
    repository: AlbumRepository,
    timelineRepository: TimelineRepository,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onChanged: () -> Unit,
    onOpenAsset: (String) -> Unit,
    refreshRevision: Int,
    modifier: Modifier = Modifier,
) {
    val detailViewModel: AlbumDetailViewModel = viewModel(
        key = "album-detail",
        factory = AlbumDetailViewModel.factory(repository, timelineRepository),
    )
    val state by detailViewModel.uiState.collectAsStateWithLifecycle()
    val assets = detailViewModel.assets.collectAsLazyPagingItems()
    val syncRuns = detailViewModel.syncRuns.collectAsLazyPagingItems()
    val candidateAssets = detailViewModel.candidateAssets.collectAsLazyPagingItems()
    var showAddAssets by remember { mutableStateOf(false) }
    var showAddPath by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(albumId) { detailViewModel.open(albumId) }
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) {
            detailViewModel.refreshAlbum()
            assets.refresh()
            syncRuns.refresh()
        }
    }
    LaunchedEffect(state.assetRefreshRevision) {
        if (state.assetRefreshRevision > 0) assets.refresh()
    }
    LaunchedEffect(state.syncHistoryRevision) {
        if (state.syncHistoryRevision > 0) syncRuns.refresh()
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    LaunchedEffect(detailViewModel) {
        detailViewModel.changes.collect { onChanged() }
    }
    BackHandler(onBack = onBack)

    AlbumDetailScreen(
        state = state,
        assets = assets,
        syncRuns = syncRuns,
        serverRoot = detailViewModel.serverRoot,
        onBack = onBack,
        onOpenAsset = onOpenAsset,
        onAddAssets = { showAddAssets = true },
        onRemoveAsset = detailViewModel::removeAsset,
        onAddPath = {
            showAddPath = true
            detailViewModel.browseFolder("")
        },
        onRemovePath = detailViewModel::previewRemovePath,
        onManualSync = detailViewModel::triggerManualSync,
        onOpenSyncRun = detailViewModel::openSyncRun,
        onSettings = { showSettings = true },
        onSetCover = detailViewModel::setCover,
        onClearCover = detailViewModel::clearCover,
        onRetry = detailViewModel::refreshAlbum,
        modifier = modifier,
    )

    if (showSettings) {
        state.album?.let { album ->
            EditAlbumSheet(
                album = album,
                isBusy = state.isBusy,
                onDismiss = { if (!state.isBusy) showSettings = false },
                onSave = { name, sortMode ->
                    detailViewModel.updateSettings(name, sortMode)
                    showSettings = false
                },
                onClearCover = detailViewModel::clearCover,
                onDelete = {
                    showSettings = false
                    showDelete = true
                },
            )
        }
    }

    if (showAddAssets && state.album?.kind == AlbumKind.NORMAL) {
        AddAssetsSheet(
            assets = candidateAssets,
            serverRoot = detailViewModel.serverRoot,
            isBusy = state.isBusy,
            onDismiss = { showAddAssets = false },
            onConfirm = { selected ->
                detailViewModel.addAssets(selected.toList())
                showAddAssets = false
            },
        )
    }
    if (showAddPath && state.album?.kind == AlbumKind.PATH_SYNC) {
        AddPathSheet(
            state = state,
            onDismiss = { showAddPath = false },
            onBrowse = detailViewModel::browseFolder,
            onPreview = { input ->
                showAddPath = false
                detailViewModel.previewAddPath(input)
            },
        )
    }
    state.pathPreview?.let {
        PathChangePreviewDialog(
            previewed = it,
            isBusy = state.isBusy,
            onDismiss = detailViewModel::dismissPathPreview,
            onConfirm = detailViewModel::applyPathPreview,
        )
    }
    if (showDelete) {
        DeleteAlbumDialog(
            isBusy = state.isBusy,
            onDismiss = { showDelete = false },
            onConfirm = detailViewModel::deleteAlbum,
        )
    }
}

@Composable
private fun AlbumDetailScreen(
    state: AlbumDetailUiState,
    assets: LazyPagingItems<MediaAsset>,
    syncRuns: LazyPagingItems<AlbumPathSyncRun>,
    serverRoot: ServerRoot,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onAddAssets: () -> Unit,
    onRemoveAsset: (String) -> Unit,
    onAddPath: () -> Unit,
    onRemovePath: (AlbumPath) -> Unit,
    onManualSync: () -> Unit,
    onOpenSyncRun: (AlbumPathSyncRun) -> Unit,
    onSettings: () -> Unit,
    onSetCover: (String) -> Unit,
    onClearCover: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = state.album
    var showPathDetails by remember(album?.id) { mutableStateOf(false) }
    var showSyncHistory by remember(album?.id) { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = PhotoTubeDimens.ScreenPadding,
            end = PhotoTubeDimens.ScreenPadding,
            bottom = 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
    ) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Spacer(Modifier.weight(1f))
                    if (album != null) {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "图集设置")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = album?.name ?: "正在加载图集",
                    color = PhotoTubeColors.Ink,
                    fontSize = 40.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                album?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = albumMetaLabel(it),
                        color = PhotoTubeColors.Muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (state.isLoading && album == null) {
            item { DetailLoading() }
        } else if (album != null) {
            state.notice?.let { item { NoticeCard(it) } }
            state.error?.let { item { DetailError(it, onRetry) } }

            if (album.kind == AlbumKind.PATH_SYNC) {
                item {
                    PathSyncToolbar(
                        album = album,
                        paths = state.paths,
                        state = state,
                        onManagePaths = { showPathDetails = !showPathDetails },
                        onManualSync = onManualSync,
                    )
                }
                if (showPathDetails) {
                    item {
                        PathSyncControls(
                            album = album,
                            paths = state.paths,
                            state = state,
                            onAddPath = onAddPath,
                            onRemovePath = onRemovePath,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showSyncHistory = !showSyncHistory }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("扫描历史", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            if (showSyncHistory) "收起" else "查看 ${syncRuns.itemCount} 条",
                            color = PhotoTubeColors.Muted,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (showSyncHistory) {
                    when (val refresh = syncRuns.loadState.refresh) {
                        is LoadState.Loading -> item { DetailLoading(compact = true) }
                        is LoadState.Error -> item {
                            DetailError(refresh.error.toTimelineError(), syncRuns::retry)
                        }
                        is LoadState.NotLoading -> {
                            if (syncRuns.itemCount == 0) {
                                item {
                                    Text(
                                        "还没有扫描记录。手动扫描后会在这里显示结果。",
                                        color = PhotoTubeColors.Muted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            } else {
                                items(
                                    count = syncRuns.itemCount,
                                    key = { index -> syncRuns[index]?.id ?: "sync-placeholder-$index" },
                                ) { index ->
                                    syncRuns[index]?.let { run ->
                                        SyncRunHistoryCard(
                                            run = run,
                                            selected = run.id == state.selectedSyncRunId,
                                            onClick = { onOpenSyncRun(run) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    when (val append = syncRuns.loadState.append) {
                        is LoadState.Loading -> item { DetailLoading(compact = true) }
                        is LoadState.Error -> item {
                            DetailError(append.error.toTimelineError(), syncRuns::retry)
                        }
                        is LoadState.NotLoading -> Unit
                    }
                }
            } else if (album.kind == AlbumKind.NORMAL) {
                item {
                    Button(
                        onClick = onAddAssets,
                        enabled = !state.isBusy,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("添加照片")
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    "照片",
                    color = PhotoTubeColors.Ink,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            when (val refresh = assets.loadState.refresh) {
                is LoadState.Loading -> item { DetailLoading() }
                is LoadState.Error -> item { DetailError(refresh.error.toTimelineError(), assets::retry) }
                is LoadState.NotLoading -> {
                    if (assets.itemCount == 0) {
                        item {
                            Text(
                                if (album.kind == AlbumKind.PATH_SYNC) {
                                    "尚无照片。保存目录不会扫描，请点击“开始扫描”。"
                                } else {
                                    "这个图集还没有照片"
                                },
                                color = PhotoTubeColors.Muted,
                                modifier = Modifier.padding(vertical = 32.dp),
                            )
                        }
                    } else {
                        items(count = (assets.itemCount + 2) / 3, key = { "album-row-$it" }) { row ->
                            AlbumAssetRow(
                                row = row,
                                assets = assets,
                                serverRoot = serverRoot,
                                allowRemove = album.kind == AlbumKind.NORMAL,
                                actionsEnabled = !state.isBusy,
                                coverAssetId = album.coverAssetId,
                                onOpenAsset = onOpenAsset,
                                onRemoveAsset = onRemoveAsset,
                                onSetCover = onSetCover,
                                onClearCover = onClearCover,
                            )
                        }
                    }
                }
            }
            when (val append = assets.loadState.append) {
                is LoadState.Loading -> item { DetailLoading(compact = true) }
                is LoadState.Error -> item { DetailError(append.error.toTimelineError(), assets::retry) }
                is LoadState.NotLoading -> Unit
            }
        } else {
            item { DetailError(state.error ?: TimelineError("图集不存在"), onRetry) }
        }
    }
}

private fun albumMetaLabel(album: Album): String {
    val kind = when (album.kind) {
        AlbumKind.NORMAL -> "普通相册"
        AlbumKind.SMART -> "智能相册"
        AlbumKind.PATH_SYNC -> "路径相册"
    }
    val order = if (album.sortMode.name.endsWith("DESC")) "最新优先" else "最早优先"
    return "$kind · ${album.assetCount} 项 · $order"
}

@Composable
private fun PathSyncToolbar(
    album: Album,
    paths: List<AlbumPath>,
    state: AlbumDetailUiState,
    onManagePaths: () -> Unit,
    onManualSync: () -> Unit,
) {
    val isSyncing = state.syncDetail?.run?.state in setOf(
        AlbumPathSyncRunState.PENDING,
        AlbumPathSyncRunState.RUNNING,
    )
    val latestState = paths.firstNotNullOfOrNull { it.lastRunState }
    val status = when {
        isSyncing -> "正在扫描"
        state.requiresManualSync -> "需要扫描"
        latestState != null -> syncPathStateLabel(latestState)
        else -> "尚未扫描"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(24.dp))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${paths.size} 个扫描目录", fontWeight = FontWeight.SemiBold)
            Text(status, color = PhotoTubeColors.Muted, fontSize = 12.sp)
        }
        TextButton(onClick = onManagePaths, modifier = Modifier.height(48.dp)) {
            Text("管理")
        }
        Button(
            onClick = onManualSync,
            enabled = !state.isBusy && (album.pathSync?.enabledPathCount ?: 0) > 0,
            modifier = Modifier.height(48.dp),
        ) {
            if (isSyncing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("扫描")
            }
        }
    }
}

@Composable
private fun PathSyncControls(
    album: Album,
    paths: List<AlbumPath>,
    state: AlbumDetailUiState,
    onAddPath: () -> Unit,
    onRemovePath: (AlbumPath) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("扫描目录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("generation ${album.pathSync?.configGeneration ?: "-"}", color = PhotoTubeColors.Muted, fontSize = 11.sp)
        }
        paths.forEach { path ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(path.relativePath, maxLines = 1)
                    Text(
                        when {
                            !path.enabled -> "已停用"
                            path.lastRunState != null -> path.lastRunState.name
                            else -> "尚未扫描"
                        },
                        color = PhotoTubeColors.Muted,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { onRemovePath(path) }, enabled = !state.isBusy) {
                    Icon(Icons.Rounded.Close, contentDescription = "移除目录")
                }
            }
        }
        TextButton(onClick = onAddPath, enabled = !state.isBusy) { Text("添加目录") }
        state.syncDetail?.let { detail ->
            Text(
                "${detail.run.state.name} · 成功 ${detail.run.succeededPaths} · 离线 ${detail.run.offlinePaths} · 失败 ${detail.run.failedPaths}",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
            )
            detail.pathResults.forEach { result ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${result.relativePath} · ${syncPathStateLabel(result.state)}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                    Text(
                        "发现 ${result.discoveredCount} · 复用 ${result.reusedCount} · 新增 ${result.addedCount} · 移除 ${result.removedCount} · 跳过 ${result.skippedCount}",
                        color = PhotoTubeColors.Muted,
                        fontSize = 11.sp,
                    )
                    result.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        }
        if (state.isLoadingSyncDetail) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在读取扫描详情…", color = PhotoTubeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SyncRunHistoryCard(
    run: AlbumPathSyncRun,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = when (run.state) {
        AlbumPathSyncRunState.SUCCEEDED -> Color(0xFFEAF5EE)
        AlbumPathSyncRunState.PARTIAL -> Color(0xFFFFF4E5)
        AlbumPathSyncRunState.FAILED,
        AlbumPathSyncRunState.CANCELLED,
        -> Color(0xFFFFEDEA)
        AlbumPathSyncRunState.PENDING,
        AlbumPathSyncRunState.RUNNING,
        -> Color(0xFFEAF2FF)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) tint else Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(syncStateLabel(run.state), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatSyncTime(run.createdAt), color = PhotoTubeColors.Muted, fontSize = 12.sp)
        }
        Text(
            "${syncTriggerLabel(run.trigger)} · generation ${run.configGeneration}",
            color = PhotoTubeColors.Muted,
            fontSize = 12.sp,
        )
        Text(
            "路径 ${run.totalPaths} · 成功 ${run.succeededPaths} · 离线 ${run.offlinePaths} · 失败 ${run.failedPaths}",
            fontSize = 13.sp,
        )
        run.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

internal fun syncStateLabel(state: AlbumPathSyncRunState): String = when (state) {
    AlbumPathSyncRunState.PENDING -> "等待扫描"
    AlbumPathSyncRunState.RUNNING -> "正在扫描"
    AlbumPathSyncRunState.SUCCEEDED -> "扫描成功"
    AlbumPathSyncRunState.PARTIAL -> "部分完成"
    AlbumPathSyncRunState.FAILED -> "扫描失败"
    AlbumPathSyncRunState.CANCELLED -> "已取消"
}

private fun syncTriggerLabel(trigger: AlbumPathSyncTrigger): String = when (trigger) {
    AlbumPathSyncTrigger.INITIAL -> "初始运行"
    AlbumPathSyncTrigger.MANUAL -> "手动扫描"
    AlbumPathSyncTrigger.LIBRARY_SCAN -> "媒体库扫描联动"
}

private fun syncPathStateLabel(state: com.yunai.phototube.data.album.AlbumPathSyncPathState): String = when (state) {
    com.yunai.phototube.data.album.AlbumPathSyncPathState.PENDING -> "等待"
    com.yunai.phototube.data.album.AlbumPathSyncPathState.RUNNING -> "扫描中"
    com.yunai.phototube.data.album.AlbumPathSyncPathState.SUCCEEDED -> "成功"
    com.yunai.phototube.data.album.AlbumPathSyncPathState.OFFLINE -> "离线"
    com.yunai.phototube.data.album.AlbumPathSyncPathState.FAILED -> "失败"
    com.yunai.phototube.data.album.AlbumPathSyncPathState.SKIPPED -> "跳过"
}

private fun formatSyncTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
}.getOrDefault(value)

@Composable
private fun AlbumAssetRow(
    row: Int,
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    allowRemove: Boolean,
    actionsEnabled: Boolean,
    coverAssetId: String?,
    onOpenAsset: (String) -> Unit,
    onRemoveAsset: (String) -> Unit,
    onSetCover: (String) -> Unit,
    onClearCover: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp),
        horizontalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
    ) {
        repeat(3) { column ->
            val index = row * 3 + column
            if (index < assets.itemCount) {
                val asset = assets[index]
                var menuExpanded by remember(asset?.id) { mutableStateOf(false) }
                AssetCell(
                    asset = asset,
                    serverRoot = serverRoot,
                    selected = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    onClick = { asset?.let { onOpenAsset(it.id) } },
                    onLongClick = if (asset != null) {
                        { menuExpanded = true }
                    } else null,
                    trailingAction = if (asset != null) {
                        {
                            AlbumAssetMenu(
                                expanded = menuExpanded,
                                onDismiss = { menuExpanded = false },
                                isCover = asset.id == coverAssetId,
                                allowRemove = allowRemove,
                                enabled = actionsEnabled,
                                onSetCover = { onSetCover(asset.id) },
                                onClearCover = onClearCover,
                                onRemove = { onRemoveAsset(asset.id) },
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    } else null,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlbumAssetMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isCover: Boolean,
    allowRemove: Boolean,
    enabled: Boolean,
    onSetCover: () -> Unit,
    onClearCover: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(if (isCover) "清除图集封面" else "设为图集封面") },
                onClick = {
                    onDismiss()
                    if (isCover) onClearCover() else onSetCover()
                },
            )
            if (allowRemove) {
                DropdownMenuItem(
                    text = { Text("从图集中移除") },
                    onClick = {
                        onDismiss()
                        onRemove()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetCell(
    asset: MediaAsset?,
    serverRoot: ServerRoot,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailingAction: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(PhotoTubeDimens.PhotoRadius))
            .background(Color(0xFFE7E9ED))
            .combinedClickable(
                enabled = asset != null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        asset?.thumbnailUrl(serverRoot, ThumbnailSize.SM)?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = asset.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Text("暂无缩略图", color = PhotoTubeColors.Muted, fontSize = 11.sp)
        if (selected) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.24f)))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "已选择",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp),
                tint = Color.White,
            )
        }
        trailingAction?.invoke(this)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAssetsSheet(
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("添加照片", style = MaterialTheme.typography.headlineSmall)
            Text("已选择 ${selectedIds.size} / 500", color = PhotoTubeColors.Muted)
            LazyColumn(Modifier.height(380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(count = (assets.itemCount + 2) / 3, key = { "picker-row-$it" }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(3) { column ->
                            val index = row * 3 + column
                            if (index < assets.itemCount) {
                                val asset = assets[index]
                                AssetCell(
                                    asset = asset,
                                    serverRoot = serverRoot,
                                    selected = asset?.id in selectedIds,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize(),
                                    onClick = {
                                        asset?.let {
                                            selectedIds = if (it.id in selectedIds) {
                                                selectedIds - it.id
                                            } else if (selectedIds.size < 500) {
                                                selectedIds + it.id
                                            } else {
                                                selectedIds
                                            }
                                        }
                                    },
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (assets.loadState.append is LoadState.Loading) {
                    item { DetailLoading(compact = true) }
                }
            }
            Button(
                onClick = { onConfirm(selectedIds) },
                enabled = selectedIds.isNotEmpty() && !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加所选照片")
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPathSheet(
    state: AlbumDetailUiState,
    onDismiss: () -> Unit,
    onBrowse: (String) -> Unit,
    onPreview: (AlbumPathInput) -> Unit,
) {
    var selectedFolder by remember { mutableStateOf<SourceFolder?>(null) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("添加扫描目录", style = MaterialTheme.typography.headlineSmall)
            Text("下一步先预览影响；确认后也不会自动扫描。", color = PhotoTubeColors.Muted)
            SourceFolderBrowser(
                listing = state.sourceFolderListing,
                isLoading = state.isBrowsingFolders,
                selectedPath = selectedFolder?.path,
                onBrowse = onBrowse,
                onSelect = { libraryId, folder ->
                    selectedLibraryId = libraryId
                    selectedFolder = folder
                },
            )
            Button(
                enabled = selectedFolder != null && !state.isBusy,
                onClick = {
                    onPreview(
                        AlbumPathInput(
                            libraryId = requireNotNull(selectedLibraryId),
                            relativePath = requireNotNull(selectedFolder).path,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("预览目录变更")
            }
        }
    }
}

@Composable
private fun PathChangePreviewDialog(
    previewed: com.yunai.phototube.data.album.PreviewedAlbumPathChange,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val preview = previewed.preview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认目录变更") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("当前覆盖 ${preview.currentCoveredAssets} 项")
                Text("将移除 ${preview.membersToRemove} 项")
                Text("其它目录仍保留 ${preview.membersRetainedByOtherPaths} 项")
                if (preview.requiresSync) {
                    Text("保存后仍需你手动点击扫描。", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) { Text("应用同一变更") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAlbumSheet(
    album: Album,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, AlbumSortMode) -> Unit,
    onClearCover: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(album.id) { mutableStateOf(album.name) }
    var sortMode by remember(album.id) { mutableStateOf(album.sortMode) }
    val normalizedName = name.trim()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("图集设置", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                supportingText = { Text("${name.length} / 120") },
            )
            Text("照片顺序", fontWeight = FontWeight.SemiBold)
            if (album.kind == AlbumKind.SMART) {
                Text("智能图集固定为最新优先", color = PhotoTubeColors.Muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sortMode == AlbumSortMode.TAKEN_AT_DESC,
                        onClick = { sortMode = AlbumSortMode.TAKEN_AT_DESC },
                        label = { Text("最新优先") },
                    )
                    FilterChip(
                        selected = sortMode == AlbumSortMode.TAKEN_AT_ASC,
                        onClick = { sortMode = AlbumSortMode.TAKEN_AT_ASC },
                        label = { Text("最早优先") },
                    )
                }
            }
            Column {
                Text("图集封面", fontWeight = FontWeight.SemiBold)
                Text(
                    if (album.coverAssetId == null) "当前使用默认封面" else "已选择成员照片作为封面",
                    color = PhotoTubeColors.Muted,
                    fontSize = 13.sp,
                )
                if (album.coverAssetId != null) {
                    TextButton(onClick = onClearCover, enabled = !isBusy) {
                        Text("清除封面")
                    }
                }
            }
            Text(
                "要更换封面，请关闭设置后点击成员照片右上角菜单。封面照片必须属于当前图集。",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
            )
            Button(
                onClick = { onSave(normalizedName, sortMode) },
                enabled = normalizedName.isNotEmpty() && !isBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("保存图集设置")
                }
            }
            TextButton(onClick = onDelete, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("删除图集")
            }
        }
    }
}

@Composable
private fun DeleteAlbumDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这个图集？") },
        text = { Text("只删除图集、成员关系和路径配置，不删除资产，也不会删除 NAS 原文件。") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) { Text("删除图集") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NoticeCard(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE5F1EA), RoundedCornerShape(18.dp))
            .padding(14.dp),
        color = Color(0xFF285C3F),
    )
}

@Composable
private fun DetailLoading(compact: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 60.dp else 180.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(if (compact) 22.dp else 40.dp))
    }
}

@Composable
private fun DetailError(error: TimelineError, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
