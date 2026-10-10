package com.yunai.phototube.ui.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction

@Composable
internal fun TrashClearScreen(server:PhotoTubeServer,onChanged:()->Unit,onClose:()->Unit) {
    val context=LocalContext.current.applicationContext
    val controller=remember(server) {TrashClearController(context,TrashRepository(server),onChanged)}
    val state by controller.status.collectAsState()
    var confirm by remember {mutableStateOf(false)}
    var stopping by remember {mutableStateOf(false)}
    var closeRequested by remember {mutableStateOf(false)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller,lifecycle) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_PAUSE) {
            confirm=false
            if(state.phase==TrashClearPhase.CLEARING) {controller.stopAfterBatch();stopping=true}
        }}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);controller.close()}
    }
    val running=state.phase==TrashClearPhase.CLEARING
    ManagementPage("清理全部回收站记录",onClose,dismissEnabled=!running,closeRequested=closeRequested,overlay={scene ->
        if(confirm) com.yunai.phototube.ui.GlassConfirmation(scene,title="清除 ${state.stats.total} 项记录？",
            message="包含尚未显示的回收站页面。这些记录和派生缓存无法恢复，但 NAS 原文件保留。之后新增的项目不会加入本次清理。",
            confirmLabel="确认清除全部已收集记录",destructive=true,
            onConfirm={confirm=false;controller.execute()},onDismiss={confirm=false})
    }) {
        Text("清除 PhotoTube 记录和派生缓存，不删除 NAS 原文件。以后扫描相同目录时，文件可能重新入库。",color=AppTheme.colors.secondary)
        ManagementGroup(when(state.phase) {TrashClearPhase.COLLECTING->"正在统计全部页面";TrashClearPhase.READY->if(state.stats.total==0L) "无需清理" else "待确认范围";TrashClearPhase.CLEARING->"正在分批清理";else->"处理结果"}) {
            Text("本次已收集 ${state.stats.total} 项",color=AppTheme.colors.text)
            if(state.phase==TrashClearPhase.COLLECTING) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(running || state.phase==TrashClearPhase.FINISHED) {
                val progress by animateFloatAsState(if(state.stats.total>0) state.stats.processed.toFloat()/state.stats.total else 0f,tween(300),label="trash-clear-progress")
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                Text("已清除 ${state.stats.succeeded} · 失败 ${state.stats.failed} · 未确认 ${state.stats.unknown} · 待处理 ${state.stats.pending}",color=AppTheme.colors.secondary)
            }
            state.message?.let {Text(it,color=AppTheme.colors.text)}
            if(state.phase==TrashClearPhase.READY) Text(if(state.stats.total==0L) "回收站是空的" else "只处理这次收集的记录。之后新增的项目不会自动加入；执行时仍以服务器状态为准。",color=AppTheme.colors.secondary)
        }
        state.failures.forEach {failure -> ManagementGroup(failure.name) {Text(failure.reason,color=AppTheme.colors.secondary)} }
        if(state.stats.failed+state.stats.unknown>state.failures.size && state.failures.isNotEmpty()) Text("这里只展示前 ${state.failures.size} 项，请返回回收站核对其他记录。",color=AppTheme.colors.secondary)
        when(state.phase) {
            TrashClearPhase.COLLECTING -> GlassSettingsAction("取消统计", {controller.close();closeRequested=true}, enabled=!closeRequested)
            TrashClearPhase.READY -> if (state.stats.total == 0L) GlassSettingsAction("返回回收站", {closeRequested=true}, enabled=!closeRequested)
                else GlassSettingsAction("清除这 ${state.stats.total} 项记录", {confirm=true},enabled=!closeRequested,destructive=true)
            TrashClearPhase.CLEARING -> GlassSettingsAction(if(stopping) "正在结束当前批次…" else "停止后续批次", {stopping=true;controller.stopAfterBatch()},enabled=!stopping)
            TrashClearPhase.ERROR -> GlassSettingsAction("重新统计", {controller.collect()},enabled=!closeRequested)
            TrashClearPhase.FINISHED -> GlassSettingsAction("返回回收站核对", {closeRequested=true},enabled=!closeRequested)
        }
    }
}
