package com.yunai.phototube.data.album

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.BatchOperationResult
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.isInvalidCursorFailure
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.remote.validatedAgainst
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.MediaAsset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AlbumDetailGateway {
    fun pagedAlbumAssets(albumId: String): Flow<PagingData<MediaAsset>>
    fun pagedPathSyncRuns(albumId: String): Flow<PagingData<AlbumPathSyncRun>>
    suspend fun getAlbumListing(albumId: String): AlbumAssetListing
    suspend fun delete(album: Album)
    suspend fun updateSettings(album: Album, name: String, sortMode: AlbumSortMode): Album
    suspend fun setCover(album: Album, assetId: String): Album
    suspend fun clearCover(album: Album): Album
    suspend fun addAssets(album: Album, assetIds: List<String>): BatchOperationResult
    suspend fun removeAssets(album: Album, assetIds: List<String>): BatchOperationResult
    suspend fun browseSourceFolders(path: String = ""): SourceFolderListing
    suspend fun getAllPaths(album: Album): List<AlbumPath>
    suspend fun previewPathChange(
        album: Album,
        request: AlbumPathChangeRequest,
    ): PreviewedAlbumPathChange
    suspend fun applyPreviewedPathChange(
        album: Album,
        previewed: PreviewedAlbumPathChange,
    ): AlbumPathChangeAccepted
    suspend fun triggerManualSync(album: Album): AlbumPathSyncRun
    fun observeSync(albumId: String, syncRunId: String): Flow<AlbumPathSyncRunDetail>
    suspend fun getSyncRunDetail(albumId: String, syncRunId: String): AlbumPathSyncRunDetail
    fun serverRoot(): ServerRoot
}

class AlbumRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) : AlbumDetailGateway {
    fun pagedAlbums(): Flow<PagingData<Album>> = Pager(
        config = pagingConfig(),
        pagingSourceFactory = { AlbumPagingSource(api(), serviceFactory.moshi) },
    ).flow

    override fun pagedAlbumAssets(albumId: String): Flow<PagingData<MediaAsset>> = Pager(
        config = pagingConfig(),
        pagingSourceFactory = { AlbumAssetPagingSource(albumId, api(), serviceFactory.moshi) },
    ).flow

    override fun pagedPathSyncRuns(albumId: String): Flow<PagingData<AlbumPathSyncRun>> = Pager(
        config = pagingConfig(),
        pagingSourceFactory = {
            AlbumPathSyncRunPagingSource(albumId, api(), serviceFactory.moshi)
        },
    ).flow

    override suspend fun getAlbumListing(albumId: String): AlbumAssetListing {
        val response = api().getAlbumAssets(albumId = albumId, limit = PAGE_SIZE, cursor = null)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空相册详情")
    }

    suspend fun createNormal(name: String): Album = createAlbum(
        CreateAlbumRequest(name = normalizedName(name), kind = AlbumKind.NORMAL),
    )

    suspend fun createPathSync(name: String, paths: List<AlbumPathInput>): Album {
        require(paths.size in 1..100) { "路径相册必须选择 1–100 个目录" }
        paths.forEach(::validatePathInput)
        return createAlbum(
            CreateAlbumRequest(
                name = normalizedName(name),
                kind = AlbumKind.PATH_SYNC,
                paths = paths,
            ),
        )
    }

