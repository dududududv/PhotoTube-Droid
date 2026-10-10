package com.yunai.phototube.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

internal data class DuplicateGroup(val hash:String,val count:Long,val bytes:Long,val changedAt:String,val reviewedAt:String?,val previewId:String,val fileName:String)
internal interface DuplicateEndpoints {
    @GET("duplicates") suspend fun groups(@Query("includeReviewed") reviewed:Boolean,@Query("cursor") cursor:String?,@Query("limit") limit:Int=100,@Query("private") privateOnly:Boolean?=null):Response<ResponseBody>
    @GET("duplicates/{hash}") suspend fun members(@Path("hash") hash:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=100,@Query("private") privateOnly:Boolean?=null):Response<ResponseBody>
    @PUT("duplicates/{hash}/review") suspend fun review(@Path("hash") hash:String,@Query("private") privateOnly:Boolean?=null):Response<ResponseBody>
    @DELETE("duplicates/{hash}/review") suspend fun reopen(@Path("hash") hash:String,@Query("private") privateOnly:Boolean?=null):Response<ResponseBody>
}
internal class DuplicatesRepository(val server:PhotoTubeServer) {
    private val api=Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(DuplicateEndpoints::class.java)
    suspend fun groups(reviewed:Boolean,cursor:String?):AlbumPage<DuplicateGroup> {
        val json=api.groups(reviewed,cursor,privateOnly=server.privateMedia.takeIf {it}).json();val rows=json.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map {index -> val row=rows.getJSONObject(index);val preview=row.getJSONObject("representative")
            DuplicateGroup(row.getString("contentHash"),row.getLong("copyCount"),row.getLong("reclaimableBytes"),row.getString("latestChangeAt"),row.nullString("reviewedAt"),preview.getString("assetId"),preview.getString("fileName"))
        },json.nullString("nextCursor"))
    }
    suspend fun members(hash:String,cursor:String?):AlbumPage<RemoteAsset> {
        validate(hash)
        val json=api.members(hash,cursor,privateOnly=server.privateMedia.takeIf {it}).json();val rows=json.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map {remoteAsset(rows.getJSONObject(it))},json.nullString("nextCursor"))
    }
    suspend fun review(hash:String,keep:Boolean) {
        validate(hash)
        val response=if(keep) api.review(hash,server.privateMedia.takeIf {it}) else api.reopen(hash,server.privateMedia.takeIf {it})
        if(!response.isSuccessful) response.json()
        response.body()?.close()
    }
    private fun validate(hash:String) {require(hash.matches(Regex("[0-9a-f]{64}")))}
    fun thumbnail(group:DuplicateGroup):String?=server.thumbnail(group.previewId,group.hash)
}
