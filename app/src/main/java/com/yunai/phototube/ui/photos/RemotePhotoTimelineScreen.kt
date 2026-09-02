package com.yunai.phototube.ui.photos

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.data.timeline.TimelineGranularity
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.data.folder.FolderRepository
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.ui.components.AlbumTopBar
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.components.FloatingAlbumDock
import com.yunai.phototube.ui.components.android16Glass
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import dev.chrisbanes.haze.hazeSource
import java.time.OffsetDateTime

@Composable
fun RemotePhotoTimelineRoute(
    repository: TimelineRepository,
    folderRepository: FolderRepository,
    tagRepository: TagRepository,
    onOpenCollections: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAsset: (String, List<MediaAsset>) -> Unit,
    tagLibraryRevision: Int,
    deletedTagId: Long?,
    refreshRevision: Int,
    modifier: Modifier = Modifier,
) {
    val timelineViewModel: TimelineViewModel = viewModel(factory = TimelineViewModel.factory(repository))
    val filterViewModel: AdvancedFilterViewModel = viewModel(
        key = "advanced-filter",
        factory = AdvancedFilterViewModel.factory(folderRepository, tagRepository),
    )
    val summaryState by timelineViewModel.summaryState.collectAsStateWithLifecycle()
    val granularity by timelineViewModel.granularity.collectAsStateWithLifecycle()
    val activeFilter by timelineViewModel.filter.collectAsStateWithLifecycle()
    val filterState by filterViewModel.state.collectAsStateWithLifecycle()
    val assets = timelineViewModel.assets.collectAsLazyPagingItems()
    var showAdvancedFilter by remember { mutableStateOf(false) }
    val openAdvancedFilter = {
        filterViewModel.begin(activeFilter)
        showAdvancedFilter = true
    }
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) {
            assets.refresh()
            timelineViewModel.refreshSummary()
        }
    }
    LaunchedEffect(tagLibraryRevision) {
        if (tagLibraryRevision > 0) {
            filterViewModel.onTagLibraryChanged(deletedTagId)
            deletedTagId?.let(timelineViewModel::removeDeletedTag)
        }
    }
    RemotePhotoTimelineScreen(
        assets = assets,
        serverRoot = timelineViewModel.serverRoot,
        summaryState = summaryState,
        granularity = granularity,
        onGranularityChanged = timelineViewModel::setGranularity,
        onRetrySummary = timelineViewModel::refreshSummary,
        onOpenCollections = onOpenCollections,
        onOpenAccount = onOpenAccount,
        onOpenSearch = onOpenSearch,
        onOpenFilter = openAdvancedFilter,
        activeFilter = activeFilter,
        onClearFilter = { timelineViewModel.applyFilter(AssetFilter()) },
        onOpenAsset = { assetId ->
            onOpenAsset(assetId, assets.itemSnapshotList.items)
        },
        modifier = modifier,
    )
    if (showAdvancedFilter) {
        AdvancedFilterSheet(
            state = filterState,
            model = filterViewModel,
            onDismiss = { showAdvancedFilter = false },
            onApply = {
                filterViewModel.buildFilter()?.let { filter ->
                    timelineViewModel.applyFilter(filter)
                    showAdvancedFilter = false
                }
            },
        )
    }
}

