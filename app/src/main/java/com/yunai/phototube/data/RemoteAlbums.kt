package com.yunai.phototube.data

import androidx.paging.*
import kotlinx.coroutines.CancellationException
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.*

internal data class RemoteAlbum(val id:String,val name:String,val kind:String,val sortMode:String,val count:Long,
    val cover:RemoteAsset?,val focusX:Float,val focusY:Float,val raw:JSONObject)
internal data class AlbumPage<T>(val items:List<T>,val cursor:String?)
internal fun remoteAsset(json:JSONObject)=json.run {RemoteAsset(getString("id"),getString("kind"),getString("state"),getString("takenAt"),nullInt("takenAtOffsetMinutes"),getString("fileName"),nullString("contentHash"),nullInt("width"),nullInt("height"),getBoolean("favorite"),if(isNull("durationSec")) null else getDouble("durationSec"))}
internal fun remoteAlbum(json:JSONObject):RemoteAlbum {
    val cover=json.optJSONObject("cover")?.let {RemoteAsset(it.getString("id"),it.getString("kind"),"BROWSABLE","1970-01-01T00:00:00Z",null,it.getString("fileName"),it.nullString("contentHash"),null,null,false,null)}
    val point=json.optJSONObject("coverFocalPoint")
    return RemoteAlbum(json.getString("id"),json.getString("name"),json.getString("kind"),json.getString("sortMode"),json.getLong("assetCount"),cover,point?.optDouble("x",.5)?.toFloat() ?: .5f,point?.optDouble("y",.5)?.toFloat() ?: .5f,json)
}
internal interface AlbumEndpoints {
    @POST("albums/{id}/assets") suspend fun add(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @HTTP(method="DELETE",path="albums/{id}/assets",hasBody=true) suspend fun remove(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @GET("albums/{id}/paths") suspend fun paths(@Path("id") id:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @POST("albums/{id}/path-change-previews") suspend fun preview(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @POST("albums/{id}/path-changes") suspend fun applyPaths(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @GET("source-folders") suspend fun folders(@Query("path") path:String):Response<ResponseBody>
    @GET("albums/{id}/syncs") suspend fun scanHistory(@Path("id") id:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @POST("albums/{id}/syncs") suspend fun scan(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @GET("albums/{id}/syncs/{run}") suspend fun scanState(@Path("id") id:String,@Path("run") run:String):Response<ResponseBody>
    @GET("albums") suspend fun list(@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @GET("albums/{id}/assets") suspend fun members(@Path("id") id:String,@Query("cursor") cursor:String?,@Query("limit") limit:Int=100):Response<ResponseBody>
    @POST("albums") suspend fun create(@Body body:RequestBody):Response<ResponseBody>
    @PATCH("albums/{id}") suspend fun update(@Path("id") id:String,@Body body:RequestBody):Response<ResponseBody>
    @DELETE("albums/{id}") suspend fun delete(@Path("id") id:String,@Query("cleanupExclusiveAssets") cleanup:Boolean):Response<ResponseBody>
}
internal class AlbumRepository(val server:PhotoTubeServer) {
    private val api=Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(AlbumEndpoints::class.java)
    private fun JSONObject.body()=toString().toRequestBody("application/json".toMediaType())
    suspend fun changeMembers(id:String,ids:Set<String>,remove:Boolean=false):JSONObject {
        require(ids.size in 1..500)
        val body=JSONObject().put("assetIds",org.json.JSONArray(ids.toList())).body()
        return (if(remove) api.remove(id,body) else api.add(id,body)).json()
    }
    suspend fun paths(id:String,cursor:String?)=api.paths(id,cursor).json()
    suspend fun previewPath(id:String,request:JSONObject)=api.preview(id,request.body()).json()
    suspend fun applyPath(id:String,request:JSONObject)=api.applyPaths(id,request.body()).json()
    suspend fun folders(path:String)=api.folders(path).json()
    suspend fun scanHistory(id:String,cursor:String?,limit:Int=100):AlbumPage<JSONObject> {
        val json=api.scanHistory(id,cursor,limit).json();val items=json.getJSONArray("items")
        return AlbumPage((0 until items.length()).map(items::getJSONObject),json.nullString("nextCursor"))
    }
    suspend fun scan(id:String,mode:String="INCREMENTAL"):JSONObject {
        require(mode in setOf("INCREMENTAL","FULL"))
        return api.scan(id,JSONObject().put("mode",mode).body()).json()
    }
    suspend fun scanState(id:String,run:String)=api.scanState(id,run).json()
    suspend fun list(cursor:String?):AlbumPage<RemoteAlbum> {
        val json=api.list(cursor).json();val items=json.getJSONArray("items")
        return AlbumPage((0 until items.length()).map {remoteAlbum(items.getJSONObject(it))},json.nullString("nextCursor"))
    }
    suspend fun members(id:String,cursor:String?):Pair<RemoteAlbum,AlbumPage<RemoteAsset>> {
        val json=api.members(id,cursor).json();val assets=json.getJSONObject("assets");val items=assets.getJSONArray("items")
        return remoteAlbum(json.getJSONObject("album")) to AlbumPage((0 until items.length()).map {remoteAsset(items.getJSONObject(it))},assets.nullString("nextCursor"))
    }
    suspend fun create(body:JSONObject)=remoteAlbum(api.create(body.body()).json())
    suspend fun update(id:String,body:JSONObject)=remoteAlbum(api.update(id,body.body()).json())
    suspend fun delete(id:String,cleanupExclusiveAssets:Boolean=false) {val response=api.delete(id,cleanupExclusiveAssets);if(!response.isSuccessful) response.json();response.body()?.close()}
}
internal class AlbumPagingSource<T:Any>(private val fetch:suspend (String?)->AlbumPage<T>):PagingSource<AssetCursor,T>() {
    override fun getRefreshKey(state:PagingState<AssetCursor,T>):AssetCursor?=null
    override suspend fun load(params:LoadParams<AssetCursor>):LoadResult<AssetCursor,T> = try {
        val key=params.key ?: AssetCursor(null);val page=fetch(key.token)
        if(page.cursor!=null && page.cursor==key.token) throw ServerError("INVALID_RESPONSE","服务器返回了重复游标")
        LoadResult.Page(page.items,key.previous,page.cursor?.let {AssetCursor(it,key)})
    } catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {
        if(error is ServerError && error.code=="INVALID_CURSOR" && params.key?.token!=null) LoadResult.Invalid() else LoadResult.Error(error)
    }
}
