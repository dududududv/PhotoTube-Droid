package com.yunai.phototube.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.ui.photos.RemotePhotoCell
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens

@Composable
fun SearchRoute(
    repository: TimelineRepository,
    refreshRevision: Int,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: SearchViewModel = viewModel(
        key = "asset-search",
        factory = SearchViewModel.factory(repository),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val assets = model.assets.collectAsLazyPagingItems()
    BackHandler(onBack = onBack)
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) assets.refresh()
    }
    SearchScreen(
        state = state,
        assets = assets,
        serverRoot = model.serverRoot,
        onQueryChanged = model::updateQuery,
        onKindChanged = model::setKind,
        onToggleFavorite = model::toggleFavorite,
        onRatingChanged = model::setRating,
        onSearch = model::search,
        onBack = onBack,
        onOpenAsset = onOpenAsset,
        modifier = modifier,
    )
}

@Composable
private fun SearchScreen(
    state: SearchUiState,
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: com.yunai.phototube.data.connection.ServerRoot,
    onQueryChanged: (String) -> Unit,
    onKindChanged: (AssetKind?) -> Unit,
    onToggleFavorite: () -> Unit,
    onRatingChanged: (Int?) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.submittedKeyword) {
        if (state.submittedKeyword == null) focusRequester.requestFocus()
    }
    Column(
        modifier = modifier.fillMaxSize().background(PhotoTubeColors.Background).statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回照片")
            }
            TextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
                    errorContainerColor = androidx.compose.ui.graphics.Color.White,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                placeholder = { Text("搜索文件名") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                isError = state.validationMessage != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onSearch()
                }),
            )
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSearch()
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("搜索") }
        }
        Text(
            "仅按文件名做大小写不敏感的字面子串匹配，不搜索路径、EXIF、OCR 或语义内容。",
            color = PhotoTubeColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        state.validationMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FlatFilterChip(state.kind == AssetKind.PHOTO, { onKindChanged(AssetKind.PHOTO) }, "照片") }
            item { FlatFilterChip(state.kind == AssetKind.VIDEO, { onKindChanged(AssetKind.VIDEO) }, "视频") }
            item { FlatFilterChip(state.favoriteOnly, onToggleFavorite, "收藏") }
            items((1..5).toList()) { rating ->
                FlatFilterChip(state.rating == rating, { onRatingChanged(rating) }, "$rating 星")
            }
        }
        val statusText = when {
            state.submittedKeyword == null -> "输入至少 3 个字符开始搜索"
            state.isLoadingSummary -> "正在统计结果…"
            state.totalCount != null -> "“${state.submittedKeyword}” · ${state.totalCount} 项"
            else -> "“${state.submittedKeyword}”"
        }
        Text(
            statusText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        state.summaryError?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        SearchResults(assets, serverRoot, state.submittedKeyword != null, onOpenAsset)
    }
}

@Composable
private fun FlatFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        border = null,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = androidx.compose.ui.graphics.Color.White,
            selectedContainerColor = PhotoTubeColors.Ink,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
        ),
    )
}

@Composable
private fun SearchResults(
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: com.yunai.phototube.data.connection.ServerRoot,
    submitted: Boolean,
    onOpenAsset: (String) -> Unit,
) {
    when (val refresh = assets.loadState.refresh) {
        is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LoadState.Error -> SearchFailure(refresh.error, assets::retry)
        is LoadState.NotLoading -> {
            if (!submitted || assets.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (submitted) "没有匹配的文件" else "", color = PhotoTubeColors.Muted)
                }
            } else {
                val rowCount = (assets.itemCount + SEARCH_COLUMNS - 1) / SEARCH_COLUMNS
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = PhotoTubeDimens.ScreenPadding,
                        end = PhotoTubeDimens.ScreenPadding,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
                ) {
                    items(count = rowCount, key = { "search-row-$it" }) { rowIndex ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(132.dp),
                            horizontalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
                        ) {
                            repeat(SEARCH_COLUMNS) { column ->
                                val index = rowIndex * SEARCH_COLUMNS + column
                                if (index < assets.itemCount) {
                                    RemotePhotoCell(
                                        asset = assets[index],
                                        serverRoot = serverRoot,
                                        onOpenAsset = onOpenAsset,
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                    )
                                } else Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    when (val append = assets.loadState.append) {
                        is LoadState.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                        is LoadState.Error -> item {
                            SearchFailure(append.error, assets::retry)
                        }
                        is LoadState.NotLoading -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFailure(failure: Throwable, onRetry: () -> Unit) {
    val error = failure.toTimelineError()
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("重试") }
    }
}

private const val SEARCH_COLUMNS = 3
