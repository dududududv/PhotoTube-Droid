package com.yunai.phototube.ui.viewer

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.yunai.phototube.data.asset.AssetRepository
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.edit.EditRepository
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.AssetChangeKind
import okhttp3.OkHttpClient
import java.util.Locale

@Composable
fun AssetViewerRoute(
    assetId: String,
    repository: AssetRepository,
    editRepository: EditRepository,
    tagRepository: TagRepository,
    sessionRepository: SessionRepository,
    httpClient: OkHttpClient,
    refreshRevision: Int,
    onPrivateAssetVisibilityChanged: (Boolean?) -> Unit,
    onBack: (changes: Set<AssetChangeKind>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewerViewModel: AssetViewerViewModel = viewModel(
        key = "asset-viewer",
        factory = AssetViewerViewModel.factory(repository, tagRepository, sessionRepository),
    )
    val editViewModel: AssetEditViewModel = viewModel(
        key = "asset-editor",
        factory = AssetEditViewModel.factory(editRepository, repository),
    )
    LaunchedEffect(assetId, refreshRevision) { viewerViewModel.open(assetId, refreshRevision) }
    val state by viewerViewModel.uiState.collectAsStateWithLifecycle()
    val editState by editViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(assetId, state.asset?.id, state.asset?.private) {
        onPrivateAssetVisibilityChanged(
            state.asset?.takeIf { it.id == assetId }?.private,
        )
    }
    var showTags by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showInfo by remember(assetId) { mutableStateOf(false) }
    var destructiveAction by remember { mutableStateOf<DestructiveAssetAction?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    BackHandler { onBack(state.changes) }
    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) {
            viewerViewModel.consumeCloseRequest()
            onBack(state.changes)
        }
    }
    LaunchedEffect(editState.applyEvent?.revision) {
        editState.applyEvent?.let { event ->
            if (event.assetId == assetId) {
                if (event.refreshAsset) {
                    viewerViewModel.refresh()
                } else {
                    viewerViewModel.applyActiveEdit(event.assetId, event.activeEdit)
                }
            }
            editViewModel.consumeApplyEvent()
        }
    }
    AssetViewerScreen(
        state = state,
        serverRoot = repository.serverRoot(),
        httpClient = httpClient,
        onBack = { onBack(state.changes) },
        onRetry = viewerViewModel::refresh,
        onFavoriteChanged = viewerViewModel::setFavorite,
        onOpenActions = { showActions = true },
        onOpenTags = {
            showTags = true
            viewerViewModel.searchTags("")
        },
        onOpenInfo = { showInfo = true },
        modifier = modifier,
    )
    if (showInfo) {
        state.asset?.let { asset ->
            AssetInfoSheet(asset = asset, onDismiss = { showInfo = false })
        }
    }
    if (showTags) {
        TagPickerSheet(
            state = state,
            onDismiss = { showTags = false },
            onQueryChanged = viewerViewModel::searchTags,
            onAdd = viewerViewModel::addTag,
            onRemove = viewerViewModel::removeTag,
            onCreateAndAdd = viewerViewModel::createAndAddTag,
            onLoadMore = viewerViewModel::loadMoreTags,
        )
    }
    if (showActions) {
        AssetActionsSheet(
            state = state,
            onDismiss = { showActions = false },
            onOpenEditor = {
                val asset = state.asset ?: return@AssetActionsSheet
                showActions = false
                editViewModel.open(asset)
                showEditor = true
            },
            onSetRating = viewerViewModel::setRating,
            onSetArchived = {
                showActions = false
                viewerViewModel.setArchived(it)
            },
            onSetPrivate = {
                showActions = false
                viewerViewModel.setPrivate(it)
            },
            onMoveToTrash = {
                showActions = false
                destructiveAction = DestructiveAssetAction.Trash
            },
            onRestore = {
                showActions = false
                viewerViewModel.restoreFromTrash()
            },
            onPurge = {
                showActions = false
                destructiveAction = DestructiveAssetAction.Purge
            },
        )
    }
    if (showEditor) {
        state.asset?.let { asset ->
            PhotoEditorDialog(
                asset = asset,
                state = editState,
                serverRoot = repository.serverRoot(),
                onDismiss = { showEditor = false },
                onRetry = editViewModel::retry,
                onRotate = editViewModel::rotateClockwise,
                onFlipHorizontal = editViewModel::flipHorizontally,
                onFlipVertical = editViewModel::flipVertically,
                onResetDraft = editViewModel::resetDraft,
                onResetToSource = editViewModel::resetToSourceTransform,
                onHorizontalCropChanged = editViewModel::updateHorizontalCrop,
                onVerticalCropChanged = editViewModel::updateVerticalCrop,
                onSave = editViewModel::saveAsNewVersion,
                onSelectVersion = editViewModel::requestSelectVersion,
                onLoadMore = editViewModel::loadMore,
                onConfirmDiscardAndSelect = editViewModel::confirmDiscardAndSelect,
                onDismissDiscardSelection = editViewModel::dismissDiscardConfirmation,
            )
        }
    }
    if (state.showPrivateUnlock) {
        PrivateUnlockDialog(
            state = state,
            onPasswordChanged = viewerViewModel::updatePrivatePassword,
            onConfirm = viewerViewModel::unlockPrivateAccess,
            onDismiss = viewerViewModel::dismissPrivateUnlock,
        )
    }
    destructiveAction?.let { action ->
        AssetDestructiveConfirmation(
            action = action,
            onDismiss = { destructiveAction = null },
            onConfirm = {
                destructiveAction = null
                when (action) {
                    DestructiveAssetAction.Trash -> viewerViewModel.moveToTrash()
                    DestructiveAssetAction.Purge -> viewerViewModel.purgeFromTrash()
                }
            },
        )
    }
}

