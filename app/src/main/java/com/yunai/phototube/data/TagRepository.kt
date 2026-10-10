package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

internal interface TagEndpoints {
    @POST("tags") suspend fun create(@Body body: RequestBody): Response<ResponseBody>
    @PATCH("tags/{id}") suspend fun rename(@Path("id") id: Long, @Body body: RequestBody): Response<ResponseBody>
    @DELETE("tags/{id}") suspend fun delete(@Path("id") id: Long): Response<ResponseBody>
    @POST("assets/{asset}/tags") suspend fun attach(@Path("asset") asset: String, @Body body: RequestBody): Response<ResponseBody>
    @DELETE("assets/{asset}/tags/{tag}") suspend fun detach(@Path("asset") asset: String, @Path("tag") tag: Long): Response<ResponseBody>
}
internal class TagRepository(server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(TagEndpoints::class.java)
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    private fun parse(row: JSONObject) = RemoteTag(row.getLong("id"), row.getString("name"), row.getLong("assetCount"))
    private fun nameBody(name: String): RequestBody {
        require(name.trim().length in 1..120)
        return JSONObject().put("name", name.trim()).body()
    }
    suspend fun create(name: String) = parse(api.create(nameBody(name)).json())
    suspend fun rename(id: Long, name: String) = parse(api.rename(id, nameBody(name)).json())
    suspend fun delete(id: Long) = api.delete(id).json()
    suspend fun attach(assetId: String, tagId: Long): JSONObject {
        require(tagId > 0)
        return api.attach(assetId, JSONObject().put("tagId", tagId).body()).json()
    }
    suspend fun detach(assetId: String, tagId: Long) {
        require(tagId > 0)
        val response = api.detach(assetId, tagId)
        if (!response.isSuccessful) response.json()
        response.body()?.close()
    }
}
internal data class PhotoTag(val id: Long, val name: String, val source: String)
internal fun photoTags(detail: JSONObject?): List<PhotoTag> {
    val tags = detail?.optJSONArray("tags") ?: return emptyList()
    return (0 until tags.length()).mapNotNull { index ->
        val tag = tags.optJSONObject(index) ?: return@mapNotNull null
        val id = tag.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
        val name = (tag.opt("name") as? String)?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        // Missing provenance must not prevent a photo from opening or imply manual tagging.
        PhotoTag(id, name, (tag.opt("source") as? String).orEmpty())
    }
}
