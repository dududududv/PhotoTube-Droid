package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassOptionRow
import com.yunai.phototube.ui.SettingsNavigationRow
import kotlinx.coroutines.*

@Composable
internal fun XmpExportsScreen(server:PhotoTubeServer,onClose:()->Unit,initialLibraries:Set<String>?=null) {
    val repository=remember(server) {XmpRepository(server)}
    val flow=rememberConfigurationPaging(server, enabled = !server.privateMedia) {Pager(PagingConfig(100,initialLoadSize=100,maxSize=500,enablePlaceholders=false)) {AlbumPagingSource(repository::list)}.flow}
    val runs=flow.collectAsLazyPagingItems()
    var available by remember {mutableStateOf<Boolean?>(null)}
    var healthError by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    var selection by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf(initialLibraries)}
    var privateExport by remember {mutableStateOf(false)}
    var picker by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf(false)}
    var preview by remember {mutableStateOf<XmpPreview?>(null)}
    var detail by rememberConfigurationState(server, enabled = !server.privateMedia) {mutableStateOf<XmpRun?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(retry) {
        available=null;healthError=null
        try {val enabled=SystemRepository(server).health().getJSONObject("xmpExport").getBoolean("available");withContext(Dispatchers.Main.immediate) {available=enabled}}
        catch(cancel:CancellationException) {throw cancel}
        catch(problem:Exception) {healthError=problem.message}
    }
    ManagementPage("XMP 导出",onClose,dismissEnabled=!busy,scrollable=false,showTitle=false,overlay={scene ->
        preview?.let {estimate -> com.yunai.phototube.ui.GlassConfirmation(scene,title="确认创建导出？",
            message="预估 ${estimate.count} 张照片，${estimate.scope.libraries.orEmpty().size} 个媒体库。",
            confirmLabel="创建导出",confirmEnabled=estimate.scope.libraries.orEmpty().size in 1..100,
            onDismiss={preview=null},details={
                Text("预估于 ${taskTimestamp(estimate.at)}。这是数量估算，实际快照在后台执行时冻结，数量可能变化。")
                Text("输出保存在服务端导出目录，不是照片下载或完整数据库备份。")
                if(estimate.scope.includePrivate) Text("包含私密照片的元数据。确认后，即使锁定私密空间，已创建的后台导出仍会继续。")
                if(estimate.scope.libraries.orEmpty().size>100) Text("范围超过 100 个媒体库，请缩小范围。")
            },onConfirm={
                preview=null;busy=true;error=null
                scope.launch {
                    try {val created=repository.create(estimate.scope);withContext(Dispatchers.Main.immediate) {detail=created;runs.refresh()}}
                    catch(cancel:CancellationException) {throw cancel}
                    catch(problem:Exception) {error="创建未确认：${problem.message}。请先核对导出历史，再决定是否重试。";runs.refresh()}
                    finally {withContext(Dispatchers.Main.immediate) {busy=false}}
                }
            })
        }
    }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("xmp-history"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item("heading") {
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ManagementTitle("XMP 导出")
                    Text("导出照片元数据快照到服务端配置的导出目录，不修改原图。",color=AppTheme.colors.secondary)
                    if(available==false) {
                        Text("服务器尚未配置导出存储。历史仍可查看。",color=AppTheme.colors.secondary)
                        GlassSettingsAction("重新检查配置",{retry++},enabled=!busy)
                    }
                    healthError?.let {Text(it);GlassSettingsAction("重试检查",{retry++},enabled=!busy)}
                    if(available==null && healthError==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    SettingsNavigationRow(selection?.let {"已选 ${it.size} 个媒体库"} ?: "全部媒体库",{picker=true},enabled=!busy)
                    if(selection!=null) GlassSettingsAction("恢复全部",{selection=null;preview=null},enabled=!busy)
                    Text(if(server.privateMedia) "本次包含普通和私密照片，并非仅导出私密照片。" else "本次不包含私密照片。",color=AppTheme.colors.secondary)
                    if(!server.privateMedia) SettingsNavigationRow("包含私密内容的导出…",{privateExport=true},enabled=available==true && !busy)
                    error?.let {Text(it,color=AppTheme.colors.text)}
                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    GlassSettingsAction("预估导出",{busy=true;error=null;scope.launch {
                        try {val estimate=repository.preview(XmpScope(selection,includePrivate=server.privateMedia));withContext(Dispatchers.Main.immediate) {preview=estimate}}
                        catch(cancel:CancellationException) {throw cancel}
                        catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {error=problem.message}}
                        finally {withContext(Dispatchers.Main.immediate) {busy=false}}
                    }},enabled=available==true && !busy,modifier=Modifier.fillMaxWidth().testTag(if(server.privateMedia) "xmp-preview-private" else "xmp-preview-normal"))
                    Text("导出历史",color=AppTheme.colors.text)
                }
            }
            items(runs.itemCount,key=runs.itemKey {it.id}) {index -> runs[index]?.let {run ->
                Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).background(AppTheme.colors.group).clickable(enabled=!busy) {detail=run}.padding(16.dp)) {
                    Text(xmpState(run.state),color=AppTheme.colors.text)
                    if(run.includePrivate) Text("包含私密内容",color=AppTheme.colors.secondary)
                    Text(taskTimestamp(run.createdAt),color=AppTheme.colors.secondary)
                    Text("成功 ${run.succeeded} · 失败 ${run.failed} · ${run.libraries.size} 个媒体库",color=AppTheme.colors.secondary)
                }
            } }
            if(runs.loadState.refresh is LoadState.Loading || runs.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            val failure=(runs.loadState.refresh as? LoadState.Error) ?: (runs.loadState.append as? LoadState.Error)
            if(failure!=null) item {Text(failure.error.message ?: "加载失败");GlassSettingsAction("重试历史",runs::retry,enabled=!busy)}
            if(runs.loadState.refresh is LoadState.NotLoading && runs.itemCount==0) item {Text("暂无导出历史",color=AppTheme.colors.secondary)}
        }
        GlassSettingsAction("刷新历史",runs::refresh,enabled=!busy)
    }
    if(privateExport) PrivateAccessScreen(server,onClose={privateExport=false}) {isolated,_ -> XmpExportsScreen(isolated,onClose={privateExport=false;runs.refresh()},initialLibraries=selection)}
    if(picker) ExportLibraryPicker(FilterCatalog(server),selection.orEmpty(),retain = !server.privateMedia,onClose={picker=false}) {selection=it;preview=null;picker=false}
    detail?.let {run -> XmpRunDetail(repository,run,onClose={detail=null;runs.refresh()})}
}

