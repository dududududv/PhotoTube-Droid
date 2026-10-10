package com.yunai.phototube.ui.remote

import com.yunai.phototube.ui.media.mediaBounds
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.GlassSettingsAction

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.ui.res.painterResource
import com.yunai.phototube.R
import com.yunai.phototube.ui.icons.LibraryFolder
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.*
import com.yunai.phototube.ui.GlassActionMenu
import com.yunai.phototube.ui.GlassMenuAction
import com.yunai.phototube.data.*
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONObject
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState

@Composable
internal fun RemoteAlbums(server:PhotoTubeServer?,ready:Boolean,topInset:Dp,modifier:Modifier=Modifier,query:String="",searchActive:Boolean=false,onLibraryChanged:()->Unit={},revision:Int=0,createRequested:Boolean=false,onCreateConsumed:()->Unit={},onLibraryTool:(LibraryTool)->Unit={},onConnect:()->Unit) {
    if(server==null || !ready) {
        Column(modifier.fillMaxSize().padding(horizontal=24.dp).padding(top=topInset+64.dp)) {
            Text("相册",fontSize=30.sp,color=AppTheme.colors.text)
            Text("连接服务器后查看你的相册",color=AppTheme.colors.secondary)
            com.yunai.phototube.ui.GlassSettingsAction("连接服务器",onConnect)
        }
        return
    }
    val repository=remember(server) {AlbumRepository(server)}
    val keyword=query.trim()
    var appliedQuery by remember {mutableStateOf("")}
    LaunchedEffect(keyword) {
        if(keyword.isNotEmpty()) delay(300)
        appliedQuery=keyword
    }
    // The backend only exposes album listing. Filter the paged stream, including
    // later pages, while preserving its order; never send an invented query parameter.
    val flow=remember(repository,appliedQuery) {
        val selectedQuery=appliedQuery
        Pager(PagingConfig(100,initialLoadSize=100,prefetchDistance=12,maxSize=500,enablePlaceholders=false)) {
            AlbumPagingSource { cursor ->
                val page=repository.list(cursor)
                page.copy(items=page.items.filter {it.name.contains(selectedQuery,ignoreCase=true)})
            }
        }.flow
    }
    var albumOrder by rememberSaveable {mutableStateOf(AlbumOrder.DEFAULT)}
    val browsingFlow=remember(repository,albumOrder) {
        Pager(PagingConfig(100,initialLoadSize=100,prefetchDistance=12,enablePlaceholders=false)) {
            AlbumPagingSource {cursor ->
                if(albumOrder==AlbumOrder.DEFAULT) repository.list(cursor)
                else AlbumPage(loadOrderedAlbums(albumOrder,repository::list),null)
            }
        }.flow
    }
    val browsingAlbums=browsingFlow.collectAsLazyPagingItems()
    val searching=searchActive && keyword!=appliedQuery
    val albums=if(searchActive) flow.collectAsLazyPagingItems() else browsingAlbums
    LaunchedEffect(revision) {if(revision>0) {browsingAlbums.refresh();if(searchActive) albums.refresh()}}
    val browsingState=rememberLazyGridState()
    val searchState=rememberLazyGridState()
    val gridState=if(searchActive) searchState else browsingState
    LaunchedEffect(appliedQuery,searchActive) {if(searchActive) searchState.scrollToItem(0)}
    var creating by rememberSaveable(server) {mutableStateOf(false)}
    LaunchedEffect(createRequested) {if(createRequested) {creating=true;onCreateConsumed()}}
    var selected by rememberConfigurationState(server) {mutableStateOf<RemoteAlbum?>(null)}
    val coverBounds=remember(appliedQuery) {mutableMapOf<String,Rect>()}
    var selectedBounds by remember {mutableStateOf<Rect?>(null)}
    val coverPainters=remember {mutableMapOf<Pair<String,String?>,Painter>()}
    var selectedCoverPainter by remember {mutableStateOf<Painter?>(null)}
    val openAlbum:(RemoteAlbum)->Unit={album ->
        selectedBounds=coverBounds[album.id]
        selectedCoverPainter=coverPainters[album.id to album.cover?.id]
        selected=album
    }
    val tile:@Composable (RemoteAlbum,Modifier)->Unit={album,tileModifier ->
        AlbumShelfCover(album,server,tileModifier,coverBounds,coverPainters) {openAlbum(album)}
    }
    val scene=rememberLayerBackdrop()
    var sortOpen by remember {mutableStateOf(false)}
    LaunchedEffect(albumOrder) {browsingState.scrollToItem(0)}
    LazyVerticalGrid(GridCells.Fixed(2),modifier.fillMaxSize().layerBackdrop(scene).testTag("remote-albums"),state=gridState,
        contentPadding=PaddingValues(start=20.dp,end=20.dp,top=topInset,bottom=140.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item(key="header",span={GridItemSpan(maxLineSpan)}) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(end=112.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("相册",fontSize=30.sp,fontWeight=FontWeight.Bold,color=AppTheme.colors.text,maxLines=1)
            }
        }
        if(!searchActive) item(key="album-heading",span={GridItemSpan(maxLineSpan)}) {
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("我的相册",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=AppTheme.colors.text,modifier=Modifier.weight(1f))
                if(albums.loadState.refresh is LoadState.NotLoading) Text(
                    "${albums.itemCount}${if(albums.loadState.append.endOfPaginationReached) "" else "+"} 个",
                    fontSize=13.sp,color=AppTheme.colors.secondary)
                Spacer(Modifier.width(10.dp))
                Box {
                    IconButton(onClick={sortOpen=true},modifier=Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.subtle)) {
                        Icon(painterResource(R.drawable.ic_album_arrow_down_wide_narrow),"相册排序",Modifier.size(21.dp),tint=AppTheme.colors.text)
                    }
                    GlassActionMenu(sortOpen,{sortOpen=false},scene,AlbumOrder.entries.map {order ->
                        GlassMenuAction(order.label,icon=if(albumOrder==order) Icons.Rounded.Check else null) {albumOrder=order}
                    })
                }
            }
        }
        if(searching) item(span={GridItemSpan(maxLineSpan)}) {Text("正在匹配相册名称…",color=AppTheme.colors.secondary)}
        items(if(searching) 0 else albums.itemCount,key=albums.itemKey {it.id}) {index ->
            albums[index]?.let {tile(it,Modifier.fillMaxWidth())}
        }
        item(key="status",span={GridItemSpan(maxLineSpan)}) {AlbumPageStatus(albums,searching,searchActive && appliedQuery.isNotEmpty())}
        if(!searchActive) item(key="utilities",span={GridItemSpan(maxLineSpan)}) {
            Column(Modifier.fillMaxWidth().testTag("album-utilities"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("更多项目",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=AppTheme.colors.text,modifier=Modifier.padding(bottom=8.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AlbumUtility("收藏",R.drawable.ic_album_heart,Modifier.weight(1f)) {onLibraryTool(LibraryTool.FAVORITES)}
                    AlbumUtility("标签",R.drawable.ic_album_tag,Modifier.weight(1f)) {onLibraryTool(LibraryTool.TAGS)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AlbumUtility("私密空间",R.drawable.ic_album_lock_keyhole,Modifier.weight(1f)) {onLibraryTool(LibraryTool.PRIVATE)}
                    AlbumUtility("回收站",R.drawable.ic_album_trash_2,Modifier.weight(1f)) {onLibraryTool(LibraryTool.TRASH)}
                }
            }
        }
    }
    if(creating) CreateRemoteAlbum(repository,onClose={creating=false;albums.refresh()},onCreated={creating=false;albums.refresh();if(searchActive) browsingAlbums.refresh();selectedBounds=null;selectedCoverPainter=null;selected=it})
    selected?.let {album -> key(album.id) {RemoteAlbumDetail(repository,album,selectedBounds,onChanged={albums.refresh();if(searchActive) browsingAlbums.refresh()},onLibraryChanged=onLibraryChanged,onClose={selected=null;selectedCoverPainter=null},resolveSource={coverBounds[album.id]},sourceCoverPainter=selectedCoverPainter)} }
}
/** Covers remain real image nodes so the existing shared-cover transition can reuse their painter. */
@Composable
private fun AlbumShelfCover(album:RemoteAlbum,server:PhotoTubeServer,modifier:Modifier,
    bounds:MutableMap<String,Rect>,painters:MutableMap<Pair<String,String?>,Painter>,onClick:()->Unit) {
    val coverKey=album.id to album.cover?.id
    DisposableEffect(coverKey) {onDispose {painters.remove(coverKey)}}
    Column(modifier.testTag("album-${album.id}").clickable(role=Role.Button,onClick=onClick)
        .semantics(mergeDescendants=true) {contentDescription="${album.name}，${album.count} 项"}) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).mediaBounds(album.id,bounds)
            .clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.subtle)) {
            Box(Modifier.fillMaxSize().testTag("remote-album-cover-${album.id}")) {
                val url=album.cover?.let(server::thumbnail)
                if(url!=null) AsyncImage(url,null,imageLoader=server.imageLoader,contentScale=ContentScale.Crop,
                    alignment=BiasAlignment(album.focusX*2-1,album.focusY*2-1),modifier=Modifier.fillMaxSize(),
                    onSuccess={painters[coverKey]=it.painter})
                else AlbumCoverPlaceholder(album,Modifier.fillMaxSize())
            }
        }
        Text(album.name,color=AppTheme.colors.text,fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold,
            modifier=Modifier.padding(top=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
        // The contract provides a combined assetCount, not separate photo/video totals.
        Text("${album.count} 项",color=AppTheme.colors.secondary,fontSize=13.sp,lineHeight=19.sp)
    }
}

