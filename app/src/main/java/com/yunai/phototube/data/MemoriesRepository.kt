package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.time.LocalDate

internal data class HomeFeed(val memories:List<RemoteAsset>,val recent:List<RemoteAsset>,val imports:List<RemoteAsset>,val albums:List<RemoteAlbum>,val jobs:List<JobSummary>)
internal data class MemoryExclusion(val id:String,val kind:String,val from:String?,val to:String?)
internal interface MemoriesEndpoints {
    @GET("home") suspend fun home():Response<ResponseBody>
    @GET("memory-exclusions") suspend fun list(@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @POST("memory-exclusions") suspend fun create(@Body body:RequestBody):Response<ResponseBody>
    @DELETE("memory-exclusions/{id}") suspend fun delete(@Path("id") id:String):Response<ResponseBody>
}
internal class MemoriesRepository(val server:PhotoTubeServer) {
    private val api=Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(MemoriesEndpoints::class.java)
    suspend fun home():HomeFeed {
        val json=api.home().json()
        fun media(key:String)=json.getJSONArray(key).let {rows -> (0 until rows.length()).map {remoteAsset(rows.getJSONObject(it))}}
        val albums=json.getJSONArray("frequentAlbums")
        return HomeFeed(media("onThisDay"),media("recentPhotos"),media("recentImports"),(0 until albums.length()).map {remoteAlbum(albums.getJSONObject(it))},parseJobSummaries(json.getJSONObject("jobSummary")))
    }
    suspend fun list(cursor:String?):AlbumPage<MemoryExclusion> {
        val json=api.list(cursor).json();val rows=json.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map {parse(rows.getJSONObject(it))},json.nullString("nextCursor"))
    }
    suspend fun create(from:LocalDate,to:LocalDate):MemoryExclusion {
        require(!to.isBefore(from)) {"结束日期不能早于开始日期"}
        val body=JSONObject().put("kind","DATE_RANGE").put("dateFrom",from.toString()).put("dateTo",to.toString())
        return parse(api.create(body.toString().toRequestBody("application/json".toMediaType())).json())
    }
    suspend fun remove(id:String) {
        val response=api.delete(id)
        if(!response.isSuccessful) response.json()
        response.body()?.close()
    }
    private fun parse(row:JSONObject)=MemoryExclusion(row.getString("id"),row.getString("kind"),row.nullString("dateFrom"),row.nullString("dateTo"))
}