@Composable
private fun ExportLibraryPicker(catalog:FilterCatalog,initial:Set<String>,retain:Boolean,onClose:()->Unit,onSelect:(Set<String>)->Unit) {
    var selected by rememberConfigurationState(catalog, enabled = retain) {mutableStateOf(initial)}
    val flow=remember {Pager(PagingConfig(100,initialLoadSize=100,enablePlaceholders=false)) {AlbumPagingSource {cursor -> catalog.folders(null,"",cursor)}}.flow}
    val libraries=flow.collectAsLazyPagingItems()
    ManagementPage("选择媒体库",onClose,scrollable=false) {
        Text("已选 ${selected.size}/100 个媒体库",color=AppTheme.colors.secondary)
        LazyColumn(Modifier.weight(1f)) {
            items(libraries.itemCount,key=libraries.itemKey {it.libraryId ?: it.path}) {index -> libraries[index]?.let {library -> library.libraryId?.let {id ->
                GlassOptionRow(library.name,id in selected,{checked -> selected=if(checked) selected+id else selected-id},
                    enabled=id in selected || selected.size<100,modifier=Modifier.padding(vertical=4.dp))
            } } }
            val error=(libraries.loadState.refresh as? LoadState.Error) ?: (libraries.loadState.append as? LoadState.Error)
            if(error!=null) item {Text(error.error.message ?: "加载失败");GlassSettingsAction("重试",libraries::retry)}
            if(libraries.loadState.refresh is LoadState.Loading || libraries.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(libraries.loadState.refresh is LoadState.NotLoading && libraries.itemCount==0) item {Text("没有媒体库")}
        }
        GlassSettingsAction("完成选择",{onSelect(selected)},enabled=selected.isNotEmpty())
    }
}

@Composable
private fun XmpRunDetail(repository:XmpRepository,initial:XmpRun,onClose:()->Unit) {
    var run by remember {mutableStateOf(initial)}
    var error by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(initial.id,retry) {lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        var attempt=0
        do {
            try {val value=repository.detail(initial.id);withContext(Dispatchers.Main.immediate) {run=value;error=null}}
            catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {error=problem.message;break}
            if(!run.active) break
            delay(listOf(1000L,2000L,3000L,5000L)[attempt.coerceAtMost(3)]);attempt++
        } while(true)
    } }
    ManagementPage("导出详情",onClose) {
        ManagementGroup(xmpState(run.state)) {
            Text("匹配 ${run.matched} · 成功 ${run.succeeded} · 失败 ${run.failed}",color=AppTheme.colors.text)
            if(run.active) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("执行尝试 ${run.attempts} 次",color=AppTheme.colors.secondary)
            Text("${run.libraries.size} 个媒体库 · ${if(run.includePrivate) "包含私密" else "不含私密"}",color=AppTheme.colors.secondary)
            Text("创建于 ${taskTimestamp(run.createdAt)}",color=AppTheme.colors.secondary)
            run.finishedAt?.let {Text("结束于 ${taskTimestamp(it)}",color=AppTheme.colors.secondary)}
            run.error?.let {Text("错误代码：$it",color=AppTheme.colors.text)}
        }
        run.path?.let {path -> ManagementGroup("快照位置") {
            Text(path,color=AppTheme.colors.text)
            run.snapshotAt?.let {Text("快照时间：${taskTimestamp(it)}",color=AppTheme.colors.secondary)}
            Text("在服务端配置的导出目录中查看文件。当前服务未提供下载或逐项明细接口。",color=AppTheme.colors.secondary)
        } }
        error?.let {Text(it,color=AppTheme.colors.text)}
        GlassSettingsAction("刷新状态",{retry++})
    }
}
private fun xmpState(state:String)=when(state) {"PENDING"->"等待导出";"RUNNING"->"正在导出";"SUCCEEDED"->"导出完成";"PARTIAL"->"部分完成";"FAILED"->"导出失败";"CANCELLED"->"已取消";else->state}
