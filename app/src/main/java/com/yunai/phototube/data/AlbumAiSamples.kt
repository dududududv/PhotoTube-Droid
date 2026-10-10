package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

internal data class AiSample(val id: String, val kind: String, val name: String)
internal data class AiSampleResult(val id: String, val labels: List<String>, val error: String?, val raw: String?, val elapsedMs: Long, val preview: String?)
internal interface AlbumAiSampleEndpoints {
    @POST("albums/{id}/ai-tag-sample")
    suspend fun select(@Path("id") album: String, @Body body: RequestBody): Response<ResponseBody>
    @POST("albums/{id}/ai-tag-sample/analyze")
    suspend fun analyze(@Path("id") album: String, @Body body: RequestBody): Response<ResponseBody>
}

internal class AlbumAiSamples(server: PhotoTubeServer) {
    // Batch duration depends on the provider and global concurrency; the screen owns cancellation.
    // Never retry a chargeable POST automatically, including after a network timeout.
    private val client = server.client.newBuilder().readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(client)
        .build().create(AlbumAiSampleEndpoints::class.java)
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    suspend fun select(album: String, size: Int, scope: AiMediaScope): List<AiSample> {
        require(size in setOf(10, 20, 30))
        val items = api.select(album, JSONObject().put("sampleSize", size).put("mediaScope", scope.name).body()).json().getJSONArray("items")
        return (0 until items.length()).map { index -> items.getJSONObject(index).let {
            AiSample(it.getString("assetId"), it.getString("kind"), it.getString("fileName"))
        } }
    }
    suspend fun analyze(album: String, ids: List<String>, photo: String, video: String, grid: Int): List<AiSampleResult> {
        require(ids.size in 1..8 && ids.distinct().size == ids.size)
        require(grid in 3..5 && photo.trim().length <= 4000 && video.trim().length <= 4000)
        val body = JSONObject().put("assetIds", JSONArray(ids))
            .put("aiPrompt", photo.trim().ifEmpty { null } ?: JSONObject.NULL)
            .put("videoAiPrompt", video.trim().ifEmpty { null } ?: JSONObject.NULL).put("videoAiGridSize", grid)
        val items = api.analyze(album, body.body()).json().getJSONArray("items")
        val results = (0 until items.length()).map { index -> items.getJSONObject(index).let {
            val labels = it.getJSONArray("labels")
            AiSampleResult(it.getString("assetId"), (0 until labels.length()).map(labels::getString),
                it.nullString("error"), it.nullString("rawResponse"), it.getLong("elapsedMs"), it.nullString("previewDataUrl"))
        } }
        if (results.map { it.id } != ids) throw ServerError("INVALID_RESPONSE", "调试结果与样本不匹配，已停止后续分析")
        return results
    }
}
