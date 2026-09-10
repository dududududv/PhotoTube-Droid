package com.yunai.phototube.ui

import com.yunai.phototube.ui.media.*
import com.yunai.phototube.data.PhotoWallViewModel
import com.yunai.phototube.ui.remote.RemotePhotoWall
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import com.yunai.phototube.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.ui.theme.*
import com.yunai.phototube.R
import com.yunai.phototube.glass.*

private data class Album(val key: String, val title: String, val count: String, val cover: Photo)
private val albums = listOf(
    Album("nature", "山野之间", "3 张照片", photos[0]),
    Album("daily", "漫步日常", "2 张照片 · 1 段视频", photos[1]),
    Album("flowers", "花与风", "1 张照片", photos[2]),
    Album("videos", "视频", "1 段视频", photos[6])
)

@Composable
fun PhotoTubeApp() {
    PhotoTubeTheme { PhotoTubeContent() }
}

@Composable
private fun PhotoTubeContent() {
    val wall:PhotoWallViewModel=viewModel()
    val theme = LocalThemeController.current
    CompositionLocalProvider(LocalGlassAppearance provides theme.glass) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var panel by rememberSaveable { mutableStateOf<String?>(null) }
        var showVideo by rememberSaveable { mutableStateOf(false) }
        var viewer by rememberSaveable(stateSaver = ViewerRequestSaver) { mutableStateOf<ViewerRequest?>(null) }
        var favoriteIds by rememberSaveable { mutableStateOf(photos.filter {it.favorite}.map {it.id}) }
        var removedIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
        var undoPhoto by remember { mutableStateOf<Photo?>(null) }
        val mediaBounds = remember { mutableMapOf<Int,Rect>() }

        var favoritesOnly by rememberSaveable { mutableStateOf(false) }
        var newestFirst by rememberSaveable { mutableStateOf(true) }
        var query by rememberSaveable { mutableStateOf("") }
        var albumQuery by rememberSaveable { mutableStateOf("") }
        val libraryState = rememberLazyListState()
        val albumsState = rememberLazyListState()
        var compact by remember { mutableStateOf(false) }
        var searchIndex by rememberSaveable { mutableIntStateOf(0) }
        var searchOffset by rememberSaveable { mutableIntStateOf(0) }
        var searchCompact by rememberSaveable { mutableStateOf(false) }
        var restoreSearch by remember { mutableStateOf(false) }
        val focusManager=LocalFocusManager.current
        val tabAlpha by animateFloatAsState(if(panel=="search") 0f else 1f,label="search-tabs")
        fun beginSearch() {
            val state=if(tab==0) libraryState else albumsState
            searchIndex=state.firstVisibleItemIndex;searchOffset=state.firstVisibleItemScrollOffset;searchCompact=compact
            panel="search"
        }
        fun endSearch() {query="";albumQuery="";panel=null;restoreSearch=true}
        LaunchedEffect(query,albumQuery,panel,restoreSearch) {
            val state=if(tab==0) libraryState else albumsState
            if(restoreSearch) {state.scrollToItem(searchIndex,searchOffset);compact=searchCompact;restoreSearch=false}
            else if(panel=="search") state.scrollToItem(0)
        }
        val threshold = with(LocalDensity.current) { 20.dp.toPx() }
        val scrollConnection = remember(threshold) {
            object : NestedScrollConnection {
                var distance = 0f
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        if (available.y * distance < 0) distance = 0f
                        distance += available.y
                        if (distance < -threshold) compact = true
                        if (distance > threshold) compact = false
                    }
                    return Offset.Zero
                }
            }
        }
        val barHeight by animateDpAsState(if (compact) 48.dp else 60.dp, spring(dampingRatio = .85f, stiffness = 450f), label = "bar-height")
        val barWidth by animateDpAsState(if (compact) 140.dp else 184.dp, spring(dampingRatio = .85f, stiffness = 450f), label = "bar-width")
        val labelProgress by animateFloatAsState(if (compact) 0f else 1f, label = "tab-labels")
        val bottomIconSize by animateDpAsState(if (compact) 22.dp else 23.dp, label = "bottom-icons")
        var haptics by rememberSaveable { mutableStateOf(true) }
        val glassStrength = theme.opacity
        val backdrop = rememberLayerBackdrop()
        val cornerInsets = rememberCornerInsets()
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        fun selectTab(index: Int) {
            if (index != tab && haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
            if (tab != index) compact = false
            tab = index
        }
        val visible = photos.filter { it.id !in removedIds && (!favoritesOnly || it.id in favoriteIds) && (query.isBlank() || it.name.contains(query)) }
            .let { if (newestFirst) it.sortedByDescending {photo -> photo.takenOn} else it.sortedBy {photo -> photo.takenOn} }
        fun openMedia(photo: Photo) { focusManager.clearFocus();viewer = ViewerRequest(visible,photo.id,mediaBounds.toMap()) }
        val visibleAlbums = albums.filter { albumQuery.isBlank() || it.title.contains(albumQuery) }
        CompositionLocalProvider(LocalMediaBounds provides mediaBounds) {
        Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            // Photos extend behind the floating controls; only the controls apply glass.
            if(tab==0) RemotePhotoWall(wall,cornerInsets.top,Modifier.nestedScroll(scrollConnection).layerBackdrop(backdrop).background(AppTheme.colors.background))
            else LazyColumn(
                Modifier.fillMaxSize().nestedScroll(scrollConnection).layerBackdrop(backdrop).background(AppTheme.colors.background),
                state = if (tab == 0) libraryState else albumsState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 138.dp)
            ) {
                item {
                    Spacer(Modifier.height(cornerInsets.top))
                    Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                        if (tab == 0) Text("图库", fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.text, modifier = Modifier.testTag("library-title"))
                    }
                    Spacer(Modifier.height(6.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(if (tab == 0) "每一刻，都值得留住" else "相册界面预览 · 尚未连接服务器", color = AppTheme.colors.secondary, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                }
                if (tab == 0) {
                    if (visible.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PhotoLibrary, null, tint = AppTheme.colors.secondary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(14.dp))
                            Text("没有找到照片", color = AppTheme.colors.text)
                            Text("试试其他关键词或筛选条件", color = AppTheme.colors.secondary, fontSize = 13.sp)
                        }
                    }
                    if (!favoritesOnly && query.isBlank() && newestFirst && removedIds.isEmpty()) {
                        item { MonthLabel("九月", "2026") }
                        item {
                            PhotoTile(photos[0], Modifier.fillMaxWidth().aspectRatio(1.65f), "山间来信") { openMedia(it) }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth().heightIn(min = 200.dp).aspectRatio(1.12f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PhotoTile(photos[1], Modifier.weight(1f).fillMaxHeight()) { openMedia(it) }
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PhotoTile(photos[2], Modifier.weight(1f).fillMaxWidth()) { openMedia(it) }
                                    PhotoTile(photos[3], Modifier.weight(1f).fillMaxWidth()) { openMedia(it) }
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            PhotoTile(photos[6], Modifier.fillMaxWidth().aspectRatio(1.8f), "城市片段") { openMedia(it) }
                        }
                        item { Spacer(Modifier.height(22.dp)); MonthLabel("八月", "2026") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                photos.slice(4..5).forEach { photo -> PhotoTile(photo, Modifier.weight(1f).aspectRatio(.85f)) { openMedia(it) } }
                            }
                        }
                    } else {
                        item { MonthLabel(if (favoritesOnly) "我的收藏" else "全部照片", "${visible.size} 张") }
                        visible.chunked(2).forEach { row -> item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEach { photo -> PhotoTile(photo, Modifier.weight(1f).aspectRatio(.85f)) { openMedia(it) } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        } }
                    }
                } else {
                    item { MonthLabel("我的相册", "${visibleAlbums.size} 本") }
                    if (visibleAlbums.isEmpty()) item { Text("没有找到相册", color = AppTheme.colors.secondary, modifier = Modifier.padding(vertical = 48.dp)) }
                    visibleAlbums.chunked(2).forEach { row -> item {
                        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { album ->
                                AlbumCard(album.title, mediaCount(albumMedia(album.key).filter {it.id !in removedIds}), albumMedia(album.key).firstOrNull {it.id !in removedIds} ?: album.cover, Modifier.weight(1f)) { panel = "album:${album.key}" }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    } }

                }
            }
            if(undoPhoto != null && panel?.startsWith("album:") != true) UndoDeletion({undoPhoto?.let {removedIds=removedIds-it.id};undoPhoto=null},
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start=16.dp,end=16.dp,bottom=100.dp))
            // Keep glass outside its source layer so it never samples itself.
            Toolbar(
                backdrop = backdrop, surfaceColor = AppTheme.colors.glass.copy(alpha = glassStrength),
                modifier = Modifier.align(Alignment.TopCenter).absolutePadding(left = cornerInsets.left, right = cornerInsets.right, top = cornerInsets.top),
                isLibrary = tab == 0, actionVisible = panel != "filter" && panel != "search", onAction = { panel = if (tab == 0) "filter" else "settings" }
            )
            Row(
                Modifier.align(Alignment.BottomCenter).absolutePadding(left = cornerInsets.left, right = cornerInsets.right, bottom = cornerInsets.bottom).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                if (tabAlpha > .01f) LiquidBottomTabs(
                    selectedTabIndex = { tab }, onTabSelected = ::selectTab, backdrop = backdrop, tabsCount = 2,
                    modifier = Modifier.width(barWidth).graphicsLayer {alpha=tabAlpha}.then(if(panel=="search") Modifier.clearAndSetSemantics {} else Modifier).testTag("bottom-tabs"), surfaceOpacity = glassStrength, barHeight = barHeight
                ) {
                    LiquidBottomTab(onClick = { selectTab(0) }, modifier = Modifier.semantics { selected = tab == 0; contentDescription = "图库标签" }) {
                        Icon(Icons.Rounded.Photo, null, Modifier.size(bottomIconSize), tint = if (tab == 0) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                        Text("图库", modifier = Modifier.height(14.dp * labelProgress).clipToBounds().graphicsLayer { alpha = labelProgress }, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = if (tab == 0) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                    }
                    LiquidBottomTab(onClick = { selectTab(1) }, modifier = Modifier.semantics { selected = tab == 1; contentDescription = "相册标签" }) {
                        Icon(Icons.Rounded.PhotoAlbum, null, Modifier.size(bottomIconSize), tint = if (tab == 1) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                        Text("相册", modifier = Modifier.height(14.dp * labelProgress).clipToBounds().graphicsLayer { alpha = labelProgress }, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = if (tab == 1) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                    }
                }

            }
        }
        }
        SearchDock(panel=="search",tab==0,if(tab==0) query else albumQuery,
            onValue={if(tab==0) query=it else albumQuery=it},onOpen=::beginSearch,onClose=::endSearch,
            backdrop=backdrop,insets=cornerInsets,collapsedSize=barHeight,iconSize=bottomIconSize,opacity=glassStrength,
            resultCount=if(tab==0) null else visibleAlbums.size)
        if (panel == "filter") FilterMenu(backdrop,cornerInsets,glassStrength,favoritesOnly,newestFirst,
            onFavorites={favoritesOnly=it},onOrder={newestFirst=it},onDismiss={panel=null},preview=true)
        if (panel?.startsWith("album:") == true) {
            val album = albums.first { it.key == panel!!.substringAfter(":") }
            AlbumDetail(album.title,albumMedia(album.key).filter {it.id !in removedIds},favoriteIds.toSet(),
                onOpen={photo,sequence,bounds -> viewer=ViewerRequest(sequence,photo.id,bounds)},
                undoPhoto=undoPhoto,onUndo={undoPhoto?.let {removedIds=removedIds-it.id};undoPhoto=null},onClose={panel=null})
        }
        if (panel == "settings") {
            SettingsScreen(
                haptics, { haptics = it }, favoritesOnly, { favoritesOnly = it }, newestFirst, { newestFirst = it },
                glassStrength, theme::updateOpacity,
                onServer = { panel=null;tab=0;wall.configure() },
                onAppearance = theme::updateGlass, onVideo = { panel = null; showVideo = true }, onClose = { panel = null }
            )
        }
        if (showVideo) VideoGlassPreview(glassStrength) { showVideo = false }
        viewer?.let { request ->
            MediaViewer(request,favoriteIds.toSet(),onFavorite={id -> favoriteIds=if(id in favoriteIds) favoriteIds-id else favoriteIds+id},
                onDelete={photo -> removedIds=removedIds+photo.id;undoPhoto=photo},onClose={viewer=null})
        }
        BackHandler(panel == null && viewer == null && (tab != 0 || query.isNotEmpty() || favoritesOnly)) { tab = 0; query = ""; favoritesOnly = false }
    }
}

@Composable
private fun Toolbar(
    backdrop: Backdrop, surfaceColor: Color, modifier: Modifier = Modifier,
    isLibrary: Boolean, actionVisible: Boolean = true, onAction: () -> Unit
) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!isLibrary) Text("相册", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.text)
        Spacer(Modifier.weight(1f))
        if (actionVisible) LiquidButton(
            onClick = onAction, backdrop = backdrop, surfaceColor = surfaceColor,
            shape = CircleShape, horizontalPadding = 0.dp,
            modifier = Modifier.size(48.dp).testTag(if (isLibrary) "filter-button" else "settings-button")
                .semantics { contentDescription = if (isLibrary) "筛选" else "设置" }
        ) {
            Icon(if (isLibrary) Icons.Rounded.FilterList else Icons.Outlined.Settings, null, Modifier.size(22.dp), tint = AppTheme.colors.text)
        }
    }
}

