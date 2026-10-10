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
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ceil

internal data class TrashEntry(val asset: RemoteAsset, val trashedAt: String, val retentionDays: Int) {
    fun remainingDays(now: Instant = Instant.now()): Int {
        val expires = OffsetDateTime.parse(trashedAt).toInstant().plusSeconds(retentionDays.toLong() * 86400)
        return ceil((expires.toEpochMilli() - now.toEpochMilli()).toDouble() / 86400000).toInt().coerceAtLeast(0)
    }
}
internal interface TrashEndpoints {
    @GET("trash") suspend fun list(@Query("cursor") cursor: String?, @Query("limit") limit: Int = 100): Response<ResponseBody>
    @POST("trash/purge") suspend fun purge(@Body body: RequestBody): Response<ResponseBody>
}
internal class TrashRepository(val server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(TrashEndpoints::class.java)
    suspend fun list(cursor: String?): AlbumPage<TrashEntry> = page(cursor,100)
    suspend fun snapshotPage(cursor: String?): AlbumPage<TrashEntry> = page(cursor,500)
    private suspend fun page(cursor: String?,limit:Int): AlbumPage<TrashEntry> {
        val json = api.list(cursor,limit).json(); val items = json.getJSONArray("items")
        return AlbumPage((0 until items.length()).map {
            val row = items.getJSONObject(it)
            TrashEntry(remoteAsset(row), row.getString("trashedAt"), row.getInt("retentionDays"))
        }, json.nullString("nextCursor"))
    }
    suspend fun restore(ids: Set<String>) = AssetActions(server).execute(AssetAction.RESTORE, ids)
    suspend fun purge(ids: Set<String>): AssetActionResult {
        require(ids.size in 1..500)
        val body = JSONObject().put("assetIds", JSONArray(ids.toList())).toString().toRequestBody("application/json".toMediaType())
        return assetActionResult(api.purge(body).json())
    }
}
