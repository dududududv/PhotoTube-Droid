package com.yunai.phototube.ui.creation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.ui.components.AlbumTopBar
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.photos.RemotePhotoCell
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import dev.chrisbanes.haze.hazeSource

@Composable
fun CreationRoute(
    repository: TimelineRepository,
    refreshRevision: Int,
    onOpenPhotos: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: CreationViewModel = viewModel(
        key = "creation",
        factory = CreationViewModel.factory(repository),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val assets = model.assets.collectAsLazyPagingItems()

    BackHandler(onBack = onOpenPhotos)
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) assets.refresh()
    }

    CreationScreen(
        state = state,
        assets = assets,
        serverRoot = model.serverRoot,
        onOpenPhotos = onOpenPhotos,
        onOpenCollections = onOpenCollections,
        onOpenSearch = onOpenSearch,
        onOpenAsset = onOpenAsset,
        onToggleFavoriteOnly = model::toggleFavoriteOnly,
        onToggleGridDensity = model::toggleGridDensity,
        modifier = modifier,
    )
}

@Composable
private fun CreationScreen(
    state: CreationUiState,
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    onOpenPhotos: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onToggleFavoriteOnly: () -> Unit,
    onToggleGridDensity: () -> Unit,
    modifier: Modifier,
) {
    val hazeState = rememberAndroid16HazeState()
    Android16HazeProvider(state = hazeState) {
        Box(modifier.fillMaxSize().background(PhotoTubeColors.Background)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.columns),
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = PhotoTubeDimens.ScreenPadding,
                    end = PhotoTubeDimens.ScreenPadding,
                    bottom = 116.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
                verticalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CreationHeader(
                        favoriteOnly = state.favoriteOnly,
                        onOpenCollections = onOpenCollections,
                        onOpenSearch = onOpenSearch,
                        onToggleFavoriteOnly = onToggleFavoriteOnly,
                    )
                }

                when (val refresh = assets.loadState.refresh) {
                    is LoadState.Loading -> fullWidthItem { CreationLoading() }
                    is LoadState.Error -> fullWidthItem {
                        CreationFailure(refresh.error, assets::retry)
                    }
                    is LoadState.NotLoading -> {
                        if (assets.itemCount == 0) {
                            fullWidthItem {
                                CreationEmpty(favoriteOnly = state.favoriteOnly)
                            }
                        } else {
                            items(
                                count = assets.itemCount,
                                key = { assets[it]?.id ?: "creation-placeholder-$it" },
                            ) { index ->
                                CreationAssetTile(
                                    asset = assets[index],
                                    serverRoot = serverRoot,
                                    onOpenAsset = onOpenAsset,
                                )
                            }
                        }
                    }
                }

                when (val append = assets.loadState.append) {
                    is LoadState.Loading -> fullWidthItem { CreationLoading(compact = true) }
                    is LoadState.Error -> fullWidthItem {
                        CreationFailure(append.error, assets::retry)
                    }
                    is LoadState.NotLoading -> Unit
                }
            }

        }
    }
}

@Composable
private fun CreationHeader(
    favoriteOnly: Boolean,
    onOpenCollections: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleFavoriteOnly: () -> Unit,
) {
    Column(Modifier.statusBarsPadding()) {
        Spacer(Modifier.height(20.dp))
        AlbumTopBar(
            onProfileClick = onOpenCollections,
            onSearchClick = onOpenSearch,
            onFilterClick = onToggleFavoriteOnly,
            filterActive = favoriteOnly,
        )
        Spacer(Modifier.height(24.dp))
        Text("创作", style = MaterialTheme.typography.displayLarge, color = PhotoTubeColors.Ink)
        Text(
            "选择照片，在查看器中开始非破坏性编辑",
            color = PhotoTubeColors.Muted,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "保存会创建新的不可变版本，不改写 NAS 原文件。当前支持可浏览的 JPG、JPEG 与 PNG。",
            color = PhotoTubeColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        FilterChip(
            selected = favoriteOnly,
            onClick = onToggleFavoriteOnly,
            label = { Text(if (favoriteOnly) "仅显示收藏" else "全部照片") },
            modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            border = null,
            colors = FilterChipDefaults.filterChipColors(
                containerColor = Color.White,
                selectedContainerColor = PhotoTubeColors.Ink,
                selectedLabelColor = Color.White,
            ),
        )
    }
}

@Composable
private fun CreationAssetTile(
    asset: MediaAsset?,
    serverRoot: ServerRoot,
    onOpenAsset: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        RemotePhotoCell(
            asset = asset,
            serverRoot = serverRoot,
            onOpenAsset = onOpenAsset,
            modifier = Modifier.fillMaxSize(),
        )
        asset?.let {
            val label = when {
                it.isEditablePhoto() -> "可编辑"
                it.state == AssetState.PROCESSING -> "处理中"
                it.state == AssetState.OFFLINE -> "离线"
                else -> "仅查看"
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun CreationLoading(compact: Boolean = false) {
    Box(
        Modifier.fillMaxWidth().height(if (compact) 64.dp else 220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compact) 24.dp else 40.dp),
            strokeWidth = if (compact) 2.dp else 4.dp,
        )
    }
}

@Composable
private fun CreationEmpty(favoriteOnly: Boolean) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Text(
            if (favoriteOnly) "收藏中没有可选择的照片" else "图库中还没有照片",
            color = PhotoTubeColors.Muted,
        )
    }
}

@Composable
private fun CreationFailure(failure: Throwable, onRetry: () -> Unit) {
    val error = failure.toTimelineError()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("重试") }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidthItem(
    content: @Composable () -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
