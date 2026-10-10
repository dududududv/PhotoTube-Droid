package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassChoiceChip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

internal fun albumPromptChanges(original: JSONObject, photo: String, video: String, gridSize: Int = original.optInt("videoAiGridSize", 3)): JSONObject {
    require(gridSize in 3..5)
    require(photo.trim().length <= 4000 && video.trim().length <= 4000)
    return JSONObject().apply {
        if (gridSize != original.optInt("videoAiGridSize", 3)) put("videoAiGridSize", gridSize)
        if (photo.trim() != original.nullString("aiPrompt").orEmpty()) put("aiPrompt", photo.trim().ifEmpty { null } ?: JSONObject.NULL)
        if (video.trim() != original.nullString("videoAiPrompt").orEmpty()) put("videoAiPrompt", video.trim().ifEmpty { null } ?: JSONObject.NULL)
    }
}

@Composable
internal fun AlbumPrompts(repository: AlbumRepository, album: RemoteAlbum, onSaved: (RemoteAlbum) -> Unit, onClose: () -> Unit) {
    var photo by rememberSaveable(repository.server, album.id) { mutableStateOf(album.raw.nullString("aiPrompt").orEmpty()) }
    var video by rememberSaveable(repository.server, album.id) { mutableStateOf(album.raw.nullString("videoAiPrompt").orEmpty()) }
    var gridSize by rememberSaveable(repository.server, album.id) { mutableIntStateOf(album.raw.optInt("videoAiGridSize", 3)) }
    var sampling by rememberSaveable(repository.server, album.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = photo.trim().length <= 4000 && video.trim().length <= 4000
    val changed = valid && albumPromptChanges(album.raw, photo, video, gridSize).length() > 0
    ManagementPage("相册 AI 提示词", onClose, dismissEnabled = !busy) {
        Text(album.name, color = AppTheme.colors.secondary)
        Text("照片与视频分别配置。留空恢复对应的系统默认提示词，修改只影响之后创建的任务。", color = AppTheme.colors.secondary)
        ManagementGroup("照片提示词") {
            GlassTextField(photo, { photo = it }, "照片提示词内容", enabled = !busy, singleLine = false, minLines = 3, maxLines = 8,
                supportingText = { Text("${photo.trim().length}/4000") }, isError = photo.trim().length > 4000, modifier = Modifier.fillMaxWidth().testTag("photo-ai-prompt"))
        }
        ManagementGroup("视频提示词") {
            GlassTextField(video, { video = it }, "视频提示词内容", enabled = !busy, singleLine = false, minLines = 3, maxLines = 8,
                supportingText = { Text("${video.trim().length}/4000") }, isError = video.trim().length > 4000, modifier = Modifier.fillMaxWidth().testTag("video-ai-prompt"))
        }
        ManagementGroup("视频拼图") {
            Text("每个视频使用一张拼图作为模型输入。", color = AppTheme.colors.secondary)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..5).forEach { size -> GlassChoiceChip("$size × $size · ${size * size} 帧", gridSize == size, { gridSize = size }, enabled = !busy) }
            }
        }
        GlassSettingsAction("抽样调试当前草稿", { sampling = true }, enabled = !busy && valid)
        error?.let { Text(it, color = AppTheme.colors.text) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        GlassSettingsAction("保存提示词", { busy = true; error = null; scope.launch {
            try { onSaved(repository.update(album.id, albumPromptChanges(album.raw, photo, video, gridSize))) }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { error = problem.message ?: "保存失败" }
            finally { busy = false }
        } }, enabled = !busy && changed)
    }
    if (sampling) AlbumAiSampleScreen(repository.server, album, photo, video, gridSize, onClose = { sampling = false })
}
