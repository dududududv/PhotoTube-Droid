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

internal data class TagRule(val id: String, val name: String, val pattern: String, val target: String,
    val tags: Set<Long>, val enabled: Boolean, val generated: Long)
internal fun tagRule(row: JSONObject): TagRule {
    val ids = row.getJSONArray("tagIds")
    return TagRule(row.getString("id"), row.getString("name"), row.getString("pattern"), row.getString("target"),
        (0 until ids.length()).map(ids::getLong).toSet(), row.getBoolean("enabled"), row.getLong("generatedAssetCount"))
}
internal data class RuleScan(val run: AlbumAiRun, val ruleOnly: Boolean, val highMatch: Boolean,
    val matched: Long, val additions: Long, val percentage: Double) {
    val canApply get() = ruleOnly && run.dryRun && run.state == "SUCCEEDED"
    val canUndo get() = ruleOnly && !run.dryRun && !run.active
}
internal fun ruleScan(row: JSONObject): RuleScan {
    val sources = row.getJSONArray("sources")
    return RuleScan(albumAiRun(row), sources.length() == 1 && sources.getString(0) == "RULE",
        row.getBoolean("highMatchWarning"), row.getLong("matchedAssets"), row.getLong("proposedAdditions"), row.getDouble("matchPercentage"))
}
internal interface TagRuleEndpoints {
    @GET("tag-rules") suspend fun list(@Query("cursor") cursor: String?, @Query("limit") limit: Int = 100): Response<ResponseBody>
    @POST("tag-rules") suspend fun create(@Body body: RequestBody): Response<ResponseBody>
    @PATCH("tag-rules/{id}") suspend fun update(@Path("id") id: String, @Body body: RequestBody): Response<ResponseBody>
    @DELETE("tag-rules/{id}") suspend fun delete(@Path("id") id: String, @Query("reclaimGeneratedTags") reclaim: Boolean): Response<ResponseBody>
    @POST("tag-scans") suspend fun preview(@Body body: RequestBody): Response<ResponseBody>
    @GET("tag-scans/{id}") suspend fun scan(@Path("id") id: String): Response<ResponseBody>
    @POST("tag-scans/{id}/apply") suspend fun apply(@Path("id") id: String): Response<ResponseBody>
    @DELETE("tag-scans/{id}") suspend fun undo(@Path("id") id: String): Response<ResponseBody>
}
internal class TagRuleRepository(server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(TagRuleEndpoints::class.java)
    private fun JSONObject.body() = toString().toRequestBody("application/json".toMediaType())
    suspend fun list(cursor: String?): AlbumPage<TagRule> {
        val json = api.list(cursor).json(); val items = json.getJSONArray("items")
        return AlbumPage((0 until items.length()).map { tagRule(items.getJSONObject(it)) }, json.nullString("nextCursor"))
    }
    suspend fun save(id: String?, name: String, pattern: String, target: String, tags: Set<Long>, enabled: Boolean): TagRule {
        require(name.trim().length in 1..120 && pattern.length in 1..512 && tags.size in 1..100 && tags.all { it > 0 })
        require(target in setOf("FILE_NAME", "RELATIVE_PATH"))
        val body = JSONObject().put("name", name.trim()).put("pattern", pattern).put("target", target)
            .put("tagIds", JSONArray(tags.toList())).put("enabled", enabled).body()
        return tagRule((if (id == null) api.create(body) else api.update(id, body)).json())
    }
    suspend fun delete(id: String, reclaim: Boolean) {
        val response = api.delete(id, reclaim); if (!response.isSuccessful) response.json(); response.body()?.close()
    }
    suspend fun preview(scope: JSONObject, ruleIds: Set<String> = emptySet()): RuleScan {
        require(ruleIds.size <= 100)
        return ruleScan(api.preview(JSONObject().put("scope", scope).put("sources", JSONArray().put("RULE"))
            .put("dryRun", true).apply { if (ruleIds.isNotEmpty()) put("ruleIds", JSONArray(ruleIds.toList())) }.body()).json())
    }
    suspend fun scan(id: String) = ruleScan(api.scan(id).json())
    suspend fun apply(preview: RuleScan): RuleScan {
        require(preview.canApply)
        return ruleScan(api.apply(preview.run.id).json())
    }
    suspend fun undo(scan: RuleScan) {
        require(scan.canUndo)
        val response = api.undo(scan.run.id); if (!response.isSuccessful) response.json(); response.body()?.close()
    }
}
