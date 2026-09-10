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
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.yunai.phototube.data.RemoteAsset
import com.yunai.phototube.data.PhotoTubeServer
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Request-local UUID identity; integer IDs only adapt the existing local viewer UI. */
internal class RemoteMedia(val asset:RemoteAsset,val server:PhotoTubeServer) {
    var detail by mutableStateOf<JSONObject?>(null)
    var error by mutableStateOf<String?>(null)
    suspend fun load() {
        try {detail=server.detail(asset.id);error=null}
        catch(cancel:CancellationException) {throw cancel}
        catch(problem:Exception) {error=problem.message ?: "读取详情失败"}
    }
    fun displayUrl():String {
        val edit=detail?.optJSONObject("activeEdit")
        return if(edit!=null && edit.optString("sourceState")=="CURRENT") server.render(asset.id,edit.getString("editVersionId"))
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
    var loaded by remember(source) {mutableStateOf(false)}
    var failed by remember(source) {mutableStateOf(false)}
    var attempt by remember(source) {mutableIntStateOf(0)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val url=if(original && source.detail!=null) source.displayUrl() else null
    Box(modifier) {
        if(!original || !loaded) AsyncImage(source.server.thumbnail(source.asset),description,
            imageLoader=source.server.imageLoader,modifier=Modifier.fillMaxSize(),contentScale=scale)
        if(original && url!=null) key(url,attempt) {
            // Decode from original bytes, bounded to 3072px instead of retaining a full camera bitmap.
            AsyncImage(ImageRequest.Builder(context).data(url).size(3072).build(),description,
                imageLoader=source.server.imageLoader,modifier=Modifier.fillMaxSize(),contentScale=scale,
                onSuccess={loaded=true;failed=false},onError={failed=true})
        }
        if(original && !loaded) Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
            val state=source.detail?.optString("state")
            val message=source.error ?: when(state) {"OFFLINE"->"文件暂时离线";"TRASHED"->"此文件已移入回收站";else->if(failed) "暂时无法读取原图" else null}
            if(message!=null) {
                Text(message,color=MediaColors.text)
                TextButton({failed=false;attempt++;if(source.error!=null) scope.launch {source.load()}}) {Text("重试")}
            } else CircularProgressIndicator(Modifier.size(24.dp),color=MediaColors.text,strokeWidth=2.dp)
        }
    }
}
