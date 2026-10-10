package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassSettingsAction
import androidx.compose.foundation.selection.selectableGroup
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class SampleInput(val photo: String, val video: String, val grid: Int)

/** Diagnostics stay in route memory; previews and raw responses are never persisted. */
private class SampleDraft(photo: String, video: String, grid: Int) : AutoCloseable {
    var photo by mutableStateOf(photo)
    var video by mutableStateOf(video)
    var grid by mutableIntStateOf(grid)
    var size by mutableIntStateOf(10)
    var media by mutableStateOf(AiMediaScope.ALL)
    var samples by mutableStateOf<List<AiSample>>(emptyList())
    var results by mutableStateOf<List<AiSampleResult>>(emptyList())
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<ServerError?>(null)
    var analyzedInput by mutableStateOf<SampleInput?>(null)
    override fun close() { samples = emptyList(); results = emptyList(); analyzedInput = null; error = null; photo = ""; video = "" }
}

@Composable
internal fun AlbumAiSampleScreen(server: PhotoTubeServer, album: RemoteAlbum, photo: String, video: String, grid: Int, onClose: () -> Unit) {
    val draft = rememberConfigurationState(server to album.id) { SampleDraft(photo, video, grid) }
    val repository = remember(server) { AlbumAiSamples(server) }
    val scope = rememberCoroutineScope()
    var operation by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    fun select() {
        busy = true; draft.message = null; draft.error = null
        operation = scope.launch {
            try {
                val selected = repository.select(album.id, draft.size, draft.media)
                draft.samples = selected; draft.results = emptyList(); draft.analyzedInput = null; expanded = null
                if (selected.isEmpty()) draft.message = "相册中没有符合条件的可用媒体。"
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                draft.error = failure as? ServerError
                draft.message = failure.message ?: "抽样失败"
            }
            finally { busy = false }
        }
    }
    fun analyze() {
        // Freeze this run's input. Each batch is awaited before sending the next.
        val ids = draft.samples.map { it.id }
        val photoPrompt = draft.photo; val videoPrompt = draft.video; val gridSize = draft.grid
        busy = true; draft.results = emptyList(); draft.error = null; expanded = null
        draft.analyzedInput = SampleInput(photoPrompt, videoPrompt, gridSize)
        draft.message = "分析进行中；若中断，请手动重试。已发生的供应商费用无法撤回。"
        operation = scope.launch {
            try {
                for (batch in ids.chunked(8)) {
                    draft.results = draft.results + repository.analyze(album.id, batch, photoPrompt, videoPrompt, gridSize)
                }
                draft.message = "分析完成，结果仅供调试，未写入标签。"
            } catch (cancel: CancellationException) {
                draft.message = "分析已中断，保留已返回结果；不会自动重试。已发生的费用无法撤回。"
                throw cancel
            } catch (failure: Exception) {
                draft.error = failure as? ServerError
                draft.message = "后续分析已停止：${failure.message ?: "请求失败"}。请稍后手动重试。"
            } finally { busy = false }
        }
    }
    ManagementPage("抽样调试", onClose, scrollable = false, overlay={scene ->
        if(confirm) com.yunai.phototube.ui.GlassConfirmation(scene,title="分析 ${draft.samples.size} 项样本？",
            message="将使用当前草稿调用 AI，可能产生费用。重试也会重新计费；结果不会写入标签。",
            confirmLabel="开始分析",onConfirm={confirm=false;analyze()},onDismiss={confirm=false})
    }) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(album.name, color = AppTheme.colors.secondary) }
            item {
                ManagementGroup("选择样本") {
                    Text("抽样不调用模型；开始分析会产生供应商费用，不写标签或创建正式任务。", color = AppTheme.colors.secondary)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30).forEach { size -> GlassChoiceChip("$size 项", draft.size == size, { draft.size = size }, enabled = !busy) }
                    }
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiMediaScope.entries.forEach { media -> GlassChoiceChip(media.label, draft.media == media, { draft.media = media }, enabled = !busy) }
                    }
                    GlassSettingsAction(if (draft.samples.isEmpty()) "随机抽样" else "重新抽样", ::select, enabled = !busy)
                    Text("当前保留 ${draft.samples.size} 项；修改上方选项后重新抽样才会改变这批样本。", color = AppTheme.colors.secondary)
                }
            }
            item {
                ManagementGroup("本次调试草稿") {
                    Text("继承打开时尚未保存的草稿。下方修改只用于本次调试，不保存到相册。", color = AppTheme.colors.secondary)
                    GlassTextField(draft.photo, { draft.photo = it }, "照片提示词", singleLine = false, minLines = 2, maxLines = 5, enabled = !busy, isError = draft.photo.trim().length > 4000, modifier = Modifier.fillMaxWidth())
                    GlassTextField(draft.video, { draft.video = it }, "视频提示词", singleLine = false, minLines = 2, maxLines = 5, enabled = !busy, isError = draft.video.trim().length > 4000, modifier = Modifier.fillMaxWidth())
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (3..5).forEach { size -> GlassChoiceChip("$size × $size", draft.grid == size, { draft.grid = size }, enabled = !busy) }
                    }
                    Text("留空使用系统默认提示词。每段最多 4000 字符。", color = AppTheme.colors.secondary)
                }
            }
            item {
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    GlassSettingsAction("停止", { operation?.cancel() })
                } else GlassSettingsAction(if (draft.results.isEmpty()) "分析这批样本" else "同一批重新分析", { confirm = true },
                    enabled = draft.samples.isNotEmpty() && draft.photo.trim().length <= 4000 && draft.video.trim().length <= 4000)
                draft.message?.let { Text(it, color = AppTheme.colors.secondary) }
                draft.error?.let { error ->
                    SelectionContainer {
                        Text(buildString {
                            append("错误代码：${error.code}")
                            error.logId?.let { append("\n日志编号：$it") }
                        }, color = AppTheme.colors.secondary)
                    }
                    if (error.httpStatus == 429) Text("请求已限流，请稍后手动重试。", color = AppTheme.colors.secondary)
                }
                draft.analyzedInput?.let { input ->
                    if (draft.results.isNotEmpty() && input != SampleInput(draft.photo, draft.video, draft.grid)) {
                        Text("草稿已修改，下方仍是上一次分析的结果。重新分析后才会更新。", color = AppTheme.colors.secondary)
                    }
                }
                Text("已返回 ${draft.results.size} / ${draft.samples.size} 项", color = AppTheme.colors.secondary)
            }
            items(draft.samples, key = { it.id }) { sample ->
                val result = draft.results.firstOrNull { it.id == sample.id }
                ManagementGroup(sample.name) {
                    Text(if (sample.kind == "VIDEO") "视频" else "照片", color = AppTheme.colors.secondary)
                    if (result == null) Text("尚未返回结果", color = AppTheme.colors.secondary)
                    else {
                        Text(result.error ?: result.labels.joinToString(" · ").ifEmpty { "未提取到标签" }, color = AppTheme.colors.text)
                        Text("耗时 ${result.elapsedMs} 毫秒", color = AppTheme.colors.secondary)
                        GlassSettingsAction(if (expanded == sample.id) "收起输入与响应" else "查看实际输入与原始响应", { expanded = if (expanded == sample.id) null else sample.id })
                        if (expanded == sample.id) {
                            result.preview?.let { preview ->
                                val request = ImageRequest.Builder(LocalContext.current).data(preview)
                                    .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
                                AsyncImage(request, "模型实际输入", imageLoader = server.imageLoader, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(240.dp))
                            } ?: Text("未生成输入预览", color = AppTheme.colors.secondary)
                            SelectionContainer { Text(result.raw ?: "未收到原始响应", color = AppTheme.colors.secondary) }
                        }
                    }
                }
            }
        }
    }
}
