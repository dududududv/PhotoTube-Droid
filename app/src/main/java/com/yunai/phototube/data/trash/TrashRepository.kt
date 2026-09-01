package com.yunai.phototube.data.trash

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.MediaAsset
import kotlinx.coroutines.flow.Flow

interface TrashFeedGateway {
    fun pagedTrash(): Flow<PagingData<MediaAsset>>
    fun serverRoot(): ServerRoot
}

class TrashRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) : TrashFeedGateway {
    override fun pagedTrash(): Flow<PagingData<MediaAsset>> {
        val api = serviceFactory.create(serverRoot())
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = 18,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                TrashPagingSource(api = api, moshi = serviceFactory.moshi)
            },
        ).flow
    }

    override fun serverRoot(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
