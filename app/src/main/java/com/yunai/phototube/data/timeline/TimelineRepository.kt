package com.yunai.phototube.data.timeline

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import kotlinx.coroutines.flow.Flow

interface TimelineGateway {
    fun pagedAssets(filter: AssetFilter): Flow<PagingData<MediaAsset>>
    suspend fun getSummary(
        filter: AssetFilter,
        granularity: TimelineGranularity,
    ): TimelineSummaryResponse
    fun serverRoot(): ServerRoot
}

class TimelineRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) : TimelineGateway {
    override fun pagedAssets(filter: AssetFilter): Flow<PagingData<MediaAsset>> {
        val api = serviceFactory.create(requireServer())
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = 18,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                TimelinePagingSource(
                    api = api,
                    moshi = serviceFactory.moshi,
                    filter = filter,
                )
            },
        ).flow
    }

    override suspend fun getSummary(
        filter: AssetFilter,
        granularity: TimelineGranularity,
    ): TimelineSummaryResponse {
        val response = serviceFactory.create(requireServer()).getTimelineSummary(
            granularity = granularity.name,
            filters = filter.toQueryMap(),
        )
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空时间轴摘要")
    }

    override fun serverRoot(): ServerRoot = requireServer()

    private fun requireServer(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
