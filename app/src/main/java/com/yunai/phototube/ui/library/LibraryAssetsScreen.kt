package com.yunai.phototube.ui.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens

@Composable
fun LibraryAssetsRoute(
    mode: LibraryCollectionMode,
    timelineRepository: TimelineRepository,
    sessionRepository: SessionRepository,
    refreshRevision: Int,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: LibraryAssetsViewModel = viewModel(
        key = "library-assets-${mode.name}",
        factory = LibraryAssetsViewModel.factory(mode, timelineRepository, sessionRepository),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val assets = model.assets.collectAsLazyPagingItems()
    DisposableEffect(model, mode) {
        model.onRouteEntered()
        onDispose(model::onRouteLeft)
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) assets.refresh()
    }
    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) {
            model.consumeCloseRequest()
            onBack()
        }
    }
    val refreshFailure = (assets.loadState.refresh as? LoadState.Error)?.error as? ApiFailure
    LaunchedEffect(refreshFailure?.error?.code) {
        if (
            mode == LibraryCollectionMode.PRIVATE &&
            refreshFailure?.error?.code == "PRIVATE_ACCESS_REQUIRED"
        ) {
            model.expirePrivateAccess()
        }
    }

    if (mode == LibraryCollectionMode.PRIVATE && !state.isUnlocked) {
        PrivateAccessGate(state, model::updatePassword, model::unlock, onBack, modifier)
    } else {
        LibraryAssetsScreen(
            title = if (mode == LibraryCollectionMode.ARCHIVED) "已归档" else "私密空间",
            assets = assets,
            serverRoot = model.serverRoot,
            isPrivate = mode == LibraryCollectionMode.PRIVATE,
            isLocking = state.isLocking,
            expiresAt = state.expiresAt,
            onBack = onBack,
            onLock = model::lock,
            onOpenAsset = onOpenAsset,
            modifier = modifier,
        )
    }
}

@Composable
private fun PrivateAccessGate(
    state: LibraryAssetsUiState,
    onPasswordChanged: (String) -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Spacer(Modifier.height(56.dp))
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(18.dp))
        Text("解锁私密空间", style = MaterialTheme.typography.headlineLarge)
        Text(
            "请输入当前账号口令。服务端授权最长 15 分钟，客户端倒计时不作为授权依据。",
            color = PhotoTubeColors.Muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("账号口令") },
        )
        state.error?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
        }
        Button(
            onClick = onUnlock,
            enabled = state.password.isNotEmpty() && !state.isUnlocking && !state.isCheckingAccess,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (state.isUnlocking || state.isCheckingAccess) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("解锁")
            }
        }
    }
}

@Composable
private fun LibraryAssetsScreen(
    title: String,
    assets: androidx.paging.compose.LazyPagingItems<com.yunai.phototube.data.timeline.MediaAsset>,
    serverRoot: com.yunai.phototube.data.connection.ServerRoot,
    isPrivate: Boolean,
    isLocking: Boolean,
    expiresAt: String?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onOpenAsset: (String) -> Unit,
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
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (isPrivate) {
                IconButton(onClick = onLock, enabled = !isLocking) {
                    if (isLocking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Lock, contentDescription = "立即锁定")
                }
            }
        }
        if (isPrivate && expiresAt != null) {
            Text(
                "服务端授权截止：$expiresAt",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
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
                        Text(if (isPrivate) "私密空间为空" else "没有已归档资产", color = PhotoTubeColors.Muted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
                        verticalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
                    ) {
                        items(count = assets.itemCount, key = { assets[it]?.id ?: "placeholder-$it" }) { index ->
                            val asset = assets[index]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(122.dp)
                                    .clip(RoundedCornerShape(PhotoTubeDimens.PhotoRadius))
                                    .background(Color(0xFFE9EBEF))
                                    .clickable(enabled = asset != null) { asset?.let { onOpenAsset(it.id) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                val url = asset?.thumbnailUrl(serverRoot, ThumbnailSize.MD)
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = asset.fileName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        placeholder = ColorPainter(Color(0xFFE9EBEF)),
                                    )
                                } else {
                                    Text(
                                        if (asset?.state == AssetState.PROCESSING) "处理中" else "暂无缩略图",
                                        color = PhotoTubeColors.Muted,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        if (assets.loadState.append is LoadState.Loading) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
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
