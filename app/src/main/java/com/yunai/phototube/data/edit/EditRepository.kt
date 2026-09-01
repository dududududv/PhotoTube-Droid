package com.yunai.phototube.data.edit

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository

class EditRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) {
    suspend fun getVersions(
        assetId: String,
        cursor: String? = null,
        limit: Int = 100,
    ): EditVersionPage {
        require(assetId.isNotBlank()) { "资产 ID 不能为空" }
        require(limit in 1..500) { "编辑历史分页大小必须为 1–500" }
        val response = api().getAssetEditVersions(assetId, limit, cursor)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空编辑历史")
    }

    suspend fun createVersion(assetId: String, request: CreateEditVersionRequest): EditVersion {
        require(assetId.isNotBlank()) { "资产 ID 不能为空" }
        val response = api().createAssetEditVersion(assetId, request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空编辑版本")
    }

    suspend fun selectVersion(assetId: String, request: SelectActiveEditRequest): ActiveEdit? {
        require(assetId.isNotBlank()) { "资产 ID 不能为空" }
        val response = api().selectAssetEditVersion(assetId, request)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body()
    }

    fun serverRoot(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private fun api() = serviceFactory.create(serverRoot())
}
