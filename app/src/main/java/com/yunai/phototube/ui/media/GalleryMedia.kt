package com.yunai.phototube.ui.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
internal data class Photo(val id: Int, val name: String, val date: String, val favorite: Boolean = false, val video: Boolean = false, val remote:RemoteMedia? = null) {
    // Remote identity comes from the API timestamp, never from localized display text.
    val takenOn: java.time.LocalDate = remote?.asset?.let { asset ->
        val zone = asset.takenAtOffsetMinutes?.let { java.time.ZoneOffset.ofTotalSeconds(it * 60) } ?: java.time.ZoneId.systemDefault()
        java.time.OffsetDateTime.parse(asset.takenAt).atZoneSameInstant(zone).toLocalDate()
    } ?: java.time.LocalDate.parse(date, java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"))
}
internal data class MediaMetadata(val width: Int, val height: Int, val bytes: Long, val duration: Long = 0L, val poster: Bitmap? = null)

/** Share the same decoded pixels between the grid, transition and detail view.
 * Eviction drops cache references only: Compose may still be drawing the bitmap. */
internal object PhotoBitmapCache {
    private val entries=object:android.util.LruCache<Int,Bitmap>(48*1024*1024) {
        override fun sizeOf(key:Int,value:Bitmap)=value.allocationByteCount
    }
    private val decodeMutex=Mutex()
    fun peek(id:Int):Bitmap?=entries.get(id)
    suspend fun load(context:Context,id:Int):Bitmap=withContext(Dispatchers.IO) {
        peek(id) ?: decodeMutex.withLock {
            peek(id) ?: requireNotNull(BitmapFactory.decodeResource(context.resources,id)) {
                "Cannot decode photo resource $id"
            }.also { bitmap ->
                bitmap.prepareToDraw()
                entries.put(id,bitmap)
            }
        }
    }
}

/** One bounded poster/metadata cache, not a video-frame sampling render pipeline. */
internal object MediaMetadataCache {
    private val entries = java.util.concurrent.ConcurrentHashMap<Int, MediaMetadata>()
    fun peek(id:Int):MediaMetadata?=entries[id]
    suspend fun load(context: Context, photo: Photo): MediaMetadata = withContext(Dispatchers.IO) {
        entries[photo.id] ?: run {
            val metadata = if (photo.video) {
                MediaMetadataRetriever().use { retriever ->
                    context.resources.openRawResourceFd(photo.id).use { fd ->
                        retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        MediaMetadata(if(rotation % 180 == 0) width else height, if(rotation % 180 == 0) height else width,
                            fd.length, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, (640f * height / width).toInt().coerceAtLeast(1)))
                    }
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, photo.id, options)
                val bytes = context.resources.openRawResourceFd(photo.id).use { it.length }
                MediaMetadata(options.outWidth, options.outHeight, bytes)
            }
            entries.putIfAbsent(photo.id, metadata) ?: metadata
        }
    }
}
@Composable
internal fun rememberMediaMetadata(photo: Photo): MediaMetadata? {
    photo.remote?.let { source ->
        val detail=source.detail
        val edit=detail?.optJSONObject("activeEdit")
        return MediaMetadata(edit?.optInt("displayWidth") ?: detail?.optInt("width",source.asset.width ?: 1) ?: (source.asset.width ?: 1).coerceAtLeast(1),
            edit?.optInt("displayHeight") ?: detail?.optInt("height",source.asset.height ?: 1) ?: (source.asset.height ?: 1).coerceAtLeast(1),
            detail?.optLong("fileSize") ?: 0L,((source.asset.durationSec ?: 0.0)*1000).toLong())
    }
    val context = LocalContext.current.applicationContext
    return produceState<MediaMetadata?>(MediaMetadataCache.peek(photo.id), photo.id) {
        value = runCatching { MediaMetadataCache.load(context, photo) }.getOrNull()
    }.value
}
@Composable
internal fun MediaArtwork(photo: Photo, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, description: String? = photo.name,original:Boolean=false) {
    if(photo.remote!=null) { RemoteArtwork(photo.remote,modifier,contentScale,description,original);return }
    if (photo.video) {
        rememberMediaMetadata(photo)?.poster?.let { Image(it.asImageBitmap(), description, modifier, contentScale = contentScale) }
    } else {
        val context=LocalContext.current.applicationContext
        val bitmap by key(photo.id) {
            produceState<Bitmap?>(PhotoBitmapCache.peek(photo.id),photo.id) {
                value=PhotoBitmapCache.load(context,photo.id)
            }
        }
        bitmap?.let { Image(it.asImageBitmap(),description,modifier,contentScale=contentScale) }
            ?: Box(modifier.semantics { description?.let {contentDescription=it} })
    }
}
internal fun mediaTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun mediaCount(media:List<Photo>):String {
    val images=media.count {!it.video};val videos=media.count {it.video}
    return listOfNotNull(if(images>0 || videos==0) "$images 张照片" else null,if(videos>0) "$videos 段视频" else null).joinToString(" · ")
}

