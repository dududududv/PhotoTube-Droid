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
import retrofit2.http.POST
import retrofit2.http.Path

internal enum class AssetAction(val path: String, val flag: String? = null) {
    FAVORITE("favorite", "favorite"), ARCHIVE("archive", "archived"), PRIVATE("private", "private"), TRASH("trash"), RESTORE("restore"),
}
internal data class AssetFailure(val id: String, val code: String, val message: String)
internal data class AssetActionResult(val succeeded: Set<String>, val failed: List<AssetFailure>) {
    fun requireSuccess(id: String) {
        failed.firstOrNull { it.id == id }?.let { throw ServerError(it.code, it.message) }
        if (id !in succeeded) throw ServerError("INVALID_RESPONSE", "服务器未返回此照片的操作结果")
    }
}
internal data class FilterActionResult(val matched:Long,val succeeded:Long,val failed:Long)

internal interface AssetActionEndpoints {
    @POST("assets/{action}") suspend fun execute(@Path("action") action: String, @Body body: RequestBody): Response<ResponseBody>
}
internal class AssetActions(private val server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(AssetActionEndpoints::class.java)
    suspend fun executeFilter(action:AssetAction,filter:WallFilters,value:Boolean?=null):FilterActionResult {
        require(action!=AssetAction.RESTORE) {"恢复不支持筛选范围"}
        require((action.flag!=null)==(value!=null))
        val body=JSONObject().put("filter",filter.json()).apply {action.flag?.let {put(it,value)}}
        // Redact before sending: a lost response may still have committed on the server.
        if(action==AssetAction.PRIVATE && value==true) server.privacyChanged(null,true)
        val result=api.execute(action.path,body.toString().toRequestBody("application/json".toMediaType())).json()
        if(action==AssetAction.PRIVATE && value==false) server.privacyChanged(null,false)
        return FilterActionResult(result.getLong("matchedCount"),result.getLong("succeededCount"),result.getLong("failedCount"))
    }
    suspend fun execute(action: AssetAction, ids: Set<String>, value: Boolean? = null): AssetActionResult {
        require(ids.size in 1..500)
        require((action.flag != null) == (value != null))
        val body = JSONObject().put("assetIds", JSONArray(ids.toList())).apply { action.flag?.let { put(it, value) } }
        if(action==AssetAction.PRIVATE && value==true) server.privacyChanged(ids,true)
        val result = api.execute(action.path, body.toString().toRequestBody("application/json".toMediaType())).json()
        val parsed=assetActionResult(result)
        if(action==AssetAction.PRIVATE && value==false) server.privacyChanged(parsed.succeeded-parsed.failed.map {it.id}.toSet(),false)
        return parsed
    }
}

internal fun assetActionResult(result: JSONObject): AssetActionResult {
    val success = result.getJSONArray("succeeded")
    val failure = result.getJSONArray("failed")
    return AssetActionResult((0 until success.length()).map(success::getString).toSet(), (0 until failure.length()).map {
        val row = failure.getJSONObject(it); AssetFailure(row.getString("target"), row.getString("code"), row.getString("message"))
    })
}
