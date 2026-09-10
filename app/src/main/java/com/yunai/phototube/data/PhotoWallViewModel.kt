package com.yunai.phototube.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset

internal enum class ConnectionStage { ADDRESS, CONNECTING, LOGIN, INITIALIZE, READY }
internal sealed interface WallEntry {
    data class Month(val key:String,val count:Long,val anchorId:String):WallEntry
    data class Asset(val value:RemoteAsset):WallEntry
}
internal fun monthKey(asset:RemoteAsset,groups:List<MonthGroup>):String {
    val date=OffsetDateTime.parse(asset.takenAt)
    return asset.takenAtOffsetMinutes?.let {YearMonth.from(date.withOffsetSameInstant(ZoneOffset.ofTotalSeconds(it*60))).toString()}
        ?: groups.firstOrNull { !date.toInstant().isBefore(OffsetDateTime.parse(it.startsAt).toInstant()) }?.key
        ?: groups.lastOrNull()?.key ?: YearMonth.from(date).toString()
}
internal class PhotoWallViewModel @JvmOverloads constructor(
    application:Application,
    private val serverFactory:(String,()->Unit)->PhotoTubeServer={url,expired -> PhotoTubeServer(application,url,expired)},
    preferenceName:String="server_connection"
):AndroidViewModel(application) {
    private val prefs=application.getSharedPreferences(preferenceName,0)
    var address by androidx.compose.runtime.mutableStateOf(prefs.getString("base_url",null) ?: "http://192.168.6.56:18473")
        private set
    var stage by androidx.compose.runtime.mutableStateOf(ConnectionStage.ADDRESS)
        private set
    var error by androidx.compose.runtime.mutableStateOf<Throwable?>(null)
        private set
    var busy by androidx.compose.runtime.mutableStateOf(false)
        private set
    var server by androidx.compose.runtime.mutableStateOf<PhotoTubeServer?>(null)
        private set
    var groups by androidx.compose.runtime.mutableStateOf(emptyList<MonthGroup>())
        private set
    var pageFlow by androidx.compose.runtime.mutableStateOf<Flow<PagingData<WallEntry>>?>(null)
        private set
    private var operation:Job?=null
    private var pagingScope:CoroutineScope?=null
    init {if(prefs.contains("base_url")) connect(address)}
    fun configure() {operation?.cancel();pagingScope?.cancel();busy=false;stage=ConnectionStage.ADDRESS;error=null;pageFlow=null;server?.imageLoader?.memoryCache?.clear()}
    private fun expired(instance:PhotoTubeServer) {viewModelScope.launch {
        if(server===instance) {pagingScope?.cancel();pageFlow=null;groups=emptyList();instance.imageLoader.memoryCache?.clear();stage=ConnectionStage.LOGIN;error=ServerError("UNAUTHORIZED","登录已过期，请重新登录")}
    }}
    fun connect(value:String) {
        if(busy) return
        operation=viewModelScope.launch {
            busy=true;error=null;stage=ConnectionStage.CONNECTING
            var candidate:PhotoTubeServer?=null
            try {
                val normalized=PhotoTubeServer.normalize(value)
                candidate=withContext(Dispatchers.IO) {lateinit var result:PhotoTubeServer;result=serverFactory(normalized){expired(result)};result}
                candidate.health()
                val old=server
                if(old!=null) {if(old.base!=normalized) old.cookies.clear();old.dispose()}
                server=candidate;address=normalized;prefs.edit().putString("base_url",normalized).apply()
                val session=candidate.session()
                when {
                    session.getBoolean("authenticated") -> loadWall(candidate)
                    session.getBoolean("passwordSet") -> {candidate.cookies.clear();stage=ConnectionStage.LOGIN}
                    else -> stage=ConnectionStage.INITIALIZE
                }
            } catch(cancel:CancellationException) {candidate?.takeIf {it!==server}?.dispose();throw cancel}
            catch(problem:Exception) {candidate?.takeIf {it!==server}?.dispose();error=problem;if(stage!=ConnectionStage.READY) stage=if(problem is ServerError && problem.code=="UNAUTHORIZED") ConnectionStage.LOGIN else ConnectionStage.ADDRESS}
            finally {busy=false}
        }
    }
    fun login(username:String,password:String) {
        if(busy) return
        val api=server ?: return
        operation=viewModelScope.launch {
            busy=true;error=null
            try {
                api.imageLoader.memoryCache?.clear()
                api.login(username,password)
                if(!api.session().getBoolean("authenticated")) throw ServerError("UNAUTHORIZED","登录会话未建立，请检查服务器设置")
                loadWall(api)
            } catch(cancel:CancellationException) {throw cancel} catch(problem:Exception) {error=problem}
            finally {busy=false}
        }
    }
    fun refresh() {
        if(busy) return
        val api=server ?: return
        operation=viewModelScope.launch {
            busy=true;error=null
            try {loadWall(api)} catch(cancel:CancellationException) {throw cancel} catch(problem:Exception) {error=problem} finally {busy=false}
        }
    }
    private suspend fun loadWall(api:PhotoTubeServer) {
        stage=ConnectionStage.READY
        val summary=api.summary()
        pagingScope?.cancel()
        val scope=CoroutineScope(viewModelScope.coroutineContext+SupervisorJob(viewModelScope.coroutineContext[Job]))
        pagingScope=scope
        groups=summary
        pageFlow=Pager(PagingConfig(pageSize=100,initialLoadSize=100,prefetchDistance=16,enablePlaceholders=false,maxSize=500)) {AssetPagingSource(api)}
            .flow.map {data -> data.map<RemoteAsset,WallEntry> {WallEntry.Asset(it)}.insertSeparators {before,after ->
                val next=(after as? WallEntry.Asset)?.value ?: return@insertSeparators null
                val key=monthKey(next,summary)
                val previous=(before as? WallEntry.Asset)?.value
                if(previous==null || monthKey(previous,summary)!=key) WallEntry.Month(key,summary.firstOrNull {it.key==key}?.count ?: 0,next.id) else null
            } }.cachedIn(scope)
        stage=ConnectionStage.READY
    }
    override fun onCleared() {server?.dispose()}
}
