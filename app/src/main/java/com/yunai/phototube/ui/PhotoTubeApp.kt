package com.yunai.phototube.ui

import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.state.*
import com.yunai.phototube.data.PhotoWallViewModel
import com.yunai.phototube.ui.remote.LibraryTool
import com.yunai.phototube.ui.remote.LibraryToolScreen
import com.yunai.phototube.ui.remote.RemoteAlbums
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
import androidx.compose.material.icons.rounded.Add
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

@Composable
fun PhotoTubeApp() {
    PhotoTubeTheme { PhotoTubeContent() }
}

@Composable
internal fun PhotoTubeContent(wall:PhotoWallViewModel=viewModel()) {
    val configurationState:ConfigurationStateStore=viewModel()
    val theme = LocalThemeController.current
    CompositionLocalProvider(LocalGlassAppearance provides theme.glass) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var panel by rememberSaveable { mutableStateOf<String?>(null) }
        var libraryTool by rememberSaveable {mutableStateOf<LibraryTool?>(null)}
        var albumRevision by rememberSaveable {mutableIntStateOf(0)}
        var createAlbum by rememberSaveable { mutableStateOf(false) }
        var showVideo by rememberSaveable { mutableStateOf(false) }
        var remoteViewer by rememberConfigurationState(wall.server, enabled = wall.stage == com.yunai.phototube.data.ConnectionStage.READY) {mutableStateOf<PagedViewerSession?>(null)}
        LaunchedEffect(wall.stage,wall.server) {
            if(wall.stage!=com.yunai.phototube.data.ConnectionStage.READY) {remoteViewer=null;libraryTool=null;configurationState.clear()}
        }

        var query by rememberSaveable { mutableStateOf("") }
        var albumQuery by rememberSaveable { mutableStateOf("") }
        val libraryState = rememberLazyListState()
        var bulkSelectedId by rememberSaveable {mutableStateOf<String?>(null)}
        var bulkRowKey by rememberSaveable {mutableStateOf<String?>(null)}
        var bulkRowOffset by rememberSaveable {mutableIntStateOf(0)}
        val searchListState = rememberLazyListState()
        val albumsState = rememberLazyListState()
        var compact by remember { mutableStateOf(false) }
        var searchIndex by rememberSaveable { mutableIntStateOf(0) }
        var searchOffset by rememberSaveable { mutableIntStateOf(0) }
        var searchCompact by rememberSaveable { mutableStateOf(false) }
        var restoreSearch by remember { mutableStateOf(false) }
        val focusManager=LocalFocusManager.current
        val tabAlpha by animateFloatAsState(if(panel=="search") 0f else 1f,label="search-tabs")
        fun beginSearch() {
            if(tab==0) wall.beginSearch()
            val state=if(tab==0) libraryState else albumsState
            searchIndex=state.firstVisibleItemIndex;searchOffset=state.firstVisibleItemScrollOffset;searchCompact=compact
            panel="search"
        }
        fun endSearch() {wall.endSearch();query="";albumQuery="";panel=null;restoreSearch=true}
        LaunchedEffect(query,albumQuery,panel,restoreSearch) {
            val state=if(tab==0) libraryState else albumsState
            if(restoreSearch) {state.scrollToItem(searchIndex,searchOffset);compact=searchCompact;restoreSearch=false}
            else if(panel=="search") searchListState.scrollToItem(0)
        }
        val searchMessage=librarySearchMessage(wall,query,panel=="search" && tab==0)
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
        val tabLabelStyle = androidx.compose.material3.LocalTextStyle.current.merge(
            androidx.compose.ui.text.TextStyle(fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold))
        val tabTextMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        val measuredTabLabels = listOf("图库", "相册").map { tabTextMeasurer.measure(it, style=tabLabelStyle, maxLines=1) }
        val tabLabelHeight = with(LocalDensity.current) { measuredTabLabels.maxOf { it.size.height }.toDp() + 1.dp }.coerceAtLeast(14.dp)
        val tabLabelWidth = with(LocalDensity.current) { measuredTabLabels.maxOf { it.size.width }.toDp() + 2.dp }
        val expandedBarHeight = (tabLabelHeight + 41.dp).coerceAtLeast(60.dp)
        val barHeight by animateDpAsState(if (compact) 48.dp else expandedBarHeight, spring(dampingRatio = .85f, stiffness = 450f), label = "bar-height")
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
        Box(Modifier.fillMaxSize().background(AppTheme.colors.background)
            .then(if(panel=="filter") Modifier.clearAndSetSemantics {} else Modifier)) {
            // Photos extend behind the floating controls; only the controls apply glass.
            androidx.compose.animation.Crossfade(targetState=tab,
                modifier=Modifier.fillMaxSize().layerBackdrop(backdrop),
                animationSpec=androidx.compose.animation.core.tween(200),label="library-albums") { visibleTab ->
            if(visibleTab==0) RemotePhotoWall(wall,cornerInsets.top,Modifier.nestedScroll(scrollConnection).background(AppTheme.colors.background),searchMessage=searchMessage,showSearchMessage=false,listState=if(panel=="search") searchListState else libraryState,onSelect={
                val state=if(panel=="search") searchListState else libraryState
                val visible=state.layoutInfo.visibleItemsInfo
                // At the top, expose the selection heading and scope controls first.
                val anchor=if(visible.any {it.key=="title"}) null else visible.firstOrNull {it.key.toString().startsWith("row-")}
                bulkSelectedId=null
                bulkRowKey=anchor?.key as? String
                bulkRowOffset=anchor?.offset?.let {-it} ?: 0
                panel="bulk-assets"
            },onSelectAsset={id,key,offset ->
                bulkSelectedId=id;bulkRowKey=key;bulkRowOffset=offset;panel="bulk-assets"
            },viewerSession=remoteViewer,selectionActionVisible=panel!="filter") { session ->
                focusManager.clearFocus()
                remoteViewer=session
            }
            else RemoteAlbums(wall.server,wall.stage==com.yunai.phototube.data.ConnectionStage.READY,cornerInsets.top,
                Modifier.nestedScroll(scrollConnection).background(AppTheme.colors.background),
                query=albumQuery,searchActive=panel=="search",revision=albumRevision,onLibraryTool={libraryTool=it},createRequested=createAlbum,onCreateConsumed={createAlbum=false},onLibraryChanged=wall::refresh,onConnect={tab=0;wall.configure()})
            }
            // A feathered scroll edge keeps theme-colored system text legible over media.
            val statusHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(Modifier.fillMaxWidth().height(statusHeight + 16.dp).background(Brush.verticalGradient(
                0f to AppTheme.colors.background,
                .72f to AppTheme.colors.background.copy(alpha=.96f),
                1f to AppTheme.colors.background.copy(alpha=0f))))
            // Keep glass outside its source layer so it never samples itself.
            Toolbar(
                backdrop = backdrop, surfaceColor = AppTheme.colors.glass.copy(alpha = glassStrength),
                modifier = Modifier.align(Alignment.TopCenter).absolutePadding(left = cornerInsets.left, right = cornerInsets.right, top = cornerInsets.top),
                onCreateAlbum={createAlbum=true}, filterActive=wall.filters.active, isLibrary = tab == 0, actionVisible = panel != "filter" && panel != "search", onAction = { panel = if (tab == 0) "filter" else "settings" }
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
                        Text("图库", modifier = Modifier.width(tabLabelWidth).height(tabLabelHeight * labelProgress).clipToBounds().graphicsLayer { alpha = labelProgress }, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = if (tab == 0) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                    }
                    LiquidBottomTab(onClick = { selectTab(1) }, modifier = Modifier.semantics { selected = tab == 1; contentDescription = "相册标签" }) {
                        Icon(Icons.Rounded.PhotoAlbum, null, Modifier.size(bottomIconSize), tint = if (tab == 1) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                        Text("相册", modifier = Modifier.width(tabLabelWidth).height(tabLabelHeight * labelProgress).clipToBounds().graphicsLayer { alpha = labelProgress }, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, fontWeight = FontWeight.Bold, color = if (tab == 1) LocalGlassPalette.current.navigationAccent else LocalGlassPalette.current.navigationContent)
                    }
                }

            }
        }
        Box(Modifier.fillMaxSize().then(if(panel=="filter") Modifier.clearAndSetSemantics {} else Modifier)) {
        SearchDock(panel=="search",tab==0,if(tab==0) query else albumQuery,
            onValue={if(tab==0) query=it else albumQuery=it},onOpen=::beginSearch,onClose=::endSearch,
            backdrop=backdrop,insets=cornerInsets,collapsedSize=barHeight,iconSize=bottomIconSize,opacity=glassStrength,
            resultCount=null,statusText=if(tab==0) when {
                wall.stage!=com.yunai.phototube.data.ConnectionStage.READY -> "连接服务后搜索图库"
                searchMessage!=null -> searchMessage
                wall.error!=null -> "搜索未完成，请重试"
                wall.busy -> "正在搜索…"
                query.isBlank() -> "按文件名搜索 · 至少 3 个字符"
                else -> "找到 ${wall.groups.sumOf {it.count}} 项内容"
            } else "按相册名称筛选")
        }
        if (panel == "filter") FilterMenu(backdrop,cornerInsets,glassStrength,wall.filters,
            onChange=wall::updateFilters,onDismiss={panel=null},server=wall.server)
        if(panel=="bulk-assets") wall.server?.let {api -> com.yunai.phototube.ui.remote.BulkAssetsScreen(api,wall.filters,wall.groups.sumOf {it.count},onChanged=wall::refresh,onClose={panel=null},groups=wall.groups,sourceFlow=wall.pageFlow,initialRowKey=bulkRowKey,initialRowOffset=bulkRowOffset,initialSelectedId=bulkSelectedId)}
        if (panel == "settings") {
            SettingsScreen(
                haptics, { haptics = it }, wall.filters.favorite==true, { wall.updateFilters(wall.filters.copy(favorite=if(it) true else null)) }, true, {},
                glassStrength, theme::updateOpacity,
                onServer = { panel=null;tab=0;wall.connect(com.yunai.phototube.data.LocalConnection.BASE) },
                onLibraryTool = if(wall.server!=null) {{libraryTool=it}} else null,
                onRemoteSettings = if(wall.stage==com.yunai.phototube.data.ConnectionStage.READY) {{panel="server-settings"}} else null,
                onAppearance = theme::updateGlass, onVideo = { showVideo = true }, onClose = { panel = null }
            )
        }
        libraryTool?.let {tool -> wall.server?.let {api ->
            LibraryToolScreen(tool,api,onChanged={albumRevision++;wall.refresh()},onClose={libraryTool=null})
        } }
        if(panel=="server-settings") wall.server?.let {api -> com.yunai.phototube.ui.remote.SystemSettings(api,onSignedOut={wall.signedOut();panel=null;tab=0},onClose={panel="settings"})}
        if (showVideo) VideoGlassPreview(glassStrength) { showVideo = false }
        remoteViewer?.let {request ->
            PagedMediaViewer(request,onClose={remoteViewer=null},onRemoteChanged=wall::refresh)
        }
        BackHandler(panel == null && libraryTool == null && remoteViewer == null && (tab != 0 || query.isNotEmpty())) { tab = 0; query = "" }
    }
}

