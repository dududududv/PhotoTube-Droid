package com.yunai.phototube.data.memory

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class MemoryExclusionPage(
    val items: List<MemoryExclusion>,
    val nextCursor: String?,
)

data class MemoryExclusion(
    val id: String,
    val kind: MemoryExclusionKind,
    val dateFrom: String?,
    val dateTo: String?,
    val personId: String?,
    val createdAt: String,
) {
    init {
        require(runCatching { UUID.fromString(id) }.isSuccess) { "回忆屏蔽 ID 必须是 UUID" }
        require(runCatching { OffsetDateTime.parse(createdAt) }.isSuccess) {
            "回忆屏蔽创建时间必须是 RFC 3339"
        }
        when (kind) {
            MemoryExclusionKind.DATE_RANGE -> {
                val from = requireDate(dateFrom, "开始日期")
                val to = requireDate(dateTo, "结束日期")
                require(!from.isAfter(to)) { "回忆屏蔽开始日期不能晚于结束日期" }
                require(personId == null) { "日期范围规则不能包含人物 ID" }
            }
            MemoryExclusionKind.PERSON -> {
                require(dateFrom == null && dateTo == null) { "人物规则不能包含日期范围" }
                require(!personId.isNullOrBlank()) { "人物规则必须包含人物 ID" }
            }
        }
    }

    val parsedDateFrom: LocalDate?
        get() = dateFrom?.let(LocalDate::parse)

    val parsedDateTo: LocalDate?
        get() = dateTo?.let(LocalDate::parse)
}

enum class MemoryExclusionKind { DATE_RANGE, PERSON }

data class CreateDateMemoryExclusionRequest(
    val kind: MemoryExclusionKind = MemoryExclusionKind.DATE_RANGE,
    val dateFrom: String,
    val dateTo: String,
) {
    init {
        require(kind == MemoryExclusionKind.DATE_RANGE) { "Android 当前只能创建日期范围规则" }
        val from = requireDate(dateFrom, "开始日期")
        val to = requireDate(dateTo, "结束日期")
        require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
    }

    constructor(dateFrom: LocalDate, dateTo: LocalDate) : this(
        dateFrom = dateFrom.toString(),
        dateTo = dateTo.toString(),
    )
}

private fun requireDate(value: String?, field: String): LocalDate {
    require(!value.isNullOrBlank()) { "$field 不能为空" }
    return runCatching { LocalDate.parse(value) }
        .getOrElse { throw IllegalArgumentException("$field 必须是 YYYY-MM-DD") }
}