@Composable
private fun MonthLabel(month: String, detail: String) {
    Row(Modifier.padding(bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(month, fontSize = 16.sp, color = AppTheme.colors.text)
        Text("  ·  $detail", fontSize = 15.sp, color = AppTheme.colors.secondary)
    }
}

private val LocalMediaBounds = staticCompositionLocalOf<MutableMap<Int,Rect>> { mutableMapOf() }

@Composable
private fun PhotoTile(photo: Photo, modifier: Modifier, caption: String? = null, onClick: (Photo) -> Unit) {
    val bounds = LocalMediaBounds.current
    var frame by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.onGloballyPositioned { frame=it.boundsInWindow();bounds[photo.id]=frame }.clip(RoundedCornerShape(22.dp))
        .clickable { bounds[photo.id]=frame;onClick(photo) }.semantics { contentDescription=photo.name }) {
        MediaArtwork(photo,Modifier.fillMaxSize(),description=null)
        if(photo.video) Text(mediaTime(rememberMediaMetadata(photo)?.duration ?: 0L),color=MediaColors.text,fontSize=12.sp,
            modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp).background(MediaColors.scrim.copy(.4f),RoundedCornerShape(6.dp)).padding(horizontal=6.dp,vertical=3.dp))
        if (caption != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(65.dp).background(Brush.verticalGradient(listOf(Color.Transparent, MediaColors.scrim.copy(.38f)))))
            Text(caption, Modifier.align(Alignment.BottomStart).padding(13.dp), color = MediaColors.text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AlbumCard(title: String, count: String, photo: Photo, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        MediaArtwork(photo, description = title, modifier =
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).testTag("album-cover-$title"), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(8.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.text)
        Text(count, fontSize = 12.sp, color = AppTheme.colors.secondary)
    }
}