/** The opening rectangle changes only drawing, never image measurement or decoding. */
@Composable
internal fun MediaTransitionArtwork(photo:Photo,source:androidx.compose.ui.geometry.Rect?,progress:()->Float,exiting:Boolean=false,sourceCornerRadius:androidx.compose.ui.unit.Dp) {
    val context=LocalContext.current.applicationContext
    val bitmap=if(photo.remote!=null) rememberRemotePoster(photo.remote) else if(photo.video) rememberMediaMetadata(photo)?.poster else {
        val loaded by key(photo.id) {
            produceState<Bitmap?>(PhotoBitmapCache.peek(photo.id),photo.id) {
                value=PhotoBitmapCache.load(context,photo.id)
            }
        }
        loaded
    }
    val image=remember(bitmap) {bitmap?.asImageBitmap()}
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        if(image==null) return@Canvas
        val p=progress().coerceIn(0f,1f)
        val ratio=image.width.toFloat()/image.height
        val w=minOf(size.width,size.height*ratio)
        val h=w/ratio
        val target=androidx.compose.ui.geometry.Rect((size.width-w)/2,(size.height-h)/2,(size.width+w)/2,(size.height+h)/2)
        val destination=source?.takeIf {it.width>0 && it.height>0 && it.bottom>0 && it.top<size.height && it.left<size.width && it.right>0}
        val origin=destination ?: target
        // Hand back to the actual tile (including its caption and corner mask)
        // before removing the dialog, rather than popping an opaque overlay off.
        val opacity=if(exiting) {
            if(destination==null) p else (p/SourceHandoffFraction).coerceIn(0f,1f)
        } else 1f
        // Finish moving before the source becomes visible: both layers must have
        // identical geometry during the cross-window opacity handoff.
        val geometryProgress = if (exiting && destination != null)
            ((p - SourceHandoffFraction) / (1f - SourceHandoffFraction)).coerceIn(0f, 1f)
        else p
        val frame=androidx.compose.ui.geometry.lerp(origin,target,geometryProgress)
        val cropWidth=minOf(image.width.toFloat(),image.height*frame.width/frame.height)
        val cropHeight=minOf(image.height.toFloat(),image.width*frame.height/frame.width)
        val path=androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(frame,androidx.compose.ui.geometry.CornerRadius(sourceCornerRadius.toPx()*(1f-geometryProgress))))
        }
        clipPath(path) {
            drawImage(image,alpha=opacity,
                srcOffset=androidx.compose.ui.unit.IntOffset(((image.width-cropWidth)/2).toInt(),((image.height-cropHeight)/2).toInt()),
                srcSize=androidx.compose.ui.unit.IntSize(cropWidth.toInt().coerceAtLeast(1),cropHeight.toInt().coerceAtLeast(1)),
                dstOffset=androidx.compose.ui.unit.IntOffset(frame.left.toInt(),frame.top.toInt()),
                dstSize=androidx.compose.ui.unit.IntSize(frame.width.toInt().coerceAtLeast(1),frame.height.toInt().coerceAtLeast(1)))
        }
    }
}
