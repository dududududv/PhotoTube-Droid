package com.yunai.phototube.data.album

import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import com.yunai.phototube.data.isSha256ContentHash
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.MediaAssetPage
import com.yunai.phototube.data.timeline.ThumbnailSize
import java.time.OffsetDateTime
import okhttp3.HttpUrl.Companion.toHttpUrl

data class AlbumPage(
    val items: List<Album>,
    val nextCursor: String?,
)

data class Album(
    val id: String,
    val userId: String,
    val name: String,
    val kind: AlbumKind,
    val sortMode: AlbumSortMode,
    val aiPrompt: String?,
    val filter: Map<String, Any?>?,
    val pathSync: AlbumPathSyncSummary?,
    val coverAssetId: String?,
    val coverFocalPoint: AlbumCoverFocalPoint?,
    val cover: AlbumCover?,
    val assetCount: Long,
    val createdAt: String,
    val updatedAt: String,
)

enum class AlbumKind { NORMAL, SMART, PATH_SYNC }

enum class AlbumSortMode { TAKEN_AT_DESC, TAKEN_AT_ASC }

data class AlbumCoverFocalPoint(val x: Double, val y: Double) {
    init {
        require(x in 0.0..1.0 && y in 0.0..1.0) { "封面焦点必须位于 0–1 范围内" }
    }
}

data class AlbumCover(
    val id: String,
    val kind: AssetKind,
    val fileName: String,
    val contentHash: String?,
) {
    fun thumbnailUrl(serverRoot: ServerRoot, size: ThumbnailSize = ThumbnailSize.MD): String? {
        val version = contentHash?.takeIf(String::isSha256ContentHash) ?: return null
        return serverRoot.apiBaseUrl.toHttpUrl()
            .newBuilder()
            .addPathSegments("assets/$id/thumbnail")
            .addQueryParameter("size", size.name)
            .addQueryParameter("v", version)
            .build()
            .toString()
    }
}

data class AlbumPathSyncSummary(
    val configGeneration: Long,
    val totalPathCount: Int,
    val enabledPathCount: Int,
    val lastRunId: String?,
    val lastRunState: AlbumPathSyncRunState?,
    val lastRunAt: String?,
)

data class AlbumAssetListing(
    val album: Album,
    val assets: MediaAssetPage,
)

data class CreateAlbumRequest(
    val name: String,
    val kind: AlbumKind = AlbumKind.NORMAL,
    val paths: List<AlbumPathInput>? = null,
)

class UpdateAlbumRequest private constructor(
    val name: String? = null,
    val coverAssetId: String? = null,
    val coverFocalPoint: AlbumCoverFocalPoint? = null,
    val sortMode: AlbumSortMode? = null,
    internal val updatesCover: Boolean = false,
) {
    init {
        require(name != null || sortMode != null || updatesCover) { "相册更新至少需要一个字段" }
        name?.let { require(it == it.trim() && it.length in 1..120) { "相册名称长度必须为 1–120" } }
        if (!updatesCover) {
            require(coverAssetId == null && coverFocalPoint == null) { "未更新封面时不能携带封面字段" }
        }
        if (coverAssetId == null) {
            require(coverFocalPoint == null) { "清除封面时焦点必须同时清除" }
        } else {
            require(updatesCover && coverAssetId.isNotBlank()) { "设置封面必须包含资产 ID" }
            require(coverFocalPoint != null) { "设置封面必须包含焦点" }
        }
    }

    companion object {
        fun settings(name: String, sortMode: AlbumSortMode?): UpdateAlbumRequest = UpdateAlbumRequest(
            name = name.trim(),
            sortMode = sortMode,
        )

        fun setCover(
            assetId: String,
            focalPoint: AlbumCoverFocalPoint = AlbumCoverFocalPoint(0.5, 0.5),
        ): UpdateAlbumRequest = UpdateAlbumRequest(
            coverAssetId = assetId,
            coverFocalPoint = focalPoint,
            updatesCover = true,
        )

        fun clearCover(): UpdateAlbumRequest = UpdateAlbumRequest(updatesCover = true)
    }
}

object UpdateAlbumRequestJsonAdapter {
    @ToJson
    fun toJson(writer: JsonWriter, value: UpdateAlbumRequest?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        value.name?.let { writer.name("name").value(it) }
        if (value.updatesCover) {
            val previousSerializeNulls = writer.serializeNulls
            writer.serializeNulls = true
            writer.name("coverAssetId").value(value.coverAssetId)
            writer.name("coverFocalPoint")
            value.coverFocalPoint?.let { point ->
                writer.beginObject()
                writer.name("x").value(point.x)
                writer.name("y").value(point.y)
                writer.endObject()
            } ?: writer.nullValue()
            writer.serializeNulls = previousSerializeNulls
        }
        value.sortMode?.let { writer.name("sortMode").value(it.name) }
        writer.endObject()
    }
}

data class AlbumAssetsRequest(val assetIds: List<String>)

data class AlbumPathInput(
    val libraryId: String,
    val relativePath: String,
    val enabled: Boolean = true,
)

data class AlbumPathPage(
    val items: List<AlbumPath>,
    val nextCursor: String?,
)

data class AlbumPath(
    val id: String,
    val albumId: String,
    val libraryId: String,
    val relativePath: String,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val lastSuccessfulSyncAt: String?,
    val lastRunState: AlbumPathSyncPathState?,
)

