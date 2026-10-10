package com.yunai.phototube.data

import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

internal data class RemoteTag(val id: Long, val name: String, val count: Long)
internal data class LibraryFolder(val libraryId: String?, val path: String, val name: String, val online: Boolean)
internal interface FilterCatalogEndpoints {
    @GET("tags") suspend fun tags(@Query("keyword") keyword: String?, @Query("cursor") cursor: String?, @Query("limit") limit: Int = 100): Response<ResponseBody>
    @GET("folders") suspend fun folders(@Query("libraryId") library: String?, @Query("path") path: String, @Query("cursor") cursor: String?, @Query("limit") limit: Int = 100): Response<ResponseBody>
}
internal class FilterCatalog(server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(FilterCatalogEndpoints::class.java)
    suspend fun tags(keyword: String, cursor: String?): AlbumPage<RemoteTag> {
        val json = api.tags(keyword.trim().ifEmpty { null }, cursor).json()
        val items = json.getJSONArray("items")
        return AlbumPage((0 until items.length()).map { val row = items.getJSONObject(it); RemoteTag(row.getLong("id"), row.getString("name"), row.getLong("assetCount")) }, json.nullString("nextCursor"))
    }
    suspend fun folders(library: String?, path: String, cursor: String?): AlbumPage<LibraryFolder> {
        val page = api.folders(library, path, cursor).json().getJSONObject("children")
        val items = page.getJSONArray("items")
        return AlbumPage((0 until items.length()).map { val row = items.getJSONObject(it); LibraryFolder(row.nullString("libraryId"), row.getString("path"), row.getString("name"), row.getBoolean("online")) }, page.nullString("nextCursor"))
    }
}
internal data class SmartFilterDraft(
    val kind: String? = null, val favorite: Boolean? = null, val archived: Boolean = false,
    val keyword: String = "", val library: String? = null, val folder: String? = null,
    val takenFrom: String? = null, val takenTo: String? = null,
    val all: Set<Long> = emptySet(), val any: Set<Long> = emptySet(), val exclude: Set<Long> = emptySet(),
) {
    fun validationError(): String? = when {
        keyword.trim().isNotEmpty() && keyword.trim().length !in 3..255 -> "文件名关键词需要 3–255 个字符"
        folder != null && library == null -> "请重新选择媒体库目录"
        listOf(all, any, exclude).any { it.size > 100 || it.any { id -> id <= 0 } } -> "每组最多选择 100 个标签"
        takenFrom != null && takenTo != null && java.time.OffsetDateTime.parse(takenFrom).toInstant() > java.time.OffsetDateTime.parse(takenTo).toInstant() -> "结束日期不能早于开始日期"
        else -> null
    }
    fun json(): JSONObject {
        require(validationError() == null) { validationError().orEmpty() }
        return JSONObject().put("archived", archived).apply {
            kind?.let { put("kind", it) }; favorite?.let { put("favorite", it) }
            keyword.trim().takeIf { it.isNotEmpty() }?.let { put("keyword", it) }
            library?.let { put("libraryId", it) }; folder?.takeIf { it.isNotEmpty() }?.let { put("folderPath", it) }
            takenFrom?.let { put("takenFrom", it) }; takenTo?.let { put("takenTo", it) }
            listOf("tagsAll" to all, "tagsAny" to any, "tagsExclude" to exclude).forEach { (key, ids) -> if (ids.isNotEmpty()) put(key, JSONArray(ids.toList())) }
        }
    }
    companion object {
        fun from(json: JSONObject): SmartFilterDraft {
            fun ids(key: String): Set<Long> = json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getLong).toSet() } ?: emptySet()
            return SmartFilterDraft(json.nullString("kind"), if (json.has("favorite") && !json.isNull("favorite")) json.getBoolean("favorite") else null,
                json.optBoolean("archived"), json.nullString("keyword").orEmpty(), json.nullString("libraryId"), json.nullString("folderPath"),
                json.nullString("takenFrom"), json.nullString("takenTo"), ids("tagsAll"), ids("tagsAny"), ids("tagsExclude"))
        }
    }
}
