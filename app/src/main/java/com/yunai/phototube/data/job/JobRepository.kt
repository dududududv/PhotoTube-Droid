package com.yunai.phototube.data.job

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import kotlinx.coroutines.flow.Flow

class JobRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) {
    fun pagedJobs(filter: JobFilter): Flow<PagingData<Job>> {
        val api = serviceFactory.create(requireServer())
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = 16,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { JobPagingSource(api, serviceFactory.moshi, filter) },
        ).flow
    }

    suspend fun getSummary(): JobSummaryResponse {
        val response = api().getJobSummary()
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空任务摘要")
    }

    suspend fun pauseQueue(kind: JobKind) {
        require(kind.canControl) { "AI 任务控制契约尚未开放给 Android" }
        val response = api().pauseJobQueue(kind.name)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    suspend fun resumeQueue(kind: JobKind) {
        require(kind.canControl) { "AI 任务控制契约尚未开放给 Android" }
        val response = api().resumeJobQueue(kind.name)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
    }

    suspend fun cancel(job: Job): Job {
        require(job.canCancel) { "只有等待、暂停或运行中的非 AI 任务可以取消" }
        val response = api().cancelJob(job.id)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空取消结果")
    }

    private fun api() = serviceFactory.create(requireServer())

    private fun requireServer() = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}