@Composable
private fun RemotePhotoTimelineScreen(
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    summaryState: TimelineSummaryState,
    granularity: TimelineGranularity,
    onGranularityChanged: (TimelineGranularity) -> Unit,
    onRetrySummary: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    activeFilter: AssetFilter,
    onClearFilter: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberAndroid16HazeState()
    val listState = rememberLazyListState()
    var selectedDockItem by remember { mutableIntStateOf(0) }
    val loadedGroups = remember(assets.itemSnapshotList.items, granularity) {
        groupLoadedTimelineAssets(assets.itemSnapshotList.items, granularity)
    }
    LaunchedEffect(granularity) { listState.scrollToItem(0) }

    Android16HazeProvider(state = hazeState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PhotoTubeColors.Background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = PhotoTubeDimens.ScreenPadding,
                    end = PhotoTubeDimens.ScreenPadding,
                    bottom = 116.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
            ) {
                item {
                    Column(Modifier.statusBarsPadding()) {
                        Spacer(Modifier.height(20.dp))
                        AlbumTopBar(
                            onProfileClick = onOpenAccount,
                            onSearchClick = onOpenSearch,
                            onFilterClick = onOpenFilter,
                            filterActive = activeFilter.toAdvancedFilterDraft().activeDimensionCount > 0,
                        )
                        Spacer(Modifier.height(16.dp))
                        RemoteTimelineHeading(
                            assets = assets,
                            granularity = granularity,
                            onGranularityChanged = onGranularityChanged,
                        )
                        val activeCount = activeFilter.toAdvancedFilterDraft().activeDimensionCount
                        if (activeCount > 0) {
                            ActiveFilterSummary(activeCount, onOpenFilter, onClearFilter)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                when (val refresh = assets.loadState.refresh) {
                    is LoadState.Loading -> item { LoadingState() }
                    is LoadState.Error -> item {
                        TimelineFailureState(refresh.error.toTimelineError(), assets::retry)
                    }
                    is LoadState.NotLoading -> {
                        if (assets.itemCount == 0) {
                            item { EmptyTimelineState() }
                        } else {
                            loadedGroups.forEach { group ->
                                item(key = "header-${granularity.name}-${group.key}") {
                                    TimelineGroupHeader(group)
                                }
                                items(
                                    items = group.rows,
                                    key = { row -> "${granularity.name}-${group.key}-${row.indices.first()}" },
                                ) { row ->
                                    EditorialPhotoRow(
                                        row = row,
                                        assets = assets,
                                        serverRoot = serverRoot,
                                        onOpenAsset = onOpenAsset,
                                    )
                                }
                            }
                        }
                    }
                }

                when (val append = assets.loadState.append) {
                    is LoadState.Loading -> item { AppendLoadingState() }
                    is LoadState.Error -> item {
                        TimelineFailureState(append.error.toTimelineError(), assets::retry, compact = true)
                    }
                    is LoadState.NotLoading -> Unit
                }

                if (summaryState is TimelineSummaryState.Error) {
                    item {
                        TimelineFailureState(
                            error = summaryState.error,
                            onRetry = onRetrySummary,
                            compact = true,
                        )
                    }
                }
            }

            FloatingAlbumDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedItem = selectedDockItem,
                onItemClick = { index ->
                    selectedDockItem = index
                    if (index == 1) onOpenCollections()
                },
            )
        }
    }
}

@Composable
private fun ActiveFilterSummary(count: Int, onOpenFilter: () -> Unit, onClearFilter: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenFilter)
            .padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("已启用 $count 类筛选", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClearFilter) { Text("清除") }
    }
}

