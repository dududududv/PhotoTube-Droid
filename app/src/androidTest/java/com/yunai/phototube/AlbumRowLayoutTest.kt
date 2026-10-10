package com.yunai.phototube

import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test

class AlbumRowLayoutTest : RemoteAppTest() {
    override val photographicFixtures = true
    @Test fun albumUsesSourceRatiosAndAlignedRowsAfterTabTransition() {
        val galleryLeft=rule.onNodeWithTag("asset-restore-0").fetchSemanticsNode().boundsInWindow.left
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick()
        waitTag("album-media-restore-0")
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val first=rule.onNodeWithTag("album-media-restore-0").fetchSemanticsNode().boundsInRoot
        val second=rule.onNodeWithTag("album-media-restore-1").fetchSemanticsNode().boundsInRoot
        assertEquals("Album and library share their horizontal inset",galleryLeft,rule.onNodeWithTag("album-media-restore-0").fetchSemanticsNode().boundsInWindow.left,1f)
        assertEquals("Preserve 48×32 source ratio",1.5f,first.width/first.height,.02f)
        assertEquals("Same row has aligned tops",first.top,second.top,1f)
        assertEquals("Same row has aligned bottoms",first.bottom,second.bottom,1f)
        Thread.sleep(450)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {bitmap ->
            java.io.File(app.getExternalFilesDir(null),"album-rows.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("图库标签").performClick()
        waitTag("remote-photo-wall")
    }
}
