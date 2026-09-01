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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.IntOffset
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
import com.yunai.phototube.data.home.HomeRepository
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
import com.yunai.phototube.ui.home.HomeFeedScreen
import com.yunai.phototube.ui.home.HomeFeedViewModel
import dev.chrisbanes.haze.hazeSource
import java.time.OffsetDateTime

@Composable
fun RemotePhotoTimelineRoute(
    repository: TimelineRepository,
    homeRepository: HomeRepository,
    folderRepository: FolderRepository,
    tagRepository: TagRepository,
    onOpenCollections: () -> Unit,
    onOpenCreation: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArchived: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenXmpExport: () -> Unit,
    onOpenSystemStatus: () -> Unit,
    onOpenMemoryExclusions: () -> Unit,
    onOpenTagManagement: () -> Unit,
    onOpenAccount: () -> Unit,
    tagLibraryRevision: Int,
    deletedTagId: Long?,
    refreshRevision: Int,
    modifier: Modifier = Modifier,
) {
    val timelineViewModel: TimelineViewModel = viewModel(factory = TimelineViewModel.factory(repository))
    val homeViewModel: HomeFeedViewModel = viewModel(
        key = "home-feed",
        factory = HomeFeedViewModel.factory(homeRepository),
    )
    val filterViewModel: AdvancedFilterViewModel = viewModel(
        key = "advanced-filter",
        factory = AdvancedFilterViewModel.factory(folderRepository, tagRepository),
    )
    val summaryState by timelineViewModel.summaryState.collectAsStateWithLifecycle()
    val granularity by timelineViewModel.granularity.collectAsStateWithLifecycle()
    val layoutMode by timelineViewModel.layoutMode.collectAsStateWithLifecycle()
    val activeFilter by timelineViewModel.filter.collectAsStateWithLifecycle()
    val filterState by filterViewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val assets = timelineViewModel.assets.collectAsLazyPagingItems()
    var showLibraryMenu by remember { mutableStateOf(false) }
    var showAdvancedFilter by remember { mutableStateOf(false) }
    val openAdvancedFilter = {
        filterViewModel.begin(activeFilter)
        showAdvancedFilter = true
    }
    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) {
            assets.refresh()
            timelineViewModel.refreshSummary()
            homeViewModel.refresh()
        }
    }
    LaunchedEffect(tagLibraryRevision) {
        if (tagLibraryRevision > 0) {
            filterViewModel.onTagLibraryChanged(deletedTagId)
            deletedTagId?.let(timelineViewModel::removeDeletedTag)
        }
    }
    if (layoutMode == PhotoLayoutMode.TIMELINE) {
        RemotePhotoTimelineScreen(
            assets = assets,
            serverRoot = timelineViewModel.serverRoot,
            summaryState = summaryState,
            granularity = granularity,
            onGranularityChanged = timelineViewModel::setGranularity,
            onRetrySummary = timelineViewModel::refreshSummary,
            onOpenCollections = onOpenCollections,
            onOpenCreation = onOpenCreation,
            onOpenSearch = onOpenSearch,
            onOpenFilter = openAdvancedFilter,
            activeFilter = activeFilter,
            onClearFilter = { timelineViewModel.applyFilter(AssetFilter()) },
            onOpenAsset = onOpenAsset,
            onSwitchLayout = timelineViewModel::toggleLayoutMode,
            onOpenLibraryMenu = { showLibraryMenu = true },
            modifier = modifier,
        )
    } else {
        HomeFeedScreen(
            state = homeState,
            serverRoot = homeViewModel.serverRoot,
            onRetry = homeViewModel::refresh,
            onOpenSearch = onOpenSearch,
            onOpenFilter = {
                timelineViewModel.toggleLayoutMode()
                openAdvancedFilter()
            },
            onOpenCollections = onOpenCollections,
            onOpenCreation = onOpenCreation,
            onOpenAsset = onOpenAsset,
            onOpenAlbum = onOpenAlbum,
            onOpenJobs = onOpenJobs,
            onSwitchLayout = timelineViewModel::toggleLayoutMode,
            onOpenLibraryMenu = { showLibraryMenu = true },
            modifier = modifier,
        )
    }
    if (showLibraryMenu) {
        LibraryMenuSheet(
            onDismiss = { showLibraryMenu = false },
            onOpenArchived = {
                showLibraryMenu = false
                onOpenArchived()
            },
            onOpenPrivate = {
                showLibraryMenu = false
                onOpenPrivate()
            },
            onOpenTrash = {
                showLibraryMenu = false
                onOpenTrash()
            },
            onOpenJobs = {
                showLibraryMenu = false
                onOpenJobs()
            },
            onOpenDuplicates = {
                showLibraryMenu = false
                onOpenDuplicates()
            },
            onOpenXmpExport = {
                showLibraryMenu = false
                onOpenXmpExport()
            },
            onOpenSystemStatus = {
                showLibraryMenu = false
                onOpenSystemStatus()
            },
            onOpenMemoryExclusions = {
                showLibraryMenu = false
                onOpenMemoryExclusions()
            },
            onOpenTagManagement = {
                showLibraryMenu = false
                onOpenTagManagement()
            },
            onOpenAccount = {
                showLibraryMenu = false
                onOpenAccount()
            },
        )
    }
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
    onOpenCreation: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    activeFilter: AssetFilter,
    onClearFilter: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onSwitchLayout: () -> Unit,
    onOpenLibraryMenu: () -> Unit,
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
                            onProfileClick = onOpenCollections,
                            onSearchClick = onOpenSearch,
                            onFilterClick = onOpenFilter,
                            filterActive = activeFilter.toAdvancedFilterDraft().activeDimensionCount > 0,
                        )
                        Spacer(Modifier.height(30.dp))
                        RemoteTimelineHeading(
                            assets = assets,
                            granularity = granularity,
                            onGranularityChanged = onGranularityChanged,
                        )
                        val activeCount = activeFilter.toAdvancedFilterDraft().activeDimensionCount
                        if (activeCount > 0) {
                            ActiveFilterSummary(activeCount, onOpenFilter, onClearFilter)
                        }
                        Spacer(Modifier.height(24.dp))
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
                    when (index) {
                        1 -> onOpenCollections()
                        2 -> onOpenCreation()
                    }
                },
                onLayoutClick = onSwitchLayout,
                onMenuClick = onOpenLibraryMenu,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryMenuSheet(
    onDismiss: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenXmpExport: () -> Unit,
    onOpenSystemStatus: () -> Unit,
    onOpenMemoryExclusions: () -> Unit,
    onOpenTagManagement: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("照片库", style = MaterialTheme.typography.headlineSmall)
            FilledTonalButton(onClick = onOpenArchived, modifier = Modifier.fillMaxWidth()) {
                Text("已归档")
            }
            FilledTonalButton(onClick = onOpenPrivate, modifier = Modifier.fillMaxWidth()) {
                Text("私密空间")
            }
            FilledTonalButton(onClick = onOpenTrash, modifier = Modifier.fillMaxWidth()) {
                Text("回收站")
            }
            FilledTonalButton(onClick = onOpenJobs, modifier = Modifier.fillMaxWidth()) {
                Text("任务中心")
            }
            FilledTonalButton(onClick = onOpenDuplicates, modifier = Modifier.fillMaxWidth()) {
                Text("完全重复项")
            }
            FilledTonalButton(onClick = onOpenXmpExport, modifier = Modifier.fillMaxWidth()) {
                Text("照片信息备份")
            }
            FilledTonalButton(onClick = onOpenMemoryExclusions, modifier = Modifier.fillMaxWidth()) {
                Text("回忆屏蔽")
            }
            FilledTonalButton(onClick = onOpenTagManagement, modifier = Modifier.fillMaxWidth()) {
                Text("标签管理")
            }
            FilledTonalButton(onClick = onOpenSystemStatus, modifier = Modifier.fillMaxWidth()) {
                Text("系统状态与缓存")
            }
            FilledTonalButton(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                Text("账号与服务器")
            }
        }
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
    Column {
        Text("照片", style = MaterialTheme.typography.displayLarge, color = PhotoTubeColors.Ink)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthLabel,
                color = PhotoTubeColors.Muted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TimelineGranularityPicker(
                selected = granularity,
                onSelected = onGranularityChanged,
            )
        }
    }
}

