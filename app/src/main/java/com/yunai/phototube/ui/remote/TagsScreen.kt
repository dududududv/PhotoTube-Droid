package com.yunai.phototube.ui.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.ui.GlassActionMenu
import com.yunai.phototube.ui.GlassMenuAction
import com.yunai.phototube.ui.media.MediaIcon
import com.yunai.phototube.ui.media.MediaSymbol
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.RemoteMedia
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TagsScreen(server: PhotoTubeServer, onClose: () -> Unit, onChanged: () -> Unit = {}, onPick: ((RemoteTag) -> Unit)? = null) {
    val repository = remember(server) { TagRepository(server) }
    val catalog = remember(server) { FilterCatalog(server) }
    var keyword by rememberSaveable(server) { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(keyword) { delay(300); query = keyword.trim() }
    val flow = remember(query) { Pager(PagingConfig(100, initialLoadSize = 100, maxSize = 500, enablePlaceholders = false)) { AlbumPagingSource { cursor -> catalog.tags(query, cursor) } }.flow }
    val tags = flow.collectAsLazyPagingItems()
    var editing by rememberSaveable(server) { mutableStateOf(false) }
    var cleanupOpen by rememberSaveable(server) { mutableStateOf(false) }
    var rulesOpen by rememberSaveable(server) { mutableStateOf(false) }
    var target by rememberConfigurationState(server) { mutableStateOf<RemoteTag?>(null) }
    var browsing by rememberConfigurationState(server) { mutableStateOf<RemoteTag?>(null) }
    var name by rememberSaveable(server) { mutableStateOf("") }
    var deleting by remember { mutableStateOf<RemoteTag?>(null) }
    var savedTag by rememberConfigurationState(server) { mutableStateOf<RemoteTag?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listBackdrop = rememberLayerBackdrop()
    val title = if (onPick == null) "标签库" else "添加标签"
    ManagementPage(title, onClose, dismissEnabled = !busy, scrollable = false, showTitle = false, overlay = { scene ->
        if(editing) com.yunai.phototube.ui.GlassConfirmation(scene,
            title=if(target==null) "新建标签" else "重命名标签", message="", confirmLabel="保存标签",
            busy=busy, confirmEnabled=name.trim().length in 1..120, closeOnConfirm=false, closeRequested=savedTag!=null,
            onDismiss={
                editing=false
                val completed=savedTag
                savedTag=null
                if(completed!=null && onPick!=null && target==null) onPick(completed)
            }, details={enabled ->
                com.yunai.phototube.ui.GlassTextField(name,{name=it},"标签名称",enabled=enabled)
                error?.let { Text(it,color=AppTheme.colors.text) }
            }, onConfirm={
                if(!busy) {
                    busy=true;error=null
                    scope.launch {
                        try {
                            val tag=target?.let {repository.rename(it.id,name)} ?: repository.create(name)
                            savedTag=tag;tags.refresh();onChanged()
                        } catch(cancel:CancellationException) {throw cancel}
                        catch(problem:Exception) {error=problem.message}
                        finally {busy=false}
                    }
                }
            })
        deleting?.let { tag -> com.yunai.phototube.ui.GlassConfirmation(scene,
            title = "删除“${tag.name}”？",
            message = "会移除该标签与所有照片的关联，不删除照片或原文件。当前关联 ${tag.count} 项内容。",
            confirmLabel = "确认删除标签", destructive = true, onDismiss = { deleting = null }, onConfirm = {
                deleting = null; busy = true; error = null
                scope.launch {
                    try { repository.delete(tag.id); tags.refresh(); onChanged() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (problem: Exception) { error = problem.message }
                    finally { busy = false }
                }
            })
        }
    }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).layerBackdrop(listBackdrop).testTag("tag-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("heading") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManagementTitle(title)
                    GlassTextField(keyword,{if(it.length<=120) keyword=it},"搜索标签",enabled=!busy)
                    GlassSettingsAction("新建标签",{target=null;name=keyword.trim();error=null;editing=true},enabled=!busy)
                }
            }
            if (busy) item("mutation-progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.takeIf { !editing }?.let { message -> item("mutation-error") { Text(message, color = AppTheme.colors.text) } }
            items(tags.itemCount, key = tags.itemKey { it.id }) { index -> tags[index]?.let { tag ->
                var menuOpen by remember(tag.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().testTag("tag-row-${tag.id}").clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.colors.group), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f)
                        .testTag(if(onPick != null) "pick-tag-${tag.id}" else "browse-tag-${tag.id}")
                        .clickable(enabled=!busy,role=Role.Button) {if(onPick != null) onPick(tag) else browsing=tag}
                        .heightIn(min=72.dp).padding(horizontal=16.dp,vertical=12.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(tag.name,color=AppTheme.colors.text,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${tag.count} 项内容",color=AppTheme.colors.secondary,style=MaterialTheme.typography.bodySmall)
                        }
                        if(onPick != null) Text("添加",color=AppTheme.colors.accent)
                        else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,Modifier.size(18.dp),tint=AppTheme.colors.secondary)
                    }
                    if(onPick == null) Box {
                        Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).testTag("tag-actions-${tag.id}")
                            .semantics {contentDescription="${tag.name}的更多操作"}
                            .clickable(enabled=!busy,role=Role.Button) {menuOpen=true},contentAlignment=Alignment.Center) {
                            MediaIcon(MediaSymbol.MORE,AppTheme.colors.secondary)
                        }
                        GlassActionMenu(menuOpen,{menuOpen=false},listBackdrop,listOf(
                            GlassMenuAction("重命名",enabled=!busy) {target=tag;name=tag.name;error=null;editing=true},
                            GlassMenuAction("删除标签",enabled=!busy,destructive=true) {deleting=tag}
                        ))
                    }
                }
            } }
            if (tags.loadState.refresh is LoadState.Loading || tags.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (tags.loadState.refresh as? LoadState.Error) ?: (tags.loadState.append as? LoadState.Error)
            if(onPick==null) item("management") {
                ManagementGroup("标签管理", compact=true) {
                    SettingsNavigationRow("标签规则",{rulesOpen=true},enabled=!busy)
                    SettingsNavigationRow("清理未使用标签",{cleanupOpen=true},enabled=!busy)
                }
            }
            if (failure != null) item { Text(failure.error.message ?: "读取失败"); GlassSettingsAction("重试",tags::retry) }
            if (tags.loadState.refresh is LoadState.NotLoading && tags.itemCount == 0) item { Text("没有匹配的标签", color = AppTheme.colors.secondary) }
        }
    }
    if (cleanupOpen) UnusedTagsScreen(server, onChanged = { tags.refresh(); onChanged() }, onClose = { cleanupOpen = false })
    if (rulesOpen) TagRulesScreen(server, onChanged, onClose = { rulesOpen = false })
    browsing?.let { tag ->
        ScopedMediaGallery(server, WallFilters(tagsAll = setOf(tag.id)), tag.name,
            "不含归档与私密内容", "此标签下没有可浏览的照片或视频。", "tag-photos",
            onChanged = { tags.refresh(); onChanged() }, onClose = { browsing = null })
    }


}

