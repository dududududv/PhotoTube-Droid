package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

internal enum class KeyUpdate { KEEP, REPLACE, CLEAR }
internal class PasswordRateLimit(val waitMillis: Long) : java.io.IOException("密码验证过于频繁")
internal fun aiSettingsBody(url: String, model: String, thinking: Boolean, keyUpdate: KeyUpdate, key: String, concurrency: Int = 2): JSONObject {
    require(concurrency in 1..8)
    require(url.trim().length in 8..2048 && model.trim().length in 1..200)
    if (keyUpdate == KeyUpdate.REPLACE) require(key.isNotBlank() && key.length <= 4096)
    return JSONObject().put("baseUrl", url.trim()).put("model", model.trim()).put("thinkingEnabled", thinking).put("concurrency", concurrency).apply {
        when (keyUpdate) { KeyUpdate.KEEP -> Unit; KeyUpdate.CLEAR -> put("apiKey", JSONObject.NULL); KeyUpdate.REPLACE -> put("apiKey", key) }
    }
}
internal interface SystemEndpoints {
    @GET("health") suspend fun health(): Response<ResponseBody>
    @GET("system/status") suspend fun status(): Response<ResponseBody>
    @GET("system/ai-settings") suspend fun ai(): Response<ResponseBody>
    @PUT("system/ai-settings") suspend fun saveAi(@Body body: RequestBody): Response<ResponseBody>
    @POST("auth/logout") suspend fun logout(): Response<ResponseBody>
    @POST("auth/password") suspend fun changePassword(@Body body: RequestBody): Response<ResponseBody>
    @GET("system/diagnostics") suspend fun diagnostics(): Response<ResponseBody>
}
internal class SystemRepository(private val server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(SystemEndpoints::class.java)
    suspend fun health() = api.health().json()
    suspend fun status() = api.status().json()
    suspend fun ai() = api.ai().json()
    suspend fun diagnostics() = SystemDiagnostics.from(api.diagnostics().json())
    suspend fun changePassword(current: String, replacement: String) {
        require(current.codePointCount(0, current.length) in 1..1024)
        require(replacement.codePointCount(0, replacement.length) in 12..1024)
        val body = JSONObject().put("currentPassword", current).put("newPassword", replacement)
        val response = api.changePassword(body.toString().toRequestBody("application/json".toMediaType()))
        checkPasswordRateLimit(response)
        if (response.code() != 204) {
            if (!response.isSuccessful) response.json()
            response.body()?.close()
            throw ServerError("INVALID_RESPONSE", "修改结果未确认，请重新登录核对，勿重复提交")
        }
        response.body()?.close()
        clearLocalSession()
    }
    fun clearLocalSession() { server.cookies.clear(); server.imageLoader.memoryCache?.clear() }
    suspend fun saveAi(body: JSONObject) = api.saveAi(body.toString().toRequestBody("application/json".toMediaType())).json()
    suspend fun logout() {
        val response = api.logout(); if (!response.isSuccessful) response.json(); response.body()?.close()
        server.cookies.clear(); server.imageLoader.memoryCache?.clear()
    }
}

internal fun checkPasswordRateLimit(response: Response<ResponseBody>) {
    if (response.code() == 429) {
        val header = response.headers()["Retry-After"]
        val seconds = header?.toLongOrNull()?.takeIf { it >= 0 }
        val wait = seconds?.coerceAtMost(Long.MAX_VALUE / 1000)?.times(1000) ?: header?.let {
            runCatching { java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis() }.getOrNull()
        } ?: 60_000L
        response.errorBody()?.close()
        throw PasswordRateLimit(wait.coerceAtLeast(1000L))
    }
}
