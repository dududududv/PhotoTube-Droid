package com.yunai.phototube

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class OriginalVersionCacheTest : RemoteAppTest() {
    @Volatile private var version = 1
    private val originals = AtomicInteger()
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        "/api/v1/assets/restore-0" -> MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("id", "restore-0").put("kind", "PHOTO").put("state", "BROWSABLE")
                .put("width", 48).put("height", 32).put("fileName", "restore-0.jpg")
                .put("contentHash", if(version == 0) JSONObject.NULL else (if(version == 1) "a" else "b").repeat(64)).put("activeEdit", JSONObject.NULL).toString())
        "/api/v1/assets/restore-0/original" -> {
            originals.incrementAndGet()
            val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if(version == 1) Color.RED else Color.GREEN)
            val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
            bitmap.recycle()
            MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
        }
        else -> null
    }
    private fun waitColor(expected: Int) {
        rule.waitUntil(10000) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap == null) false else {
                val actual = bitmap.getPixel(bitmap.width/2, bitmap.height/2)
                bitmap.recycle()
                Color.red(actual) == Color.red(expected) && Color.green(actual) == Color.green(expected)
            }
        }
    }
    private fun close() {
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun changedDetailHashReloadsOriginalButUnchangedHashReusesCache() {
        rule.onNodeWithTag("asset-restore-0").performClick()
        waitColor(Color.RED)
        close()
        version = 2
        rule.onNodeWithTag("asset-restore-0").performClick()
        waitColor(Color.GREEN)
        assertEquals(2, originals.get())
        close()
        rule.onNodeWithTag("asset-restore-0").performClick()
        waitColor(Color.GREEN)
        assertEquals(2, originals.get())
        close()
        version = 0 // Fresh detail explicitly clears the old list hash.
        repeat(2) { index ->
            rule.onNodeWithTag("asset-restore-0").performClick()
            waitColor(Color.GREEN)
            assertEquals(3 + index, originals.get())
            close()
        }
    }
}
