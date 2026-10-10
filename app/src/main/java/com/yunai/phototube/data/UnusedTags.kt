package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

internal data class UnusedTag(val id: Long, val name: String, val updatedAt: String)
internal data class UnusedTagPage(val items: List<UnusedTag>, val cursor: String?, val total: Long)
internal data class UnusedTagResult(val deleted: Set<Long>, val skipped: Set<Long>)
internal interface UnusedTagEndpoints {
    @GET("tags/unused") suspend fun preview(@Query("cursor") cursor: String?, @Query("limit") limit: Int = 100): Response<ResponseBody>
    @POST("tags/cleanup-unused") suspend fun cleanup(@Body body: RequestBody): Response<ResponseBody>
}
internal class UnusedTagsRepository(server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(UnusedTagEndpoints::class.java)
    suspend fun preview(cursor: String?): UnusedTagPage {
        val value = api.preview(cursor).json()
        val rows = value.getJSONArray("items")
        val next = value.nullString("nextCursor")
        if (next != null && next == cursor) throw ServerError("INVALID_RESPONSE", "服务器返回了重复游标")
        return UnusedTagPage((0 until rows.length()).map { rows.getJSONObject(it).let { row ->
            UnusedTag(row.getLong("id"), row.getString("name"), row.getString("updatedAt"))
        } }, next, value.getLong("total"))
    }
    suspend fun cleanup(tags: List<UnusedTag>): UnusedTagResult {
        require(tags.size in 1..500 && tags.map { it.id }.distinct().size == tags.size)
        val value = api.cleanup(JSONObject().put("tags", JSONArray(tags.map {
            JSONObject().put("id", it.id).put("name", it.name).put("updatedAt", it.updatedAt)
        })).toString().toRequestBody("application/json".toMediaType())).json()
        fun ids(key: String): Set<Long> = value.getJSONArray(key).let { rows -> (0 until rows.length()).map(rows::getLong).toSet() }
        val result = UnusedTagResult(ids("deletedIds"), ids("skippedIds"))
        if (result.deleted.intersect(result.skipped).isNotEmpty() || result.deleted + result.skipped != tags.map { it.id }.toSet()) {
            throw ServerError("INVALID_RESPONSE", "清理结果与提交标签不匹配，请刷新后核对")
        }
        return result
    }
}
