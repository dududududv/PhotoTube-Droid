package com.yunai.phototube

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yunai.phototube.ui.media.AlbumDetail
import com.yunai.phototube.ui.media.albumMedia
import com.yunai.phototube.ui.media.MediaMetadataCache
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Bundled sample photos only; no account, server, or device gallery. */
class DemoAlbumControlsTest {
    @get:Rule val rule = createComposeRule()
    @Test fun undoDisappearsAfterRecoveryAndIsDisabledDuringExit() {
        val media=albumMedia("nature")
        val pending=mutableStateOf(true)
        var undos=0
        rule.setContent {PhotoTubeTheme {
            AlbumDetail("本地合成相册",media,emptySet(),onOpen={_,_,_->},
                undoPhoto=if(pending.value) media.first() else null,
                onUndo={undos++;pending.value=false},onClose={})
        }}
        rule.onNodeWithTag("undo-deletion").performClick()
        rule.onNodeWithTag("undo-deletion").assertDoesNotExist()
        assertEquals(1,undos)
        rule.runOnIdle {pending.value=true}
        rule.mainClock.autoAdvance=false
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("undo-deletion").assertIsNotEnabled()
        assertEquals(1,undos)
        rule.mainClock.autoAdvance=true
    }
    @Test fun glassMenuResizesAndSortsWhileCloseCompletesOnce() {
        val media = albumMedia("nature")
        var closes = 0
        var opened = emptyList<Int>()
        val visible = mutableStateOf(true)
        rule.setContent { PhotoTubeTheme {
            if(visible.value) AlbumDetail("本地合成相册", media, emptySet(),
                onOpen={_, sorted, _ -> opened=sorted.map {it.id}}, undoPhoto=null, onUndo={},
                onClose={closes++;visible.value=false})
        } }
        rule.waitUntil(5000) {media.all {MediaMetadataCache.peek(it.id)!=null}}
        rule.waitForIdle()
        val target = "album-media-${media.first().id}"
        val firstBounds=rule.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot
        val metadata=requireNotNull(MediaMetadataCache.peek(media.first().id))
        assertEquals(metadata.width.toFloat()/metadata.height,firstBounds.width/firstBounds.height,.02f)
        val secondBounds=rule.onNodeWithTag("album-media-${media[1].id}").fetchSemanticsNode().boundsInRoot
        assertEquals(firstBounds.top,secondBounds.top,1f)
        assertEquals(firstBounds.bottom,secondBounds.bottom,1f)
        val original = firstBounds.width
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithTag("glass-action-menu").assertIsDisplayed()
        rule.onNodeWithText("放大缩略图").performClick()
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot.width > original * 1.4f)
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("最早在前").performClick()
        rule.onNodeWithTag(target).performClick()
        assertEquals(media.sortedBy {it.takenOn}.map {it.id}, opened)
        rule.mainClock.autoAdvance=false
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        assertEquals(0,closes)
        rule.mainClock.advanceTimeBy(350)
        rule.mainClock.autoAdvance=true
        rule.waitForIdle()
        assertEquals(1,closes)
        rule.onNodeWithTag("album-detail").assertDoesNotExist()
    }
}
