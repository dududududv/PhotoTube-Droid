package com.yunai.phototube.ui.remote

import com.yunai.phototube.ui.media.mediaTransitionSource
import com.yunai.phototube.ui.media.mediaBounds

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*
import java.time.*

/** A finite home-feed sequence has no continuation cursor. UI coordinates expire on detach. */
private class MemoryViewerState(var request: ViewerRequest) {
    fun detach() { request = request.copy(bounds = emptyMap(), resolveSource = null) }
}

@Composable
internal fun MemoriesScreen(server:PhotoTubeServer,onLibraryChanged:()->Unit,onClose:()->Unit) {
    val repository=remember(server) {MemoriesRepository(server)}
    var feed by rememberConfigurationState(server) {mutableStateOf<HomeFeed?>(null)}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    var exclusions by rememberSaveable(server) {mutableStateOf(false)}
    var tasks by rememberSaveable(server) {mutableStateOf(false)}
    var album by rememberConfigurationState(server) {mutableStateOf<RemoteAlbum?>(null)}
    var viewer by rememberConfigurationState(server) {mutableStateOf<MemoryViewerState?>(null)}
    val attachedViewer = viewer
    DisposableEffect(attachedViewer) { onDispose { attachedViewer?.detach() } }
    val bounds=remember {mutableMapOf<String,Rect>()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(retry) {lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        loading=true;error=null
        try {val result=repository.home();withContext(Dispatchers.Main.immediate) {feed=result}}
        catch(cancel:CancellationException) {throw cancel}
        catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {error=problem.message}}
        finally {withContext(Dispatchers.Main.immediate) {loading=false}}
    } }
    fun open(asset:RemoteAsset,sequence:List<RemoteAsset>,section:String) {
        val photos=sequence.mapIndexed {i,item -> Photo(-i-1,item.fileName,taskTimestamp(item.takenAt),item.favorite,item.kind=="VIDEO",RemoteMedia(item,server))}
        viewer=MemoryViewerState(ViewerRequest(photos,photos.first {it.remote!!.asset.id==asset.id}.id,photos.mapNotNull {photo -> bounds["$section:${photo.remote!!.asset.id}"]?.let {photo.id to it}}.toMap(), sourceCornerRadius=18.dp, resolveSource={id -> photos.firstOrNull {it.id==id}?.remote?.asset?.id?.let {bounds["$section:$it"]}}))
    }
    ManagementPage("回忆与精选",onClose) {
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text("刷新失败：$it",color=AppTheme.colors.text)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Box(Modifier.width(88.dp)) {GlassSettingsAction("刷新",{retry++},enabled=!loading)}
            Box(Modifier.weight(1f)) {SettingsNavigationRow("回忆日期屏蔽",{exclusions=true})}
        }
        feed?.let {data ->
            listOf("往年今日" to data.memories,"最近拍摄" to data.recent,"最近导入" to data.imports).forEach {(title,assets) ->
                if(assets.isNotEmpty()) MemorySection(title) { coverWidth ->
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        items(assets,key={it.id}) {asset ->
                            Column(Modifier.width(coverWidth).testTag("memory-$title-${asset.id}").clickable {open(asset,assets,title)}) {
                                AsyncImage(server.thumbnail(asset),asset.fileName,imageLoader=server.imageLoader,contentScale=ContentScale.Crop,
                                    modifier=Modifier.fillMaxWidth().aspectRatio(1f).mediaBounds("$title:${asset.id}", bounds).mediaTransitionSource().clip(RoundedCornerShape(18.dp)).background(AppTheme.colors.subtle))
                                Text(taskTimestamp(asset.takenAt).substringBefore(' '),color=AppTheme.colors.secondary,modifier=Modifier.padding(top=8.dp))
                            }
                        }
                    }
                }
            }
            if(data.memories.isEmpty() && data.recent.isEmpty() && data.imports.isEmpty()) Text("暂无可展示的照片",color=AppTheme.colors.secondary)
            if(data.albums.isNotEmpty()) MemorySection("常用相册") { coverWidth ->
                LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(data.albums,key={it.id}) {item -> Column(Modifier.width(coverWidth).testTag("memory-album-${item.id}").clickable {album=item}) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(AppTheme.colors.subtle)) {
                            item.cover?.let {AsyncImage(server.thumbnail(it),item.name,imageLoader=server.imageLoader,contentScale=ContentScale.Crop,alignment=androidx.compose.ui.BiasAlignment(item.focusX*2-1,item.focusY*2-1),modifier=Modifier.fillMaxSize())}
                        }
                        Text(item.name,color=AppTheme.colors.text,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp));Text("${item.count} 项",color=AppTheme.colors.secondary)
                    } }
                }
            }
            ManagementGroup("后台任务") {
                Text("等待 ${data.jobs.sumOf {it.pending}} · 执行中 ${data.jobs.sumOf {it.running}} · 失败 ${data.jobs.sumOf {it.failed}}",color=AppTheme.colors.secondary)
                SettingsNavigationRow("查看任务中心",{tasks=true})
            }
        }
    }
    if(exclusions) MemoryExclusionsScreen(repository,onChanged={retry++},onClose={exclusions=false})
    if(tasks) TasksScreen(server,onClose={tasks=false})
    album?.let {RemoteAlbumDetail(AlbumRepository(server),it,null,onChanged={retry++;onLibraryChanged()},onLibraryChanged=onLibraryChanged,onClose={album=null})}
    viewer?.request?.let {request -> MediaViewer(request,request.media.filter {it.favorite}.map {it.id}.toSet(),onFavorite={},onDelete={},onClose={viewer=null},onRemoteChanged={retry++;onLibraryChanged()})}
}

