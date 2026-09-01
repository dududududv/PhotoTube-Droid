package com.yunai.phototube.ui.collections

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.album.AlbumKind
import com.yunai.phototube.data.album.AlbumRepository
import com.yunai.phototube.data.album.SourceFolder
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.ui.components.AlbumTopBar
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.FloatingAlbumDock
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import dev.chrisbanes.haze.hazeSource

@Composable
fun RemoteCollectionsRoute(
    repository: AlbumRepository,
    onAlbumClick: (String) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenCreation: () -> Unit,
    refreshRevision: Int,
    modifier: Modifier = Modifier,
) {
    val collectionsViewModel: CollectionsViewModel = viewModel(
        factory = CollectionsViewModel.factory(repository),
    )
    val state by collectionsViewModel.uiState.collectAsStateWithLifecycle()
    val albums = collectionsViewModel.albums.collectAsLazyPagingItems()
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(refreshRevision) {
        if (refreshRevision > 0) albums.refresh()
    }
    LaunchedEffect(state.createdAlbum?.id) {
        state.createdAlbum?.let { album ->
            albums.refresh()
            collectionsViewModel.consumeCreatedAlbum()
            showCreate = false
            onAlbumClick(album.id)
        }
    }

    RemoteCollectionsScreen(
        albums = albums,
        serverRoot = collectionsViewModel.serverRoot,
        error = state.error,
        onAlbumClick = onAlbumClick,
        onOpenPhotos = onOpenPhotos,
        onOpenCreation = onOpenCreation,
        onCreate = { showCreate = true },
        modifier = modifier,
    )

    if (showCreate) {
        CreateAlbumSheet(
            state = state,
            onDismiss = { if (!state.isCreating) showCreate = false },
            onBrowse = collectionsViewModel::browseFolder,
            onCreateNormal = collectionsViewModel::createNormal,
            onCreatePath = collectionsViewModel::createPathSync,
        )
    }
}

@Composable
private fun RemoteCollectionsScreen(
    albums: androidx.paging.compose.LazyPagingItems<Album>,
    serverRoot: ServerRoot,
    error: TimelineError?,
    onAlbumClick: (String) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenCreation: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberAndroid16HazeState()
    var selectedDockItem by remember { mutableIntStateOf(1) }
    Android16HazeProvider(state = hazeState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PhotoTubeColors.Background),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = PhotoTubeDimens.ScreenPadding,
                    end = PhotoTubeDimens.ScreenPadding,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column(Modifier.statusBarsPadding()) {
                        Spacer(Modifier.height(20.dp))
                        AlbumTopBar()
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("图集", style = MaterialTheme.typography.displayLarge)
                                Text("普通相册与路径相册", color = PhotoTubeColors.Muted)
                            }
                            IconButton(
                                onClick = onCreate,
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(PhotoTubeColors.Ink, CircleShape),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "新建相册", tint = Color.White)
                            }
                        }
                    }
                }

                when (val refresh = albums.loadState.refresh) {
                    is LoadState.Loading -> item { CenterLoading() }
                    is LoadState.Error -> item {
                        CollectionError(refresh.error.toTimelineError(), albums::retry)
                    }
                    is LoadState.NotLoading -> {
                        if (albums.itemCount == 0) {
                            item { RemoteEmptyAlbums(onCreate) }
                        } else {
                            items(count = albums.itemCount, key = { albums[it]?.id ?: "album-$it" }) { index ->
                                albums[index]?.let { album ->
                                    RemoteAlbumCard(album, serverRoot) { onAlbumClick(album.id) }
                                }
                            }
                        }
                    }
                }

                when (val append = albums.loadState.append) {
                    is LoadState.Loading -> item { CenterLoading(compact = true) }
                    is LoadState.Error -> item {
                        CollectionError(append.error.toTimelineError(), albums::retry)
                    }
                    is LoadState.NotLoading -> Unit
                }

                error?.let { item { CollectionError(it, albums::retry) } }
            }

            FloatingAlbumDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedItem = selectedDockItem,
                onItemClick = {
                    selectedDockItem = it
                    when (it) {
                        0 -> onOpenPhotos()
                        2 -> onOpenCreation()
                    }
                },
                onLayoutClick = {},
                onMenuClick = onCreate,
            )
        }
    }
}

