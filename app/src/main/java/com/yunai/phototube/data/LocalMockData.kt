package com.yunai.phototube.data

import android.content.Context
import com.yunai.phototube.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

/** In-process fixtures. No DNS, socket, remote service, or private session is used. */
internal class LocalMockData(private val context: Context) {
    companion object { const val BASE = "https://mock.phototube.invalid" }
    private val pictures = listOf(R.drawable.mountains,R.drawable.lake,R.drawable.forest,R.drawable.flowers,R.drawable.meadow,R.drawable.street)
    private val names = listOf("山间","湖畔","森林","花园","草地","街道")
    private fun asset(index: Int): JSONObject = JSONObject().put("id","mock-$index")
        .put("kind",if(index==5) "VIDEO" else "PHOTO").put("state","BROWSABLE")
        .put("takenAt","2026-09-01T10:00:00Z").put("takenAtOffsetMinutes",480)
        .put("fileName",names[index%6]+if(index==5) ".mp4" else ".jpg")
        .put("contentHash","a".repeat(64)).put("width",if(index%3==1) 800 else 1200).put("height",800)
        .put("favorite",index%4==0).put("archived",false).put("private",false)
        .put("durationSec",if(index==5) 10 else JSONObject.NULL).put("relativePath","mock/示例-$index.jpg")
        .put("albumIds",JSONArray().put("mock-album")).put("tags",JSONArray())
    private fun album(id:String="mock-album") = JSONObject().put("id",id).put("name","本地示例相册")
        .put("kind","NORMAL").put("sortMode","TAKEN_AT_DESC")
        .put("assetCount",24).put("cover",asset(0)).put("coverFocalPoint",JSONObject.NULL)
        .put("pathSync",JSONObject.NULL)
    private fun page(items:JSONArray)=JSONObject().put("items",items).put("nextCursor",JSONObject.NULL)
    fun respond(request: Request): Response {
        val path=request.url.encodedPath.removePrefix("/api/v1/")
        fun response(body:ByteArray,type:String,code:Int=200)=Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(code).message("Local mock").body(body.toResponseBody(type.toMediaType())).build()
        fun json(body:Any,code:Int=200)=response(body.toString().toByteArray(),"application/json",code)
        if(request.method!="GET") return json(JSONObject().put("code","MOCK_READ_ONLY").put("message","本地 mock 演示：未执行任何真实操作").put("retryable",false).put("logId","mock-only"),409)
        if(path.startsWith("assets/") && (path.endsWith("/thumbnail") || path.endsWith("/original"))) {
            val index=path.split('/')[1].removePrefix("mock-").toIntOrNull() ?: 0
            val video=index==5 && path.endsWith("/original")
            val resource=if(video) R.raw.glass_video else pictures[index%pictures.size]
            return response(context.resources.openRawResource(resource).use {it.readBytes()},if(video) "video/mp4" else "image/jpeg")
        }
        val assets=JSONArray((0 until 24).filter {names[it%6].contains(request.url.queryParameter("keyword").orEmpty())}.map(::asset))
        return when {
            path=="health" -> json("""{"status":"ok","database":{"reachable":true,"migrationVersion":0},"xmpExport":{"available":false}}""")
            path=="auth/session" -> json("""{"authenticated":true,"passwordSet":true,"user":{"id":"mock-user","username":"本地 Mock"},"privateAccessUnlocked":false,"privateAccessExpiresAt":null,"originalDeleteEnabled":false}""")
            path=="assets/timeline-summary" -> json(JSONObject().put("granularity","MONTH").put("groups",JSONArray().put(JSONObject().put("key","2026-09").put("startsAt","2026-09-01T00:00:00Z").put("count",assets.length()))))
            path=="assets" -> json(page(assets))
            path.matches(Regex("assets/mock-\\d+")) -> json(asset(path.substringAfterLast('-').toInt()))
            path=="albums" -> json(page(JSONArray().put(album())))
            path.matches(Regex("albums/[^/]+/assets")) -> json(JSONObject().put("album",album(path.split('/')[1])).put("assets",page(assets)))
            path.endsWith("/paths") -> json(page(JSONArray().put(JSONObject().put("id","mock-path").put("libraryId","mock-library").put("relativePath","示例照片").put("enabled",true))))
            path=="source-folders" -> json(JSONObject().put("libraryId","mock-library").put("items",JSONArray().put(JSONObject().put("name","示例照片").put("path","示例照片"))))
            path=="tags" -> json(page(JSONArray().put(JSONObject().put("id",1).put("name","风景").put("assetCount",24))))
            path=="home" -> json(JSONObject().put("onThisDay",JSONArray().put(asset(0))).put("recentPhotos",JSONArray()).put("recentImports",JSONArray()).put("frequentAlbums",JSONArray().put(album())).put("jobSummary",page(JSONArray())))
            path=="jobs/summary" -> json(page(JSONArray()))
            path.endsWith("/syncs") || path in setOf("jobs","jobs/scan-batches","ai-tag-tasks","tag-rules","trash","duplicates","memory-exclusions","xmp-exports") -> json(page(JSONArray()))
            else -> json(JSONObject().put("code","MOCK_NOT_CONFIGURED").put("message","此页面的本地 mock 场景尚未配置；不会连接真实服务").put("retryable",false).put("logId","mock-only"),404)
        }
    }
}