/** Covers own their shape; a section is not another full-width card. */
@Composable
private fun MemorySection(title: String, content: @Composable (androidx.compose.ui.unit.Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Two covers plus a glimpse of the next item make horizontal browsing discoverable.
        val coverWidth = ((maxWidth - 24.dp) / 2.15f).coerceIn(120.dp, 200.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = AppTheme.colors.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            content(coverWidth)
        }
    }
}

@Composable
internal fun MemoryExclusionsScreen(repository:MemoriesRepository,onChanged:()->Unit,onClose:()->Unit) {
    val flow=remember(repository) {Pager(PagingConfig(100,initialLoadSize=100,maxSize=500,enablePlaceholders=false)) {AlbumPagingSource(repository::list)}.flow}
    val rules=flow.collectAsLazyPagingItems()
    var from by remember {mutableStateOf<LocalDate?>(null)}
    var to by remember {mutableStateOf<LocalDate?>(null)}
    var dateField by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var removing by remember {mutableStateOf<MemoryExclusion?>(null)}
    val scope=rememberCoroutineScope()
    ManagementPage("回忆日期屏蔽",onClose,dismissEnabled=!busy,scrollable=false,overlay={scene ->
        removing?.let {rule -> com.yunai.phototube.ui.GlassConfirmation(scene,title="取消此日期屏蔽？",
            message="这个范围的照片将允许再次出现在往年今日中。",confirmLabel="确认取消屏蔽",cancelLabel="保留屏蔽",
            onDismiss={removing=null},onConfirm={
                removing=null;busy=true;error=null
                scope.launch {
                    try {repository.remove(rule.id);withContext(Dispatchers.Main.immediate) {rules.refresh();onChanged()}}
                    catch(cancel:CancellationException) {throw cancel}
                    catch(problem:Exception) {error=problem.message}
                    finally {busy=false}
                }
            })
        }
    }) {
        Text("仅影响往年今日，不删除照片，也不影响图库、搜索和相册。",color=AppTheme.colors.secondary)
        val valid=from!=null && to!=null && !to!!.isBefore(from)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AppTheme.colors.group).padding(6.dp)) {
            SettingsNavigationRow("开始日期",{dateField="from"},enabled=!busy,value=from?.toString() ?: "请选择")
            SettingsNavigationRow("结束日期",{dateField="to"},enabled=!busy,value=to?.toString() ?: "请选择")
        }
        Text(when {
            valid -> "${from} 至 ${to}\n共 ${java.time.temporal.ChronoUnit.DAYS.between(from,to)+1} 天，包含开始及结束日期"
            from!=null && to!=null -> "结束日期不能早于开始日期"
            from!=null -> "已选开始日期，请选择结束日期"
            to!=null -> "已选结束日期，请选择开始日期"
            else -> "请选择需要从往年今日中屏蔽的日期范围"
        },color=if(from!=null && to!=null && !valid) AppTheme.colors.destructive else AppTheme.colors.secondary,
            modifier=Modifier.testTag("memory-range-summary"))
        error?.let {Text(it,color=AppTheme.colors.text)}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        GlassSettingsAction("屏蔽此日期范围",{val first=from!!;val last=to!!;busy=true;error=null;scope.launch {
            try {repository.create(first,last);withContext(Dispatchers.Main.immediate) {from=null;to=null;rules.refresh();onChanged()}}
            catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {error=problem.message}
            finally {busy=false}
        }},enabled=valid && !busy)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(rules.itemCount,key=rules.itemKey {it.id}) {index -> rules[index]?.let {rule ->
                ManagementGroup(if(rule.kind=="DATE_RANGE") "${rule.from} 至 ${rule.to}" else "其他屏蔽规则") {
                    GlassSettingsAction("取消屏蔽",{removing=rule},enabled=!busy)
                }
            } }
            if(rules.loadState.refresh is LoadState.Loading || rules.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val failure=(rules.loadState.refresh as? LoadState.Error) ?: (rules.loadState.append as? LoadState.Error)
            if(failure!=null) item {Text(failure.error.message ?: "加载失败");GlassSettingsAction("重试",rules::retry,enabled=!busy)}
            if(rules.loadState.refresh is LoadState.NotLoading && rules.itemCount==0) item {Text("尚未屏蔽任何日期",color=AppTheme.colors.secondary)}
        }
    }
    dateField?.let {field -> MemoryDatePicker(if(field=="from") from else to,onClose={dateField=null}) {if(field=="from") from=it else to=it;dateField=null} }

}

@Composable
private fun MemoryDatePicker(initial:LocalDate?,onClose:()->Unit,onSelect:(LocalDate)->Unit) {
    CalendarDatePicker(initial,"屏蔽日期","日期范围包含开始日和结束日。",confirmLabel="选择日期",onClose=onClose,onSelect=onSelect)
}
