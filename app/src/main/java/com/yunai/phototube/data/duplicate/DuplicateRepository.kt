package com.yunai.phototube.data.duplicate

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.isSha256ContentHash
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.MediaAsset
import kotlinx.coroutines.flow.Flow

class DuplicateRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) {
    fun pagedGroups(query: DuplicateQuery): Flow<PagingData<DuplicateGroup>> {
        val api = serviceFactory.create(serverRoot())
        return Pager(config = pagingConfig()) {
            DuplicateGroupPagingSource(api, serviceFactory.moshi, query)
        }.flow
    }

    fun pagedAssets(contentHash: String, privateScope: Boolean): Flow<PagingData<MediaAsset>> {
        require(contentHash.isSha256ContentHash()) { "重复组 contentHash 必须是 64 位小写 SHA-256" }
        val api = serviceFactory.create(serverRoot())
        return Pager(config = pagingConfig()) {
            DuplicateAssetPagingSource(api, serviceFactory.moshi, contentHash, privateScope)
        }.flow
    }

    suspend fun review(contentHash: String, privateScope: Boolean) {
        require(contentHash.isSha256ContentHash()) { "重复组 contentHash 必须是 64 位小写 SHA-256" }
        val response = api().reviewDuplicateGroup(contentHash, privateScope)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    suspend fun reopen(contentHash: String, privateScope: Boolean) {
        require(contentHash.isSha256ContentHash()) { "重复组 contentHash 必须是 64 位小写 SHA-256" }
        val response = api().reopenDuplicateGroup(contentHash, privateScope)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    fun serverRoot(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private fun api() = serviceFactory.create(serverRoot())

    private fun pagingConfig() = PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = 12,
        enablePlaceholders = false,
    )

    private companion object {
        const val PAGE_SIZE = 100
    }
}
