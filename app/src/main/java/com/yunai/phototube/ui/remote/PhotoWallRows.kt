package com.yunai.phototube.ui.remote

import com.yunai.phototube.data.RemoteAsset
import com.yunai.phototube.data.WallEntry
import kotlin.math.abs

internal sealed interface PhotoWallRow {
    val key:String
    data class Month(val index:Int,val entry:WallEntry.Month):PhotoWallRow {
        override val key="month-${entry.key}-${entry.anchorId}"
    }
    data class Photo(val index:Int,val asset:RemoteAsset,val ratio:Float)
    data class Photos(val photos:List<Photo>,val height:Float):PhotoWallRow {
        override val key="row-${photos.first().asset.id}"
    }
}

/** Justified rows in server order. Month boundaries and the final row never stretch short rows. */
internal fun photoWallRows(entries:List<WallEntry>,width:Float,targetHeight:Float=160f,gap:Float=6f):List<PhotoWallRow> {
    val rows=mutableListOf<PhotoWallRow>()
    val pending=mutableListOf<PhotoWallRow.Photo>()
    var sum=0f
    fun height(count:Int= pending.size,ratio:Float=sum)=(width-gap*(count-1))/ratio
    fun flush(justify:Boolean) {
        if(pending.isEmpty()) return
        val fitted=height()
        rows+=PhotoWallRow.Photos(pending.toList(),if(justify) fitted else minOf(targetHeight,fitted))
        pending.clear();sum=0f
    }
    entries.forEachIndexed {index,entry ->
        when(entry) {
            is WallEntry.Month -> {flush(false);rows+=PhotoWallRow.Month(index,entry)}
            is WallEntry.Asset -> {
                val asset=entry.value
                val ratio=if((asset.width ?: 0)>0 && (asset.height ?: 0)>0) asset.width!!.toFloat()/asset.height!! else 1f
                // Retain the source aspect ratio, including panoramas and tall portraits.
                if(pending.isNotEmpty() && height(pending.size+1,sum+ratio)<targetHeight &&
                    abs(height()-targetHeight)<abs(height(pending.size+1,sum+ratio)-targetHeight)) flush(true)
                pending+=PhotoWallRow.Photo(index,asset,ratio);sum+=ratio
                if(height()<=targetHeight) flush(true)
            }
        }
    }
    flush(false)
    return rows
}
