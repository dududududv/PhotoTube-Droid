package com.yunai.phototube.data.memory

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface MemoryExclusionGateway {
    fun pagedExclusions(): Flow<PagingData<MemoryExclusion>>
    suspend fun createDateRange(dateFrom: LocalDate, dateTo: LocalDate): MemoryExclusion
    suspend fun delete(exclusionId: String)
}

class MemoryExclusionRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) : MemoryExclusionGateway {
    override fun pagedExclusions(): Flow<PagingData<MemoryExclusion>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = PAGE_SIZE,
            prefetchDistance = 16,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { MemoryExclusionPagingSource(api(), serviceFactory.moshi) },
    ).flow

    override suspend fun createDateRange(dateFrom: LocalDate, dateTo: LocalDate): MemoryExclusion {
        val response = api().createMemoryExclusion(CreateDateMemoryExclusionRequest(dateFrom, dateTo))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空回忆屏蔽规则")
    }

    override suspend fun delete(exclusionId: String) {
        require(runCatching { java.util.UUID.fromString(exclusionId) }.isSuccess) {
            "回忆屏蔽 ID 必须是 UUID"
        }
        val response = api().deleteMemoryExclusion(exclusionId)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    private fun api() = serviceFactory.create(
        requireNotNull(sessionRepository.currentServer()) { "尚未配置 PhotoTube 服务地址" },
    )

    private companion object {
        const val PAGE_SIZE = 100
    }
}
