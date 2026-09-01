package com.yunai.phototube.data.duplicate

import com.yunai.phototube.data.isSha256ContentHash
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.ThumbnailSize
import okhttp3.HttpUrl.Companion.toHttpUrl

data class DuplicateGroupPage(
    val items: List<DuplicateGroup>,
    val nextCursor: String?,
)

data class DuplicateGroup(
    val contentHash: String,
    val copyCount: Long,
    val reclaimableBytes: Long,
    val latestChangeAt: String,
    val reviewedAt: String?,
    val representative: DuplicatePreview,
) {
    init {
        require(contentHash.isSha256ContentHash()) { "重复组 contentHash 必须是 64 位小写 SHA-256" }
        require(copyCount >= 2) { "重复组至少应有两个在线副本" }
        require(reclaimableBytes >= 0) { "可释放容量不能为负数" }
    }
}

data class DuplicatePreview(
    val assetId: String,
    val kind: AssetKind,
    val fileName: String,
    val fileSize: Long,
    val contentHash: String,
) {
    fun thumbnailUrl(serverRoot: ServerRoot, size: ThumbnailSize = ThumbnailSize.MD): String {
        require(contentHash.isSha256ContentHash()) { "缩略图版本必须是 64 位小写 SHA-256" }
        return serverRoot.apiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("assets/$assetId/thumbnail")
            .addQueryParameter("size", size.name)
            .addQueryParameter("v", contentHash)
            .build()
            .toString()
    }
}

data class DuplicateQuery(
    val includeReviewed: Boolean = false,
    val privateScope: Boolean = false,
)
