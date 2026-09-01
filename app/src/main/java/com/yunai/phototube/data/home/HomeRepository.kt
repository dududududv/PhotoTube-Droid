package com.yunai.phototube.data.home

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository

interface HomeFeedGateway {
    suspend fun getHomeFeed(): HomeFeed
    fun serverRoot(): ServerRoot
}

class HomeRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) : HomeFeedGateway {
    override suspend fun getHomeFeed(): HomeFeed {
        val response = serviceFactory.create(serverRoot()).getHomeFeed()
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空首页摘要")
    }

    override fun serverRoot(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }
}