@Composable
internal fun PhotoTagEditor(source: RemoteMedia, onChanged: () -> Unit, onClose: () -> Unit) {
    val repository = remember(source.server) { TagRepository(source.server) }
    var picking by rememberSaveable(source.asset.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshNeeded by rememberSaveable(source.asset.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun mutate(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                operation()
                refreshNeeded = true
                onChanged()
                source.detail = source.server.detail(source.asset.id)
                refreshNeeded = false
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { error = (if (refreshNeeded) "操作已提交，刷新标签失败：" else "") + problem.message }
            finally { busy = false }
        }
    }
    ManagementPage("照片标签", onClose, dismissEnabled = !busy) {
        Text(source.asset.fileName, color = AppTheme.colors.secondary)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = AppTheme.colors.text) }
        if (refreshNeeded) {
            Text("标签变更已提交，请刷新后继续编辑。", color = AppTheme.colors.secondary)
            GlassSettingsAction("重新读取标签",{mutate {}},enabled=!busy)
        }
        val current = photoTags(source.detail)
        if (current.isEmpty()) Text("这张照片还没有标签", color = AppTheme.colors.secondary)
        current.forEach { tag -> key(tag.id) {
            ManagementGroup(tag.name) {
                Text(when (tag.source) { "MANUAL" -> "人工标签"; "AI" -> "AI 标签"; "RULE" -> "规则标签"; else -> tag.source }, color = AppTheme.colors.secondary)
                GlassSettingsAction("移除标签",{mutate {repository.detach(source.asset.id,tag.id)}},enabled=!busy && !refreshNeeded,modifier=Modifier.testTag("remove-photo-tag-${tag.id}"))
            }
        } }
        Text("移除自动标签后，服务端会阻止后续扫描立即添加回来。", color = AppTheme.colors.secondary)
        SettingsNavigationRow("添加标签",{picking=true},enabled=!busy && !refreshNeeded)
    }
    if (picking) TagsScreen(source.server, onClose = { picking = false }, onPick = { tag -> picking = false; mutate { repository.attach(source.asset.id, tag.id) } })
}
