package com.yunai.phototube.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.edit.EDIT_PPM
import com.yunai.phototube.data.edit.EditRenderState
import com.yunai.phototube.data.edit.EditSourceState
import com.yunai.phototube.data.edit.EditTransform
import com.yunai.phototube.data.edit.EditVersion
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.components.DiagnosticErrorText

@Composable
fun PhotoEditorDialog(
    asset: MediaAsset,
    state: AssetEditUiState,
    serverRoot: ServerRoot,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onResetDraft: () -> Unit,
    onResetToSource: () -> Unit,
    onHorizontalCropChanged: (Float, Float) -> Unit,
    onVerticalCropChanged: (Float, Float) -> Unit,
    onSave: () -> Unit,
    onSelectVersion: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onConfirmDiscardAndSelect: () -> Unit,
    onDismissDiscardSelection: () -> Unit,
) {
    var showExitConfirmation by remember { mutableStateOf(false) }
    val requestClose = {
        if (state.hasUnsavedChanges) showExitConfirmation = true else onDismiss()
    }
    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = requestClose)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C0E))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            EditorTopBar(
                state = state,
                onClose = requestClose,
                onSave = onSave,
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                !state.isReady -> EditorLoadFailure(state, onRetry)
                else -> EditorWorkspace(
                    asset = asset,
                    state = state,
                    serverRoot = serverRoot,
                    onRotate = onRotate,
                    onFlipHorizontal = onFlipHorizontal,
                    onFlipVertical = onFlipVertical,
                    onResetDraft = onResetDraft,
                    onResetToSource = onResetToSource,
                    onHorizontalCropChanged = onHorizontalCropChanged,
                    onVerticalCropChanged = onVerticalCropChanged,
                    onSelectVersion = onSelectVersion,
                    onLoadMore = onLoadMore,
                )
            }
        }
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("放弃未保存的编辑？") },
            text = { Text("裁剪、旋转和镜像草稿尚未保存。已保存的历史版本不会受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        onDismiss()
                    },
                ) { Text("放弃草稿") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("继续编辑") }
            },
        )
    }
    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDiscardSelection,
            title = { Text("切换显示版本？") },
            text = { Text("切换会放弃当前未保存草稿，但不会删除任何已保存版本。") },
            confirmButton = {
                TextButton(onClick = onConfirmDiscardAndSelect) { Text("放弃并切换") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscardSelection) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    state: AssetEditUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "退出编辑", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text("非破坏性编辑", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("只保存配方，不修改原文件", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        Button(
            onClick = onSave,
            enabled = state.hasUnsavedChanges && !state.isSaving,
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Save, contentDescription = null, Modifier.size(18.dp))
            }
            Text("保存新版本", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun EditorLoadFailure(state: AssetEditUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(
            message = state.error?.message ?: "编辑历史读取失败",
            logId = state.error?.logId,
            modifier = Modifier.fillMaxWidth(),
            messageColor = Color.White,
        )
        FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("重试")
        }
    }
}

@Composable
private fun EditorWorkspace(
    asset: MediaAsset,
    state: AssetEditUiState,
    serverRoot: ServerRoot,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onResetDraft: () -> Unit,
    onResetToSource: () -> Unit,
    onHorizontalCropChanged: (Float, Float) -> Unit,
    onVerticalCropChanged: (Float, Float) -> Unit,
    onSelectVersion: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        EditPreview(
            asset = asset,
            transform = state.draft,
            serverRoot = serverRoot,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF17191D))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EditorControls(
                transform = state.draft,
                enabled = !state.isSaving,
                onRotate = onRotate,
                onFlipHorizontal = onFlipHorizontal,
                onFlipVertical = onFlipVertical,
                onResetDraft = onResetDraft,
                onResetToSource = onResetToSource,
            )
            CropSliders(
                transform = state.draft,
                enabled = !state.isSaving,
                onHorizontalCropChanged = onHorizontalCropChanged,
                onVerticalCropChanged = onVerticalCropChanged,
            )
            EditHistory(
                state = state,
                onSelectVersion = onSelectVersion,
                onLoadMore = onLoadMore,
            )
            state.message?.let {
                Text(it, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
            }
            state.error?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EditPreview(
    asset: MediaAsset,
    transform: EditTransform,
    serverRoot: ServerRoot,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val rotated = transform.rotationDegrees == 90 || transform.rotationDegrees == 270
        val sourceWidth = (asset.width ?: 4).coerceAtLeast(1)
        val sourceHeight = (asset.height ?: 3).coerceAtLeast(1)
        val frameAspect = if (rotated) {
            sourceHeight.toFloat() / sourceWidth
        } else {
            sourceWidth.toFloat() / sourceHeight
        }
        val widthFromHeight = maxHeight * frameAspect
        val frameWidth = if (widthFromHeight <= maxWidth) widthFromHeight else maxWidth
        val frameHeight = frameWidth / frameAspect
        Box(
            modifier = Modifier
                .requiredSize(frameWidth, frameHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = asset.originalUrl(serverRoot),
                contentDescription = "${asset.fileName} 编辑预览",
                modifier = Modifier
                    .requiredSize(
                        width = if (rotated) frameHeight else frameWidth,
                        height = if (rotated) frameWidth else frameHeight,
                    )
                    .graphicsLayer(
                        rotationZ = transform.rotationDegrees.toFloat(),
                        scaleX = if (transform.flipHorizontal) -1f else 1f,
                        scaleY = if (transform.flipVertical) -1f else 1f,
                    ),
                contentScale = ContentScale.FillBounds,
            )
            CropMask(transform)
        }
    }
}

@Composable
private fun CropMask(transform: EditTransform) {
    Canvas(Modifier.fillMaxSize()) {
        val left = size.width * transform.cropXPPM / EDIT_PPM
        val top = size.height * transform.cropYPPM / EDIT_PPM
        val right = left + size.width * transform.cropWidthPPM / EDIT_PPM
        val bottom = top + size.height * transform.cropHeightPPM / EDIT_PPM
        val shade = Color.Black.copy(alpha = 0.5f)
        drawRect(shade, size = androidx.compose.ui.geometry.Size(size.width, top))
        drawRect(shade, topLeft = androidx.compose.ui.geometry.Offset(0f, bottom))
        drawRect(
            shade,
            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(left, bottom - top),
        )
        drawRect(
            shade,
            topLeft = androidx.compose.ui.geometry.Offset(right, top),
            size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top),
        )
        drawRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun EditorControls(
    transform: EditTransform,
    enabled: Boolean,
    onRotate: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onResetDraft: () -> Unit,
    onResetToSource: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorControl("旋转", enabled, onRotate) {
            Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null)
        }
        EditorControl("水平", enabled, onFlipHorizontal, transform.flipHorizontal) {
            Icon(Icons.Rounded.Flip, contentDescription = null)
        }
        EditorControl("垂直", enabled, onFlipVertical, transform.flipVertical) {
            Icon(Icons.Rounded.SwapVert, contentDescription = null)
        }
        EditorControl("撤销草稿", enabled, onResetDraft) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
        }
        TextButton(onClick = onResetToSource, enabled = enabled) { Text("重置配方") }
    }
}

