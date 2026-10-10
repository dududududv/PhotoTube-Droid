package com.yunai.phototube.ui.remote

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassSettingsAction
import androidx.compose.foundation.selection.selectableGroup
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.*
import kotlinx.coroutines.*

private data class AiConfirmation(val run: AlbumAiRun?, val mode: AiScanMode? = null, val mediaScope: AiMediaScope = AiMediaScope.PHOTO)

@Composable
internal fun AlbumAiScreen(server: PhotoTubeServer, album: RemoteAlbum, onChanged: () -> Unit, onClose: () -> Unit) {
    val repository = remember(server) { AlbumAiRepository(server) }
    var runs by rememberConfigurationState(server to album.id) { mutableStateOf<List<AlbumAiRun>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Exception?>(null) }
    var expanded by rememberSaveable(album.id) { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<AiConfirmation?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var mediaScope by rememberSaveable(album.id) { mutableStateOf(AiMediaScope.PHOTO) }
    val undone = rememberConfigurationState(server to album.id) { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var presented by rememberSaveable(album.id) { mutableStateOf(false) }
    val transition = remember { Animatable(if (presented) 1f else 0f) }
    var closing by rememberSaveable(album.id) { mutableStateOf(false) }
    fun close() {
        if (!closing) {
            closing = true
            scope.launch { transition.animateTo(0f, tween(240, easing = FastOutSlowInEasing)); onChanged(); onClose() }
        }
    }
    fun replace(run: AlbumAiRun) { runs = (listOf(run) + runs.filterNot { it.id == run.id }).sortedByDescending { it.createdAt }.take(10) }
    fun execute(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action(); onChanged() }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { error = problem }
            finally { busy = false; refresh++ }
        }
    }
    // Poll only while this page is visible. A new refresh cancels any stale read
    // before a control response can be overwritten by an earlier request.
    LaunchedEffect(refresh, busy) {
        if (busy) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                val history = repository.history(album.id)
                withContext(Dispatchers.Main.immediate) { runs = history; loading = false }
                var delayIndex = 0
                while (runs.any { it.polling }) {
                    delay(listOf(1000L, 2000L, 3000L, 5000L)[delayIndex.coerceAtMost(3)])
                    for (run in runs.filter { it.polling }) {
                        val detail = repository.detail(run.id)
                        withContext(Dispatchers.Main.immediate) { replace(detail) }
                    }
                    delayIndex++
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { withContext(Dispatchers.Main.immediate) { error = problem } }
            finally { withContext(Dispatchers.Main.immediate) { loading = false } }
        }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ThemeSystemBars()
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f); setWindowAnimations(0) } }
        LaunchedEffect(Unit) {
            if (closing) { onChanged(); onClose() }
            else if (!presented) { presented = true; withFrameNanos { }; transition.animateTo(1f, spring(1f, 380f)) }
        }
        val colors = AppTheme.colors
        val scene = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = transition.value
            scaleX = .94f + .06f * transition.value; scaleY = scaleX
        }.background(colors.groupedBackground).testTag("album-ai")) {
            Column(Modifier.fillMaxSize().layerBackdrop(scene).background(colors.groupedBackground)
                .statusBarsPadding().navigationBarsPadding().padding(top = 80.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("AI 打标", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text(album.name, color = colors.secondary)
                AiGroup {
                    Text("整理相册中的内容", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    Text("使用已保存的照片或视频提示词。任务在服务器后台执行，离开页面后会继续。", color = colors.secondary)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiMediaScope.entries.forEach { choice ->
                            GlassChoiceChip(choice.label, mediaScope == choice, { mediaScope = choice }, enabled = !busy && runs.none { it.active }, modifier = Modifier.testTag("ai-scope-${choice.name}"))
                        }
                    }
                    if (mediaScope != AiMediaScope.PHOTO) Text("视频由服务器提取带时间戳的画面后打标，不上传视频原文件。", color = colors.secondary)
                    val available = !loading && !busy && error == null && runs.none { it.active }
                    GlassSettingsAction(if (mediaScope == AiMediaScope.PHOTO) "新照片打标" else if (mediaScope == AiMediaScope.VIDEO) "新视频打标" else "新内容打标",
                        { execute { val run = repository.create(album.id, AiScanMode.INCREMENTAL, mediaScope); replace(run); expanded = run.id } }, enabled = available, modifier = Modifier.testTag("ai-incremental"))
                    GlassSettingsAction("全部重新打标", { confirmation = AiConfirmation(null, AiScanMode.FULL, mediaScope) }, enabled = available)
                    if (runs.any { it.active }) Text("当前相册已有未结束任务，请先完成或取消。", color = colors.secondary)
                }
                if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { problem -> AiGroup {
                    Text(problem.message ?: "请求失败", color = colors.text)
                    if (problem is ServerError) Text(listOfNotNull(problem.code, problem.logId).joinToString(" · "), color = colors.secondary, fontSize = 12.sp)
                    GlassSettingsAction("重新读取任务状态", { error = null; refresh++ }, enabled = !busy)
                } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("最近任务", fontWeight = FontWeight.SemiBold, color = colors.text)
                    Box(Modifier.width(88.dp)) { GlassSettingsAction("刷新", { error = null; refresh++ }, enabled = !busy && !loading) }
                }
                if (!loading && runs.isEmpty() && error == null) Text("尚无 AI 打标任务", color = colors.secondary)
                runs.forEach { run -> key(run.id) {
                    AiGroup {
                        Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == run.id) null else run.id }.testTag("ai-run-${run.id}")) {
                            Text(when (run.mode) { "INCREMENTAL" -> "新增${run.mediaScope.label}打标"; "FULL" -> "全部重新打标"; else -> "历史预览" }, color = colors.text, fontWeight = FontWeight.SemiBold)
                            Text("${run.mediaScope.label} · ${aiStateLabel(run.state)} · ${run.processed} / ${run.total}", color = colors.secondary, modifier = Modifier.padding(top = 6.dp))
                            Text(taskTimestamp(run.createdAt), color = colors.secondary, fontSize = 12.sp)
                        }
                        val progress by animateFloatAsState(if (run.total > 0) (run.processed.toDouble() / run.total).toFloat().coerceIn(0f, 1f) else if (run.state == "SUCCEEDED") 1f else 0f, tween(350), label = "ai-progress")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = colors.accent, trackColor = colors.subtle)
                        if (run.total == 0L && run.state == "SUCCEEDED") Text("没有需要打标的新${run.mediaScope.label}", color = colors.secondary)
                        run.error?.let { Text(it, color = colors.text) }
                        if (expanded == run.id) {
                            Text("缓存 ${run.cached} · 跳过 ${run.skipped} · 冲突 ${run.conflicts}\n模型请求 ${run.attempts} 次 · 每批 ${run.batchSize} 张", color = colors.secondary)
                            if (!run.direct) Text("旧版预览不能继续或应用，请取消未结束任务后重新发起。", color = colors.secondary)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (run.direct && run.state in setOf("PENDING", "RUNNING", "PAUSED")) {
                                    val action = if (run.state == "PAUSED") AiTaskAction.RESUME else AiTaskAction.PAUSE
                                    GlassSettingsAction(if (action == AiTaskAction.RESUME) "继续" else "暂停", { execute { replace(repository.control(run.id, action)) } }, enabled = !busy)
                                }
                                if (run.active) GlassSettingsAction("取消任务", { execute { replace(repository.control(run.id, AiTaskAction.CANCEL)) } }, enabled = !busy)
                                if (run.canUndo && run.id !in undone) GlassSettingsAction("撤销标签", { confirmation = AiConfirmation(run) }, enabled = !busy)
                            }
                            if (run.id in undone) Text("已撤销该批次标签", color = colors.secondary)
                            if (run.active) Text("暂停或取消不会撤销已写入的标签。", color = colors.secondary, fontSize = 12.sp)
                            run.report.forEach { item ->
                                Text("${item.name} · ${item.count} 张", color = colors.text)
                                if (item.samples.isNotEmpty()) Text(item.samples.joinToString("、"), color = colors.secondary, fontSize = 12.sp)
                            }
                        }
                    }
                } }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                LiquidButton(::close, scene, Modifier.size(48.dp).semantics { contentDescription = "返回相册" }, shape = CircleShape, horizontalPadding = 0.dp, surfaceColor = colors.glass.copy(.4f)) { MediaIcon(MediaSymbol.BACK, colors.text) }
            }
        }
        confirmation?.let { request ->
            val full = request.mode == AiScanMode.FULL
            com.yunai.phototube.ui.GlassConfirmation(scene,
                title=if(full) "全部重新打标？" else "撤销此批次标签？",
                message=if(full) "会重新调用模型处理本相册的${request.mediaScope.label}，可能产生费用。人工标签和已移除的自动标签会得到保护。" else "只撤销本批次的自动标签变更，保留后续人工修改。这与取消任务不同。",
                confirmLabel=if(full) "开始打标" else "撤销标签",destructive=!full,
                onDismiss={confirmation=null},onConfirm={confirmation=null;execute {
                    if(full) {val run=repository.create(album.id,AiScanMode.FULL,request.mediaScope);replace(run);expanded=run.id}
                    else request.run?.let {repository.undo(it.id);undone.add(it.id)}
                }})
        }
    }
}

@Composable
private fun AiGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AppTheme.colors.group)
        .animateContentSize(spring(1f, 420f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
internal fun aiStateLabel(state: String) = when (state) {
    "PENDING" -> "等待中"; "RUNNING" -> "进行中"; "PAUSED" -> "已暂停"
    "SUCCEEDED" -> "已完成"; "FAILED" -> "失败"; "CANCELLED" -> "已取消"; else -> state
}

internal fun taskTimestamp(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
}.getOrDefault(value)