@Composable
private fun AlbumUtility(label:String,icon:Int,modifier:Modifier=Modifier,onClick:()->Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.subtle)
        .clickable(role=Role.Button,onClick=onClick).heightIn(min=50.dp).padding(horizontal=14.dp,vertical=12.dp)
        .testTag("album-utility-$label"),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(icon),null,Modifier.size(22.dp),tint=AppTheme.colors.secondary)
        Text(label,color=AppTheme.colors.text,fontSize=15.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun AlbumPageStatus(albums:LazyPagingItems<RemoteAlbum>,searching:Boolean,hasQuery:Boolean) {
    when {
        searching -> Unit
        albums.loadState.refresh is LoadState.Error -> AlbumFailure((albums.loadState.refresh as LoadState.Error).error,albums::retry)
        albums.loadState.append is LoadState.Error -> AlbumFailure((albums.loadState.append as LoadState.Error).error,albums::retry)
        albums.loadState.refresh is LoadState.Loading || albums.loadState.append is LoadState.Loading -> AlbumLoading()
        albums.itemCount==0 && albums.loadState.append.endOfPaginationReached ->
            Text(if(hasQuery) "没有符合名称的相册" else "还没有相册",color=AppTheme.colors.secondary,modifier=Modifier.padding(24.dp))
    }
}

/** Same empty/original-only cover in the grid and transition, including its text size. */
@Composable
internal fun AlbumCoverPlaceholder(album:RemoteAlbum,modifier:Modifier=Modifier,sourceWidth:Float?=null,progress:()->Float={1f}) {
    BoxWithConstraints(modifier.background(AppTheme.colors.subtle),contentAlignment=Alignment.Center) {
        val startScale=sourceWidth?.div(constraints.maxWidth.toFloat())?.coerceIn(.01f,1f) ?: 1f
        Text(if(album.cover?.originalOnlyFormat!=null) "${album.cover.originalOnlyFormat!!.uppercase(java.util.Locale.ROOT)} 原文件"
            else if(album.cover!=null) "封面处理中" else if(album.count==0L) "空相册" else "未设置封面",
            color=AppTheme.colors.secondary,modifier=Modifier.graphicsLayer {
                val scale=startScale+(1f-startScale)*progress().coerceIn(0f,1f)
                scaleX=1f/scale;scaleY=scaleX
            })
    }
}

internal fun albumKindLabel(kind:String)=when(kind) {"PATH_SYNC"->"文件夹相册";"SMART"->"智能相册";else->"普通相册"}
@Composable private fun AlbumLoading() {Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator(Modifier.size(24.dp))}}
@Composable private fun AlbumFailure(error:Throwable,retry:()->Unit) {Column(Modifier.padding(20.dp)) {Text(error.message ?: "加载失败",color=AppTheme.colors.text);com.yunai.phototube.ui.GlassSettingsAction("重试",retry)}}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RemoteAlbumDetail(repository:AlbumRepository,initial:RemoteAlbum,sourceBounds:Rect?,onChanged:()->Unit,onLibraryChanged:()->Unit,onClose:()->Unit,resolveSource:(()->Rect?)?=null,sourceCoverPainter:Painter?=null) {
    var sorting by rememberSaveable(initial.id) {mutableStateOf(false)}
    var organizing by rememberSaveable(initial.id) { mutableStateOf(false) }
    val detailState=rememberConfigurationState(repository.server to initial.id) {AlbumDetailState(repository,initial)}
    var album by detailState.album
    var generation by detailState.generation
    var columns by rememberSaveable {mutableIntStateOf(3)}
    var menu by remember {mutableStateOf(false)}
    var editing by rememberSaveable(initial.id) {mutableStateOf(false)}
    var renameSaved by rememberSaveable(initial.id) {mutableStateOf(false)}
    var name by rememberSaveable(initial.id) {mutableStateOf(initial.name)}
    var pathsOpen by rememberSaveable(initial.id) {mutableStateOf(false)}
    var adding by rememberSaveable(initial.id) {mutableStateOf(false)}
    var removing by rememberSaveable(initial.id) {mutableStateOf(false)}
    var removalAnchor by rememberSaveable(initial.id) {mutableStateOf<String?>(null)}
    var firstVisibleAssetId by remember {mutableStateOf<String?>(null)}
    var actions by remember {mutableStateOf<RemoteAsset?>(null)}
    var cover by rememberConfigurationState(repository.server to initial.id) {mutableStateOf<RemoteAsset?>(null)}
    var rulesOpen by rememberSaveable(initial.id) {mutableStateOf(false)}
    var promptOpen by rememberSaveable(initial.id) {mutableStateOf(false)}
    var aiOpen by rememberSaveable(initial.id) {mutableStateOf(false)}
    var ruleScanOpen by remember {mutableStateOf(false)}
    var scanning by rememberSaveable(initial.id) {mutableStateOf(false)}
    var scanHistory by rememberSaveable(initial.id) {mutableStateOf(false)}
    var cleanupExclusive by rememberSaveable(initial.id) { mutableStateOf(false) }
    var confirmDelete by remember {mutableStateOf(false)}
    var busy by detailState.busy
    var error by remember {mutableStateOf<Throwable?>(null)}
    var viewer by rememberConfigurationState(repository.server to initial.id) {mutableStateOf<PagedViewerSession?>(null)}
    val scope=rememberCoroutineScope()
    var presented by rememberSaveable {mutableStateOf(false)}
    val transition=remember {Animatable(if(presented) 1f else 0f)}
    var closing by rememberSaveable {mutableStateOf(false)}
    val latestOnClose by rememberUpdatedState(onClose)
    val latestSourceResolver by rememberUpdatedState(resolveSource)
    var transitionSource by remember {mutableStateOf(sourceBounds)}
    var fadingFrom by remember {mutableStateOf<Float?>(null)}
    fun close() {
        if(closing) return
        // A resolver returning null means the cover is no longer attached. Never
        // fall back to the stale opening rectangle after rotation or paging.
        val destination=(latestSourceResolver?.let {it()} ?: sourceBounds.takeIf {latestSourceResolver==null})
            ?.takeIf {it.width>0f && it.height>0f}
        if(destination!=null) transitionSource=destination else fadingFrom=transition.value
        closing=true
    }
    val members=detailState.members.collectAsLazyPagingItems()
    BindPagedViewer(viewer,members) {it}
    val bounds=remember {mutableMapOf<String,Rect>()}
    fun update(body:JSONObject,onSaved:()->Unit = {}) {
        if(busy) return
        busy=true;error=null
        scope.launch {try {album=repository.update(album.id,body);generation++;onChanged();onSaved()} catch(cancel:CancellationException){throw cancel} catch(problem:Exception){error=problem} finally {busy=false}}
    }
    fun removeMember(asset:RemoteAsset) {
        if(busy || album.kind!="NORMAL") return
        busy=true;error=null
        scope.launch {
            try {
                val result=repository.changeMembers(album.id,setOf(asset.id),remove=true)
                val failed=result.getJSONArray("failed")
                if(failed.length()>0) error=ServerError("MEMBER_REMOVE_FAILED",failed.getJSONObject(0).getString("message"))
                generation++;onChanged()
            } catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {error=problem}
            finally {busy=false}
        }
    }
    Dialog(onDismissRequest=::close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        ThemeSystemBars()
        val view=LocalView.current
        SideEffect {(view.parent as? DialogWindowProvider)?.window?.apply {setDimAmount(0f);setWindowAnimations(0)}}
        // One coroutine owns both directions. Layout callbacks must not gate
        // presentation: a missed state update would leave the page clipped to its cover.
        LaunchedEffect(closing) {
            if(closing) {
                transition.animateTo(0f,tween(300,easing=FastOutSlowInEasing))
                latestOnClose()
            } else {
                // A frame-clock tick precedes drawing. Starting the spring there
                // lets window creation and the first grid render consume its
                // early frames, so the cover can jump straight to a large page.
                // Keep the source-sized cover until this window submits pixels.
                awaitAlbumFirstFrame(view)
                withFrameNanos { }
                transition.animateTo(1f,spring(dampingRatio=1f,stiffness=380f))
                presented=true
            }
        }
        val scene=rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().testTag("album-detail")
            .graphicsLayer {
                val p=(fadingFrom ?: transition.value).coerceIn(0f,1f)
                val fadeAlpha=fadingFrom?.let {start -> (transition.value/start.coerceAtLeast(.001f)).coerceIn(0f,1f)}
                val source=transitionSource
                if(source==null) {
                    alpha=fadeAlpha ?: p
                    scaleX=1f;scaleY=1f
                    translationX=0f;translationY=0f
                    clip=false
                    return@graphicsLayer
                }
                alpha=fadeAlpha ?: 1f
                val startScale=(source.width/size.width).coerceIn(.01f,1f)
                val scale=startScale+(1f-startScale)*p
                transformOrigin=TransformOrigin(0f,0f)
                scaleX=scale;scaleY=scale
                translationX=source.left*(1f-p);translationY=source.top*(1f-p)
                // Uniform scaling keeps text and icons undistorted; the viewport
                // grows from the square cover to the full-height album page.
                val visibleHeight=(source.height+(size.height-source.height)*p)/scale
                val radius=16.dp.toPx()*(1f-p)/scale
                shape=GenericShape {layerSize,_ ->
                    addRoundRect(RoundRect(0f,0f,layerSize.width,visibleHeight,CornerRadius(radius)))
                }
                clip=true
            }
            .background(AppTheme.colors.background)) {
            val coverModifier=Modifier.fillMaxWidth().aspectRatio(1f)
                .graphicsLayer {alpha=(1f-transition.value/.6f).coerceIn(0f,1f)}
            val coverAsset=album.cover
            val coverUrl=coverAsset?.let(repository.server::thumbnail)
            if(coverUrl!=null) {
                val focus=BiasAlignment(album.focusX*2-1,album.focusY*2-1)
                if(sourceCoverPainter!=null && coverAsset.id==initial.cover?.id) {
                    // Reuse pixels already visible in the source card; do not wait
                    // for a larger AsyncImage request during the opening frames.
                    Image(sourceCoverPainter,null,contentScale=ContentScale.Crop,alignment=focus,modifier=coverModifier)
                } else AsyncImage(coverUrl,null,imageLoader=repository.server.imageLoader,
                    contentScale=ContentScale.Crop,alignment=focus,modifier=coverModifier)
            } else AlbumCoverPlaceholder(album,coverModifier,transitionSource?.width) {
                fadingFrom ?: transition.value
            }
            BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer {alpha=((transition.value-.12f)/.6f).coerceIn(0f,1f)}) {
                val listState=rememberLazyListState()
                val snapshot=members.itemSnapshotList
                val rowWidth=(maxWidth-PhotoWallMetrics.horizontalInset*2).value.coerceAtLeast(1f)
                val rows=remember(snapshot,rowWidth,columns) {
                    photoWallRows(snapshot.items.map {WallEntry.Asset(it)},rowWidth,rowWidth/columns,PhotoWallMetrics.gap.value)
                        .filterIsInstance<PhotoWallRow.Photos>()
                }
                val rowMap=remember(rows) {rows.associateBy {it.key}}
                LaunchedEffect(rowMap,viewer,listState) {
                    if(viewer!=null) return@LaunchedEffect
                    snapshotFlow {listState.layoutInfo.visibleItemsInfo.map {it.key}}.collect { keys ->
                        firstVisibleAssetId=keys.firstNotNullOfOrNull {key -> rowMap[key]?.photos?.firstOrNull()?.asset?.id}
                        keys.forEach {key -> rowMap[key]?.photos?.forEach { photo ->
                            if(members.itemSnapshotList.items.getOrNull(photo.index)?.id==photo.asset.id) members[photo.index]
                        } }
                    }
                }
                LazyColumn(state=listState,modifier=Modifier.fillMaxSize().layerBackdrop(scene).background(AppTheme.colors.background).testTag("album-grid"),
                    contentPadding=PaddingValues(start=PhotoWallMetrics.horizontalInset,end=PhotoWallMetrics.horizontalInset,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(PhotoWallMetrics.gap)) {
                    item(key="album-title") {Column(Modifier.statusBarsPadding().padding(top=72.dp,bottom=8.dp)) {
                        Text(album.name,color=AppTheme.colors.text,fontSize=30.sp,fontWeight=FontWeight.Bold)
                        Text("${album.count} 项 · ${albumKindLabel(album.kind)}",color=AppTheme.colors.secondary)
                        error?.takeIf {!editing}?.let {Text(it.message ?: "操作失败",color=AppTheme.colors.text)}
                        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }}
                    items(rows.size,key={rows[it].key}) { rowIndex ->
                        val row=rows[rowIndex]
                        Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(PhotoWallMetrics.gap)) {
                            row.photos.forEach { photo ->
                                val asset=photo.asset
                                Box(Modifier.width((row.height*photo.ratio).dp).fillMaxHeight().mediaBounds(asset.id,bounds)
                                    .combinedClickable(onLongClick={actions=asset},onClick={viewer=PagedViewerSession.create(members,{it},repository.server,asset,bounds)})
                                    .testTag("album-media-${asset.id}")) {
                                    AssetThumbnail(asset,repository.server,Modifier.fillMaxSize())
                                    GlassActionMenu(actions?.id==asset.id, {actions=null}, scene, buildList {
                                        add(GlassMenuAction("设为相册封面", enabled=!busy, icon=Icons.Rounded.Edit) {cover=asset})
                                        if(album.kind=="NORMAL") add(GlassMenuAction("从相册移除（保留原文件）", enabled=!busy,
                                            destructive=true, icon=Icons.Rounded.Clear) {removeMember(asset)})
                                    }, controls={
                                        Text(asset.fileName,color=AppTheme.colors.secondary,fontSize=12.sp,maxLines=2,
                                            overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp))
                                    })
                                }
                            }
                        }
                    }
                    if(members.loadState.refresh is LoadState.Loading || members.loadState.append is LoadState.Loading) item {AlbumLoading()}
                    val failure=(members.loadState.refresh as? LoadState.Error) ?: (members.loadState.append as? LoadState.Error)
                    if(failure!=null) item {AlbumFailure(failure.error,members::retry)}
                    if(members.loadState.refresh is LoadState.NotLoading && members.itemCount==0) item {Text("相册中还没有内容",color=AppTheme.colors.secondary,modifier=Modifier.padding(24.dp))}
                }
            }
            Row(Modifier.fillMaxWidth().graphicsLayer {alpha=((transition.value-.25f)/.55f).coerceIn(0f,1f)}.statusBarsPadding().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                LiquidButton(::close,scene,Modifier.size(48.dp).semantics {contentDescription="返回相册列表"},shape=CircleShape,horizontalPadding=0.dp,surfaceColor=AppTheme.colors.glass.copy(.4f)) {MediaIcon(MediaSymbol.BACK,AppTheme.colors.text)}
                Spacer(Modifier.weight(1f))
                if(album.kind=="NORMAL") {
                    Box(Modifier.width(76.dp)) { GlassSettingsAction("添加", {adding=true}, enabled=!busy, sourceBackdrop=scene) }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.width(76.dp)) { GlassSettingsAction("选择", {removalAnchor=firstVisibleAssetId;removing=true}, enabled=!busy, sourceBackdrop=scene) }
                    Spacer(Modifier.width(8.dp))
                }
                Box {
                    LiquidButton({menu=true},scene,Modifier.size(48.dp).semantics {contentDescription="相册选项"},shape=CircleShape,horizontalPadding=0.dp,surfaceColor=AppTheme.colors.glass.copy(.4f)) {MediaIcon(MediaSymbol.MORE,AppTheme.colors.text)}
                    GlassActionMenu(menu, {menu=false}, scene, buildList {
                        if(album.kind!="SMART") add(GlassMenuAction("排序 · "+if(album.sortMode=="TAKEN_AT_DESC") "最新在前" else "最早在前", section="显示", icon=Icons.AutoMirrored.Rounded.List, enabled=!busy, onClick={sorting=true}))
                        add(GlassMenuAction(if(columns==3) "放大缩略图" else "缩小缩略图", section="显示", icon=Icons.Rounded.Search, onClick={columns=if(columns==3) 2 else 3;menu=false}))
                        add(GlassMenuAction("重命名", section="相册", icon=Icons.Rounded.Edit, enabled=!busy, onClick={name=album.name;renameSaved=false;error=null;editing=true;menu=false}))
                        if(album.kind=="PATH_SYNC") add(GlassMenuAction("管理文件夹", section="相册", icon=Icons.Rounded.LibraryFolder, onClick={menu=false;pathsOpen=true}))
                        if(album.kind=="SMART") add(GlassMenuAction("智能相册规则", section="相册", icon=Icons.Rounded.Settings, onClick={menu=false;rulesOpen=true}))
                        add(GlassMenuAction("清除自定义封面", section="相册", icon=Icons.Rounded.Refresh, enabled=album.raw.nullString("coverAssetId")!=null && !busy, onClick={menu=false;update(JSONObject().put("coverAssetId",JSONObject.NULL))}))
                        add(GlassMenuAction("整理与 AI", section="管理", icon=Icons.Rounded.Settings, onClick={menu=false;organizing=true}))
                        add(GlassMenuAction("删除相册", section="删除", icon=Icons.Rounded.Delete, enabled=!busy, destructive=true, onClick={menu=false;cleanupExclusive=false;confirmDelete=true}))
                    })
                    GlassActionMenu(sorting,{sorting=false},scene,listOf(
                        GlassMenuAction("最新在前",icon=if(album.sortMode=="TAKEN_AT_DESC") Icons.Rounded.Check else null) {update(JSONObject().put("sortMode","TAKEN_AT_DESC"))},
                        GlassMenuAction("最早在前",icon=if(album.sortMode=="TAKEN_AT_ASC") Icons.Rounded.Check else null) {update(JSONObject().put("sortMode","TAKEN_AT_ASC"))}
                    ))
                }
            }
        }
        if(organizing) ManagementPage("整理与 AI", onClose={organizing=false}, compactHeader=true) {
            Text(album.name, color=AppTheme.colors.secondary)
            SettingsNavigationRow("规则扫描", {ruleScanOpen=true})
            SettingsNavigationRow("AI 打标与任务", {aiOpen=true})
            SettingsNavigationRow("AI 提示词", {promptOpen=true})
            if(album.kind=="PATH_SYNC") {
                SettingsNavigationRow("最近一次扫描", {scanHistory=true})
                SettingsNavigationRow("手动扫描文件夹", {scanning=true})
            }
        }
        if(pathsOpen) AlbumPaths(repository,album,onChanged={generation++;onChanged()},onClose={pathsOpen=false;generation++})
        if(removing) AlbumMemberPicker(repository,album,onChanged={generation++;onChanged()},onClose={removing=false},remove=true,
            sourceItems=members,initialAssetId=removalAnchor,columns=columns)
        if(adding) AlbumMemberPicker(repository,album,onChanged={generation++;onChanged()},onClose={adding=false})
        cover?.let {asset -> AlbumCoverEditor(repository,album,asset,onSaved={album=it;cover=null;onChanged()},onClose={cover=null})}
        if(rulesOpen) SmartAlbumEditor(repository,album,onClose={rulesOpen=false},onSaved={album=it;rulesOpen=false;generation++;onChanged()})
        if(ruleScanOpen) RuleScanScreen(repository.server,album,onChanged={generation++;onChanged();onLibraryChanged()},onClose={ruleScanOpen=false})
        if(aiOpen) AlbumAiScreen(repository.server,album,onChanged={generation++;onChanged();onLibraryChanged()},onClose={aiOpen=false})
        if(promptOpen) AlbumPrompts(repository,album,onSaved={album=it;promptOpen=false;onChanged()},onClose={promptOpen=false})
        if(scanHistory) AlbumScanHistory(repository,album,onClose={scanHistory=false})
        if(scanning) AlbumScanDialog(repository,album,onClose={scanning=false;generation++;onChanged();onLibraryChanged()})
        if(editing) com.yunai.phototube.ui.GlassConfirmation(scene,title="重命名相册",message="",confirmLabel="保存",
            busy=busy,confirmEnabled=name.trim().length in 1..120,closeOnConfirm=false,closeRequested=renameSaved,
            onDismiss={editing=false;renameSaved=false},details={enabled ->
                com.yunai.phototube.ui.GlassTextField(name,{name=it},"相册名称",enabled=enabled)
                error?.let {Text(it.message ?: "保存失败",color=AppTheme.colors.text)}
            },onConfirm={update(JSONObject().put("name",name.trim())) {renameSaved=true}})
        if(confirmDelete) com.yunai.phototube.ui.GlassConfirmation(scene,title="删除“${album.name}”？",
            message="默认保留照片记录。两种方式都不会删除 NAS 原文件。",confirmLabel="删除相册",destructive=true,
            onDismiss={confirmDelete=false},details={enabled ->
                if(album.kind!="SMART") {
                    com.yunai.phototube.ui.GlassOptionRow("同时清理此相册独占的照片记录",cleanupExclusive,{cleanupExclusive=it},enabled=enabled)
                    if(cleanupExclusive) {
                        Text("独占照片记录的数量尚未预估。",color=AppTheme.colors.warning)
                        Text("照片及视频的标签、编辑等附属记录也会清理，不进入回收站。其他相册共享的照片与全局标签保留；重新扫描不会恢复已清理的信息。")
                    }
                }
            },onConfirm={confirmDelete=false;busy=true;scope.launch {
                try {repository.delete(album.id,cleanupExclusive && album.kind!="SMART");onChanged();close()}
                catch(cancel:CancellationException) {throw cancel}
                catch(problem:Exception) {error=problem}
                finally {busy=false}
            }})
        viewer?.let {request -> PagedMediaViewer(request,onClose={viewer=null},onRemoteChanged={generation++;onChanged();onLibraryChanged()})}
    }
}
