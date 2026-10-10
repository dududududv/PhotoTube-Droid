package com.yunai.phototube

import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AnimatedPhotoTest : RemoteAppTest() {
    override fun fileName(i: Int) = if (i == 0) "animated.gif" else super.fileName(i)
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/assets/restore-0/original") return null
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("two-frame.gif").use { it.readBytes() }
        return MockResponse().setHeader("Content-Type", "image/gif").setBody(Buffer().write(bytes))
    }
    @Test fun gifOriginalActuallyChangesFramesInViewer() {
        rule.onNodeWithTag("asset-restore-0").performClick()
        waitTag("media-viewer")
        val colors = mutableSetOf<String>()
        // Native animated drawable frames follow wall time, not Compose's test clock.
        rule.waitUntil(10000) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap != null) {
                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                if (Color.red(pixel) > 180 && Color.blue(pixel) < 40) colors += "red"
                if (Color.blue(pixel) > 180 && Color.red(pixel) < 40) colors += "blue"
                bitmap.recycle()
            }
            colors.size == 2
        }
        assertEquals(setOf("red", "blue"), colors)
        rule.onNodeWithText("暂时无法读取原图").assertDoesNotExist()
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        waitTag("asset-restore-0")
    }
}
