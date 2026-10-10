package com.yunai.phototube.ui.remote

import com.yunai.phototube.ui.media.mediaBounds

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.yunai.phototube.ui.icons.LibraryFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import kotlinx.coroutines.*

@Composable
internal fun DirectoryBrowser(server: PhotoTubeServer, onChanged: () -> Unit, onClose: () -> Unit) {
    val repository = remember(server) { DirectoryRepository(server) }
    var trail by rememberConfigurationState(server) { mutableStateOf(emptyList<DirectoryNode>()) }
    var photos by rememberConfigurationState(server) { mutableStateOf<DirectoryNode?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val current = trail.lastOrNull()
    ManagementPage("文件夹", onClose, scrollable = false,
        onNavigateUp = if (trail.isEmpty()) null else { { trail = trail.dropLast(1) } }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            GlassChoiceChip("媒体库",trail.isEmpty(),{trail=emptyList()})
            trail.forEachIndexed { index, node ->
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = AppTheme.colors.secondary, modifier = Modifier.size(16.dp))
                GlassChoiceChip(node.name,index==trail.lastIndex,{trail=trail.take(index+1)})
            }
        }
        AnimatedContent(current, modifier = Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(spring(.95f, 420f), initialScale = .97f)) togetherWith fadeOut(tween(100))
        }, label = "directory-level") { target ->
            key(target?.key, revision) {
                DirectoryLevel(repository, target, onEnter = { if(current?.key == target?.key) trail = trail + it }, onPhotos = { photos = it })
            }
        }
    }
    photos?.let { directory ->
        DirectoryPhotos(server, directory, onChanged = { revision++; onChanged() }, onClose = { photos = null })
    }
}

@Composable
private fun DirectoryLevel(repository: DirectoryRepository, location: DirectoryNode?, onEnter: (DirectoryNode) -> Unit, onPhotos: (DirectoryNode) -> Unit) {
    val currentState = rememberConfigurationState(repository.server to location?.key) { mutableStateOf<DirectoryNode?>(null) }
    var current by currentState
    val flow = rememberConfigurationPaging(repository.server to location?.key) {
        Pager(PagingConfig(100, initialLoadSize = 100, prefetchDistance = 12, maxSize = 500, enablePlaceholders = false)) {
            AlbumPagingSource { cursor ->
                val result = repository.list(location?.libraryId, location?.path.orEmpty(), cursor)
                withContext(Dispatchers.Main.immediate) { current = result.current }
                result.children
            }
        }.flow
    }
    val folders = flow.collectAsLazyPagingItems()
    val colors = AppTheme.colors
    LazyColumn(Modifier.fillMaxSize().testTag("directory-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (location == null) "选择媒体库" else location.name, modifier = Modifier.weight(1f), color = colors.text, fontWeight = FontWeight.SemiBold)
                Box(Modifier.width(88.dp)) { GlassSettingsAction("刷新",{current=null;folders.refresh()},enabled=folders.loadState.refresh !is LoadState.Loading) }
            }
            current?.let { node ->
                if (node.kind != "ROOT") {
                    Text("本层直接包含 ${node.directAssetCount} 项", color = colors.secondary)
                    SettingsNavigationRow("查看照片与视频 · 包含子目录",{onPhotos(node)},enabled=node.online,modifier=Modifier.testTag("browse-directory-photos"))
                }
            }
        }
        items(folders.itemCount, key = folders.itemKey { it.key }) { index -> folders[index]?.let { folder ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.group)
                .clickable(enabled = folder.online) { onEnter(folder) }.testTag("directory-${folder.key}").padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(colors.accent.copy(.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LibraryFolder, null, tint = colors.accent, modifier = Modifier.size(27.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(folder.name, color = colors.text, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (!folder.online) "媒体库离线" else "本层 ${folder.directAssetCount} 项${if (folder.hasChildren) " · 有子目录" else ""}", color = colors.secondary)
                }
                if (folder.online) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
            }
        } }
        if (folders.loadState.refresh is LoadState.Loading || folders.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        val failure = (folders.loadState.refresh as? LoadState.Error) ?: (folders.loadState.append as? LoadState.Error)
        if (failure != null) item { Column {
            Text(failure.error.message ?: "目录加载失败", color = colors.text)
            GlassSettingsAction("重试",folders::retry)
        } }
        if (folders.loadState.refresh is LoadState.NotLoading && folders.itemCount == 0) item {
            Text(if (location == null) "没有可浏览的媒体库" else "没有子目录，可查看本目录的照片与视频。", color = colors.secondary)
        }
    }
}