@Composable
private fun AssetViewerScreen(
    state: AssetViewerUiState,
    serverRoot: ServerRoot,
    httpClient: OkHttpClient,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onOpenActions: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding(),
    ) {
        ViewerTopBar(state, onBack, onFavoriteChanged, onOpenActions)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF101114)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.asset != null -> AssetMedia(state.asset, serverRoot, httpClient)
                state.isLoading -> CircularProgressIndicator(color = Color.White)
                else -> ViewerError(state.error, onRetry)
            }
        }
        state.asset?.let { AssetMetadata(it, onOpenTags, onOpenInfo) }
        state.error?.takeIf { state.asset != null }?.let {
            DiagnosticErrorText(
                message = it.message,
                logId = it.logId,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ViewerTopBar(
    state: AssetViewerUiState,
    onBack: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onOpenActions: () -> Unit,
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
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.asset?.fileName ?: "正在加载",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
        )
        state.asset?.takeIf { it.state != AssetState.TRASHED }?.let { asset ->
            IconButton(
                enabled = !state.isSavingFavorite,
                onClick = { onFavoriteChanged(!asset.favorite) },
            ) {
                if (state.isSavingFavorite) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (asset.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (asset.favorite) "取消收藏" else "收藏",
                        tint = if (asset.favorite) PhotoTubeColors.Alert else PhotoTubeColors.Ink,
                    )
                }
            }
        }
        if (state.asset != null) {
            IconButton(enabled = !state.isSavingMutation, onClick = onOpenActions) {
                if (state.isSavingMutation) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多资产操作")
                }
            }
        }
    }
}

