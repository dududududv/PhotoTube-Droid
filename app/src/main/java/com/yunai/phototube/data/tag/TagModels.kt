package com.yunai.phototube.data.tag

import java.time.OffsetDateTime

data class TagPage(
    val items: List<Tag>,
    val nextCursor: String?,
)

data class Tag(
    val id: Long,
    val name: String,
    val assetCount: Long,
    val createdAt: String,
    val updatedAt: String,
) {
    init {
        require(id > 0) { "标签 ID 必须大于 0" }
        validateTagName(name)
        require(assetCount >= 0) { "标签资产数不能为负数" }
        require(runCatching { OffsetDateTime.parse(createdAt) }.isSuccess) { "标签创建时间必须是 RFC 3339" }
        require(runCatching { OffsetDateTime.parse(updatedAt) }.isSuccess) { "标签更新时间必须是 RFC 3339" }
    }
}

data class CreateTagRequest(val name: String) {
    init {
        require(name == name.trim()) { "标签名称不能包含首尾空白" }
        validateTagName(name)
    }
}

data class UpdateTagRequest(val name: String) {
    init {
        require(name == name.trim()) { "标签名称不能包含首尾空白" }
        validateTagName(name)
    }
}

data class TagDeleteResult(val affectedAssetCount: Long) {
    init {
        require(affectedAssetCount >= 0) { "受影响资产数不能为负数" }
    }
}

data class AssetTagRequest(val tagId: Long) {
    init {
        require(tagId > 0) { "标签 ID 必须大于 0" }
    }
}

internal fun normalizedTagName(raw: String): String = raw.trim().also(::validateTagName)

private fun validateTagName(name: String) {
    val codePoints = name.codePointCount(0, name.length)
    require(name.isNotEmpty() && codePoints <= 120) { "标签名称必须为 1–120 个字符" }
}
