package com.yunai.phototube.ui.viewer

import android.content.Context
import android.content.Intent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.android16Glass
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.AssetChangeKind
import dev.chrisbanes.haze.hazeSource
import okhttp3.OkHttpClient
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

@Composable
fun AssetViewerRoute(
    assetId: String,
    stripAssets: List<MediaAsset>,
    repository: AssetRepository,
    editRepository: EditRepository,
    tagRepository: TagRepository,
    sessionRepository: SessionRepository,
    httpClient: OkHttpClient,
    refreshRevision: Int,
    onPrivateAssetVisibilityChanged: (Boolean?) -> Unit,
    onSelectAsset: (String) -> Unit,
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
    val context = LocalContext.current
    var carriedChanges by remember { mutableStateOf<Set<AssetChangeKind>>(emptySet()) }
    var showTags by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showInfo by remember(assetId) { mutableStateOf(false) }
    var destructiveAction by remember { mutableStateOf<DestructiveAssetAction?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val leaveViewer = { onBack(carriedChanges + state.changes) }
    BackHandler { leaveViewer() }
    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) {
            viewerViewModel.consumeCloseRequest()
            leaveViewer()
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
        stripAssets = stripAssets,
        serverRoot = repository.serverRoot(),
        httpClient = httpClient,
        onBack = leaveViewer,
        onRetry = viewerViewModel::refresh,
        onFavoriteChanged = viewerViewModel::setFavorite,
        onOpenActions = { showActions = true },
        onOpenInfo = { showInfo = true },
        onOpenEditor = {
            val asset = state.asset ?: return@AssetViewerScreen
            editViewModel.open(asset)
            showEditor = true
        },
        onMoveToTrash = { destructiveAction = DestructiveAssetAction.Trash },
        onShare = { asset -> shareAsset(context, asset, repository.serverRoot()) },
        onSelectAsset = { nextAssetId ->
            if (nextAssetId != assetId) {
                carriedChanges = carriedChanges + state.changes
                onSelectAsset(nextAssetId)
            }
        },
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
            onOpenTags = {
                showActions = false
                showTags = true
                viewerViewModel.searchTags("")
            },
            onOpenInfo = {
                showActions = false
                showInfo = true
            },
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
    stripAssets: List<MediaAsset>,
    serverRoot: ServerRoot,
    httpClient: OkHttpClient,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFavoriteChanged: (Boolean) -> Unit,
    onOpenActions: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenEditor: () -> Unit,
    onMoveToTrash: () -> Unit,
    onShare: (MediaAsset) -> Unit,
    onSelectAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ViewerSystemBars()
    val hazeState = rememberAndroid16HazeState()
    var showChrome by remember(state.asset?.id) { mutableStateOf(true) }
    val displayedStripAssets = remember(state.asset?.id, stripAssets) {
        val current = state.asset
        val loaded = stripAssets.distinctBy(MediaAsset::id)
        if (current == null || loaded.any { it.id == current.id }) loaded else listOf(current)
    }
    val currentStripIndex = displayedStripAssets.indexOfFirst { it.id == state.asset?.id }
    Android16HazeProvider(state = hazeState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF7F8FA)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.asset != null -> AssetMedia(
                        asset = state.asset,
                        serverRoot = serverRoot,
                        httpClient = httpClient,
                        onToggleChrome = { showChrome = !showChrome },
                        onOpenInfo = onOpenInfo,
                        onPreviousAsset = displayedStripAssets.getOrNull(currentStripIndex - 1)
                            ?.let { previous -> { onSelectAsset(previous.id) } },
                        onNextAsset = displayedStripAssets.getOrNull(currentStripIndex + 1)
                            ?.let { next -> { onSelectAsset(next.id) } },
                    )
                    state.isLoading -> CircularProgressIndicator(color = PhotoTubeColors.Ink)
                    else -> ViewerError(state.error, onRetry)
                }
            }
            AnimatedVisibility(
                visible = showChrome,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ViewerTopBar(
                    state = state,
                    onBack = onBack,
                    onOpenActions = onOpenActions,
                )
            }
            state.asset?.let {
                AnimatedVisibility(
                    visible = showChrome,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ViewerBottomChrome(
                        asset = it,
                        state = state,
                        stripAssets = displayedStripAssets,
                        serverRoot = serverRoot,
                        onSelectAsset = onSelectAsset,
                        onShare = { onShare(it) },
                        onFavoriteChanged = { onFavoriteChanged(!it.favorite) },
                        onOpenInfo = onOpenInfo,
                        onOpenEditor = onOpenEditor,
                        onMoveToTrash = onMoveToTrash,
                    )
                }
            }
            state.error?.takeIf { state.asset != null }?.let {
                DiagnosticErrorText(
                    message = it.message,
                    logId = it.logId,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewerSystemBars() {
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color(0xFFF7F8FA).toArgb(), Color(0xFFF7F8FA).toArgb()),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        onDispose {
            val background = PhotoTubeColors.Background.toArgb()
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(background, background),
                navigationBarStyle = SystemBarStyle.light(background, background),
            )
        }
    }
}