@Composable
private fun RemoteTimelineHeading(
    assets: LazyPagingItems<MediaAsset>,
    granularity: TimelineGranularity,
    onGranularityChanged: (TimelineGranularity) -> Unit,
) {
    val firstAsset = assets.itemSnapshotList.items.firstOrNull()
    val monthLabel = firstAsset?.takenAt?.let { value ->
        runCatching {
            val date = OffsetDateTime.parse(value)
            "${date.year}年${date.monthValue}月"
        }.getOrNull()
    } ?: "时间轴"
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "照片",
                color = PhotoTubeColors.Ink,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = monthLabel,
                color = PhotoTubeColors.Muted,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        TimelineGranularityPicker(
            selected = granularity,
            onSelected = onGranularityChanged,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun TimelineGranularityPicker(
    selected: TimelineGranularity,
    onSelected: (TimelineGranularity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        TimelineGranularity.YEAR to "年",
        TimelineGranularity.MONTH to "月",
        TimelineGranularity.DAY to "日",
    )
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val controlWidth = 144.dp
    val touchHeight = 48.dp
    val visualHeight = 40.dp
    val inset = 2.dp
    val segmentWidth = controlWidth / 3
    val indicatorOffset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
        label = "时间粒度滑块",
    )
    Box(
        modifier = modifier
            .size(controlWidth, touchHeight),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(controlWidth, visualHeight)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.02f),
                    spotColor = Color.Black.copy(alpha = 0.035f),
                )
                .android16Glass(cornerRadius = 20.dp, strength = 0.78f, showBorder = false)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.50f),
                        1f to Color.White.copy(alpha = 0.34f),
                    ),
                    RoundedCornerShape(20.dp),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = indicatorOffset + inset)
                .size(segmentWidth - inset * 2, visualHeight - inset * 2)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color.Black.copy(alpha = 0.025f),
                    spotColor = Color.Black.copy(alpha = 0.04f),
                )
                .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(18.dp)),
        )
        Row {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(segmentWidth, touchHeight)
                        .clip(RoundedCornerShape(24.dp))
                        .semantics {
                            contentDescription = "按${label}分组"
                            this.selected = isSelected
                        }
                        .clickable(role = Role.Tab) { onSelected(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (isSelected) PhotoTubeColors.Ink else PhotoTubeColors.Muted,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineGroupHeader(group: LoadedTimelineGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.title,
            color = PhotoTubeColors.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        group.relativeLabel?.let { relative ->
            Text(
                relative,
                color = PhotoTubeColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun EditorialPhotoRow(
    row: EditorialTimelineRow,
    assets: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    onOpenAsset: (String) -> Unit,
) {
    val height = when (row.style) {
        EditorialRowStyle.FEATURED -> 142.dp
        EditorialRowStyle.TRIPLE -> 144.dp
        EditorialRowStyle.DOUBLE -> 128.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(PhotoTubeDimens.GridGap),
    ) {
        if (row.style == EditorialRowStyle.FEATURED && row.indices.size == 1) {
            val index = row.indices.single()
            RemotePhotoCell(
                asset = assets[index],
                serverRoot = serverRoot,
                onOpenAsset = onOpenAsset,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val slotCount = when (row.style) {
                EditorialRowStyle.TRIPLE -> 3
                EditorialRowStyle.FEATURED, EditorialRowStyle.DOUBLE -> 2
            }
            repeat(slotCount) { column ->
                val index = row.indices.getOrNull(column)
                val weight = if (row.style == EditorialRowStyle.FEATURED && column == 0) 1.72f else 1f
                if (index != null) {
                    RemotePhotoCell(
                        asset = assets[index],
                        serverRoot = serverRoot,
                        onOpenAsset = onOpenAsset,
                        modifier = Modifier.weight(weight).fillMaxSize(),
                    )
                } else {
                    Spacer(Modifier.weight(weight))
                }
            }
        }
    }
}

@Composable
internal fun RemotePhotoCell(
    asset: MediaAsset?,
    serverRoot: ServerRoot,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PhotoTubeDimens.PhotoRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFFE9EBEF)),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnailUrl = asset?.thumbnailUrl(serverRoot, ThumbnailSize.MD)
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = asset.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFFE9EBEF)),
                error = ColorPainter(Color(0xFFDADDE3)),
            )
        } else {
            Text(
                text = if (asset?.state == AssetState.PROCESSING) "处理中" else "暂无缩略图",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
            )
        }
        if (asset?.kind == AssetKind.VIDEO) {
            Text(
                text = "视频",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (asset != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onOpenAsset(asset.id) },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = PhotoTubeColors.Ink)
    }
}

@Composable
private fun AppendLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = PhotoTubeColors.Ink, strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyTimelineState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有照片", style = MaterialTheme.typography.titleLarge, color = PhotoTubeColors.Ink)
        Spacer(Modifier.height(6.dp))
        Text("请先在 PhotoTube 中配置路径相册并手动扫描", color = PhotoTubeColors.Muted)
    }
}

@Composable
private fun TimelineFailureState(
    error: TimelineError,
    onRetry: () -> Unit,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 16.dp else 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(
            message = error.message,
            logId = error.logId,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error.retryable) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PhotoTubeColors.Ink),
            ) {
                Text("重试")
            }
        }
    }
}
