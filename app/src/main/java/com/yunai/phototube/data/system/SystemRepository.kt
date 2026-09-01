package com.yunai.phototube.data.system

import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SystemStatusGateway {
    suspend fun getStatus(): SystemStatus
    suspend fun getLocalCacheSnapshot(): LocalCacheSnapshot
    suspend fun clearLocalMediaCaches(): LocalCacheSnapshot
}

class SystemRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
    private val localMediaCache: LocalMediaCache,
) : SystemStatusGateway {
    override suspend fun getStatus(): SystemStatus {
        val response = api().getSystemStatus()
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空系统状态")
    }

    override suspend fun getLocalCacheSnapshot(): LocalCacheSnapshot = withContext(Dispatchers.IO) {
        localMediaCache.snapshot()
    }

    override suspend fun clearLocalMediaCaches(): LocalCacheSnapshot = withContext(Dispatchers.IO) {
        localMediaCache.clearAll()
        localMediaCache.snapshot()
    }

    private fun api() = serviceFactory.create(
        requireNotNull(sessionRepository.currentServer()) { "尚未配置 PhotoTube 服务地址" },
    )
}
