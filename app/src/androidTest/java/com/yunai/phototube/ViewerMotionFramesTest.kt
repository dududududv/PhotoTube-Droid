package com.yunai.phototube

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Bundled lake image only. Deterministic animation samples, not a frame-rate benchmark. */
class ViewerMotionFramesTest {
    @get:Rule val rule = createComposeRule()
    @Test fun returningDuringEntranceClosesOnceAndReleasesSource() = interruptedClose(true)
    @Test fun returningWithoutSourceClosesOnce() = interruptedClose(false)

    @Test fun staleSourceIsResolvedAgainAndReleasedOnReturn() = interruptedClose(true, true)

    private fun interruptedClose(withSource: Boolean, removedSource: Boolean = false) {
        val photo = photos.first()
        val visible = mutableStateOf(true)
        var closes = 0
        var resolutions = 0
        var handoff: MediaSourceHandoff? = null
        rule.mainClock.autoAdvance = false
        try {
            rule.setContent { PhotoTubeTheme {
                handoff = LocalMediaSourceHandoff.current
                if (visible.value) MediaViewer(
                    ViewerRequest(listOf(photo), photo.id,
                        if (withSource) mapOf(photo.id to Rect(20f, 150f, 220f, 300f)) else emptyMap(), 4.dp,
                        resolveSource = if (removedSource) ({ resolutions++; null }) else null),
                    emptySet(), {}, {}, { closes++; visible.value=false })
            } }
            rule.mainClock.advanceTimeBy(64)
            rule.onNodeWithTag("media-viewer").assertExists()
            val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressBack()
            device.pressBack()
            rule.mainClock.advanceTimeBy(1200)
            rule.waitForIdle()
            assertEquals(1, closes)
            if (removedSource) assertEquals(1, resolutions)
            rule.onNodeWithTag("media-viewer").assertDoesNotExist()
            org.junit.Assert.assertNull(handoff?.source)
            org.junit.Assert.assertNull(handoff?.opacity)
            rule.mainClock.advanceTimeBy(1000)
            assertEquals(1, closes)
        } finally { rule.mainClock.autoAdvance=true }
    }

    @Test fun closingHandsBackToBundledThumbnail() {
        val photo = photos.first()
        var bounds = Rect.Zero
        val opened = mutableStateOf(false)
        var closes = 0
        rule.setContent { PhotoTubeTheme {
            Box(Modifier.fillMaxSize().background(Color(0xFFE5E9E7)).safeDrawingPadding().padding(18.dp)) {
                Box(Modifier.padding(top=120.dp).size(150.dp,100.dp)
                    .onGloballyPositioned { bounds=it.boundsInWindow() }
                    .mediaTransitionSource().clip(RoundedCornerShape(4.dp)).testTag("motion-source")
                    .clickable { opened.value=true }) {
                    MediaArtwork(photo,Modifier.fillMaxSize(),ContentScale.Crop)
                }
            }
            if(opened.value) MediaViewer(ViewerRequest(listOf(photo),photo.id,mapOf(photo.id to bounds),4.dp),
                emptySet(),{}, {}, {closes++;opened.value=false})
        } }
        rule.waitUntil(5000) { PhotoBitmapCache.peek(photo.id)!=null }
        rule.onNodeWithTag("motion-source").performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance=false
        val frames=mutableListOf<Bitmap>()
        val inst=InstrumentationRegistry.getInstrumentation()
        try {
            rule.onNodeWithContentDescription("关闭照片").performClick()
            val times=listOf(0L,32L,64L,96L,144L,208L,304L,448L,704L)
            times.forEachIndexed {index,time ->
                if(index>0) rule.mainClock.advanceTimeBy(time-times[index-1])
                rule.waitForIdle()
                frames += requireNotNull(inst.uiAutomation.takeScreenshot())
            }
            rule.mainClock.autoAdvance=true
            rule.waitForIdle()
            assertEquals(1,closes)
            rule.onNodeWithTag("media-viewer").assertDoesNotExist()
            rule.onNodeWithTag("motion-source").assertIsDisplayed()
            val width=270
            val height=frames.first().height*width/frames.first().width
            val sheet=Bitmap.createBitmap(width*3,(height+30)*3,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(sheet)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint=Paint().apply {color=android.graphics.Color.BLACK;textSize=16f}
            frames.forEachIndexed {i,bitmap ->
                val x=(i%3)*width;val y=(i/3)*(height+30)
                canvas.drawText("mock / ${times[i]} ms",x+8f,y+21f,paint)
                canvas.drawBitmap(bitmap,null,android.graphics.Rect(x,y+30,x+width,y+30+height),null)
            }
            File(inst.targetContext.getExternalFilesDir(null),"viewer-mock-close-frames.png").outputStream().use {
                sheet.compress(Bitmap.CompressFormat.PNG,100,it)
            }
            sheet.recycle()
        } finally {rule.mainClock.autoAdvance=true;frames.forEach {it.recycle()}}
    }
}
