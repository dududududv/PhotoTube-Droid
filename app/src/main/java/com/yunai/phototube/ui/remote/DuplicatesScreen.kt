package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSwitchRow
import com.yunai.phototube.ui.GlassSettingsAction
import kotlinx.coroutines.*

@Composable
internal fun DuplicatesScreen(server:PhotoTubeServer,onClose:()->Unit,onLibraryChanged:()->Unit={}) {
    val repository=remember(server) {DuplicatesRepository(server)}
    var includeReviewed by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf(false)}
    var selected by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf<DuplicateGroup?>(null)}
    val flow=rememberConfigurationPaging(server to includeReviewed, enabled = !server.privateMedia) {Pager(PagingConfig(100,initialLoadSize=100,maxSize=500,enablePlaceholders=false)) {AlbumPagingSource {cursor -> repository.groups(includeReviewed,cursor)}}.flow}
    val groups=flow.collectAsLazyPagingItems()
    val context=LocalContext.current
    ManagementPage(if(server.privateMedia) "私密重复照片" else "完全重复",onClose,scrollable=false,showTitle=false) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("duplicate-groups"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item("heading") {
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ManagementTitle(if(server.privateMedia) "私密重复照片" else "完全重复")
                    if(server.privateMedia) Text("仅比较私密空间内的副本，不与普通照片混合计数。",color=AppTheme.colors.secondary)
                    Text("内容完全相同的文件。这里只核对副本和管理提醒，不会自动删除原文件。",color=AppTheme.colors.secondary)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppTheme.colors.group)
                        .padding(horizontal=14.dp,vertical=4.dp)) {
                        GlassSwitchRow("包括已保留的组",includeReviewed,{includeReviewed=it})
                    }
                }
            }
            items(groups.itemCount,key=groups.itemKey {it.hash}) {index -> groups[index]?.let {group ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppTheme.colors.group).clickable {selected=group}.testTag("duplicate-group-${group.hash}").padding(14.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    AsyncImage(repository.thumbnail(group),group.fileName,imageLoader=server.imageLoader,contentScale=ContentScale.Crop,modifier=Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(group.fileName,maxLines=2,color=AppTheme.colors.text)
                        Text("${group.count} 份副本 · 理论可节省 ${android.text.format.Formatter.formatFileSize(context,group.bytes)}",color=AppTheme.colors.secondary)
                        if(group.reviewedAt!=null) Text("已保留全部副本",color=AppTheme.colors.secondary)
                    }
                }
            } }
            if(groups.loadState.refresh is LoadState.Loading || groups.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val error=(groups.loadState.refresh as? LoadState.Error) ?: (groups.loadState.append as? LoadState.Error)
            if(error!=null) item {Text(error.error.message ?: "加载失败");GlassSettingsAction("重试",groups::retry)}
            if(groups.loadState.refresh is LoadState.NotLoading && groups.itemCount==0) item {Text(if(includeReviewed) "没有完全重复的文件" else "没有待处理的重复组",color=AppTheme.colors.secondary)}
        }
        GlassSettingsAction("刷新",groups::refresh,enabled=groups.loadState.refresh !is LoadState.Loading)
    }
    selected?.let {group -> DuplicateMembers(repository,group,onChanged={groups.refresh()},onLibraryChanged=onLibraryChanged,onClose={selected=null;groups.refresh()})}
}

@Composable
private fun DuplicateMembers(repository:DuplicatesRepository,group:DuplicateGroup,onChanged:()->Unit,onLibraryChanged:()->Unit,onClose:()->Unit) {
    val flow=rememberConfigurationPaging(repository.server to group.hash, enabled = !repository.server.privateMedia) {Pager(PagingConfig(100,initialLoadSize=100,maxSize=500,enablePlaceholders=false)) {AlbumPagingSource {cursor -> repository.members(group.hash,cursor)}}.flow}
    val members=flow.collectAsLazyPagingItems()
    var reviewed by rememberConfigurationState(repository.server to group.hash, enabled = !repository.server.privateMedia) {mutableStateOf(group.reviewedAt!=null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var viewer by rememberConfigurationState(repository.server to group.hash, enabled = !repository.server.privateMedia) {mutableStateOf<PagedViewerSession?>(null)}
    BindPagedViewer(viewer, members) { it }
    val scope=rememberCoroutineScope()
    ManagementPage("重复组",onClose,dismissEnabled=!busy,scrollable=false,showTitle=false) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("duplicate-members"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item("heading") {
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ManagementTitle("重复组")
                    Text("同组文件内容完全一致；保留操作只关闭提醒。有新副本时会再次提醒。",color=AppTheme.colors.secondary)
                    Text("最近变化：${taskTimestamp(group.changedAt)}",color=AppTheme.colors.secondary)
                    error?.let {Text(it,color=AppTheme.colors.text)}
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            items(members.itemCount,key=members.itemKey {it.id}) {index -> (if(viewer!=null) members.peek(index) else members[index])?.let {asset ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppTheme.colors.group).clickable(enabled=!busy) {
                    viewer=PagedViewerSession.create(members,{it},repository.server,asset,emptyMap())
                }.testTag("duplicate-media-${asset.id}").padding(14.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    AsyncImage(repository.server.thumbnail(asset),asset.fileName,imageLoader=repository.server.imageLoader,contentScale=ContentScale.Crop,modifier=Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f)) {Text(asset.fileName,color=AppTheme.colors.text);Text(taskTimestamp(asset.takenAt),color=AppTheme.colors.secondary);Text("查看详情与文件信息",color=AppTheme.colors.secondary)}
                }
            } }
            if(members.loadState.refresh is LoadState.Loading || members.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val loadError=(members.loadState.refresh as? LoadState.Error) ?: (members.loadState.append as? LoadState.Error)
            if(loadError!=null) item {Text(loadError.error.message ?: "加载失败");GlassSettingsAction("重试",members::retry,enabled=!busy)}
            if(members.loadState.refresh is LoadState.NotLoading && members.itemCount==0) item {Text("此组已不足两项，请返回刷新重复组。",color=AppTheme.colors.secondary)}
        }
        GlassSettingsAction(if(reviewed) "恢复重复提醒" else "保留全部副本",{val keep=!reviewed;busy=true;error=null;scope.launch {
            try {repository.review(group.hash,keep);withContext(Dispatchers.Main.immediate) {reviewed=keep;onChanged()}}
            catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {error=problem.message}
            finally {busy=false}
        }},enabled=!busy && members.itemCount>0)
    }
    viewer?.let {request -> PagedMediaViewer(request,onClose={viewer=null},onRemoteChanged={members.refresh();onChanged();onLibraryChanged()})}
}
