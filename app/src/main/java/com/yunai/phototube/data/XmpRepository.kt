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

internal data class XmpScope(val libraries:Set<String>?=null,val includePrivate:Boolean=false) {
    fun body():RequestBody {
        require(libraries==null || libraries.size in 1..100)
        return JSONObject().put("includePrivate",includePrivate).apply {libraries?.let {put("libraryIds",JSONArray(it.sorted()))}}
            .toString().toRequestBody("application/json".toMediaType())
    }
}
internal data class XmpPreview(val scope:XmpScope,val count:Long,val at:String)
internal data class XmpRun(val id:String,val state:String,val libraries:List<String>,val includePrivate:Boolean,val matched:Long,val succeeded:Long,val failed:Long,
    val attempts:Int,val snapshot:String?,val snapshotAt:String?,val path:String?,val error:String?,val createdAt:String,val finishedAt:String?) {
    val active get()=state in setOf("PENDING","RUNNING")
}
internal interface XmpEndpoints {
    @POST("xmp-exports/preview") suspend fun preview(@Body body:RequestBody):Response<ResponseBody>
    @POST("xmp-exports") suspend fun create(@Body body:RequestBody):Response<ResponseBody>
    @GET("xmp-exports") suspend fun list(@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @GET("xmp-exports/{id}") suspend fun detail(@Path("id") id:String):Response<ResponseBody>
}
internal class XmpRepository(val server:PhotoTubeServer) {
    private val api=Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(XmpEndpoints::class.java)
    suspend fun preview(scope:XmpScope):XmpPreview {
        val json=api.preview(scope.body()).json();val ids=json.getJSONArray("resolvedLibraryIds")
        require(json.getBoolean("includePrivate")==scope.includePrivate) {"服务端返回的导出范围与本次选择不一致"}
        return XmpPreview(XmpScope((0 until ids.length()).map(ids::getString).toSet(),json.getBoolean("includePrivate")),json.getLong("estimatedAssetCount"),json.getString("estimatedAt"))
    }
    suspend fun create(scope:XmpScope)=parse(api.create(scope.body()).json())
    suspend fun detail(id:String)=parse(api.detail(id).json())
    suspend fun list(cursor:String?):AlbumPage<XmpRun> {
        val json=api.list(cursor).json();val items=json.getJSONArray("items")
        return AlbumPage((0 until items.length()).map {parse(items.getJSONObject(it))},json.nullString("nextCursor"))
    }
    private fun parse(json:JSONObject):XmpRun {
        val ids=json.getJSONArray("libraryIds")
        return XmpRun(json.getString("id"),json.getString("state"),(0 until ids.length()).map(ids::getString),json.getBoolean("includePrivate"),
            json.getLong("matchedCount"),json.getLong("succeededCount"),json.getLong("failedCount"),json.getInt("attemptCount"),
            json.nullString("snapshotId"),json.nullString("snapshotAt"),json.nullString("logicalPath"),json.nullString("errorCode"),json.getString("createdAt"),json.nullString("finishedAt"))
    }
}
