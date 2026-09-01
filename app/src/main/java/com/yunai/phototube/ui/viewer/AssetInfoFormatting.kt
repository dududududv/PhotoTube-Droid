package com.yunai.phototube.ui.viewer

import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.AssetState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.pow

private val assetDateTimeFormatter = DateTimeFormatter.ofPattern(
    "yyyy年M月d日 HH:mm:ss XXX",
    Locale.SIMPLIFIED_CHINESE,
)

internal fun formatAssetDateTime(value: String): String = try {
    OffsetDateTime.parse(value).format(assetDateTimeFormatter)
} catch (_: DateTimeParseException) {
    value
}

internal fun formatAssetFileSize(bytes: Long): String {
    require(bytes >= 0) { "文件大小不能为负数" }
    if (bytes < 1024) return "$bytes B"

    val units = listOf("KB", "MB", "GB", "TB")
    val exponent = floor(log(bytes.toDouble(), 1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return String.format(Locale.ROOT, "%.1f %s", value, units[exponent - 1])
}

internal fun formatAssetDuration(seconds: Double): String {
    require(seconds >= 0.0 && seconds.isFinite()) { "媒体时长必须是有限非负数" }
    val roundedSeconds = seconds.toLong()
    val hours = roundedSeconds / 3600
    val minutes = (roundedSeconds % 3600) / 60
    val remainingSeconds = roundedSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds)
    }
}

internal fun assetKindLabel(kind: AssetKind): String = when (kind) {
    AssetKind.PHOTO -> "照片"
    AssetKind.VIDEO -> "视频"
}

internal fun assetStateLabel(state: AssetState): String = when (state) {
    AssetState.DISCOVERED -> "正在发现"
    AssetState.BROWSABLE -> "可浏览"
    AssetState.PROCESSING -> "后台处理中"
    AssetState.FAILED -> "处理失败"
    AssetState.OFFLINE -> "原文件离线"
    AssetState.TRASHED -> "位于回收站"
}

internal fun takenAtSourceLabel(source: String): String = when (source) {
    "EXIF" -> "相机 EXIF"
    "FILE_MTIME" -> "文件修改时间"
    "FILE_NAME" -> "文件名"
    "UNKNOWN" -> "来源未知"
    "MANUAL" -> "手动设置"
    else -> source
}
