package com.yunai.phototube.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

internal enum class TrashClearPhase { COLLECTING, READY, CLEARING, FINISHED, ERROR }
internal data class TrashClearState(val phase: TrashClearPhase=TrashClearPhase.COLLECTING, val stats: TrashClearStats=TrashClearStats(),
    val message: String?=null, val failures: List<TrashClearFailure> = emptyList())
internal class TrashClearController(private val context:Context,private val repository:TrashRepository,private val onChanged:()->Unit) {
    private val worker=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val state=MutableStateFlow(TrashClearState())
    val status=state.asStateFlow()
    private var plan:TrashClearPlan?=null
    private var job:Job?=null
    private val stop=AtomicBoolean(false)
    private var closed=false
    init {collect()}
    fun collect() {
        if(closed || job?.isActive==true) return
        state.value=TrashClearState()
        job=worker.launch {
            try {
                withContext(Dispatchers.IO) {
                    plan?.close();plan=TrashClearPlan.create(context)
                    plan!!.collect(repository) {stats -> state.update {it.copy(stats=stats)}}
                }
                state.update {it.copy(phase=TrashClearPhase.READY)}
            } catch(cancel:CancellationException) {throw cancel}
            catch(_:Exception) {state.update {it.copy(phase=TrashClearPhase.ERROR,message="统计未完成，没有清除任何记录。请重新统计。")}}
        }
    }
    fun execute() {
        if(closed || state.value.phase!=TrashClearPhase.READY || state.value.stats.total==0L) return
        val current=plan ?: return
        stop.set(false);state.update {it.copy(phase=TrashClearPhase.CLEARING,message=null)}
        job=worker.launch {
            try {
                current.execute(repository,stop) {stats -> state.update {it.copy(stats=stats)}}
                val failures=withContext(Dispatchers.IO) {current.failures()}
                state.update {it.copy(phase=TrashClearPhase.FINISHED,failures=failures,message=when {
                    it.stats.unknown>0 -> "有未确认的结果，已停止后续批次。请返回刷新后核对。"
                    stop.get() && it.stats.pending>0 -> "已停止，剩余记录没有继续处理。"
                    it.stats.failed>0 -> "已处理本次清单，部分记录未清除。"
                    else -> "已清除本次收集的全部记录。"
                })}
            } catch(cancel:CancellationException) {throw cancel}
            catch(_:Exception) {
                val failures=withContext(Dispatchers.IO) {current.failures()}
                state.update {it.copy(phase=TrashClearPhase.FINISHED,failures=failures,message="本批请求结果未确认，后续批次已停止。请返回刷新后核对，不会自动重试。")}
            } finally {
                withContext(NonCancellable+Dispatchers.Main.immediate) {
                    repository.server.imageLoader.memoryCache?.clear()
                    onChanged()
                }
            }
        }
    }
    fun stopAfterBatch() {stop.set(true)}
    fun close() {
        if(closed) return
        closed=true;stop.set(true);worker.cancel()
        val active=job
        CoroutineScope(Dispatchers.IO).launch {active?.join();plan?.close();plan=null}
    }
}
