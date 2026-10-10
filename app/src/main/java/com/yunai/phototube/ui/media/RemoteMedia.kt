package com.yunai.phototube.ui.media

import com.yunai.phototube.ui.theme.MediaColors
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.yunai.phototube.data.RemoteAsset
import com.yunai.phototube.data.PhotoTubeServer
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Request-local UUID identity; integer IDs only adapt the existing local viewer UI. */
internal class RemoteMedia(val asset:RemoteAsset,val server:PhotoTubeServer,private val originalOnly:Boolean=false) {
    var detail by mutableStateOf<JSONObject?>(null)
    var error by mutableStateOf<String?>(null)
    suspend fun load() {
        try {val result=server.detail(asset.id);kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {detail=result;error=null}}
        catch(cancel:CancellationException) {throw cancel}
        catch(problem:Exception) {error=problem.message ?: "读取详情失败"}
    }
    fun motionUrl():String? {
        val motion=detail?.optJSONObject("motionPhoto") ?: return null
        if(asset.kind!="PHOTO" || detail?.optString("state") !in setOf("BROWSABLE","PROCESSING")) return null
        return if(motion.optString("format")=="OPPO_MOTION_PHOTO_V2" && motion.optString("videoMime")=="video/mp4") server.motionVideo(asset.id) else null
    }
    fun contentVersion(): String? {
        // A freshly read explicit null must override a stale list hash.
        val hash = if (detail?.has("contentHash") == true) detail?.optString("contentHash") else asset.contentHash
        return hash?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    }
    fun displayUrl():String {
        if(originalOnly) return server.original(asset.id)
        val edit=detail?.optJSONObject("activeEdit")
        val hash=contentVersion()
        return if(edit!=null && edit.optString("sourceState")=="CURRENT" && hash!=null) server.render(asset.id,edit.getString("editVersionId"),hash)
            else server.original(asset.id)
    }
}

@Composable
internal fun rememberRemotePoster(source:RemoteMedia):Bitmap? {
    val context=LocalContext.current
    return produceState<Bitmap?>(null,source) {
        val url=source.server.thumbnail(source.asset) ?: return@produceState
        val result=source.server.imageLoader.execute(ImageRequest.Builder(context).data(url).size(640).allowHardware(false).build())
        value=(result as? SuccessResult)?.image?.toBitmap()
    }.value
}

@Composable
internal fun RemoteArtwork(source:RemoteMedia,modifier:Modifier,scale:ContentScale,description:String?,original:Boolean) {
    val url=if(original && source.detail!=null) source.displayUrl() else null
    val version = source.contentVersion()
    var loaded by remember(source,url,version) {mutableStateOf(false)}
    var failed by remember(source,url,version) {mutableStateOf(false)}
    var attempt by remember(source,url,version) {mutableIntStateOf(0)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    Box(modifier) {
        if(!original || !loaded) AsyncImage(source.server.thumbnail(source.asset),description,
            imageLoader=source.server.imageLoader,modifier=Modifier.fillMaxSize(),contentScale=scale)
        if(original && url!=null) key(url,version,attempt) {
            // Decode from original bytes, bounded to 3072px instead of retaining a full camera bitmap.
            AsyncImage(ImageRequest.Builder(context).data(url).size(3072)
                .apply {
                    // The original URL is stable when NAS bytes change. Never reuse
                    // an unversioned bitmap; versioned edits also include their URL.
                    if (version != null) memoryCacheKey("$url#content=$version")
                    else memoryCachePolicy(CachePolicy.DISABLED)
                }.build(),description,
                imageLoader=source.server.imageLoader,modifier=Modifier.fillMaxSize(),contentScale=scale,
                onSuccess={loaded=true;failed=false},onError={failed=true})
        }
        if(original && !loaded) Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
            val state=source.detail?.optString("state")
            val message=source.error ?: when(state) {"OFFLINE"->"文件暂时离线";"TRASHED"->"此文件已移入回收站";else->if(failed) {if(source.detail?.optJSONObject("activeEdit")?.optString("sourceState")=="CURRENT") "编辑预览暂不可用" else "暂时无法读取原图"} else null}
            if(message!=null) {
                Text(message,color=MediaColors.text)
                Box(Modifier.width(160.dp)) {com.yunai.phototube.ui.GlassSettingsAction("重试",{failed=false;attempt++;if(source.error!=null) scope.launch {source.load()}},media=true)}
            } else CircularProgressIndicator(Modifier.size(24.dp),color=MediaColors.text,strokeWidth=2.dp)
        }
    }
}
