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
import java.time.Instant

internal data class FrozenOriginal(val assetId: String, val name: String, val path: String, val bytes: Long)
// Not a data class: tokens must never be included by generated toString().
internal class OriginalDeleteConfirmation(val token: String, val phrase: String, val expiresAt: Instant,
    val assetCount: Int, val fileCount: Int, val totalBytes: Long, val items: List<FrozenOriginal>)
internal interface OriginalDeleteEndpoints {
    @POST("assets/delete-original/prepare") suspend fun prepare(@Body body: RequestBody): Response<ResponseBody>
    @POST("assets/delete-original") suspend fun execute(@Body body: RequestBody): Response<ResponseBody>
}
internal class OriginalDeleteRepository(private val server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(OriginalDeleteEndpoints::class.java)
    suspend fun enabled() = server.session().let { it.getBoolean("authenticated") && it.getBoolean("originalDeleteEnabled") }
    suspend fun prepare(ids: Set<String>, password: String): OriginalDeleteConfirmation {
        require(ids.size in 1..20)
        require(password.codePointCount(0, password.length) in 1..1024)
        val response = api.prepare(JSONObject().put("assetIds", JSONArray(ids.toList())).put("password", password).body())
        checkPasswordRateLimit(response)
        val json = response.json()
        val rows = json.getJSONArray("items")
        val items = (0 until rows.length()).map { val item=rows.getJSONObject(it)
            FrozenOriginal(item.getString("assetId"), item.getString("fileName"), item.getString("relativePath"), item.getLong("fileSize"))
        }
        // Resume may return more assets than the selection; the returned list is authoritative.
        require(items.isNotEmpty() && items.all { it.bytes >= 0 })
        require(json.getInt("fileCount") == items.size && json.getInt("assetCount") == items.map { it.assetId }.toSet().size)
        require(items.fold(0L) { sum, item -> Math.addExact(sum, item.bytes) } == json.getLong("totalBytes"))
        val token=json.getString("confirmationToken"); val phrase=json.getString("confirmationPhrase")
        require(token.isNotEmpty() && token.length <= 512 && phrase.isNotEmpty() && phrase.length <= 128)
        return OriginalDeleteConfirmation(token, phrase, Instant.parse(json.getString("expiresAt")), json.getInt("assetCount"), items.size, json.getLong("totalBytes"), items)
    }
    suspend fun execute(confirmation: OriginalDeleteConfirmation, phrase: String): AssetActionResult {
        require(phrase == confirmation.phrase) { "确认短语不一致" }
        require(Instant.now().isBefore(confirmation.expiresAt)) { "确认凭证已过期，请重新验证" }
        return assetActionResult(api.execute(JSONObject().put("confirmationToken", confirmation.token).put("confirmationPhrase", phrase).body()).json())
    }
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
}
