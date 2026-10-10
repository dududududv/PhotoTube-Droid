package com.yunai.phototube.data

import org.json.JSONObject
import retrofit2.Retrofit

internal data class DirectoryNode(
    val libraryId: String?, val path: String, val name: String, val kind: String,
    val online: Boolean, val directAssetCount: Long, val hasChildren: Boolean,
) {
    val key get() = "${libraryId.orEmpty()}:$path"
}
internal data class DirectoryListing(val current: DirectoryNode, val children: AlbumPage<DirectoryNode>)

/** Aggregation counts describe the direct layer, never the recursive asset query. */
internal class DirectoryRepository(val server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client)
        .build().create(FilterCatalogEndpoints::class.java)

    suspend fun list(libraryId: String?, path: String, cursor: String?): DirectoryListing {
        require(libraryId != null || path.isEmpty())
        val result = api.folders(libraryId, path, cursor).json()
        val current = node(result.getJSONObject("current"))
        check(current.libraryId == libraryId && current.path == path) { "目录响应与请求范围不一致" }
        val page = result.getJSONObject("children")
        val items = page.getJSONArray("items")
        val children = (0 until items.length()).map { node(items.getJSONObject(it)) }
        check(children.all { child ->
            if (libraryId == null) child.kind == "LIBRARY" && child.path.isEmpty()
            else child.libraryId == libraryId && child.kind == "DIRECTORY" &&
                child.path.substringBeforeLast('/', "") == path
        }) { "服务器返回了其他层级的目录" }
        return DirectoryListing(current, AlbumPage(children, page.nullString("nextCursor")))
    }

    private fun node(value: JSONObject): DirectoryNode {
        val result = DirectoryNode(value.nullString("libraryId"), value.getString("path"), value.getString("name"),
            value.getString("kind"), value.getBoolean("online"), value.getLong("directAssetCount"), value.getBoolean("hasChildren"))
        check(result.kind in setOf("ROOT", "LIBRARY", "DIRECTORY") && result.directAssetCount >= 0 &&
            (result.kind == "ROOT") == (result.libraryId == null)) { "目录摘要格式不正确" }
        return result
    }
}
