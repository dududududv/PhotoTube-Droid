package com.yunai.phototube.ui.photos

import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.AssetKind
import java.time.LocalDate
import java.time.ZoneId

enum class TagFilterMode { ALL, ANY, EXCLUDE }

data class FolderFilterSelection(
    val libraryId: String,
    val path: String,
) {
    init {
        require(libraryId.isNotBlank()) { "媒体库 ID 不能为空" }
        require(path.isNotBlank()) { "筛选目录不能为空" }
    }
}

data class AdvancedFilterDraft(
    val kind: AssetKind? = null,
    val favorite: Boolean? = null,
    val rating: Int? = null,
    val takenFrom: LocalDate? = null,
    val takenTo: LocalDate? = null,
    val folder: FolderFilterSelection? = null,
    val tagModes: Map<Long, TagFilterMode> = emptyMap(),
) {
    init {
        require(rating == null || rating in 1..5) { "评分必须为 1–5" }
        require(tagModes.keys.all { it > 0 }) { "标签 ID 必须大于 0" }
        require(TagFilterMode.entries.all { mode -> tagModes.values.count { it == mode } <= 100 }) {
            "每种标签条件不能超过 100 个"
        }
    }

    fun toAssetFilter(zoneId: ZoneId = ZoneId.systemDefault()): AssetFilter {
        require(takenFrom == null || takenTo == null || !takenFrom.isAfter(takenTo)) {
            "开始日期不能晚于结束日期"
        }
        return AssetFilter(
        kind = kind,
        libraryId = folder?.libraryId,
        folderPath = folder?.path,
        favorite = favorite,
        rating = rating,
        takenFrom = takenFrom?.atStartOfDay(zoneId)?.toOffsetDateTime(),
        takenTo = takenTo
            ?.plusDays(1)
            ?.atStartOfDay(zoneId)
            ?.minusNanos(1)
            ?.toOffsetDateTime(),
        tagsAll = tagModes.idsFor(TagFilterMode.ALL),
        tagsAny = tagModes.idsFor(TagFilterMode.ANY),
            tagsExclude = tagModes.idsFor(TagFilterMode.EXCLUDE),
        )
    }

    val activeDimensionCount: Int
        get() = listOfNotNull(
            kind,
            favorite,
            rating,
            takenFrom ?: takenTo,
            folder,
            tagModes.takeIf { it.isNotEmpty() },
        ).size
}

fun AssetFilter.toAdvancedFilterDraft(): AdvancedFilterDraft = AdvancedFilterDraft(
    kind = kind,
    favorite = favorite,
    rating = rating,
    takenFrom = takenFrom?.toLocalDate(),
    takenTo = takenTo?.toLocalDate(),
    folder = if (libraryId != null && folderPath != null) {
        FolderFilterSelection(libraryId, folderPath)
    } else {
        null
    },
    tagModes = buildMap {
        tagsAll.forEach { put(it, TagFilterMode.ALL) }
        tagsAny.forEach { put(it, TagFilterMode.ANY) }
        tagsExclude.forEach { put(it, TagFilterMode.EXCLUDE) }
    },
)

private fun Map<Long, TagFilterMode>.idsFor(mode: TagFilterMode): Set<Long> =
    filterValues { it == mode }.keys
