package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import kotlinx.coroutines.*
import org.json.JSONObject

internal data class JobAssetIdentity(val assetId: String?, val reason: String)
internal fun jobAssetIdentity(error: String): JobAssetIdentity {
    val suffix = Regex("（assetId=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})）$").find(error)
    return JobAssetIdentity(suffix?.groupValues?.get(1), suffix?.let { error.substring(0,it.range.first) } ?: error)
}

@Composable
internal fun JobAssetDetails(server: PhotoTubeServer, assetId: String, onClose: () -> Unit) {
    var data by remember(server,assetId) { mutableStateOf<JSONObject?>(null) }
    var albums by remember(server,assetId) { mutableStateOf<List<RemoteAlbum>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var privateOpen by remember { mutableStateOf(false) }
    var privateRequired by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(server,assetId,retry) {
        loading=true; data=null; error=null; privateRequired=false
        try {
            val detail=server.detail(assetId)
            if(detail.optBoolean("private") && !server.privateMedia) {
                withContext(Dispatchers.Main.immediate) { privateRequired=true }
            } else {
                withContext(Dispatchers.Main.immediate) { data=detail }
                val repository=AlbumRepository(server)
                val expected=detail.optJSONArray("albumIds")?.let { array -> (0 until array.length()).map(array::getString).toSet() }.orEmpty()
                val found=mutableListOf<RemoteAlbum>(); var cursor:String?=null; val seen=mutableSetOf<String>()
                do {
                    val page=repository.list(cursor);found+=page.items;cursor=page.cursor
                    check(cursor==null || seen.add(cursor)) { "服务器返回重复游标" }
                } while(cursor!=null && !found.map {it.id}.containsAll(expected))
                withContext(Dispatchers.Main.immediate) { albums=found }
            }
        } catch(cancel:CancellationException) { throw cancel }
        catch(problem:Exception) { withContext(Dispatchers.Main.immediate) {
            privateRequired=problem is ServerError && problem.code=="PRIVATE_ACCESS_REQUIRED"
            error=if(problem is ServerError && problem.httpStatus in listOf(403,404)) "文件记录已删除或当前没有查看权限；不代表 NAS 原文件已删除。" else problem.message ?: "读取失败"
        } } finally { withContext(NonCancellable+Dispatchers.Main.immediate) { loading=false } }
    }
    ManagementPage("失败文件",onClose) {
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it,color=AppTheme.colors.secondary); GlassSettingsAction("重新读取",{retry++},enabled=!loading) }
        if(privateRequired) {
            Text("这是私密文件，请解锁后查看。",color=AppTheme.colors.secondary)
            SettingsNavigationRow("解锁私密空间",{privateOpen=true})
        }
        data?.let { detail ->
            ManagementGroup("文件信息") {
                Text(detail.getString("fileName"),color=AppTheme.colors.text)
                val ids=detail.optJSONArray("albumIds")?.let { array -> (0 until array.length()).map(array::getString) }
                val names=albums.filter { it.id in ids.orEmpty() }
                Text("相册："+when {ids==null->"暂无归属信息";ids.isEmpty()->"未加入相册";names.size!=ids.size->"部分相册暂时不可见";else->names.joinToString("、") {it.name}},color=AppTheme.colors.secondary)
                SelectionContainer { Text(detail.optString("relativePath"),color=AppTheme.colors.text) }
                Text("路径相对于媒体挂载目录。解析失败不会删除原文件。",color=AppTheme.colors.secondary)
                GlassSettingsAction(if(copied) "已复制路径" else "复制路径",{clipboard.setText(AnnotatedString(detail.optString("relativePath")));copied=true})
                SettingsNavigationRow("打开原文件",{viewing=true},enabled=detail.optString("state") !in listOf("OFFLINE","TRASHED"))
            }
        }
    }
    if(privateOpen) PrivateAccessScreen(server,onClose={privateOpen=false}) { privateServer,close -> JobAssetDetails(privateServer,assetId,close) }
    if(viewing) data?.let { detail ->
        val photo=remember(detail) { val asset=remoteAsset(detail);Photo(1,asset.fileName,"",asset.favorite,asset.kind=="VIDEO",RemoteMedia(asset,server,originalOnly=true)) }
        MediaViewer(ViewerRequest(listOf(photo),photo.id,emptyMap()),emptySet(),{}, {}, {viewing=false},onRemoteChanged={viewing=false;retry++})
    }
}