@Composable
private fun AssetMedia(asset: MediaAsset, serverRoot: ServerRoot, httpClient: OkHttpClient) {
    if (asset.state == AssetState.PROCESSING) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            asset.thumbnailUrl(serverRoot, ThumbnailSize.MD)?.let { thumbnailUrl ->
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = asset.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alpha = 0.52f,
                )
            }
            Text(
                "媒体仍在后台处理中",
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.66f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 13.sp,
            )
        }
        return
    }
    if (!asset.canReadOriginal()) {
        val label = when (asset.state) {
            AssetState.DISCOVERED -> "媒体仍在发现阶段"
            AssetState.FAILED -> "媒体处理失败"
            AssetState.OFFLINE -> "原文件当前离线"
            AssetState.TRASHED -> "资产位于回收站"
            AssetState.BROWSABLE, AssetState.PROCESSING -> error("已在前置分支处理")
        }
        Text(label, color = Color.White.copy(alpha = 0.78f))
        return
    }
    var showMotionVideo by remember(asset.id) { mutableStateOf(false) }
    val motionVideoUrl = asset.motionVideoUrl(serverRoot)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showMotionVideo && motionVideoUrl != null) {
            AuthenticatedVideoPlayer(motionVideoUrl, httpClient)
        } else if (asset.kind == AssetKind.VIDEO) {
            AuthenticatedVideoPlayer(asset.originalUrl(serverRoot), httpClient)
        } else {
            OriginalPhoto(asset, serverRoot)
        }
        if (motionVideoUrl != null) {
            FilledTonalButton(
                onClick = { showMotionVideo = !showMotionVideo },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp),
            ) {
                Icon(
                    imageVector = if (showMotionVideo) Icons.Rounded.Photo else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (showMotionVideo) "显示静态照片" else "播放动态照片")
            }
        }
    }
}

@Composable
private fun OriginalPhoto(asset: MediaAsset, serverRoot: ServerRoot) {
    var scale by remember(asset.id) { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = asset.thumbnailUrl(serverRoot, ThumbnailSize.MD),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alpha = 0.55f,
        )
        AsyncImage(
            model = asset.displayPhotoUrl(serverRoot),
            contentDescription = asset.fileName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .transformable(transformState),
            contentScale = ContentScale.Fit,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AuthenticatedVideoPlayer(url: String, httpClient: OkHttpClient) {
    val context = LocalContext.current
    var playbackFailure by remember(url) { mutableStateOf<VideoPlaybackFailure?>(null) }
    val player = remember(url, httpClient) {
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackFailure = videoPlaybackFailureForHttpCode(error.videoHttpResponseCode())
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackFailure = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        playbackFailure?.let { failure ->
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.78f), MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(failure.message, color = Color.White)
                Button(
                    onClick = {
                        playbackFailure = null
                        if (failure.retryFromStart) player.seekTo(0)
                        player.prepare()
                        player.playWhenReady = true
                    },
                ) {
                    Text(if (failure.retryFromStart) "从头重试" else "重试播放")
                }
            }
        }
    }
}

