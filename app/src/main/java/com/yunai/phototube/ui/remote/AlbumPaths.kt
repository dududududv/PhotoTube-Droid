package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.GlassConfirmation
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun AlbumPaths(repository:AlbumRepository,album:RemoteAlbum,onChanged:()->Unit,onClose:()->Unit) {
    var paths by rememberConfigurationState(repository.server to album.id) {mutableStateOf<List<JSONObject>>(emptyList())}
    var generation by rememberConfigurationState(repository.server to album.id) {mutableLongStateOf(0L)}
    var closing by rememberSaveable(repository.server,album.id) {mutableStateOf(false)}
    var reload by remember {mutableIntStateOf(0)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var selecting by rememberConfigurationState(repository.server to album.id) {mutableStateOf(false)}
    var replacing by rememberConfigurationState(repository.server to album.id) {mutableStateOf<JSONObject?>(null)}
    var pending by rememberConfigurationState(repository.server to album.id) {mutableStateOf<JSONObject?>(null)}
    var preview by rememberConfigurationState(repository.server to album.id) {mutableStateOf<JSONObject?>(null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(reload) {
        busy=true;error=null
        try {
            val loadedGeneration=repository.members(album.id,null).first.raw.getJSONObject("pathSync").getLong("configGeneration")
            val result=mutableListOf<JSONObject>();var cursor:String?=null;val seen=mutableSetOf<String>()
            do {val page=repository.paths(album.id,cursor);val entries=page.getJSONArray("items");for(i in 0 until entries.length()) result+=entries.getJSONObject(i);cursor=page.nullString("nextCursor");if(cursor!=null && !seen.add(cursor)) kotlin.error("服务器返回了重复游标")} while(cursor!=null)
            withContext(Dispatchers.Main.immediate) {generation=loadedGeneration;paths=result}
        } catch(cancel:CancellationException){throw cancel} catch(problem:Exception){withContext(Dispatchers.Main.immediate) {error=problem.message}} finally {withContext(NonCancellable + Dispatchers.Main.immediate) {busy=false}}
    }
    fun propose(request:JSONObject) {
        if(busy || closing) return
        busy=true;error=null
        scope.launch {try {val result=repository.previewPath(album.id,request);pending=request;preview=result} catch(cancel:CancellationException){throw cancel} catch(problem:Exception){error=problem.message;if(problem is ServerError && problem.code.contains("CONFLICT")) reload++} finally {busy=false}}
    }
    fun request(action:String,path:JSONObject?=null)=JSONObject().put("action",action).put("expectedGeneration",generation).apply {path?.let {put("pathId",it.getString("id"))}}
    ManagementPage("相册文件夹",onClose,dismissEnabled=!busy,closeRequested=closing,onCloseAttempt={closing=true;true},overlay={scene ->
        preview?.let {impact -> GlassConfirmation(scene,title="确认目录变更",
            message="将移除 ${impact.getLong("membersToRemove")} 项相册成员，${impact.getLong("membersRetainedByOtherPaths")} 项由其他目录保留。不会删除原文件。"+(if(impact.getBoolean("requiresSync")) "保存后需要手动扫描。" else ""),
            confirmLabel="确认变更",busy=busy,confirmEnabled=pending!=null,
            onDismiss={preview=null;pending=null},onConfirm={
                pending?.let {exact ->
                    busy=true;preview=null;pending=null
                    scope.launch {
                        try {repository.applyPath(album.id,exact);onChanged();reload++}
                        catch(cancel:CancellationException){throw cancel}
                        catch(problem:Exception){error=problem.message}
                        finally {busy=false}
                    }
                }
            })
        }
    }) {
        Text("修改前会预览成员影响。保存目录后不会自动扫描。",color=AppTheme.colors.secondary)
        paths.forEach {path ->
            ManagementGroup(path.getString("relativePath")) {
                Text(if(path.getBoolean("enabled")) "已启用" else "已停用",color=AppTheme.colors.secondary)
                SettingsNavigationRow("更换",{replacing=path;selecting=true},enabled=!busy && !closing)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {GlassSettingsAction(if(path.getBoolean("enabled")) "停用" else "启用",{
                        propose(request("UPDATE",path).put("path",JSONObject().put("libraryId",path.getString("libraryId")).put("relativePath",path.getString("relativePath")).put("enabled",!path.getBoolean("enabled"))))
                    },enabled=!busy && !closing)}
                    Box(Modifier.weight(1f)) {GlassSettingsAction("移除",{propose(request("REMOVE",path))},enabled=!busy && !closing)}
                }
            }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it);GlassSettingsAction("重新读取配置",{pending=null;preview=null;reload++},enabled=!busy && !closing)}
        SettingsNavigationRow("添加目录",{replacing=null;selecting=true},enabled=!busy && !closing && generation>0)
        GlassSettingsAction("完成",{closing=true},enabled=!busy && !closing)
    }
    if(selecting) AlbumFolderPicker(repository,onClose={selecting=false}) {library,path ->
        selecting=false
        propose(request(if(replacing==null) "ADD" else "UPDATE",replacing).put("path",JSONObject().put("libraryId",library).put("relativePath",path).put("enabled",true)))
    }

}

@Composable
internal fun AlbumFolderPicker(repository:AlbumRepository,onClose:()->Unit,onSelect:(String,String)->Unit) {
    var path by rememberSaveable(repository.server) {mutableStateOf("")}
    var closing by rememberSaveable(repository.server) {mutableStateOf(false)}
    var selectedLibrary by rememberSaveable(repository.server) {mutableStateOf<String?>(null)}
    var selectedPath by rememberSaveable(repository.server) {mutableStateOf<String?>(null)}
    var data by remember {mutableStateOf<JSONObject?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    LaunchedEffect(repository.server, path, retry) {
        data = null
        error = null
        try {
            val folders = repository.folders(path)
            withContext(Dispatchers.Main.immediate) { data = folders }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (problem: Exception) {
            withContext(Dispatchers.Main.immediate) { error = problem.message ?: "无法读取文件夹" }
        }
    }
    ManagementPage("选择文件夹",onClose={
        val library=selectedLibrary
        val chosen=selectedPath
        if(library!=null && chosen!=null) onSelect(library,chosen) else onClose()
    },closeRequested=closing,onCloseAttempt={closing=true;true}) {
        Text(path.ifEmpty {"媒体根目录"},color=AppTheme.colors.secondary)
        if(path.isNotEmpty()) GlassSettingsAction("上一级",{path=path.substringBeforeLast('/',"")},enabled=!closing)
        data?.getJSONArray("items")?.let {items ->for(i in 0 until items.length()) {
            val item=items.getJSONObject(i)
            SettingsNavigationRow(item.getString("name"),{path=item.getString("path")},enabled=!closing)
        }}
        if(data==null && error==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it);GlassSettingsAction("重试",{retry++},enabled=!closing)}
        GlassSettingsAction("选择此目录",{if(!closing) data?.let {selectedLibrary=it.getString("libraryId");selectedPath=path;closing=true}},enabled=!closing && data!=null && path.isNotEmpty())
        GlassSettingsAction("取消",{closing=true},enabled=!closing)
    }
}