@Composable
private fun Toolbar(
    backdrop: Backdrop, surfaceColor: Color, modifier: Modifier = Modifier,
    isLibrary: Boolean, onCreateAlbum:()->Unit, filterActive:Boolean=false, actionVisible: Boolean = true, onAction: () -> Unit
) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        if (actionVisible && !isLibrary) LiquidActionGroup(backdrop,surfaceColor,Modifier.testTag("album-toolbar")) {
            IconButton(onClick=onCreateAlbum,modifier=Modifier.size(48.dp).testTag("create-album-button")) {
                Icon(Icons.Rounded.Add,"新建相册",Modifier.size(22.dp),tint=AppTheme.colors.text)
            }
            Spacer(Modifier.width(0.5.dp).height(20.dp).background(AppTheme.colors.separator))
            IconButton(onClick=onAction,modifier=Modifier.size(48.dp).testTag("settings-button")) {
                Icon(Icons.Outlined.Settings,"设置",Modifier.size(22.dp),tint=AppTheme.colors.text)
            }
        }
        if (actionVisible && isLibrary) LiquidButton(
            onClick = onAction, backdrop = backdrop, surfaceColor = surfaceColor,
            shape = CircleShape, horizontalPadding = 0.dp,
            modifier = Modifier.size(48.dp).testTag(if (isLibrary) "filter-button" else "settings-button")
                .semantics { contentDescription = if (isLibrary) "筛选" else "设置" }
        ) {
            Icon(if (isLibrary) Icons.Rounded.FilterList else Icons.Outlined.Settings, null, Modifier.size(22.dp), tint = if(isLibrary && filterActive) AppTheme.colors.accent else AppTheme.colors.text)
        }
    }
}

