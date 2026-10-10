package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassSegmentedControl
import com.yunai.phototube.ui.GlassOptionRow
import com.yunai.phototube.ui.GlassSettingsAction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import com.yunai.phototube.ui.theme.AppTheme
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.*
import kotlinx.coroutines.*
import org.json.*

@Composable
internal fun CreateRemoteAlbum(repository:AlbumRepository,onClose:()->Unit,onCreated:(RemoteAlbum)->Unit) {
    var name by rememberSaveable(repository.server) {mutableStateOf("")}
    var kind by rememberSaveable(repository.server) {mutableStateOf("NORMAL")}
    var pickingPhotos by rememberSaveable {mutableStateOf(false)}
    var photos by rememberConfigurationState(repository.server) {mutableStateOf<List<RemoteAsset>>(emptyList())}
    var coverId by rememberSaveable {mutableStateOf<String?>(null)}
    var creationComplete by rememberSaveable {mutableStateOf(false)}
    var path by rememberSaveable(repository.server) {mutableStateOf("")}
    var listing by remember {mutableStateOf<JSONObject?>(null)}
    var selected by rememberConfigurationState(repository.server) {mutableStateOf<Map<String,String>>(emptyMap())}
    var error by rememberConfigurationState(repository.server) {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(false)}
    var saving by remember {mutableStateOf(false)}
    var createUnconfirmed by rememberConfigurationState(repository.server) {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var smartKind by rememberSaveable(repository.server) {mutableStateOf<WallKind?>(null)}
    var favorite by rememberSaveable(repository.server) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(path,kind,retry) {
        if(kind!="PATH_SYNC") return@LaunchedEffect
        listing=null;loading=true;error=null
        try {listing=repository.folders(path)} catch(cancel:CancellationException){throw cancel} catch(problem:Exception){error=problem.message} finally {loading=false}
    }
    var createdAlbum by rememberConfigurationState(repository.server) {mutableStateOf<RemoteAlbum?>(null)}
    var closeRequested by rememberSaveable {mutableStateOf(false)}
    fun submit() {
        if(saving || createUnconfirmed || closeRequested || createdAlbum!=null) return
        saving=true;createUnconfirmed=true;error=null
        val body=JSONObject().put("name",name.trim()).put("kind",kind)
        if(kind=="PATH_SYNC") body.put("paths",JSONArray().apply {selected.forEach {(folder,library)->put(JSONObject().put("libraryId",library).put("relativePath",folder).put("enabled",true))}})
        if(kind=="SMART") body.put("filter",JSONObject().put("archived",false).apply {smartKind?.let {put("kind",it.name)};if(favorite) put("favorite",true)})
        scope.launch(Dispatchers.Main.immediate) {
            try {
                val created=repository.create(body)
                createUnconfirmed=false
                createdAlbum=created
                if(kind=="NORMAL" && photos.isNotEmpty()) {
                    val ids=photos.map {it.id}.toSet()
                    val result=assetActionResult(repository.changeMembers(created.id,ids))
                    val succeeded=(result.succeeded intersect ids)-result.failed.map {it.id}.toSet()
                    if(succeeded.size!=ids.size) {
                        error="相册已创建，${succeeded.size} 项已添加。其余 ${ids.size-succeeded.size} 项未确认，请进入相册核对后添加。"
                        return@launch
                    }
                    createdAlbum=created.copy(count=succeeded.size.toLong())
                    coverId?.takeIf {it in succeeded}?.let {id ->
                        createdAlbum=repository.update(created.id,JSONObject().put("coverAssetId",id)
                            .put("coverFocalPoint",JSONObject().put("x",.5).put("y",.5)))
                    }
                }
                creationComplete=true
            } catch(cancel:CancellationException) {
                throw cancel
            } catch(problem:Exception) {
                // Validation/auth/rate-limit rejection is definitive; a timeout or 5xx
                // may follow a committed creation and must not offer blind resubmission.
                if(problem is ServerError && problem.httpStatus in listOf(400,401,403,404,409,422,429)) createUnconfirmed=false
                error=if(createdAlbum!=null) "相册已创建，照片或封面保存未完成。请进入相册核对。${problem.message.orEmpty()}" else problem.message
            } finally {saving=false}
        }
    }
    val editable = !saving && !createUnconfirmed && !closeRequested && createdAlbum==null
    ManagementPage("新建相册", onClose={createdAlbum?.let(onCreated) ?: onClose()}, dismissEnabled=!saving,
        closeRequested=closeRequested || creationComplete) {
            GlassTextField(name,{name=it},"名称",enabled=editable)
            GlassSegmentedControl(listOf("NORMAL" to "普通相册", "PATH_SYNC" to "同步目录", "SMART" to "智能相册"),
                kind, { kind = it }, enabled = editable)
            if(kind=="PATH_SYNC") {
                Text("选择服务端目录，创建后按相册配置同步内容。")
                Text(if(path.isEmpty()) "媒体根目录" else path)
                if(path.isNotEmpty()) GlassSettingsAction("上一级",{path=path.substringBeforeLast('/',"")},enabled=editable)
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                listing?.let {folders ->
                    val library=folders.getString("libraryId")
                    if(path.isNotEmpty()) GlassOptionRow(if(path in selected) "取消选择此目录" else "选择此目录",path in selected,
                        {selected=if(it) selected+(path to library) else selected-path},enabled=editable && (selected.size<100 || path in selected))
                    val children=folders.getJSONArray("items")
                    for(i in 0 until children.length()) {val child=children.getJSONObject(i);SettingsNavigationRow(child.getString("name"),{path=child.getString("path")},enabled=editable)}
                    if(children.length()==0) Text("没有子目录")
                }
                selected.keys.forEach {folder -> GlassOptionRow("$folder · 移除",true,{selected=selected-folder},enabled=editable)}
            } else if(kind=="NORMAL") {
                SettingsNavigationRow(if(photos.isEmpty()) "选择照片" else "已选 ${photos.size} 项 · 更改",{pickingPhotos=true},enabled=editable)
                if(photos.isNotEmpty()) {
                    Text("选择一张作为封面",color=AppTheme.colors.secondary)
                    LazyRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(photos,key={it.id}) {asset ->
                            Box(Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).testTag("draft-cover-${asset.id}")
                                .semantics {contentDescription="封面：${asset.fileName}"}
                                .selectable(coverId==asset.id,enabled=editable,role=Role.RadioButton) {coverId=asset.id}) {
                                AssetThumbnail(asset,repository.server,Modifier.fillMaxSize())
                                MediaSelectionMark(coverId==asset.id,Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
                            }
                        }
                    }
                } else Text("可以先建空相册，稍后再添加照片。",color=AppTheme.colors.secondary)
            }
            else {
                Text("按规则自动收集已入库内容")
                GlassSegmentedControl(listOf(null to "全部", WallKind.PHOTO to "照片", WallKind.VIDEO to "视频"),
                    smartKind, { smartKind = it }, enabled = editable)
                GlassOptionRow("只包含收藏",favorite,{favorite=it},enabled=editable)
            }
            error?.let {Text(it);if(kind=="PATH_SYNC") GlassSettingsAction("重新读取目录",{retry++},enabled=editable && !loading)}
            if(createdAlbum!=null && !saving && !creationComplete) {
                if(error==null) Text("相册已创建，请进入相册核对照片和封面。")
                GlassSettingsAction("进入已建相册",{closeRequested=true})
            }
            if(createUnconfirmed && !saving) Text("创建结果未确认，请返回相册列表核对，避免重复创建。")
        GlassSettingsAction(if(saving) "正在创建…" else "创建", ::submit,
            enabled=editable && !closeRequested && createdAlbum==null && name.trim().length in 1..120 && (kind!="PATH_SYNC" || selected.isNotEmpty()))
        if(createdAlbum==null) GlassSettingsAction(if(createUnconfirmed) "返回相册列表" else "取消", {closeRequested=true}, enabled=!saving)
    }
    if(pickingPhotos) AlbumMemberPicker(repository,album=null,onChanged={},onClose={pickingPhotos=false},
        draftSelection=photos,onPicked={chosen ->
            photos=chosen
            if(chosen.none {it.id==coverId}) coverId=chosen.firstOrNull()?.id
            pickingPhotos=false
        })

}

