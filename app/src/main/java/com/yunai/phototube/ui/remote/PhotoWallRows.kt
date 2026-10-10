package com.yunai.phototube.ui.remote

import com.yunai.phototube.data.RemoteAsset
import com.yunai.phototube.data.WallEntry
import kotlin.math.abs
import androidx.compose.ui.unit.dp

internal object PhotoWallMetrics {
    val horizontalInset = 18.dp
    val gap = 6.dp
}

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

/** Shared source-ratio layout for both bundled demos and the paged photo wall. */
internal data class JustifiedRow<T>(val items:List<T>,val height:Float)
internal fun <T> justifiedRows(items:List<T>,width:Float,targetHeight:Float,gap:Float,ratioOf:(T)->Float):List<JustifiedRow<T>> {
    val rows=mutableListOf<JustifiedRow<T>>()
    val pending=mutableListOf<T>()
    var sum=0f
    fun height(count:Int=pending.size,ratio:Float=sum)=(width-gap*(count-1))/ratio
    fun flush(justify:Boolean) {
        if(pending.isEmpty()) return
        rows+=JustifiedRow(pending.toList(),if(justify) height() else minOf(targetHeight,height()))
        pending.clear();sum=0f
    }
    items.forEach {item ->
        val ratio=ratioOf(item).takeIf {it.isFinite() && it>0f} ?: 1f
        if(pending.isNotEmpty() && height(pending.size+1,sum+ratio)<targetHeight &&
            abs(height()-targetHeight)<abs(height(pending.size+1,sum+ratio)-targetHeight)) flush(true)
        pending+=item;sum+=ratio
        if(height()<=targetHeight) flush(true)
    }
    flush(false)
    return rows
}

/** Justified rows in server order. Month boundaries and the final row never stretch short rows. */
internal fun photoWallRows(entries:List<WallEntry>,width:Float,targetHeight:Float=160f,gap:Float=PhotoWallMetrics.gap.value):List<PhotoWallRow> {
    val rows=mutableListOf<PhotoWallRow>()
    val pending=mutableListOf<PhotoWallRow.Photo>()
    fun flush() {
        rows+=justifiedRows(pending,width,targetHeight,gap) {it.ratio}.map {PhotoWallRow.Photos(it.items,it.height)}
        pending.clear()
    }
    entries.forEachIndexed {index,entry ->
        when(entry) {
            is WallEntry.Month -> {flush();rows+=PhotoWallRow.Month(index,entry)}
            is WallEntry.Asset -> {
                val asset=entry.value
                val ratio=if((asset.width ?: 0)>0 && (asset.height ?: 0)>0) asset.width!!.toFloat()/asset.height!! else 1f
                pending+=PhotoWallRow.Photo(index,asset,ratio)
            }
        }
    }
    flush()
    return rows
}

/** Insert timeline boundaries without changing the source Paging indices. */
internal fun selectionPhotoRows(assets:List<RemoteAsset>,groups:List<com.yunai.phototube.data.MonthGroup>,width:Float):List<PhotoWallRow> {
    val entries=buildList<WallEntry> {
        var previous:String?=null
        assets.forEach {asset ->
            if(groups.isNotEmpty()) {
                val month=com.yunai.phototube.data.monthKey(asset,groups)
                if(month!=previous) add(WallEntry.Month(month,groups.firstOrNull {it.key==month}?.count ?: 0,asset.id))
                previous=month
            }
            add(WallEntry.Asset(asset))
        }
    }
    val indices=assets.withIndex().associate {it.value.id to it.index}
    return photoWallRows(entries,width).map {row ->
        if(row is PhotoWallRow.Photos) row.copy(photos=row.photos.map {it.copy(index=indices.getValue(it.asset.id))}) else row
    }
}

/** A former row leader may move inside another row after width/window changes. */
internal fun selectionAnchorIndex(rows:List<PhotoWallRow>,rowKey:String?):Int {
    if(rowKey==null || !rowKey.startsWith("row-")) return -1
    val id=rowKey.removePrefix("row-")
    return rows.indexOfFirst {row -> row is PhotoWallRow.Photos && row.photos.any {it.asset.id==id}}
}
