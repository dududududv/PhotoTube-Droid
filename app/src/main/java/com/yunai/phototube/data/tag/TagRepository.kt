package com.yunai.phototube.data.tag

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.AssetTag
import kotlinx.coroutines.flow.Flow

interface TagLibraryGateway {
    fun pagedTags(keyword: String): Flow<PagingData<Tag>>
    suspend fun create(name: String): Tag
    suspend fun rename(tagId: Long, name: String): Tag
    suspend fun delete(tagId: Long): TagDeleteResult
}

class TagRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) : TagLibraryGateway {
    override fun pagedTags(keyword: String): Flow<PagingData<Tag>> {
        val normalizedKeyword = keyword.trim().takeIf(String::isNotEmpty)
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = 16,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                TagPagingSource(api(), serviceFactory.moshi, normalizedKeyword)
            },
        ).flow
    }

    suspend fun getTags(keyword: String?, cursor: String? = null): TagPage {
        val response = api().getTags(
            limit = PAGE_SIZE,
            cursor = cursor,
            keyword = keyword?.trim()?.takeIf(String::isNotEmpty),
        )
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空标签列表")
    }

    suspend fun createAndAdd(assetId: String, name: String): AssetTag {
        val tag = create(name)
        return add(assetId, tag.id)
    }

    override suspend fun create(name: String): Tag {
        val response = api().createTag(CreateTagRequest(normalizedTagName(name)))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空标签")
    }

    override suspend fun rename(tagId: Long, name: String): Tag {
        require(tagId > 0) { "标签 ID 必须大于 0" }
        val response = api().updateTag(tagId, UpdateTagRequest(normalizedTagName(name)))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空标签")
    }

    override suspend fun delete(tagId: Long): TagDeleteResult {
        require(tagId > 0) { "标签 ID 必须大于 0" }
        val response = api().deleteTag(tagId)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空标签删除结果")
    }

    suspend fun add(assetId: String, tagId: Long): AssetTag {
        require(tagId > 0) { "标签 ID 必须大于 0" }
        val response = api().addAssetTag(assetId, AssetTagRequest(tagId))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空资产标签")
    }

    suspend fun remove(assetId: String, tagId: Long) {
        require(tagId > 0) { "标签 ID 必须大于 0" }
        val response = api().removeAssetTag(assetId, tagId)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    private fun api() = serviceFactory.create(
        requireNotNull(sessionRepository.currentServer()) { "尚未配置 PhotoTube 服务地址" },
    )

    private companion object {
        const val PAGE_SIZE = 100
    }
}