@Composable
private fun TimelineGranularityPicker(
    selected: TimelineGranularity,
    onSelected: (TimelineGranularity) -> Unit,
) {
    val options = listOf(
        TimelineGranularity.YEAR to "年",
        TimelineGranularity.MONTH to "月",
        TimelineGranularity.DAY to "日",
    )
    val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val controlWidth = 196.dp
    val controlHeight = 52.dp
    val inset = 3.dp
    val segmentWidth = (controlWidth - inset * 2) / 3
    val indicatorOffset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
        label = "时间粒度滑块",
    )
    Box(
        modifier = Modifier
            .size(controlWidth, controlHeight)
            .shadow(
                elevation = 7.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .android16Glass(cornerRadius = 26.dp, strength = 0.78f, showBorder = false)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.50f),
                    1f to Color.White.copy(alpha = 0.34f),
                ),
                RoundedCornerShape(26.dp),
            )
            .padding(inset),
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .size(segmentWidth, controlHeight - inset * 2)
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(23.dp),
                    ambientColor = Color.Black.copy(alpha = 0.03f),
                    spotColor = Color.Black.copy(alpha = 0.05f),
                )
                .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(23.dp)),
        )
        Row {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .size(segmentWidth, controlHeight - inset * 2)
                        .clip(RoundedCornerShape(23.dp))
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
                        fontSize = 17.sp,
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
            .padding(top = 22.dp, bottom = 8.dp),
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
        EditorialRowStyle.FEATURED -> 150.dp
        EditorialRowStyle.TRIPLE -> 152.dp
        EditorialRowStyle.DOUBLE -> 136.dp
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
