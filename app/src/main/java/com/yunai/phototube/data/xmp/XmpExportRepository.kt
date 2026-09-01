package com.yunai.phototube.data.xmp

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import kotlinx.coroutines.flow.Flow

class XmpExportRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) {
    fun pagedRuns(): Flow<PagingData<XmpExportRun>> {
        val api = serviceFactory.create(requireServer())
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE,
                prefetchDistance = 6,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { XmpExportPagingSource(api, serviceFactory.moshi) },
        ).flow
    }

    suspend fun isAvailable(): Boolean {
        val response = api().getHealth()
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body()?.xmpExport?.available
            ?: error("PhotoTube health 缺少 XMP 能力状态")
    }

    suspend fun preview(request: XmpExportRequest): XmpExportPreview {
        val response = api().previewXmpExport(request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空 XMP 预估")
    }

    suspend fun create(request: XmpExportRequest): XmpExportRun {
        val response = api().createXmpExport(request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空 XMP 导出运行")
    }

    suspend fun getRun(runId: String): XmpExportRun {
        require(runId.isNotBlank()) { "XMP 导出运行 ID 不能为空" }
        val response = api().getXmpExportRun(runId)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空 XMP 导出详情")
    }

    private fun api() = serviceFactory.create(requireServer())

    private fun requireServer() = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
