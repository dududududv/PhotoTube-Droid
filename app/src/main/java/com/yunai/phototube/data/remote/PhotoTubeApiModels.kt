package com.yunai.phototube.data.remote

import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

data class HealthResponse(
    val status: String,
    val version: String?,
    val database: DatabaseHealth,
    val aiWorker: AiWorkerHealth?,
    val xmpExport: XmpExportHealth,
)

data class DatabaseHealth(
    val reachable: Boolean,
    val migrationVersion: Long?,
    val dirty: Boolean = false,
)

data class AiWorkerHealth(
    val reachable: Boolean,
    val modelReady: Boolean,
    val modelName: String? = null,
    val modelVersion: String? = null,
    val provider: String? = null,
    val endpoint: String? = null,
)

data class XmpExportHealth(
    val available: Boolean,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class SessionInfo(
    val authenticated: Boolean,
    val passwordSet: Boolean,
    val privateAccessUnlocked: Boolean,
    val privateAccessExpiresAt: String? = null,
    val user: SessionUser? = null,
)

data class SessionUser(
    val id: String,
    val username: String,
    val displayName: String,
)

data class ApiErrorBody(
    val code: String,
    val message: String,
    val target: String? = null,
    val retryable: Boolean,
    val logId: String,
)

data class FavoriteByIdsRequest(
    val assetIds: List<String>,
    val favorite: Boolean,
)

data class ArchiveByIdsRequest(
    val assetIds: List<String>,
    val archived: Boolean,
)

data class RatingByIdsRequest(
    val assetIds: List<String>,
    val rating: Int?,
)

object RatingByIdsRequestJsonAdapter {
    @ToJson
    fun toJson(writer: JsonWriter, value: RatingByIdsRequest?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("assetIds")
        writer.beginArray()
        value.assetIds.forEach(writer::value)
        writer.endArray()
        writer.name("rating")
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        writer.value(value.rating)
        writer.serializeNulls = previousSerializeNulls
        writer.endObject()
    }
}

data class PrivateByIdsRequest(
    val assetIds: List<String>,
    val private: Boolean,
)

data class AssetIdsRequest(
    val assetIds: List<String>,
)

data class PrivateAccessRequest(
    val password: String,
)

data class BatchOperationResult(
    val succeeded: List<String>,
    val failed: List<BatchItemFailure>,
)

data class BatchItemFailure(
    val target: String,
    val code: String,
    val message: String,
)

/**
 * 校验批量响应是否精确覆盖本次请求，防止 UI 把矛盾、漏报或越界结果当成成功。
 * 部分成功本身是合法状态：这里只校验集合结构，不会因 [failed] 非空而抛错。
 */
internal fun BatchOperationResult.validatedAgainst(
    requestedTargets: Collection<String>,
): BatchOperationResult {
    val requested = requestedTargets.toSet()
    check(requested.isNotEmpty()) { "批量结果缺少请求目标" }

    val succeededTargets = succeeded.toSet()
    val failedTargets = failed.map(BatchItemFailure::target).toSet()
    check(succeededTargets.size == succeeded.size) { "PhotoTube 批量结果包含重复成功目标" }
    check(failedTargets.size == failed.size) { "PhotoTube 批量结果包含重复失败目标" }
    check(succeededTargets.intersect(failedTargets).isEmpty()) {
        "PhotoTube 批量结果把同一目标同时标为成功和失败"
    }
    check(succeededTargets + failedTargets == requested) {
        "PhotoTube 批量结果与请求目标不一致"
    }
    return this
}
