package com.yunai.phototube.data

import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.time.OffsetDateTime

internal data class JobSummary(val kind: String, val pending: Long, val paused: Long, val running: Long,
    val succeeded: Long, val failed: Long, val cancelled: Long, val discovered: Long, val total: Long?, val finished: String?) {
    val active get() = pending + paused + running > 0
}
internal data class AiTask(val albumId: String, val albumName: String, val run: AlbumAiRun)
internal data class TaskRow(val key: String, val section: Int, val timestamp: String?, val job: JobSummary? = null, val ai: AiTask? = null, val scan: ScanBatch? = null)
internal fun mergedTasks(jobs: List<JobSummary>, ai: List<AiTask>, scans: List<ScanBatch> = emptyList()): List<TaskRow> = (
    jobs.filterNot { it.kind in setOf("SCAN_LIBRARY", "ALBUM_PATH_SYNC", "EXTRACT_METADATA", "GENERATE_THUMBNAIL") }.map { TaskRow("job:${it.kind}", if (it.active) 0 else if (it.failed > 0) 1 else 2, it.finished, job = it) } +
        ai.map { TaskRow("ai:${it.run.id}", if (it.run.active) 0 else if (it.run.state == "FAILED") 1 else 2, it.run.createdAt, ai = it) }
        + scans.map { TaskRow("scan:${it.id}", if (it.active) 0 else if (it.state == "FAILED") 1 else 2, it.createdAt, scan = it) }
    ).sortedWith(compareBy<TaskRow> { it.section }.thenByDescending { row -> row.timestamp?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } }.thenBy { it.key })
internal interface TaskEndpoints {
    @GET("jobs/scan-batches") suspend fun scans(@Query("cursor") cursor: String?, @Query("limit") limit: Int = 20): Response<ResponseBody>
    @GET("jobs/summary") suspend fun summary(): Response<ResponseBody>
    @GET("ai-tag-tasks") suspend fun ai(@Query("cursor") cursor: String?, @Query("limit") limit: Int = 20): Response<ResponseBody>
    @GET("jobs") suspend fun jobs(@Query("kind") kind: String, @Query("cursor") cursor: String?, @Query("limit") limit: Int = 100, @Query("parentJobId") parentJobId: String? = null, @Query("state") state: String? = null): Response<ResponseBody>
}
internal class TasksRepository(server: PhotoTubeServer) {
    private val api = Retrofit.Builder().baseUrl("${server.base}/api/v1/").client(server.client).build().create(TaskEndpoints::class.java)
    suspend fun scans(cursor: String?): AlbumPage<ScanBatch> {
        val value = api.scans(cursor).json(); val rows = value.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map { scanBatch(rows.getJSONObject(it)) }, value.nullString("nextCursor"))
    }
    suspend fun summary(): List<JobSummary> {
        return parseJobSummaries(api.summary().json())
    }

    suspend fun ai(cursor: String?): AlbumPage<AiTask> {
        val json = api.ai(cursor).json(); val rows = json.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map { val row = rows.getJSONObject(it); AiTask(row.getString("albumId"), row.getString("albumName"), albumAiRun(row.getJSONObject("run"))) }, json.nullString("nextCursor"))
    }
    suspend fun jobs(kind: String, cursor: String?, parentJobId: String? = null, state: String? = null): AlbumPage<JSONObject> {
        val json = api.jobs(kind, cursor, parentJobId = parentJobId, state = state).json(); val rows = json.getJSONArray("items")
        return AlbumPage((0 until rows.length()).map(rows::getJSONObject), json.nullString("nextCursor"))
    }
}

internal fun parseJobSummaries(json: JSONObject): List<JobSummary> {
    val rows = json.getJSONArray("items")
    return (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        JobSummary(row.getString("kind"), row.getLong("pending"), row.getLong("paused"), row.getLong("running"),
            row.getLong("succeeded"), row.getLong("failed"), row.getLong("cancelled"), row.getLong("discovered"),
            if (row.isNull("total")) null else row.getLong("total"), row.nullString("lastFinishedAt"))
    }
}