data class AlbumPathChangeRequest(
    val action: AlbumPathChangeAction,
    val expectedGeneration: Long,
    val pathId: String? = null,
    val path: AlbumPathInput? = null,
) {
    init {
        require(expectedGeneration > 0) { "路径配置 generation 必须大于 0" }
        when (action) {
            AlbumPathChangeAction.ADD -> require(path != null && pathId == null)
            AlbumPathChangeAction.UPDATE -> require(path != null && !pathId.isNullOrBlank())
            AlbumPathChangeAction.REMOVE -> require(path == null && !pathId.isNullOrBlank())
        }
    }

    companion object {
        fun add(generation: Long, path: AlbumPathInput) = AlbumPathChangeRequest(
            action = AlbumPathChangeAction.ADD,
            expectedGeneration = generation,
            path = path,
        )

        fun update(generation: Long, pathId: String, path: AlbumPathInput) = AlbumPathChangeRequest(
            action = AlbumPathChangeAction.UPDATE,
            expectedGeneration = generation,
            pathId = pathId,
            path = path,
        )

        fun remove(generation: Long, pathId: String) = AlbumPathChangeRequest(
            action = AlbumPathChangeAction.REMOVE,
            expectedGeneration = generation,
            pathId = pathId,
        )
    }
}

enum class AlbumPathChangeAction { ADD, UPDATE, REMOVE }

data class AlbumPathChangePreview(
    val configGeneration: Long,
    val action: AlbumPathChangeAction,
    val affectedPathId: String?,
    val currentCoveredAssets: Long,
    val membersToRemove: Long,
    val membersRetainedByOtherPaths: Long,
    val requiresSync: Boolean,
)

data class AlbumPathChangeAccepted(
    val configGeneration: Long,
    val action: AlbumPathChangeAction,
    val path: AlbumPath?,
    val syncRunId: String? = null,
)

data class PreviewedAlbumPathChange(
    val request: AlbumPathChangeRequest,
    val preview: AlbumPathChangePreview,
)

data class SourceFolderListing(
    val libraryId: String,
    val path: String,
    val items: List<SourceFolder>,
)

data class SourceFolder(
    val name: String,
    val path: String,
)

data class AlbumPathSyncRunPage(
    val items: List<AlbumPathSyncRun>,
    val nextCursor: String?,
)

data class AlbumPathSyncRun(
    val id: String,
    val albumId: String,
    val configGeneration: Long,
    val trigger: AlbumPathSyncTrigger,
    val state: AlbumPathSyncRunState,
    val totalPaths: Int,
    val succeededPaths: Int,
    val failedPaths: Int,
    val offlinePaths: Int,
    val createdAt: String,
    val startedAt: String?,
    val finishedAt: String?,
    val error: String?,
) {
    init {
        require(id.isNotBlank() && albumId.isNotBlank()) { "同步运行 ID 与相册 ID 不能为空" }
        require(configGeneration > 0) { "同步运行 generation 必须大于 0" }
        require(totalPaths >= 0 && succeededPaths >= 0 && failedPaths >= 0 && offlinePaths >= 0) {
            "同步路径计数不能为负数"
        }
        require(succeededPaths + failedPaths + offlinePaths <= totalPaths) {
            "同步路径汇总不能超过总路径数"
        }
        requireRfc3339(createdAt, "同步创建时间")
        startedAt?.let { requireRfc3339(it, "同步开始时间") }
        finishedAt?.let { requireRfc3339(it, "同步完成时间") }
    }
}

enum class AlbumPathSyncTrigger { INITIAL, MANUAL, LIBRARY_SCAN }

enum class AlbumPathSyncRunState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this in setOf(SUCCEEDED, PARTIAL, FAILED, CANCELLED)
}

data class AlbumPathSyncRunDetail(
    val run: AlbumPathSyncRun,
    val pathResults: List<AlbumPathSyncPathResult>,
) {
    init {
        require(pathResults.size <= 100) { "同步详情最多返回 100 条逐路径结果" }
    }
}

data class AlbumPathSyncPathResult(
    val pathId: String,
    val libraryId: String,
    val relativePath: String,
    val state: AlbumPathSyncPathState,
    val discoveredCount: Long,
    val reusedCount: Long,
    val addedCount: Long,
    val removedCount: Long,
    val skippedCount: Long,
    val startedAt: String?,
    val finishedAt: String?,
    val error: String?,
) {
    init {
        require(pathId.isNotBlank() && libraryId.isNotBlank()) { "同步路径与媒体库 ID 不能为空" }
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) { "同步历史必须使用相对路径" }
        require(listOf(discoveredCount, reusedCount, addedCount, removedCount, skippedCount).all { it >= 0 }) {
            "同步逐路径计数不能为负数"
        }
        startedAt?.let { requireRfc3339(it, "路径同步开始时间") }
        finishedAt?.let { requireRfc3339(it, "路径同步完成时间") }
    }
}

enum class AlbumPathSyncPathState { PENDING, RUNNING, SUCCEEDED, OFFLINE, FAILED, SKIPPED }

private fun requireRfc3339(value: String, field: String) {
    require(runCatching { OffsetDateTime.parse(value) }.isSuccess) { "$field 必须是 RFC 3339" }
}
