package com.yunai.phototube.ui.photos

import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineGranularity
import java.time.LocalDate
import java.time.OffsetDateTime

internal data class LoadedTimelineGroup(
    val key: String,
    val title: String,
    val relativeLabel: String?,
    val rows: List<EditorialTimelineRow>,
)

internal data class EditorialTimelineRow(
    val indices: List<Int>,
    val style: EditorialRowStyle,
)

internal enum class EditorialRowStyle { FEATURED, TRIPLE, DOUBLE }

internal fun groupLoadedTimelineAssets(
    assets: List<MediaAsset>,
    granularity: TimelineGranularity,
    today: LocalDate = LocalDate.now(),
): List<LoadedTimelineGroup> {
    val grouped = linkedMapOf<String, MutableList<Int>>()
    val parsedDates = mutableMapOf<String, OffsetDateTime?>()
    assets.forEachIndexed { index, asset ->
        val date = runCatching { OffsetDateTime.parse(asset.takenAt) }.getOrNull()
        val key = date?.groupKey(granularity) ?: "unknown"
        grouped.getOrPut(key) { mutableListOf() } += index
        parsedDates.putIfAbsent(key, date)
    }
    return grouped.map { (key, indices) ->
        val date = parsedDates[key]
        LoadedTimelineGroup(
            key = key,
            title = date?.groupTitle(granularity) ?: "时间未知",
            relativeLabel = if (granularity == TimelineGranularity.DAY) {
                date?.toLocalDate()?.relativeLabel(today)
            } else null,
            rows = editorialRows(indices),
        )
    }
}

internal fun editorialRows(indices: List<Int>): List<EditorialTimelineRow> {
    if (indices.isEmpty()) return emptyList()
    if (indices.size == 1) {
        return listOf(EditorialTimelineRow(indices, EditorialRowStyle.FEATURED))
    }
    if (indices.size <= 3) {
        return listOf(EditorialTimelineRow(indices, EditorialRowStyle.TRIPLE))
    }
    val rows = mutableListOf(
        EditorialTimelineRow(indices.take(2), EditorialRowStyle.FEATURED),
    )
    var cursor = 2
    var useTriple = true
    while (cursor < indices.size) {
        val desired = if (useTriple) 3 else 2
        val rowIndices = indices.drop(cursor).take(desired)
        rows += EditorialTimelineRow(
            indices = rowIndices,
            style = if (useTriple && rowIndices.size == 3) {
                EditorialRowStyle.TRIPLE
            } else {
                EditorialRowStyle.DOUBLE
            },
        )
        cursor += rowIndices.size
        useTriple = !useTriple
    }
    return rows
}

private fun OffsetDateTime.groupKey(granularity: TimelineGranularity): String = when (granularity) {
    TimelineGranularity.DAY -> "%04d-%02d-%02d".format(year, monthValue, dayOfMonth)
    TimelineGranularity.MONTH -> "%04d-%02d".format(year, monthValue)
    TimelineGranularity.YEAR -> year.toString()
}

private fun OffsetDateTime.groupTitle(granularity: TimelineGranularity): String = when (granularity) {
    TimelineGranularity.DAY -> "${monthValue}月${dayOfMonth}日"
    TimelineGranularity.MONTH -> "${year}年${monthValue}月"
    TimelineGranularity.YEAR -> "${year}年"
}

private fun LocalDate.relativeLabel(today: LocalDate): String? = when (this) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> null
}