@Composable
private fun DirectoryPhotos(server: PhotoTubeServer, directory: DirectoryNode, onChanged: () -> Unit, onClose: () -> Unit) {
    val filters = remember(directory.key) { WallFilters(libraryId = directory.libraryId, folderPath = directory.path.ifEmpty { null }) }
    ScopedMediaGallery(server, filters, directory.name, "包含子目录 · 不含归档与私密内容",
        "此目录及子目录中没有可浏览的照片或视频。", "directory-photos", onChanged, onClose)
}

/** Shared paged browsing for a server-defined directory or tag scope. */
@Composable
internal fun ScopedMediaGallery(server: PhotoTubeServer, filters: WallFilters, title: String,
    description: String, emptyText: String, gridTag: String, onChanged: () -> Unit, onClose: () -> Unit) {
    val flow = rememberConfigurationPaging(server to filters) { Pager(PagingConfig(100, initialLoadSize = 100, prefetchDistance = 16, maxSize = 500, enablePlaceholders = false)) { AssetPagingSource(server, filters) }.flow }
    val items = flow.collectAsLazyPagingItems()
    var revision by remember { mutableIntStateOf(0) }
    var count by remember { mutableStateOf<Long?>(null) }
    var summaryError by remember { mutableStateOf(false) }
    var selecting by rememberConfigurationState(server to filters) { mutableStateOf(false) }
    var viewer by rememberConfigurationState(server to filters) { mutableStateOf<PagedViewerSession?>(null) }
    BindPagedViewer(viewer, items) { it }
    val bounds = remember { mutableMapOf<String, Rect>() }
    LaunchedEffect(server, filters, revision) {
        count = null; summaryError = false
        try { val total = server.summary(filters).sumOf { it.count }; withContext(Dispatchers.Main.immediate) { count = total } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { withContext(Dispatchers.Main.immediate) { summaryError = true } }
    }
    ManagementPage(title, onClose, scrollable = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${count?.let { "$it 项 · " }.orEmpty()}$description", color = AppTheme.colors.secondary, modifier = Modifier.weight(1f))
            Box(Modifier.width(88.dp)) { GlassSettingsAction("选择",{selecting=true},enabled=count!=null && count!!>0,
                modifier=Modifier.testTag("$gridTag-select")) }
        }
        if (summaryError) GlassSettingsAction("数量读取失败 · 重试",{revision++})
        LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f).testTag(gridTag), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items.itemCount, key = items.itemKey { it.id }) { index ->
                (if (viewer == null) items[index] else items.peek(index))?.let { asset ->
                    AssetThumbnail(asset, server, Modifier.fillMaxWidth().aspectRatio(1f)
                        .mediaBounds(asset.id, bounds)
                        .clickable { viewer = PagedViewerSession.create(items, { it }, server, asset, bounds) })
                }
            }
            if (items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading) item(span = { GridItemSpan(maxLineSpan) }) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
            if (failure != null) item(span = { GridItemSpan(maxLineSpan) }) { Column {
                Text(failure.error.message ?: "照片加载失败", color = AppTheme.colors.text)
                GlassSettingsAction("重试",items::retry)
            } }
            if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(emptyText, color = AppTheme.colors.secondary)
            }
        }
    }
    viewer?.let { PagedMediaViewer(it, onClose = { viewer = null }, onRemoteChanged = { items.refresh(); revision++; onChanged() }) }
    if (selecting) BulkAssetsScreen(server, filters, count ?: 0L,
        onChanged = { items.refresh(); revision++; onChanged() }, onClose = { selecting = false })
}
