package com.yunai.phototube

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Audit 38/57: generated failures and media, isolated loopback transport only. */
class DarkErrorReadabilityTest : RemoteAppTest() {
    private val failing = AtomicBoolean(true)
    private val writes = AtomicInteger()
    private val message = "本地合成读取失败，请重试"
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    override fun readFixture(request: RecordedRequest): MockResponse? {
        val path = request.requestUrl!!.encodedPath
        if (path == "/api/v1/folders") {
            if (failing.get()) return json("""{"code":"UNAVAILABLE","message":"$message"}""").setResponseCode(503)
        }
        return when (path) {
            "/api/v1/auth/session" -> json("""{"authenticated":true,"passwordSet":true,"user":{"username":"fixture"},"privateAccessUnlocked":false,"originalDeleteEnabled":false}""")
            "/api/v1/xmp-exports" -> json("""{"items":[],"nextCursor":null}""")
            "/api/v1/system/status" -> json("""{"generatedAt":"2026-10-09T00:00:00Z","alerts":[]}""")
            else -> null
        }
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet()
        return MockResponse().setResponseCode(405)
    }
    private fun darkSettings() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("theme-mode-DARK") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("返回设置").performClick()
    }
    private fun assertRenderedError(name: String) {
        rule.waitUntil(10000) { rule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        val node = rule.onNodeWithText(message).assertIsDisplayed()
        val pixels = node.captureToImage().toPixelMap()
        var dark = 0
        var light = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (maxOf(color.red, color.green, color.blue) < .25f) dark++
            if (minOf(color.red, color.green, color.blue) > .7f) light++
        }
        // Check rendered glyphs, not merely a semantics node on an invisible label.
        assertTrue("Error background must be dark", dark > pixels.width * pixels.height / 2)
        assertTrue("Error must contain visible light glyph pixels", light > 40)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            java.io.File(app.getExternalFilesDir(null), "mock-dark-error-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
    @Test fun exportScopeErrorIsReadableAndRetryRestoresSelection() {
        darkSettings()
        rule.onNodeWithText("服务状态与 AI 配置").performScrollTo().performClick()
        rule.onNodeWithText("XMP 导出与历史").performScrollTo().performClick()
        rule.onNodeWithText("全部媒体库").performClick()
        assertRenderedError("export-scope")
        rule.onNodeWithText("完成选择").assertIsNotEnabled()
        failing.set(false)
        rule.onNodeWithText("重试", substring = false).performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("测试媒体库").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("测试媒体库").performClick()
        rule.onNodeWithText("完成选择").assertIsEnabled().performClick()
        rule.onNodeWithText("已选 1 个媒体库").assertIsDisplayed()
        assertEquals(0, writes.get())
    }
}
