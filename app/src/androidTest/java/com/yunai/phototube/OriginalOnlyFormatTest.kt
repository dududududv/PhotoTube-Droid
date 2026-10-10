package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class OriginalOnlyFormatTest : RemoteAppTest() {
    private val thumbnails = AtomicInteger()
    private val originals = AtomicInteger()
    override fun fileName(i: Int) = if(i == 0) "indexed.BMP" else super.fileName(i)
    override fun readFixture(request: RecordedRequest): MockResponse? {
        val path = request.requestUrl!!.encodedPath
        if (path == "/api/v1/assets/restore-0/thumbnail") thumbnails.incrementAndGet()
        if (path != "/api/v1/assets/restore-0/original") return null
        originals.incrementAndGet()
        // Genuine 48x32 uncompressed 24-bit BMP, not renamed PNG data.
        val pixels = 48 * 32 * 3
        val buffer = ByteBuffer.allocate(54 + pixels).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('B'.code.toByte()).put('M'.code.toByte()).putInt(54 + pixels).putInt(0).putInt(54)
        buffer.putInt(40).putInt(48).putInt(32).putShort(1).putShort(24).putInt(0).putInt(pixels)
        buffer.putInt(0).putInt(0).putInt(0).putInt(0)
        repeat(48 * 32) { buffer.put(80).put(140.toByte()).put(200.toByte()) }
        return MockResponse().setHeader("Content-Type", "image/bmp").setBody(Buffer().write(buffer.array()))
    }
    @Test fun indexedBmpShowsFormatAndOpensOriginalWithoutDerivativeRequests() {
        rule.onNodeWithText("BMP 原文件").assertIsDisplayed()
        assertEquals(0, thumbnails.get())
        rule.onNodeWithTag("asset-restore-0").performClick()
        waitTag("media-viewer")
        rule.waitUntil(10000) { originals.get() > 0 }
        rule.waitUntil(10000) { rule.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("暂时无法读取原图").assertDoesNotExist()
        assertEquals(0, thumbnails.get())
        rule.onNodeWithContentDescription("关闭照片").performClick()
        waitTag("asset-restore-0")
        rule.onNodeWithText("BMP 原文件").assertIsDisplayed()
    }
}