@Composable
internal fun AlbumScanDialog(repository:AlbumRepository,album:RemoteAlbum,onClose:()->Unit,historyRun:JSONObject?=null) {
    var run by rememberConfigurationState(repository.server to album.id) {mutableStateOf<JSONObject?>(historyRun ?: album.raw.optJSONObject("pathSync")?.let {summary ->
        summary.nullString("lastRunId")?.let {id -> JSONObject().put("id",id).put("state",summary.optString("lastRunState"))}
    })}
    var results by rememberConfigurationState(repository.server to album.id) {mutableStateOf<JSONArray?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by rememberConfigurationState(repository.server to album.id) {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    var startUnconfirmed by rememberConfigurationState(repository.server to album.id) {mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var scanMode by rememberSaveable(album.id) { mutableStateOf("INCREMENTAL") }
    val id=run?.optString("id")
    LaunchedEffect(id, retry) {
        if (id == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var attempt = 0
            while (true) {
                try {
                    val detail = repository.scanState(album.id, id)
                    withContext(Dispatchers.Main.immediate) {
                        run = detail.getJSONObject("run")
                        results = detail.getJSONArray("pathResults")
                        error = null
                    }
                    if (run!!.getString("state") !in listOf("PENDING", "RUNNING")) break
                    delay(listOf(1000L, 2000L, 3000L, 5000L)[attempt.coerceAtMost(3)])
                    attempt++
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (problem: Exception) {
                    withContext(Dispatchers.Main.immediate) { error = problem.message }
                    break
                }
            }
        }
    }
    var closing by rememberSaveable { mutableStateOf(false) }
    fun startScan() {
        if (busy || startUnconfirmed || closing) return
        busy = true
        error = null
        scope.launch {
            try {
                val latest=repository.scanHistory(album.id,null,1).items.firstOrNull()
                if(latest?.optString("state") in listOf("PENDING","RUNNING")) {
                    withContext(Dispatchers.Main.immediate) { run=latest;error="上一轮扫描尚未结束，已显示当前进度。" }
                    return@launch
                }
                withContext(Dispatchers.Main.immediate) { startUnconfirmed=true }
                val created = repository.scan(album.id, scanMode)
                withContext(Dispatchers.Main.immediate) { run = created; startUnconfirmed = false }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (problem: Exception) {
                withContext(Dispatchers.Main.immediate) { error = problem.message }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { busy = false }
            }
        }
    }
    ManagementPage("扫描文件夹", onClose, closeRequested=closing, onCloseAttempt={closing=true;true}) {
        Text("仅扫描“${album.name}”启用的目录；关闭此窗口不会取消服务器任务。")
        if(historyRun == null) {
            FlowRow(Modifier.selectableGroup(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("INCREMENTAL" to "扫描新增", "FULL" to "强制重新扫描").forEach { (mode,label) ->
                    GlassChoiceChip(label,scanMode==mode,{scanMode=mode},enabled=!busy && !closing && !startUnconfirmed && run?.optString("state") !in listOf("PENDING","RUNNING"))
                }
            }
            Text(if(scanMode=="FULL") "重新处理所有文件的媒体信息和缩略图，保留标签、编辑及原文件，不触发 AI。" else "处理新增、变更及此前失败或未完成的文件。")
        }
        run?.let {Text("状态：${albumRunLabel(it.optString("state"))}\n成功 ${it.optInt("succeededPaths")} · 失败 ${it.optInt("failedPaths")} · 离线 ${it.optInt("offlinePaths")}")}
        run?.nullString("error")?.let {Text(it)}
        results?.let {paths ->for(i in 0 until paths.length()) {
            val result=paths.getJSONObject(i)
            Text(result.optString("relativePath")+" · "+albumRunLabel(result.optString("state")),modifier=Modifier.padding(top=12.dp))
            Text("发现 ${result.optLong("discoveredCount")} · 新增 ${result.optLong("addedCount")} · 移除关系 ${result.optLong("removedCount")} · 跳过 ${result.optLong("skippedCount")}")
            result.nullString("error")?.let {Text(it)}
        }}
        if(startUnconfirmed && !busy) Text("启动结果未确认，请关闭此窗口并查看最近一次扫描，避免重复启动。")
        error?.let {Text(it);if(id!=null) GlassSettingsAction("重新查询状态",{retry++},enabled=!closing)}
        if (historyRun == null && (run == null || run?.optString("state") !in listOf("PENDING", "RUNNING"))) {
            GlassSettingsAction(if (busy) "正在启动…" else "开始扫描", ::startScan, enabled = !busy && !startUnconfirmed && !closing)
        }
        GlassSettingsAction("关闭", {closing=true}, enabled=!closing)
    }
}

internal fun albumRunLabel(state:String)=when(state) {
    "PENDING"->"等待中";"RUNNING"->"扫描中";"SUCCEEDED"->"已完成";"PARTIAL"->"部分完成";"FAILED"->"失败";"CANCELLED"->"已取消";"OFFLINE"->"离线";else->state
}
