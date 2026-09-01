package com.yunai.phototube.ui.trash

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.theme.PhotoTubeColors
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Composable
fun TrashRoute(
    model: TrashViewModel,
    refreshRevision: Int,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val assets = model.assets.collectAsLazyPagingItems()
    var purgeTarget by remember { mutableStateOf<MediaAsset?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) assets.refresh()
    }
    TrashScreen(
        assets = assets,
        serverRoot = model.serverRoot,
        state = state,
        onBack = onBack,
        onOpenAsset = onOpenAsset,
        onRestore = model::restore,
        onPurge = { asset -> purgeTarget = asset },
        modifier = modifier,
    )
    purgeTarget?.let { asset ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("彻底清除记录？") },
            text = {
                Text(
                    "此操作不可恢复，会删除 PhotoTube 数据库记录和派生缓存，但不会删除 NAS 原文件。原文件仍在扫描目录时，未来扫描可能重新发现。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    purgeTarget = null
                    model.purge(asset.id)
                }) { Text("确认彻底清除") }
            },
            dismissButton = { TextButton(onClick = { purgeTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TrashScreen(
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    state: TrashUiState,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onRestore: (String) -> Unit,
    onPurge: (MediaAsset) -> Unit,
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
            Text("回收站", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "恢复不会改变资产进入回收站前的状态；彻底清理也不会删除 NAS 原文件。",
            color = PhotoTubeColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
        state.error?.let {
            DiagnosticErrorText(
                message = it.message,
                logId = it.logId,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        when (val refresh = assets.loadState.refresh) {
            is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                refresh.error.toTimelineError().let { error ->
                    DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
                }
                Button(onClick = assets::retry, modifier = Modifier.padding(top = 12.dp)) { Text("重试") }
            }
            is LoadState.NotLoading -> {
                if (assets.itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("回收站为空", color = PhotoTubeColors.Muted)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(count = assets.itemCount, key = { assets[it]?.id ?: "placeholder-$it" }) { index ->
                            assets[index]?.let { asset ->
                                TrashAssetRow(
                                    asset = asset,
                                    serverRoot = serverRoot,
                                    isBusy = state.busyAssetId == asset.id,
                                    actionsEnabled = state.busyAssetId == null,
                                    onOpen = { onOpenAsset(asset.id) },
                                    onRestore = { onRestore(asset.id) },
                                    onPurge = { onPurge(asset) },
                                )
                            }
                        }
                        if (assets.loadState.append is LoadState.Loading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashAssetRow(
    asset: MediaAsset,
    serverRoot: ServerRoot,
    isBusy: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = asset.thumbnailUrl(serverRoot, ThumbnailSize.SM),
            contentDescription = asset.fileName,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFFE9EBEF)),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFFE9EBEF)),
            error = ColorPainter(Color(0xFFDADDE3)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(asset.fileName, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Text(retentionLabel(asset), color = PhotoTubeColors.Muted, fontSize = 12.sp)
            Row {
                TextButton(onClick = onRestore, enabled = actionsEnabled) { Text("恢复") }
                TextButton(onClick = onPurge, enabled = actionsEnabled) {
                    Text("彻底清理", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (isBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

private fun retentionLabel(asset: MediaAsset): String {
    val trashedAt = asset.trashedAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    val days = asset.retentionDays
    if (trashedAt == null || days == null) return "保留期未知"
    val deadline = trashedAt.plusDays(days.toLong())
    val remaining = ChronoUnit.DAYS.between(OffsetDateTime.now(), deadline).coerceAtLeast(0)
    return "删除于 ${asset.trashedAt} · 还剩 $remaining 天"
}