    override suspend fun delete(album: Album) {
        val response = api().deleteAlbum(album.id)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    override suspend fun updateSettings(album: Album, name: String, sortMode: AlbumSortMode): Album {
        require(album.kind != AlbumKind.SMART || sortMode == album.sortMode) {
            "智能相册排序由筛选规则固定"
        }
        return update(album.id, UpdateAlbumRequest.settings(normalizedName(name), sortMode))
    }

    override suspend fun setCover(album: Album, assetId: String): Album {
        require(assetId.isNotBlank()) { "封面资产 ID 不能为空" }
        return update(album.id, UpdateAlbumRequest.setCover(assetId))
    }

    override suspend fun clearCover(album: Album): Album = update(
        album.id,
        UpdateAlbumRequest.clearCover(),
    )

    private suspend fun update(albumId: String, request: UpdateAlbumRequest): Album {
        val response = api().updateAlbum(albumId, request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空相册")
    }

    override suspend fun addAssets(album: Album, assetIds: List<String>): BatchOperationResult {
        require(album.kind == AlbumKind.NORMAL) { "只有普通相册可以手工添加照片" }
        val ids = validatedAssetBatch(assetIds)
        val response = api().addAlbumAssets(album.id, AlbumAssetsRequest(ids))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return (response.body() ?: error("PhotoTube 返回了空相册成员结果"))
            .validatedAgainst(ids)
    }

    override suspend fun removeAssets(album: Album, assetIds: List<String>): BatchOperationResult {
        require(album.kind == AlbumKind.NORMAL) { "只有普通相册可以手工移除照片" }
        val ids = validatedAssetBatch(assetIds)
        val response = api().removeAlbumAssets(album.id, AlbumAssetsRequest(ids))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return (response.body() ?: error("PhotoTube 返回了空相册成员结果"))
            .validatedAgainst(ids)
    }

    override suspend fun browseSourceFolders(path: String): SourceFolderListing {
        require(!path.startsWith('/') && ".." !in path.split('/') && '\\' !in path) {
            "只能浏览媒体挂载内的相对目录"
        }
        val response = api().getSourceFolders(path)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空目录列表")
    }

    override suspend fun getAllPaths(album: Album): List<AlbumPath> {
        require(album.kind == AlbumKind.PATH_SYNC) { "只有路径相册有路径配置" }
        return loadAlbumPathsWithCursorRecovery { cursor ->
            val response = api().getAlbumPaths(album.id, limit = PAGE_SIZE, cursor = cursor)
            if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
            response.body() ?: error("PhotoTube 返回了空路径列表")
        }
    }

    override suspend fun previewPathChange(
        album: Album,
        request: AlbumPathChangeRequest,
    ): PreviewedAlbumPathChange {
        require(album.kind == AlbumKind.PATH_SYNC) { "只有路径相册可以修改路径" }
        require(request.expectedGeneration == album.pathSync?.configGeneration) {
            "路径配置已变化，请刷新后重试"
        }
        val response = api().previewAlbumPathChange(album.id, request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        val preview = response.body() ?: error("PhotoTube 返回了空路径变更预览")
        check(preview.action == request.action) { "PhotoTube 返回了不匹配的路径变更预览" }
        return PreviewedAlbumPathChange(request, preview)
    }

    override suspend fun applyPreviewedPathChange(
        album: Album,
        previewed: PreviewedAlbumPathChange,
    ): AlbumPathChangeAccepted {
        require(album.kind == AlbumKind.PATH_SYNC) { "只有路径相册可以修改路径" }
        require(previewed.request.expectedGeneration == previewed.preview.configGeneration) {
            "路径预览 generation 已失效"
        }
        val response = api().applyAlbumPathChange(album.id, previewed.request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空路径变更结果")
    }

    override suspend fun triggerManualSync(album: Album): AlbumPathSyncRun {
        require(album.kind == AlbumKind.PATH_SYNC) { "只有路径相册可以扫描目录" }
        val response = api().triggerAlbumPathSync(album.id)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空同步运行")
    }

    override fun observeSync(albumId: String, syncRunId: String): Flow<AlbumPathSyncRunDetail> = flow {
        var attempt = 0
        while (true) {
            val detail = getSyncRunDetail(albumId, syncRunId)
            emit(detail)
            if (detail.run.state.isTerminal) break
            delay(POLL_DELAYS_MS[attempt.coerceAtMost(POLL_DELAYS_MS.lastIndex)])
            attempt += 1
        }
    }

    override suspend fun getSyncRunDetail(albumId: String, syncRunId: String): AlbumPathSyncRunDetail {
        require(albumId.isNotBlank() && syncRunId.isNotBlank()) { "相册 ID 与同步运行 ID 不能为空" }
        val response = api().getAlbumPathSyncRun(albumId, syncRunId)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空同步详情")
    }

    override fun serverRoot(): ServerRoot = requireServer()

    private suspend fun createAlbum(request: CreateAlbumRequest): Album {
        val response = api().createAlbum(request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空相册")
    }

    private fun api() = serviceFactory.create(requireServer())

    private fun requireServer(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private fun normalizedName(name: String): String = name.trim().also {
        require(it.length in 1..120) { "相册名称长度必须为 1–120" }
    }

    private fun validatePathInput(path: AlbumPathInput) {
        require(path.libraryId.isNotBlank()) { "目录缺少媒体库 ID" }
        require(path.relativePath.length in 1..4096) { "目录路径长度必须为 1–4096" }
        require(!path.relativePath.startsWith('/') && ".." !in path.relativePath.split('/')) {
            "目录必须是媒体挂载内的相对路径"
        }
    }

    private fun validatedAssetBatch(assetIds: List<String>): List<String> = assetIds.distinct().also { ids ->
        require(ids.isNotEmpty()) { "至少选择一项资产" }
        require(ids.size <= 500) { "单次最多操作 500 项资产" }
    }

    private fun pagingConfig() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = 12,
        enablePlaceholders = false,
    )

    private companion object {
        const val PAGE_SIZE = 100
        val POLL_DELAYS_MS = longArrayOf(1_000, 2_000, 3_000, 5_000)
    }
}

internal suspend fun loadAlbumPathsWithCursorRecovery(
    loadPage: suspend (String?) -> AlbumPathPage,
): List<AlbumPath> {
    var restartCount = 0
    while (true) {
        val result = mutableListOf<AlbumPath>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        try {
            do {
                val page = loadPage(cursor)
                result += page.items
                cursor = page.nextCursor
                check(cursor == null || seenCursors.add(cursor)) {
                    "PhotoTube 返回了循环路径游标"
                }
            } while (cursor != null)
            return result
        } catch (failure: Throwable) {
            if (!failure.isInvalidCursorFailure(cursor) || restartCount >= 1) throw failure
            restartCount += 1
        }
    }
}
