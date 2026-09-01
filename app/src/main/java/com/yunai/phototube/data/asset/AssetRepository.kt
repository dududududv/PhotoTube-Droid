package com.yunai.phototube.data.asset

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.BatchItemFailure
import com.yunai.phototube.data.remote.BatchOperationResult
import com.yunai.phototube.data.remote.ArchiveByIdsRequest
import com.yunai.phototube.data.remote.AssetIdsRequest
import com.yunai.phototube.data.remote.FavoriteByIdsRequest
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import com.yunai.phototube.data.remote.PrivateByIdsRequest
import com.yunai.phototube.data.remote.RatingByIdsRequest
import com.yunai.phototube.data.remote.toApiFailure
import com.yunai.phototube.data.remote.validatedAgainst
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.MediaAsset

interface TrashMutationGateway {
    suspend fun restoreAsset(assetId: String)
    suspend fun purgeAsset(assetId: String)
}

class AssetRepository(
    private val sessionRepository: SessionRepository,
    private val serviceFactory: PhotoTubeServiceFactory,
    private val onProtectedMediaInvalidated: () -> Unit = {},
) : TrashMutationGateway {
    suspend fun getAsset(assetId: String): MediaAsset {
        val response = api().getAsset(assetId)
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return response.body() ?: error("PhotoTube 返回了空资产详情")
    }

    suspend fun setFavorite(assetId: String, favorite: Boolean) {
        val response = api().setAssetsFavorite(
            FavoriteByIdsRequest(assetIds = listOf(assetId), favorite = favorite),
        )
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        val result = response.body() ?: error("PhotoTube 返回了空收藏结果")
        result.requireAssetSucceeded(assetId)
    }

    suspend fun setArchived(assetId: String, archived: Boolean) {
        val response = api().setAssetsArchived(
            ArchiveByIdsRequest(assetIds = listOf(assetId), archived = archived),
        )
        requireSingleSuccess(response, assetId, "归档")
    }

    suspend fun setRating(assetId: String, rating: Int?) {
        require(rating == null || rating in 1..5) { "评分必须为 1–5，或清除评分" }
        val response = api().setAssetsRating(
            RatingByIdsRequest(assetIds = listOf(assetId), rating = rating),
        )
        requireSingleSuccess(response, assetId, "评分")
    }

    suspend fun setPrivate(assetId: String, private: Boolean) {
        val response = api().setAssetsPrivate(
            PrivateByIdsRequest(assetIds = listOf(assetId), private = private),
        )
        requireSingleSuccess(response, assetId, "私密状态")
        onProtectedMediaInvalidated()
    }

    suspend fun moveToTrash(assetId: String) {
        val response = api().trashAssets(AssetIdsRequest(listOf(assetId)))
        requireSingleSuccess(response, assetId, "移入回收站")
        onProtectedMediaInvalidated()
    }

    suspend fun restore(assetIds: List<String>): BatchOperationResult {
        val ids = assetIds.validatedBatch()
        val response = api().restoreAssets(AssetIdsRequest(ids))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        return (response.body() ?: error("PhotoTube 返回了空恢复结果"))
            .validatedAgainst(ids)
    }

    override suspend fun restoreAsset(assetId: String) {
        restore(listOf(assetId)).requireAssetSucceeded(assetId)
    }

    suspend fun purge(assetIds: List<String>): BatchOperationResult {
        val ids = assetIds.validatedBatch()
        val response = api().purgeTrash(AssetIdsRequest(ids))
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        val result = (response.body() ?: error("PhotoTube 返回了空清理结果"))
            .validatedAgainst(ids)
        onProtectedMediaInvalidated()
        return result
    }

    override suspend fun purgeAsset(assetId: String) {
        purge(listOf(assetId)).requireAssetSucceeded(assetId)
    }

    fun serverRoot(): ServerRoot = requireNotNull(sessionRepository.currentServer()) {
        "尚未配置 PhotoTube 服务地址"
    }

    private fun api() = serviceFactory.create(serverRoot())

    private fun requireSingleSuccess(
        response: retrofit2.Response<BatchOperationResult>,
        assetId: String,
        operation: String,
    ) {
        if (!response.isSuccessful) throw response.toApiFailure(serviceFactory.moshi)
        val result = response.body() ?: error("PhotoTube 返回了空${operation}结果")
        result.requireAssetSucceeded(assetId)
    }
}

class AssetMutationFailure(
    val item: BatchItemFailure,
) : Exception(item.message)

internal fun BatchOperationResult.requireAssetSucceeded(assetId: String) {
    validatedAgainst(listOf(assetId))
    failed.firstOrNull()?.let { throw AssetMutationFailure(it) }
    check(assetId in succeeded) { "PhotoTube 批量结果没有包含目标资产" }
}

internal fun List<String>.validatedBatch(): List<String> = distinct().also { ids ->
    require(ids.isNotEmpty()) { "至少选择一个资产" }
    require(ids.size <= 500) { "单次最多操作 500 个资产" }
}