@Composable
private fun ViewerTopBar(
    state: AssetViewerUiState,
    onBack: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        state.asset?.let { asset ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = viewerSourceLabel(asset),
                    color = PhotoTubeColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = viewerDateLabel(asset.takenAt),
                    color = PhotoTubeColors.Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        } ?: Spacer(Modifier.weight(1f))
        if (state.asset != null) {
            ViewerIconButton(enabled = !state.isSavingMutation, onClick = onOpenActions) {
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
private fun ViewerIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
private fun AssetMedia(
    asset: MediaAsset,
    serverRoot: ServerRoot,
    httpClient: OkHttpClient,
    onToggleChrome: () -> Unit,
    onOpenInfo: () -> Unit,
    onPreviousAsset: (() -> Unit)?,
    onNextAsset: (() -> Unit)?,
) {
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
        Text(label, color = PhotoTubeColors.Ink.copy(alpha = 0.72f))
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
            OriginalPhoto(
                asset = asset,
                serverRoot = serverRoot,
                onToggleChrome = onToggleChrome,
                onOpenInfo = onOpenInfo,
                onPreviousAsset = onPreviousAsset,
                onNextAsset = onNextAsset,
            )
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
private fun OriginalPhoto(
    asset: MediaAsset,
    serverRoot: ServerRoot,
    onToggleChrome: () -> Unit,
    onOpenInfo: () -> Unit,
    onPreviousAsset: (() -> Unit)?,
    onNextAsset: (() -> Unit)?,
) {
    var scale by remember(asset.id) { mutableFloatStateOf(1f) }
    var horizontalDrag by remember(asset.id) { mutableFloatStateOf(0f) }
    var verticalDrag by remember(asset.id) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 82.dp, bottom = 142.dp)
            .background(Color(0xFFF7F8FA))
            .pointerInput(asset.id, scale) {
                if (scale == 1f) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var previousPosition = down.position
                        var multiTouch = false
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.count { it.pressed } > 1) multiTouch = true
                            event.changes.firstOrNull { it.id == down.id }?.let { change ->
                                val delta = change.position - previousPosition
                                horizontalDrag += delta.x
                                verticalDrag += delta.y
                                previousPosition = change.position
                            }
                            pressed = event.changes.any { it.pressed }
                        }
                        if (!multiTouch) {
                            when {
                                verticalDrag < -72f && abs(verticalDrag) > abs(horizontalDrag) -> onOpenInfo()
                                horizontalDrag > 88f && abs(horizontalDrag) > abs(verticalDrag) -> onPreviousAsset?.invoke()
                                horizontalDrag < -88f && abs(horizontalDrag) > abs(verticalDrag) -> onNextAsset?.invoke()
                            }
                        }
                        horizontalDrag = 0f
                        verticalDrag = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = asset.thumbnailUrl(serverRoot, ThumbnailSize.MD),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alpha = 0.46f,
        )
        AsyncImage(
            model = asset.displayPhotoUrl(serverRoot),
            contentDescription = asset.fileName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .pointerInput(asset.id) {
                    detectTapGestures(onTap = { onToggleChrome() })
                }
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
private fun ViewerBottomChrome(
    asset: MediaAsset,
    state: AssetViewerUiState,
    stripAssets: List<MediaAsset>,
    serverRoot: ServerRoot,
    onSelectAsset: (String) -> Unit,
    onShare: () -> Unit,
    onFavoriteChanged: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenEditor: () -> Unit,
    onMoveToTrash: () -> Unit,
) {
    val effectiveAssets = remember(asset.id, stripAssets) {
        val loaded = stripAssets.distinctBy(MediaAsset::id)
        if (loaded.any { it.id == asset.id }) loaded else listOf(asset)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .android16Glass(
                cornerRadius = 0.dp,
                strength = 0.54f,
                showBorder = false,
            )
            .background(Color.White.copy(alpha = 0.82f))
            .navigationBarsPadding(),
    ) {
        ViewerFilmstrip(
            assets = effectiveAssets,
            selectedAssetId = asset.id,
            serverRoot = serverRoot,
            onSelectAsset = onSelectAsset,
        )
        HorizontalDivider(color = PhotoTubeColors.Ink.copy(alpha = 0.06f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerActionButton(Icons.Outlined.Share, "分享", onShare, enabled = asset.canReadOriginal())
            ViewerActionButton(
                imageVector = if (asset.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (asset.favorite) "取消收藏" else "收藏",
                onClick = onFavoriteChanged,
                enabled = !state.isSavingFavorite && asset.state != AssetState.TRASHED,
                tint = if (asset.favorite) PhotoTubeColors.Alert else PhotoTubeColors.Ink,
                loading = state.isSavingFavorite,
            )
            ViewerActionButton(Icons.Outlined.Info, "照片信息", onOpenInfo)
            ViewerActionButton(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "编辑照片",
                onClick = onOpenEditor,
                enabled = asset.isEditablePhoto(),
            )
            ViewerActionButton(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "移入回收站",
                onClick = onMoveToTrash,
                enabled = asset.state != AssetState.TRASHED && !state.isSavingMutation,
            )
        }
    }
}

@Composable
private fun ViewerFilmstrip(
    assets: List<MediaAsset>,
    selectedAssetId: String,
    serverRoot: ServerRoot,
    onSelectAsset: (String) -> Unit,
) {
    val selectedIndex = assets.indexOfFirst { it.id == selectedAssetId }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, assets.size) {
        listState.animateScrollToItem((selectedIndex - 4).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(assets, key = { _, item -> item.id }) { _, item ->
            val selected = item.id == selectedAssetId
            AsyncImage(
                model = item.thumbnailUrl(serverRoot, ThumbnailSize.SM),
                contentDescription = if (selected) "当前照片" else item.fileName,
                modifier = Modifier
                    .width(if (selected) 36.dp else 30.dp)
                    .height(if (selected) 46.dp else 38.dp)
                    .graphicsLayer(alpha = if (selected) 1f else 0.56f)
                    .clip(RoundedCornerShape(if (selected) 8.dp else 6.dp))
                    .clickable(enabled = !selected) { onSelectAsset(item.id) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ViewerActionButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = PhotoTubeColors.Ink,
    loading: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = if (enabled) tint else PhotoTubeColors.Muted.copy(alpha = 0.44f),
            )
        }
    }
}

private val viewerDateFormatter = DateTimeFormatter.ofPattern(
    "yyyy年M月d日 · HH:mm",
    Locale.SIMPLIFIED_CHINESE,
)

private fun viewerDateLabel(value: String): String = try {
    OffsetDateTime.parse(value).format(viewerDateFormatter)
} catch (_: DateTimeParseException) {
    value
}

private fun viewerSourceLabel(asset: MediaAsset): String = asset.title
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: asset.relativePath
        .substringBeforeLast('/', missingDelimiterValue = "")
        .substringAfterLast('/')
        .ifBlank { assetKindLabel(asset.kind) }

private fun shareAsset(context: Context, asset: MediaAsset, serverRoot: ServerRoot) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, asset.title ?: asset.fileName)
        putExtra(Intent.EXTRA_TEXT, asset.originalUrl(serverRoot))
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享照片"))
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetInfoSheet(asset: MediaAsset, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF7F8FA),
    ) {
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
    onOpenTags: () -> Unit,
    onOpenInfo: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenTags,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("管理标签")
                }
                FilledTonalButton(
                    onClick = onOpenInfo,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("照片信息")
                }
            }
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
            messageColor = PhotoTubeColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text("重试") }
    }
}
