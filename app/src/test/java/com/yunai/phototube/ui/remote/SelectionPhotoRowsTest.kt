package com.yunai.phototube.ui.remote

import com.yunai.phototube.data.*
import org.junit.Assert.*
import org.junit.Test

class SelectionPhotoRowsTest {
    @Test fun anchorTracksPhotoWhenItIsNoLongerTheRowLeader() {
        val assets=(0..5).map {i -> RemoteAsset("synthetic-$i","PHOTO","BROWSABLE","2026-09-01T00:00:00Z",0,"sample",null,48,32,false,null)}
        val rows=selectionPhotoRows(assets,emptyList(),1000f)
        assertTrue(rows.none {it.key=="row-synthetic-1"})
        assertEquals(0,selectionAnchorIndex(rows,"row-synthetic-1"))
        assertEquals(-1,selectionAnchorIndex(rows,"row-missing"))
        assertEquals(-1,selectionAnchorIndex(rows,null))
        assertEquals(-1,selectionAnchorIndex(rows,"month-2026-09"))
    }

    @Test fun monthBoundariesKeepRowsSeparateAndPagingIndicesUnchanged() {
        val assets=listOf("2026-09-02","2026-09-01","2026-08-31","2026-08-30").mapIndexed {i,date ->
            RemoteAsset("synthetic-$i","PHOTO","BROWSABLE","${date}T00:00:00Z",0,"sample",null,48,32,false,null)
        }
        val groups=listOf(MonthGroup("2026-09","2026-09-01T00:00:00Z",2),MonthGroup("2026-08","2026-08-01T00:00:00Z",2))
        val rows=selectionPhotoRows(assets,groups,1000f)
        assertEquals(listOf("2026-09","2026-08"),rows.filterIsInstance<PhotoWallRow.Month>().map {it.entry.key})
        val photos=rows.filterIsInstance<PhotoWallRow.Photos>()
        assertEquals(listOf(listOf(0,1),listOf(2,3)),photos.map {row -> row.photos.map {it.index}})
        assertEquals(assets.map {it.id},photos.flatMap {it.photos}.map {it.asset.id})
        assertEquals(listOf(0,1,2,3),selectionPhotoRows(assets,emptyList(),1000f).filterIsInstance<PhotoWallRow.Photos>().flatMap {it.photos}.map {it.index})
    }
}
