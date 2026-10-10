package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

internal enum class AiScanMode { INCREMENTAL, FULL }
internal enum class AiMediaScope(val label: String) { PHOTO("照片"), VIDEO("视频"), ALL("照片和视频") }
internal enum class AiTaskAction { PAUSE, RESUME, CANCEL }
internal data class AiTagReport(val name: String, val count: Long, val samples: List<String>)
internal data class AlbumAiRun(
    val id: String, val state: String, val mode: String, val dryRun: Boolean,
    val processed: Long, val total: Long, val cached: Long, val attempts: Long,
    val skipped: Long, val conflicts: Long, val batchSize: Int,
    val createdAt: String, val error: String?, val report: List<AiTagReport>,
    val mediaScope: AiMediaScope = AiMediaScope.PHOTO,
) {
    val active get() = state in setOf("PENDING", "RUNNING", "PAUSED")
    val direct get() = !dryRun && mode in setOf("INCREMENTAL", "FULL")
    val polling get() = state in setOf("PENDING", "RUNNING")
    val canUndo get() = direct && !active && processed > 0
}
internal fun albumAiRun(value: JSONObject): AlbumAiRun {
    val report = value.getJSONArray("report")
    return AlbumAiRun(
        value.getString("id"), value.getString("state"), value.getString("scanMode"), value.getBoolean("dryRun"),
        value.getLong("processedAssets"), value.getLong("totalAssets"), value.getLong("cachedAssets"),
        value.getLong("providerAttemptCount"), value.getLong("skippedCount"), value.getLong("conflictCount"),
        value.getInt("batchSize"), value.getString("createdAt"), value.nullString("error"),
        (0 until report.length()).map { index ->
            val row = report.getJSONObject(index)
            val samples = row.getJSONArray("sampleFileNames")
            AiTagReport(row.getString("tagName"), row.getLong("matchedAssetCount"),
                (0 until samples.length()).map(samples::getString))
        },
        AiMediaScope.valueOf(value.optString("mediaScope", "PHOTO")),
    )
}
internal interface AlbumAiEndpoints {
    @GET("tag-scans") suspend fun history(@Query("albumId") albumId: String): Response<ResponseBody>
    @POST("tag-scans") suspend fun create(@Body body: RequestBody): Response<ResponseBody>
    @GET("tag-scans/{id}") suspend fun detail(@Path("id") id: String): Response<ResponseBody>
    @POST("tag-scans/{id}/control") suspend fun control(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>
    @DELETE("tag-scans/{id}") suspend fun undo(@Path("id") id: String): Response<ResponseBody>
}
internal class AlbumAiRepository(server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client)
        .build().create(AlbumAiEndpoints::class.java)
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    suspend fun history(albumId: String): List<AlbumAiRun> {
        val response = api.history(albumId)
        if (!response.isSuccessful) response.json()
        val array = response.body()?.use { JSONArray(it.string()) }
            ?: throw ServerError("INVALID_RESPONSE", "任务列表为空响应")
        return (0 until array.length()).map { albumAiRun(array.getJSONObject(it)) }
    }
    suspend fun create(albumId: String, mode: AiScanMode, mediaScope: AiMediaScope = AiMediaScope.PHOTO): AlbumAiRun = albumAiRun(api.create(
        JSONObject().put("scope", JSONObject().put("type", "ALBUM").put("albumId", albumId))
            .put("sources", JSONArray().put("AI")).put("dryRun", false).put("scanMode", mode.name)
            .apply { if (mediaScope != AiMediaScope.PHOTO) put("mediaScope", mediaScope.name) }.body(),
    ).json())
    suspend fun detail(id: String) = albumAiRun(api.detail(id).json())
    suspend fun control(id: String, action: AiTaskAction) = albumAiRun(api.control(id,
        JSONObject().put("action", action.name).body()).json())
    suspend fun undo(id: String) {
        val response = api.undo(id)
        if (!response.isSuccessful) response.json()
        response.body()?.close()
    }
}
