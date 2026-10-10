package com.yunai.phototube.data

import org.json.JSONObject

internal data class ScanStage(
    val stage: String, val kind: String, val pending: Long, val paused: Long, val running: Long,
    val succeeded: Long, val failed: Long, val cancelled: Long, val upstreamOpen: Boolean,
    val total: Long?, val queuePaused: Boolean, val error: String?,
)
internal data class ScanBatch(val id: String, val albumId: String?, val albumName: String?, val createdAt: String,
    val finishedAt: String?, val state: String, val stages: List<ScanStage>) {
    val active get() = state in setOf("PENDING", "RUNNING", "PAUSED")
}
internal fun scanBatch(value: JSONObject): ScanBatch {
    val stages = value.getJSONArray("stages")
    return ScanBatch(value.getString("id"), value.nullString("albumId"), value.nullString("albumName"),
        value.getString("createdAt"), value.nullString("finishedAt"), value.getString("state"),
        (0 until stages.length()).map { stages.getJSONObject(it).let { row ->
            ScanStage(row.getString("stage"), row.getString("kind"), row.getLong("pending"), row.getLong("paused"),
                row.getLong("running"), row.getLong("succeeded"), row.getLong("failed"), row.getLong("cancelled"),
                row.getBoolean("upstreamOpen"), if (row.isNull("total")) null else row.getLong("total"),
                row.getBoolean("queuePaused"), row.nullString("error"))
        } })
}
