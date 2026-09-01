package com.yunai.phototube.data.timeline

import com.yunai.phototube.data.isSha256ContentHash
import com.yunai.phototube.data.unicodeCodePointCount
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.edit.ActiveEdit
import com.yunai.phototube.data.edit.EditSourceState
import java.time.OffsetDateTime
import okhttp3.HttpUrl.Companion.toHttpUrl

data class MediaAssetPage(
    val items: List<MediaAsset>,
    val nextCursor: String?,
)

data class MediaAsset(
    val id: String,
    val userId: String,
    val kind: AssetKind,
    val state: AssetState,
    val takenAt: String,
    val takenAtOffsetMinutes: Int?,
    val takenAtSource: String,
    val importedAt: String,
    val libraryId: String,
    val relativePath: String,
    val fileName: String,
    val fileSize: Long,
    val contentHash: String?,
    val width: Int?,
    val height: Int?,
    val favorite: Boolean,
    val archived: Boolean,
    val private: Boolean,
    val rating: Int?,
    val trashedAt: String? = null,
    val retentionDays: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val durationSec: Double? = null,
    val motionPhoto: MotionPhoto? = null,
    val activeEdit: ActiveEdit? = null,
    val tags: List<AssetTag>? = null,
) {
    fun thumbnailUrl(serverRoot: ServerRoot, size: ThumbnailSize): String? {
        val version = contentHash?.takeIf(String::isSha256ContentHash) ?: return null
        activeEdit?.takeIf { it.sourceState == EditSourceState.CURRENT }?.let { active ->
            return editRenderUrl(serverRoot, active.editVersionId, size.name)
        }
        return serverRoot.apiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("assets/$id/thumbnail")
            .addQueryParameter("size", size.name)
            .addQueryParameter("v", version)
            .build()
            .toString()
    }

    fun displayPhotoUrl(serverRoot: ServerRoot): String {
        require(canReadOriginal()) { "只有可浏览资产才能读取原图" }
        return activeEdit
            ?.takeIf { it.sourceState == EditSourceState.CURRENT && contentHash?.isSha256ContentHash() == true }
            ?.let { editRenderUrl(serverRoot, it.editVersionId, "PREVIEW") }
            ?: originalUrl(serverRoot)
    }

    fun editRenderUrl(serverRoot: ServerRoot, editVersionId: String, size: String): String {
        val version = requireNotNull(contentHash?.takeIf(String::isSha256ContentHash)) {
            "编辑渲染需要 64 位小写 SHA-256 contentHash"
        }
        require(size in setOf("SM", "MD", "PREVIEW")) { "不支持的编辑渲染尺寸" }
        return serverRoot.apiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("assets/$id/edit-versions/$editVersionId/render")
            .addQueryParameter("size", size)
            .addQueryParameter("v", version)
            .build()
            .toString()
    }

    fun isEditablePhoto(): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return kind == AssetKind.PHOTO &&
            state == AssetState.BROWSABLE &&
            contentHash?.isSha256ContentHash() == true &&
            extension in setOf("jpg", "jpeg", "png")
    }

    fun canReadOriginal(): Boolean = state == AssetState.BROWSABLE

    fun originalUrl(serverRoot: ServerRoot): String {
        require(canReadOriginal()) { "只有可浏览资产才能读取原图" }
        return serverRoot.apiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("assets/$id/original")
            .build()
            .toString()
    }

    fun motionVideoUrl(serverRoot: ServerRoot): String? {
        if (!canReadOriginal()) return null
        return motionPhoto?.let {
            serverRoot.apiBaseUrl.toHttpUrl()
                .newBuilder()
                .addPathSegments("assets/$id/motion-video")
                .build()
                .toString()
        }
    }
}

data class MotionPhoto(
    val format: String,
    val videoMime: String,
    val width: Int,
    val height: Int,
    val durationSec: Double,
)

data class AssetTag(
    val id: Long,
    val name: String,
    val source: String,
    val confidence: Double?,
    val confirmed: Boolean,
)

enum class AssetKind { PHOTO, VIDEO }

enum class AssetState { DISCOVERED, BROWSABLE, PROCESSING, FAILED, OFFLINE, TRASHED }

enum class ThumbnailSize { SM, MD }

data class TimelineSummaryResponse(
    val granularity: TimelineGranularity,
    val groups: List<TimelineGroup>,
    val totalCount: Long? = null,
)

data class TimelineGroup(
    val key: String,
    val startsAt: String,
    val count: Long,
)

enum class TimelineGranularity { DAY, MONTH, YEAR }

data class AssetFilter(
    val kind: AssetKind? = null,
    val libraryId: String? = null,
    val keyword: String? = null,
    val folderPath: String? = null,
    val favorite: Boolean? = null,
    val rating: Int? = null,
    val archived: Boolean = false,
    val private: Boolean = false,
    val takenFrom: OffsetDateTime? = null,
    val takenTo: OffsetDateTime? = null,
    val tagsAll: Set<Long> = emptySet(),
    val tagsAny: Set<Long> = emptySet(),
    val tagsExclude: Set<Long> = emptySet(),
) {
    init {
        require(folderPath == null || !libraryId.isNullOrBlank()) {
            "folderPath 必须与 libraryId 同时提供"
        }
        require(libraryId == null || libraryId.isNotBlank()) { "媒体库 ID 不能为空" }
        folderPath?.let { path ->
            require(
                path.isNotBlank() &&
                    path.length <= 4096 &&
                    !path.startsWith('/') &&
                    !path.endsWith('/') &&
                    !path.contains('\\') &&
                    path.split('/').all { it.isNotEmpty() && it != "." && it != ".." },
            ) { "folderPath 必须是安全的媒体库相对路径" }
        }
        keyword?.trim()?.let {
            require(it.unicodeCodePointCount() in 3..255) { "搜索关键词长度必须为 3–255 个 Unicode 字符" }
        }
        require(rating == null || rating in 1..5) { "评分必须为 1–5" }
        require(takenFrom == null || takenTo == null || !takenFrom.isAfter(takenTo)) {
            "拍摄时间下界不能晚于上界"
        }
        require(tagsAll.size <= 100 && tagsAny.size <= 100 && tagsExclude.size <= 100) {
            "单组标签筛选不能超过 100 个"
        }
        require((tagsAll + tagsAny + tagsExclude).all { it > 0 }) { "标签 ID 必须大于 0" }
    }

    fun toQueryMap(): Map<String, String> = buildMap {
        kind?.let { put("kind", it.name) }
        libraryId?.let { put("libraryId", it) }
        keyword?.trim()?.let { put("keyword", it) }
        folderPath?.let { put("folderPath", it) }
        favorite?.let { put("favorite", it.toString()) }
        rating?.let { put("rating", it.toString()) }
        put("archived", archived.toString())
        put("private", private.toString())
        takenFrom?.let { put("takenFrom", it.toString()) }
        takenTo?.let { put("takenTo", it.toString()) }
        tagsAll.toCsv()?.let { put("tagsAll", it) }
        tagsAny.toCsv()?.let { put("tagsAny", it) }
        tagsExclude.toCsv()?.let { put("tagsExclude", it) }
    }

    fun withoutTag(tagId: Long): AssetFilter = copy(
        tagsAll = tagsAll - tagId,
        tagsAny = tagsAny - tagId,
        tagsExclude = tagsExclude - tagId,
    )
}

private fun Set<Long>.toCsv(): String? = takeIf { it.isNotEmpty() }
    ?.sorted()
    ?.joinToString(",")
