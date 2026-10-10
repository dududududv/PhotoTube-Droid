package com.yunai.phototube.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal data class TrashClearStats(val total: Long=0, val succeeded: Long=0, val failed: Long=0, val unknown: Long=0) {
    val pending get()=total-succeeded-failed-unknown
    val processed get()=succeeded+failed+unknown
}
internal data class TrashClearFailure(val name: String, val reason: String)

/** Disk-backed, deduplicated manifest. Only a single page/batch is held in memory. */
internal class TrashClearPlan private constructor(private val file: File, private val db: SQLiteDatabase) : AutoCloseable {
    var stats=TrashClearStats()
        private set
    private var complete=false
    companion object {
        private val activePlans = mutableSetOf<String>()

        fun create(context: Context): TrashClearPlan = synchronized(activePlans) {
            val directory = File(context.cacheDir, "trash-clear-plans").apply { check(mkdirs() || isDirectory) }
            val cutoff = System.currentTimeMillis() - 86_400_000L
            // Clean crash leftovers as complete SQLite databases, including sidecars.
            // An old timestamp does not mean a manifest is no longer in use.
            directory.listFiles()?.filter {
                it.name.startsWith("plan-") && it.extension == "db" &&
                    it.absolutePath !in activePlans && it.lastModified() < cutoff
            }?.forEach { SQLiteDatabase.deleteDatabase(it) }
            val file = File.createTempFile("plan-", ".db", directory)
            var db: SQLiteDatabase? = null
            try {
                db = SQLiteDatabase.openOrCreateDatabase(file, null)
                db.execSQL("CREATE TABLE targets (seq INTEGER PRIMARY KEY, id TEXT UNIQUE NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', reason TEXT)")
                db.execSQL("CREATE INDEX targets_pending ON targets(status,seq)")
                db.execSQL("CREATE TABLE cursors (token TEXT PRIMARY KEY)")
                activePlans.add(file.absolutePath)
                TrashClearPlan(file, db)
            } catch (problem: Exception) {
                db?.close()
                SQLiteDatabase.deleteDatabase(file)
                throw problem
            }
        }
    }
    suspend fun collect(repository: TrashRepository, progress: (TrashClearStats)->Unit) = withContext(Dispatchers.IO) {
        check(!complete && stats.total==0L)
        var cursor:String?=null
        do {
            ensureActive()
            cursor?.let {token ->
                val values=ContentValues().apply {put("token",token)}
                if(db.insertWithOnConflict("cursors",null,values,SQLiteDatabase.CONFLICT_IGNORE)==-1L)
                    throw ServerError("INVALID_CURSOR","回收站返回重复游标，请重新统计")
            }
            val page=repository.snapshotPage(cursor)
            var added=0L
            db.beginTransaction()
            try {
                page.items.forEach {entry ->
                    val values=ContentValues().apply {put("id",entry.asset.id);put("name",entry.asset.fileName)}
                    if(db.insertWithOnConflict("targets",null,values,SQLiteDatabase.CONFLICT_IGNORE)!=-1L) added++
                }
                db.setTransactionSuccessful()
            } finally {db.endTransaction()}
            stats=stats.copy(total=stats.total+added);progress(stats)
            cursor=page.cursor
        } while(cursor!=null)
        complete=true
        stats
    }
    suspend fun execute(repository: TrashRepository, stop: AtomicBoolean, progress: (TrashClearStats)->Unit): TrashClearStats = withContext(Dispatchers.IO) {
        check(complete) {"必须先完整收集回收站清单"}
        while(!stop.get()) {
            ensureActive()
            val ids=db.rawQuery("SELECT id FROM targets WHERE status='PENDING' ORDER BY seq LIMIT 500",null).use {rows ->
                buildSet {while(rows.moveToNext()) add(rows.getString(0))}
            }
            if(ids.isEmpty()) break
            val result=try {repository.purge(ids)} catch(problem:Exception) {
                withContext(NonCancellable + Dispatchers.IO) {
                    record(ids,AssetActionResult(emptySet(),emptyList()),"请求结果未确认，请刷新回收站核对")
                    progress(stats)
                }
                throw problem
            }
            // Once the response arrived, record it even if the screen is being dismissed.
            withContext(NonCancellable + Dispatchers.IO) {record(ids,result,"服务端未返回此项的结果");progress(stats)}
            if(stats.unknown>0) break
        }
        stats
    }
    private fun record(ids:Set<String>,result:AssetActionResult,unknownReason:String) {
        var succeeded=0L;var failed=0L;var unknown=0L
        val failures=result.failed.associateBy {it.id}
        db.beginTransaction()
        try {
            ids.forEach {id ->
                val failure=failures[id]
                val status=when {failure!=null->{failed++;"FAILED"};id in result.succeeded->{succeeded++;"SUCCEEDED"};else->{unknown++;"UNKNOWN"}}
                val values=ContentValues().apply {
                    put("status",status)
                    put("reason",failure?.let {"${it.message}（${it.code}）"} ?: if(status=="UNKNOWN") unknownReason else null)
                }
                db.update("targets",values,"id=? AND status='PENDING'",arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        stats=stats.copy(succeeded=stats.succeeded+succeeded,failed=stats.failed+failed,unknown=stats.unknown+unknown)
    }
    fun failures(limit:Int=20):List<TrashClearFailure> = db.rawQuery("SELECT name,reason FROM targets WHERE status IN ('FAILED','UNKNOWN') ORDER BY seq LIMIT ?",arrayOf(limit.toString())).use {rows ->
        buildList {while(rows.moveToNext()) add(TrashClearFailure(rows.getString(0),rows.getString(1)))}
    }
    override fun close() = synchronized(activePlans) {
        if (activePlans.remove(file.absolutePath)) {
            try { db.close() } finally { SQLiteDatabase.deleteDatabase(file) }
        }
    }
}