@Composable
private fun EditorControl(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: @Composable () -> Unit,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled) {
        icon()
        Text(label, modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
        if (selected) Text(" · 已开", fontSize = 10.sp)
    }
}

@Composable
private fun CropSliders(
    transform: EditTransform,
    enabled: Boolean,
    onHorizontalCropChanged: (Float, Float) -> Unit,
    onVerticalCropChanged: (Float, Float) -> Unit,
) {
    val horizontal = transform.cropXPPM.toFloat() / EDIT_PPM..(
        transform.cropXPPM + transform.cropWidthPPM
        ).toFloat() / EDIT_PPM
    val vertical = transform.cropYPPM.toFloat() / EDIT_PPM..(
        transform.cropYPPM + transform.cropHeightPPM
        ).toFloat() / EDIT_PPM
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("左右裁剪", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            RangeSlider(
                value = horizontal,
                onValueChange = { onHorizontalCropChanged(it.start, it.endInclusive) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                enabled = enabled,
                valueRange = 0f..1f,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("上下裁剪", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            RangeSlider(
                value = vertical,
                onValueChange = { onVerticalCropChanged(it.start, it.endInclusive) },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                enabled = enabled,
                valueRange = 0f..1f,
            )
        }
        Text(
            "保留 ${(transform.cropWidthPPM / 10_000f).toInt()}% × ${(transform.cropHeightPPM / 10_000f).toInt()}% · 旋转 ${transform.rotationDegrees}°",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun EditHistory(
    state: AssetEditUiState,
    onSelectVersion: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        Text("历史版本", color = Color.White, modifier = Modifier.padding(start = 6.dp))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.activeEdit == null,
                onClick = { onSelectVersion(null) },
                enabled = !state.isSaving,
                label = { Text("原图") },
            )
        }
        items(state.versions, key = EditVersion::id) { version ->
            val stale = version.sourceState == EditSourceState.STALE
            FilterChip(
                selected = state.activeEdit?.editVersionId == version.id,
                onClick = { onSelectVersion(version.id) },
                enabled = !state.isSaving && !stale,
                label = {
                    Text(
                        buildString {
                            append(version.createdAt.replace('T', ' ').take(16))
                            if (stale) append(" · 原图已变化")
                            if (version.renderState == EditRenderState.PENDING) append(" · 生成中")
                        },
                    )
                },
            )
        }
        if (state.nextCursor != null) {
            item {
                TextButton(onClick = onLoadMore, enabled = !state.isLoadingMore && !state.isSaving) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text("更早版本")
                }
            }
        }
    }
}
