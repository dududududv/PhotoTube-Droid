package com.yunai.phototube.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

internal data class EditVersion(val id:String,val sourceHash:String,val active:Boolean,val sourceState:String,val renderState:String,val width:Int,val height:Int,val createdAt:String,val transform:JSONObject)
internal interface EditVersionEndpoints {
    @GET("assets/{id}/edit-versions") suspend fun list(@Path("id") id:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @POST("assets/{id}/edit-versions") suspend fun create(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @PATCH("assets/{id}/active-edit") suspend fun select(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
}
internal class EditVersionsRepository(val server:PhotoTubeServer) {
    private val api=Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(EditVersionEndpoints::class.java)
    suspend fun list(id:String,cursor:String?):AlbumPage<EditVersion> {
        val json=api.list(id,cursor).json();val rows=json.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map {parse(rows.getJSONObject(it))},json.nullString("nextCursor"))
    }
    suspend fun create(id:String,expected:String?,hash:String,transform:EditTransform):EditVersion {
        require(hash.matches(Regex("[a-f0-9]{64}")))
        val body=JSONObject().put("expectedActiveEditVersionId",expected ?: JSONObject.NULL).put("sourceContentHash",hash).put("transform",transform.json())
        return parse(api.create(id,body.toString().toRequestBody("application/json".toMediaType())).json())
    }
    private fun parse(row:JSONObject)=EditVersion(row.getString("id"),row.getString("sourceContentHash"),row.getBoolean("active"),row.getString("sourceState"),row.getString("renderState"),
        row.getInt("outputWidth"),row.getInt("outputHeight"),row.getString("createdAt"),row.getJSONObject("transform"))

    suspend fun select(id:String,expected:String?,target:String?):JSONObject? {
        val body=JSONObject().put("expectedActiveEditVersionId",expected ?: JSONObject.NULL).put("targetEditVersionId",target ?: JSONObject.NULL)
        val response=api.select(id,body.toString().toRequestBody("application/json".toMediaType()))
        if(!response.isSuccessful) response.json()
        val text=response.body()?.use {it.string()} ?: throw ServerError("INVALID_RESPONSE","未收到版本切换结果")
        return if(text.trim()=="null") null else JSONObject(text)
    }
}
