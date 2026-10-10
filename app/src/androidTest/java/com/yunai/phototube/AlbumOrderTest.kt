package com.yunai.phototube

import com.yunai.phototube.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Pure synthetic metadata; no transport or device account. */
class AlbumOrderTest {
    private fun album(id:String,name:String,count:Long)=RemoteAlbum(id,name,"NORMAL","TAKEN_AT_DESC",count,null,.5f,.5f,JSONObject())

    @Test fun laterPagesParticipateInNameAndCountOrdering()=runBlocking {
        val first=album("a","Zebra",1)
        val second=album("b","Alpha",8)
        val third=album("c","Beta",8)
        val seen=mutableListOf<String?>()
        val fetch:suspend (String?)->AlbumPage<RemoteAlbum> = {cursor ->
            seen+=cursor
            if(cursor==null) AlbumPage(listOf(first),"next") else AlbumPage(listOf(third,second),null)
        }
        assertEquals(listOf("b","c","a"),loadOrderedAlbums(AlbumOrder.NAME,fetch).map {it.id})
        assertEquals(listOf(null,"next"),seen)
        assertEquals(listOf("b","c","a"),loadOrderedAlbums(AlbumOrder.COUNT,fetch).map {it.id})
        assertEquals(listOf("a","c","b"),loadOrderedAlbums(AlbumOrder.DEFAULT,fetch).map {it.id})
    }

    @Test fun cyclicCursorsFailWithoutPublishingPartialOrder()=runBlocking {
        var calls=0
        try {
            loadOrderedAlbums(AlbumOrder.NAME) {
                calls++
                AlbumPage(listOf(album("a","Alpha",1)),"repeat")
            }
            fail("Repeated cursors must fail")
        } catch(expected:IllegalStateException) {
            assertEquals(2,calls)
        }
    }
}