@Composable
private fun RemoteAlbumCard(album: Album, serverRoot: ServerRoot, onClick: () -> Unit) {
    val tint = when (album.kind) {
        AlbumKind.NORMAL -> Color(0xFFFFC2C8)
        AlbumKind.SMART -> Color(0xFFB8C4FF)
        AlbumKind.PATH_SYNC -> Color(0xFFBCE0D4)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(tint),
        ) {
            val coverUrl = album.cover?.thumbnailUrl(serverRoot)
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "${album.name} 封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                            ),
                        ),
                )
            } else {
                Text(
                    text = when (album.kind) {
                        AlbumKind.NORMAL -> "普通相册"
                        AlbumKind.SMART -> "智能相册"
                        AlbumKind.PATH_SYNC -> "路径相册"
                    },
                    modifier = Modifier.align(Alignment.Center),
                    color = PhotoTubeColors.Ink.copy(alpha = 0.62f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "${album.assetCount} 项",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
                    .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(album.name, style = MaterialTheme.typography.titleLarge)
        val syncLabel = album.pathSync?.let { summary ->
            when {
                summary.lastRunState != null -> "${summary.enabledPathCount} 个目录 · ${summary.lastRunState.name}"
                else -> "${summary.enabledPathCount} 个目录 · 尚未扫描"
            }
        }
        if (syncLabel != null) {
            Text(syncLabel, color = PhotoTubeColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RemoteEmptyAlbums(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("从一个图集开始", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("普通相册手工整理，路径相册由你明确扫描", color = PhotoTubeColors.Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) { Text("新建图集") }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlbumSheet(
    state: CollectionsUiState,
    onDismiss: () -> Unit,
    onBrowse: (String) -> Unit,
    onCreateNormal: (String) -> Unit,
    onCreatePath: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AlbumKind.NORMAL) }
    var selectedFolder by remember { mutableStateOf<SourceFolder?>(null) }
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("新建图集", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == AlbumKind.NORMAL,
                    onClick = { kind = AlbumKind.NORMAL },
                    label = { Text("普通相册") },
                )
                FilterChip(
                    selected = kind == AlbumKind.PATH_SYNC,
                    onClick = {
                        kind = AlbumKind.PATH_SYNC
                        if (state.folderListing == null) onBrowse("")
                    },
                    label = { Text("路径相册") },
                )
            }

            if (kind == AlbumKind.PATH_SYNC) {
                Text("选择目录只保存配置，不会开始扫描。", color = PhotoTubeColors.Muted, fontSize = 13.sp)
                SourceFolderBrowser(
                    listing = state.folderListing,
                    isLoading = state.isBrowsingFolders,
                    selectedPath = selectedFolder?.path,
                    onBrowse = onBrowse,
                    onSelect = { libraryId, folder ->
                        selectedLibraryId = libraryId
                        selectedFolder = folder
                    },
                )
            }

            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !state.isCreating && name.isNotBlank() &&
                    (kind == AlbumKind.NORMAL || selectedFolder != null),
                onClick = {
                    if (kind == AlbumKind.NORMAL) {
                        onCreateNormal(name)
                    } else {
                        onCreatePath(name, requireNotNull(selectedLibraryId), requireNotNull(selectedFolder).path)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (kind == AlbumKind.NORMAL) "创建" else "保存路径相册（不扫描）")
                }
            }
        }
    }
}

@Composable
internal fun SourceFolderBrowser(
    listing: com.yunai.phototube.data.album.SourceFolderListing?,
    isLoading: Boolean,
    selectedPath: String?,
    onBrowse: (String) -> Unit,
    onSelect: (String, SourceFolder) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color.White.copy(alpha = 0.54f), RoundedCornerShape(20.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                enabled = listing?.path?.isNotEmpty() == true,
                onClick = { onBrowse(listing?.path?.substringBeforeLast('/', "").orEmpty()) },
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "上一级")
            }
            Text(listing?.path?.ifEmpty { "媒体根目录" } ?: "正在读取目录", Modifier.weight(1f), maxLines = 1)
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn {
                items(listing?.items.orEmpty(), key = SourceFolder::path) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (folder.path == selectedPath) Color(0xFFE2EDFF) else Color.Transparent,
                            )
                            .clickable { onBrowse(folder.path) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(folder.name, Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { onSelect(requireNotNull(listing).libraryId, folder) }) {
                            Text(if (folder.path == selectedPath) "已选择" else "选择")
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "进入")
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterLoading(compact: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 72.dp else 220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(if (compact) 24.dp else 40.dp))
    }
}

@Composable
private fun CollectionError(error: TimelineError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
