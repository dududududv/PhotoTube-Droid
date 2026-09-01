package com.yunai.phototube.data.folder

import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.session.SessionRepository

class FolderRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
) {
    suspend fun browse(
        libraryId: String? = null,
        path: String = "",
        cursor: String? = null,
    ): FolderListing {
        require(libraryId != null || path.isEmpty()) { "虚拟根不能携带目录路径" }
        require(path.isEmpty() || path.isSafeRelativeFolderPath()) { "目录路径不合法" }
        val response = api().getFolders(
            libraryId = libraryId,
            path = path,
            limit = PAGE_SIZE,
            cursor = cursor,
        )
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空目录列表")
    }

    private fun api() = serviceFactory.create(
        requireNotNull(sessionRepository.currentServer()) { "尚未配置 PhotoTube 服务地址" },
    )

    private companion object {
        const val PAGE_SIZE = 100
    }
}
