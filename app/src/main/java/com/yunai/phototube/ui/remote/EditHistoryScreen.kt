package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.RemoteMedia
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun EditHistoryScreen(source:RemoteMedia,onChanged:()->Unit,onClose:()->Unit) {
    val repository=remember(source) {EditVersionsRepository(source.server)}
    val flow=rememberConfigurationPaging(source.server to source.asset.id) {Pager(PagingConfig(100,initialLoadSize=100,maxSize=500,enablePlaceholders=false)) {AlbumPagingSource {cursor -> repository.list(source.asset.id,cursor)}}.flow}
    val versions=flow.collectAsLazyPagingItems()
    var busy by remember {mutableStateOf(false)}
    var error by rememberSaveable(source.asset.id) {mutableStateOf<String?>(null)}
    var needsRefresh by rememberSaveable(source.asset.id) {mutableStateOf(false)}
    var editing by rememberSaveable(source.asset.id) {mutableStateOf(false)}
    var restore by rememberSaveable(source.asset.id) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val active=source.detail?.optJSONObject("activeEdit")?.nullString("editVersionId")
    fun choose(target:String?) {
        val expected=active
        busy=true
        // Persist the uncertainty before sending: recreation must not allow a stale retry.
        needsRefresh=true
        error="切换结果未确认，请刷新当前版本后再操作。"
        scope.launch {
            try {
                val result=repository.select(source.asset.id,expected,target)
                withContext(Dispatchers.Main.immediate) {
                    source.detail=JSONObject(source.detail!!.toString()).put("activeEdit",result ?: JSONObject.NULL)
                    needsRefresh=false;error=null
                    source.server.imageLoader.memoryCache?.clear();versions.refresh();onChanged()
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {error=problem.message;needsRefresh=true}}
            finally {withContext(Dispatchers.Main.immediate) {busy=false}}
        }
    }
    ManagementPage("编辑历史",onClose,dismissEnabled=!busy,scrollable=false, overlay={scene ->
        if(restore) com.yunai.phototube.ui.GlassConfirmation(scene, title="恢复原图？",
            message="切回原始照片，已有编辑版本仍保留，可以随时重新选择。", confirmLabel="确认恢复",
            onConfirm={restore=false;choose(null)}, onDismiss={restore=false})
    }) {
        LazyColumn(Modifier.weight(1f).testTag("edit-history-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("版本切换只改变显示效果，原图与历史版本都会保留。", color = AppTheme.colors.secondary) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item { Text(message, color = AppTheme.colors.text) } }
            source.error?.let { message -> item { Text(message, color = AppTheme.colors.text) } }
            item {
                GlassSettingsAction("刷新当前版本",{busy=true;scope.launch {
                    try {source.load();withContext(Dispatchers.Main.immediate) {needsRefresh=source.error!=null;if(!needsRefresh) error=null;versions.refresh()}}
                    finally {busy=false}
                }},enabled=!busy)
            }
            item {
                if(source.asset.fileName.substringAfterLast('.').lowercase() in setOf("jpg","jpeg","png")) SettingsNavigationRow("裁切、旋转与镜像",{editing=true},enabled=!busy && !needsRefresh && source.detail!=null)
                else Text("新建编辑版本目前支持 JPEG 和 PNG。",color=AppTheme.colors.secondary)
            }
            item {
                if(active==null) Text("正在显示原图",color=AppTheme.colors.secondary)
                else GlassSettingsAction("恢复原图",{restore=true},enabled=!busy && !needsRefresh && source.detail!=null)
            }
            items(versions.itemCount,key=versions.itemKey {it.id}) {index -> versions[index]?.let {version ->
                ManagementGroup(taskTimestamp(version.createdAt)) {
                    if(version.sourceState=="CURRENT") EditVersionPreview(source,version)
                    Text("${version.width} × ${version.height}"+(if(version.id==active) " · 当前版本" else ""),color=AppTheme.colors.text)
                    Text(when {version.sourceState!="CURRENT" -> "原文件已变化，此版本不能重新应用";version.renderState=="PENDING" -> "效果已保存，预览正在准备";else -> "预览可用"},color=AppTheme.colors.secondary)
                    GlassSettingsAction("使用此版本",{choose(version.id)},enabled=!busy && !needsRefresh && source.detail!=null && version.sourceState=="CURRENT" && version.id!=active)
                }
            } }
            if(versions.loadState.refresh is LoadState.Loading || versions.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val failure=(versions.loadState.refresh as? LoadState.Error) ?: (versions.loadState.append as? LoadState.Error)
            if(failure!=null) item {Text(failure.error.message ?: "加载失败");GlassSettingsAction("重试历史",versions::retry,enabled=!busy)}
            if(versions.loadState.refresh is LoadState.NotLoading && versions.itemCount==0) item {Text("暂无编辑历史",color=AppTheme.colors.secondary)}
        }
    }
    if(editing) PhotoEditorScreen(source,onSaved={editing=false;versions.refresh();onChanged()},onClose={editing=false})

}

@Composable
private fun EditVersionPreview(source:RemoteMedia,version:EditVersion) {
    var failed by remember(version.id) {mutableStateOf(false)}
    var retry by remember(version.id) {mutableIntStateOf(0)}
    key(version.id,retry) {AsyncImage(source.server.render(source.asset.id,version.id,version.sourceHash,"MD"),"编辑版本预览",imageLoader=source.server.imageLoader,
        contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.subtle),
        onSuccess={failed=false},onError={failed=true})}
    if(failed) GlassSettingsAction("预览暂不可用，重试",{failed=false;retry++})
}