@Composable
private fun AssetMetadata(
    asset: MediaAsset,
    onOpenTags: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(asset.title ?: asset.fileName, style = MaterialTheme.typography.titleMedium)
                Text(asset.relativePath, color = PhotoTubeColors.Muted, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = listOfNotNull(asset.width, asset.height).joinToString(" × ").ifEmpty { asset.kind.name },
                color = PhotoTubeColors.Muted,
                fontSize = 13.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (asset.tags != null) {
                TextButton(
                    onClick = onOpenTags,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    modifier = Modifier.weight(1f),
                ) {
                    val label = asset.tags.takeIf { it.isNotEmpty() }
                        ?.joinToString(" · ") { it.name }
                        ?: "添加标签"
                    Text(label, maxLines = 1)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextButton(onClick = onOpenInfo) { Text("照片信息") }
        }
        asset.motionPhoto?.let { motion ->
            Text(
                "OPPO 动态照片 · ${String.format(Locale.getDefault(), "%.1f", motion.durationSec)} 秒 · ${motion.width} × ${motion.height}",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetInfoSheet(asset: MediaAsset, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("照片信息", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "以下信息来自 PhotoTube，当前仅供查看。",
                    color = PhotoTubeColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            if (!asset.title.isNullOrBlank() || !asset.description.isNullOrBlank()) {
                item {
                    AssetInfoSectionTitle("内容")
                    asset.title?.takeIf(String::isNotBlank)?.let { AssetInfoRow("标题", it) }
                    asset.description?.takeIf(String::isNotBlank)?.let { AssetInfoRow("描述", it) }
                }
            }
            item {
                AssetInfoSectionTitle("拍摄")
                AssetInfoRow("拍摄时间", formatAssetDateTime(asset.takenAt))
                AssetInfoRow("时间来源", takenAtSourceLabel(asset.takenAtSource))
                AssetInfoRow("类型", assetKindLabel(asset.kind))
                AssetInfoRow(
                    "尺寸",
                    if (asset.width != null && asset.height != null) {
                        "${asset.width} × ${asset.height} 像素"
                    } else {
                        "尚未探测"
                    },
                )
                asset.durationSec?.let { AssetInfoRow("时长", formatAssetDuration(it)) }
                asset.motionPhoto?.let { motion ->
                    AssetInfoRow("动态照片", "${motion.format} · ${formatAssetDuration(motion.durationSec)}")
                }
            }
            item {
                AssetInfoSectionTitle("文件")
                AssetInfoRow("文件名", asset.fileName, selectable = true)
                AssetInfoRow("相对路径", asset.relativePath, selectable = true)
                AssetInfoRow("大小", formatAssetFileSize(asset.fileSize))
                AssetInfoRow("内容哈希", asset.contentHash ?: "尚未计算", selectable = asset.contentHash != null)
            }
            item {
                AssetInfoSectionTitle("PhotoTube")
                AssetInfoRow("状态", assetStateLabel(asset.state))
                AssetInfoRow("导入时间", formatAssetDateTime(asset.importedAt))
                AssetInfoRow("评分", asset.rating?.let { "$it 星" } ?: "未评分")
                AssetInfoRow("收藏", if (asset.favorite) "是" else "否")
                AssetInfoRow("归档", if (asset.archived) "是" else "否")
                AssetInfoRow("私密", if (asset.private) "是" else "否")
                AssetInfoRow("显示版本", if (asset.activeEdit == null) "原图" else "非破坏性编辑版本")
                asset.trashedAt?.let { AssetInfoRow("移入回收站", formatAssetDateTime(it)) }
                asset.retentionDays?.let { AssetInfoRow("保留期限", "$it 天") }
                AssetInfoRow("媒体库 ID", asset.libraryId, selectable = true)
                AssetInfoRow("资产 ID", asset.id, selectable = true)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AssetInfoSectionTitle(title: String) {
    Text(
        text = title,
        color = PhotoTubeColors.Muted,
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AssetInfoRow(label: String, value: String, selectable: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = PhotoTubeColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.width(88.dp),
        )
        if (selectable) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(value, color = PhotoTubeColors.Ink, fontSize = 14.sp)
            }
        } else {
            Text(
                text = value,
                color = PhotoTubeColors.Ink,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
    state: AssetViewerUiState,
    onDismiss: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onAdd: (Tag) -> Unit,
    onRemove: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val selectedIds = state.asset?.tags.orEmpty().mapTo(mutableSetOf()) { it.id }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("管理标签", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.tagQuery,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索或新建标签") },
            )
            if (
                state.tagQuery.isNotBlank() &&
                state.availableTags.none { it.name.equals(state.tagQuery.trim(), ignoreCase = true) }
            ) {
                FilledTonalButton(
                    enabled = !state.isSavingTag,
                    onClick = { onCreateAndAdd(state.tagQuery) },
                ) {
                    Text("创建“${state.tagQuery.trim()}”并添加")
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                items(state.availableTags, key = Tag::id) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tag.name, style = MaterialTheme.typography.titleMedium)
                            Text("${tag.assetCount} 项", color = PhotoTubeColors.Muted, fontSize = 12.sp)
                        }
                        val selected = tag.id in selectedIds
                        TextButton(
                            enabled = !state.isSavingTag,
                            onClick = { if (selected) onRemove(tag.id) else onAdd(tag) },
                        ) {
                            Text(if (selected) "移除" else "添加")
                        }
                    }
                }
                if (state.nextTagCursor != null) {
                    item {
                        TextButton(onClick = onLoadMore, enabled = !state.isLoadingTags) {
                            Text("加载更多")
                        }
                    }
                }
            }
            if (state.isLoadingTags || state.isSavingTag) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetActionsSheet(
    state: AssetViewerUiState,
    onDismiss: () -> Unit,
    onOpenEditor: () -> Unit,
    onSetRating: (Int?) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onSetPrivate: (Boolean) -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val asset = state.asset ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("资产操作", style = MaterialTheme.typography.headlineSmall)
            if (asset.state == AssetState.TRASHED) {
                FilledTonalButton(
                    onClick = onRestore,
                    enabled = !state.isSavingMutation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("从回收站恢复")
                }
                TextButton(
                    onClick = onPurge,
                    enabled = !state.isSavingMutation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("彻底清除 PhotoTube 记录", color = MaterialTheme.colorScheme.error)
                }
            } else {
                if (asset.isEditablePhoto()) {
                    FilledTonalButton(
                        onClick = onOpenEditor,
                        enabled = !state.isSavingMutation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("非破坏性编辑")
                    }
                }
                Text("评分", color = PhotoTubeColors.Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { rating ->
                        IconButton(
                            enabled = !state.isSavingMutation,
                            onClick = { onSetRating(rating) },
                        ) {
                            Icon(
                                imageVector = if ((asset.rating ?: 0) >= rating) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Rounded.StarBorder
                                },
                                contentDescription = "设为 $rating 星",
                                tint = if ((asset.rating ?: 0) >= rating) Color(0xFFFFB300) else PhotoTubeColors.Muted,
                            )
                        }
                    }
                    if (asset.rating != null) {
                        TextButton(onClick = { onSetRating(null) }, enabled = !state.isSavingMutation) {
                            Text("清除")
                        }
                    }
                }
                FilledTonalButton(
                    onClick = { onSetArchived(!asset.archived) },
                    enabled = !state.isSavingMutation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (asset.archived) "取消归档" else "归档")
                }
                FilledTonalButton(
                    onClick = { onSetPrivate(!asset.private) },
                    enabled = !state.isSavingMutation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (asset.private) "移出私密空间" else "设为私密")
                }
                TextButton(
                    onClick = onMoveToTrash,
                    enabled = !state.isSavingMutation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("移入回收站", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PrivateUnlockDialog(
    state: AssetViewerUiState,
    onPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解锁私密内容") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("请输入当前登录账号的口令。授权事实由 PhotoTube 会话保存，最长 15 分钟。")
                OutlinedTextField(
                    value = state.privatePassword,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("账号口令") },
                )
                state.error?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = state.privatePassword.isNotEmpty() && !state.isUnlockingPrivate,
            ) {
                if (state.isUnlockingPrivate) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("解锁并继续")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private enum class DestructiveAssetAction { Trash, Purge }

@Composable
private fun AssetDestructiveConfirmation(
    action: DestructiveAssetAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isPurge = action == DestructiveAssetAction.Purge
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPurge) "彻底清除记录？" else "移入回收站？") },
        text = {
            Text(
                if (isPurge) {
                    "此操作不可恢复，会删除 PhotoTube 数据库记录和派生缓存，但不会删除 NAS 原文件。原文件仍在扫描目录时，未来扫描可能把它重新发现为新资产。"
                } else {
                    "资产可以从回收站恢复。原文件仍保留在 NAS 上，不会被移动或删除。"
                },
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(if (isPurge) "确认彻底清除" else "移入回收站") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ViewerError(error: TimelineError?, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DiagnosticErrorText(
            message = error?.message ?: "资产加载失败",
            logId = error?.logId,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            messageColor = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text("重试") }
    }
}
