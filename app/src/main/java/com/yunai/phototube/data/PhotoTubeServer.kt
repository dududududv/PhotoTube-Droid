package com.yunai.phototube.data

import android.content.Context
import androidx.paging.*
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CancellationException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal data class RemoteAsset(val id:String,val kind:String,val state:String,val takenAt:String,val takenAtOffsetMinutes:Int?,
    val fileName:String,val contentHash:String?,val width:Int?,val height:Int?,val favorite:Boolean,val durationSec:Double?)
internal data class MonthGroup(val key:String,val startsAt:String,val count:Long)
internal data class AssetPage(val items:List<RemoteAsset>,val nextCursor:String?)
internal class ServerError(val code:String,override val message:String,val logId:String?=null,val retryable:Boolean=false,val httpStatus:Int?=null):IOException(message)
internal interface PhotoTubeEndpoints {
    @GET("assets/{id}") suspend fun detail(@Path("id") id:String):retrofit2.Response<ResponseBody>
    @GET("health") suspend fun health():retrofit2.Response<ResponseBody>
    @GET("auth/session") suspend fun session():retrofit2.Response<ResponseBody>
    @POST("auth/login") suspend fun login(@Body body:RequestBody):retrofit2.Response<ResponseBody>
    @GET("assets/timeline-summary") suspend fun summary(@Query("granularity") granularity:String="MONTH",@QueryMap filters:Map<String,String> = WallFilters().query()):retrofit2.Response<ResponseBody>
    @GET("assets") suspend fun assets(@Query("cursor") cursor:String?,@Query("limit") limit:Int=100,@QueryMap filters:Map<String,String> = WallFilters().query()):retrofit2.Response<ResponseBody>
}
internal fun JSONObject.nullString(key:String)=if(isNull(key)) null else getString(key)
internal fun JSONObject.nullInt(key:String)=if(isNull(key)) null else getInt(key)
internal fun retrofit2.Response<ResponseBody>.json():JSONObject {
    if(!isSuccessful) {
        val error=runCatching {JSONObject(errorBody()?.string().orEmpty())}.getOrNull()
        throw ServerError(error?.optString("code")?.takeIf {it.isNotEmpty()} ?: "HTTP_${code()}",error?.optString("message")?.takeIf {it.isNotEmpty()} ?: "服务器请求失败（${code()}）",error?.nullString("logId"),error?.optBoolean("retryable") ?: false,code())
    }
    return body()?.use {JSONObject(it.string())} ?: JSONObject()
}
internal class PhotoTubeServer(private val context:Context,address:String,private val onUnauthorized:()->Unit,private val transport:OkHttpClient?=null,
    sessionCookies:SessionCookieJar?=null,private val privateAllowed:(()->Boolean)?=null,private val onPrivateLocked:(()->Unit)?=null,private val privacyParent:PhotoTubeServer?=null) {
    companion object {
        private val cleanupExecutor=java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task,"phototube-network-cleanup").apply {isDaemon=true}
        }
        fun normalize(address:String):String {
            val url=address.trim().toHttpUrl()
            require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null && url.encodedPath=="/") {"请填写服务根地址，不要包含 /api/v1、账号或查询参数"}
            require(url.host in setOf("mock.phototube.invalid","localhost","127.0.0.1","::1")) {"项目仅允许本地 mock，禁止连接真实服务"}
            return url.toString().trimEnd('/')
        }
    }
    val base=normalize(address)
    val origin="$base/".toHttpUrl()
    val privateMedia get()=privateAllowed!=null
    init { require(origin.host=="mock.phototube.invalid" || transport!=null) {"本地测试服务必须由隔离测试显式注入"} }
    private val mock=LocalMockData(context)
    val cookies=sessionCookies ?: SessionCookieJar(context,origin)
    val client=(transport?.newBuilder() ?: OkHttpClient.Builder()).cookieJar(cookies).proxy(java.net.Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            val request=chain.request()
            check(request.url.host==origin.host && request.url.port==origin.port) { "禁止跨来源请求" }
            check(origin.host=="mock.phototube.invalid" || transport!=null && origin.host in setOf("localhost","127.0.0.1","::1")) { "禁止访问真实网络" }
            chain.proceed(request)
        }
        .dns { host ->
            check(host in setOf("localhost","127.0.0.1","::1")) { "禁止解析外部地址" }
            listOf(java.net.InetAddress.getLoopbackAddress())
        }
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(40,TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            if(privateAllowed?.invoke()==false) throw IOException("私密空间已锁定")
            val response=chain.proceed(chain.request())
            val expired=response.code==401 || response.code==403 && runCatching {
                JSONObject(response.peekBody(4096).string()).optString("code")=="PRIVATE_ACCESS_REQUIRED"
            }.getOrDefault(false)
            if(privateMedia && expired) onPrivateLocked?.invoke()
            if(privateAllowed?.invoke()==false) {response.close();throw IOException("私密空间已锁定")}
            response
        }
        // OkHttp independently retries 503 + Retry-After: 0. Normalize it to
        // a one-second minimum so only the bounded derivative policy retries.
        .addNetworkInterceptor {chain ->
            val response=chain.proceed(chain.request())
            if(response.code==503 && response.header("Retry-After")=="0") response.newBuilder().header("Retry-After","1").build() else response
        }
        .addInterceptor {chain ->
            var response=chain.proceed(chain.request())
            // Respect Retry-After for pending derivatives; never retry 429 or POST.
            var retries=0
            while(response.code==503 && chain.request().url.encodedPath.endsWith("/thumbnail") && retries<2) {
                val pending=runCatching {JSONObject(response.peekBody(4096).string()).optString("code")=="DERIVATIVE_PENDING"}.getOrDefault(false)
                if(!pending) break
                val header=response.header("Retry-After")
                val waitMillis=header?.toLongOrNull()?.times(1000) ?: header?.let {
                    runCatching {java.time.ZonedDateTime.parse(it,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()}.getOrNull()
                } ?: 2000L
                if(waitMillis !in 0L..5000L) break
                response.close()
                Thread.sleep(waitMillis)
                if(chain.call().isCanceled()) throw IOException("请求已取消")
                response=chain.proceed(chain.request());retries++
            }
            if(response.code==401 && !chain.request().url.encodedPath.endsWith("/auth/login")) {
                // A rejected password verification does not invalidate the existing session.
                val verification = chain.request().url.encodedPath in setOf("/api/v1/auth/password", "/api/v1/auth/private-access", "/api/v1/assets/delete-original/prepare")
                val invalidPassword = verification && runCatching {
                    JSONObject(response.peekBody(4096).string()).optString("code") == "INVALID_CREDENTIALS"
                }.getOrDefault(false)
                if (!invalidPassword) { cookies.clear(); onUnauthorized() }
            }
            response
        }
        // In-process responses still pass through lock and response policies above.
        // This terminal interceptor prevents mock requests from reaching DNS/sockets.
        .addInterceptor {chain ->
            if(origin.host=="mock.phototube.invalid") mock.respond(chain.request())
            else chain.proceed(chain.request())
        }.build()
    private val endpoints=Retrofit.Builder().baseUrl("$base/api/v1/").client(client).build().create(PhotoTubeEndpoints::class.java)
    private fun createImageLoader()=ImageLoader.Builder(context).components {
        add(coil3.gif.AnimatedImageDecoder.Factory())
        add(OkHttpNetworkFetcherFactory(callFactory={client}))
    }.diskCache(null)
        .apply {if(privateMedia) memoryCache(null)}.build()
    var imageLoader by androidx.compose.runtime.mutableStateOf(createImageLoader())
        private set
    private val seenThumbnails=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var redacted by androidx.compose.runtime.mutableStateOf(emptySet<String>())
    private val privacyRevision=java.util.concurrent.atomic.AtomicLong()
    suspend fun privacyChanged(hiddenIds:Set<String>?,hiding:Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
        val ordinary=privacyParent ?: this@PhotoTubeServer
        ordinary.privacyRevision.incrementAndGet()
        if(hiding) ordinary.redacted=ordinary.redacted+(hiddenIds ?: ordinary.seenThumbnails.toSet())
        val previous=ordinary.imageLoader
        ordinary.imageLoader=ordinary.createImageLoader()
        previous.memoryCache?.clear()
        cleanupExecutor.execute {previous.shutdown()}
        if(hiding) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.io.File(ordinary.context.cacheDir,"shared-media").listFiles()?.filter {file ->
                hiddenIds==null || hiddenIds.any {id -> file.name.startsWith("$id.")}
            }?.forEach {file -> if(file.exists() && !file.delete()) throw IOException("内容已移入私密空间，但本地分享缓存未能清理")}
        }
    }
    fun privateFork(allowed:()->Boolean,onLocked:()->Unit):PhotoTubeServer {
        // Rebuild policy interceptors from the injected transport, not this server's
        // decorated client. Preserve fixture TLS/transport behavior while isolating
        // cancellation, connections and caches from the ordinary library.
        val isolatedTransport=transport?.newBuilder()
            ?.dispatcher(Dispatcher())?.connectionPool(ConnectionPool())?.cache(null)?.build()
        return PhotoTubeServer(context,base,onUnauthorized,isolatedTransport,cookies,allowed,onLocked,this)
    }
    suspend fun health() {
        val json=endpoints.health().json()
        require(json.getString("status") in listOf("ok","degraded") && json.has("database") && json.has("xmpExport")) {"该地址没有返回 PhotoTube 健康信息"}
        if(!json.getJSONObject("database").getBoolean("reachable")) throw ServerError("DATABASE_OFFLINE","服务器数据库不可用",retryable=true)
    }
    suspend fun detail(id:String)=endpoints.detail(id).json()
    fun original(id:String)=origin.newBuilder().addPathSegments("api/v1/assets").addPathSegment(id).addPathSegment("original").build().toString()
    fun motionVideo(id:String)=origin.newBuilder().addPathSegments("api/v1/assets").addPathSegment(id).addPathSegment("motion-video").build().toString()
    fun render(id:String,version:String,sourceHash:String,size:String="PREVIEW")=origin.newBuilder().addPathSegments("api/v1/assets").addPathSegment(id).addPathSegment("edit-versions").addPathSegment(version).addPathSegment("render").addQueryParameter("v",sourceHash).addQueryParameter("size",size).build().toString()
    suspend fun session()=endpoints.session().json()
    suspend fun login(username:String,password:String) {
        val response=endpoints.login(JSONObject().put("username",username).put("password",password).toString().toRequestBody("application/json".toMediaType()))
        if(response.code()!=204) {response.json();throw ServerError("INVALID_RESPONSE","登录响应格式不正确")}
        response.body()?.close()
    }
    suspend fun summary(filters:WallFilters=WallFilters()):List<MonthGroup> {
        val json=endpoints.summary(filters=filters.query()).json()
        require(json.getString("granularity")=="MONTH")
        val groups=json.getJSONArray("groups")
        return (0 until groups.length()).map {i -> groups.getJSONObject(i).let {MonthGroup(it.getString("key"),it.getString("startsAt"),it.getLong("count"))}}
    }
    suspend fun assets(cursor:String?,filters:WallFilters=WallFilters()):AssetPage {
        val requestedPrivacyRevision=privacyRevision.get()
        val json=endpoints.assets(cursor,filters=filters.query()).json();val items=json.getJSONArray("items")
        val page=AssetPage((0 until items.length()).map {i -> items.getJSONObject(i).let {
            RemoteAsset(it.getString("id"),it.getString("kind"),it.getString("state"),it.getString("takenAt"),it.nullInt("takenAtOffsetMinutes"),
                it.getString("fileName"),it.nullString("contentHash"),it.nullInt("width"),it.nullInt("height"),it.getBoolean("favorite"),if(it.isNull("durationSec")) null else it.getDouble("durationSec"))
        }},json.nullString("nextCursor"))
        if(!privateMedia && !filters.privateOnly) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            if(requestedPrivacyRevision==privacyRevision.get()) redacted=redacted-page.items.map {it.id}.toSet()
        }
        return page
    }
    fun thumbnail(asset:RemoteAsset):String?=if(asset.originalOnlyFormat != null) null else thumbnail(asset.id,asset.contentHash)
    fun thumbnail(id:String,contentHash:String?):String? {
        if(!privateMedia && id in redacted) return null
        seenThumbnails.add(id)
        return contentHash?.let {hash -> origin.newBuilder().addPathSegments("api/v1/assets").addPathSegment(id).addPathSegment("thumbnail").addQueryParameter("size","MD").addQueryParameter("v",hash).build().toString()}
    }
    fun dispose() {
        imageLoader.memoryCache?.clear()
        // TLS shutdown can write to the socket; lifecycle cleanup runs on main.
        cleanupExecutor.execute {
            client.dispatcher.cancelAll()
            imageLoader.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
internal class AssetCursor(val token:String?,val previous:AssetCursor?=null,val followingToken:String?=null) {
    override fun equals(other:Any?)=other is AssetCursor && token==other.token
    override fun hashCode()=token?.hashCode() ?: 0
}
internal class AssetPagingSource(private val server:PhotoTubeServer,private val filters:WallFilters=WallFilters()):PagingSource<AssetCursor,RemoteAsset>() {
    override fun getRefreshKey(state:PagingState<AssetCursor,RemoteAsset>):AssetCursor? {
        val anchor=state.anchorPosition ?: return null
        val page=state.closestPageToPosition(anchor) ?: return null
        return page.nextKey?.previous ?: page.prevKey?.let {previous ->
            previous.followingToken?.let {AssetCursor(it,previous)}
        }
    }
    override suspend fun load(params:LoadParams<AssetCursor>):LoadResult<AssetCursor,RemoteAsset> = try {
        val key=params.key ?: AssetCursor(null)
        val page=server.assets(key.token,filters)
        if(page.nextCursor!=null && page.nextCursor==key.token) throw ServerError("INVALID_RESPONSE","服务器返回了重复的分页游标")
        // Keep the current opaque cursor on the backward key too: the final
        // page has no next key from which to recover its refresh position.
        val previous=key.previous?.let {AssetCursor(it.token,it.previous,key.token)}
        LoadResult.Page(page.items,prevKey=previous,nextKey=page.nextCursor?.let {AssetCursor(it,key)})
    } catch(error:CancellationException) {throw error} catch(error:Exception) {
        if(error is ServerError && error.code=="INVALID_CURSOR" && params.key?.token!=null) {
            if(params is LoadParams.Refresh) load(LoadParams.Refresh(null,params.loadSize,params.placeholdersEnabled))
            else LoadResult.Invalid()
        } else LoadResult.Error(error)
    }
}
