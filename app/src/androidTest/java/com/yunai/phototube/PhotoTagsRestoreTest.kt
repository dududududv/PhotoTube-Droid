package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PhotoTagsRestoreTest : RemoteAppTest() {
    private val removed = AtomicBoolean(false)
    private val failDetail = AtomicBoolean(false)
    private val mutations = AtomicInteger()
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/assets/restore-0") return null
        if (failDetail.get()) return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"标签详情暂不可用"}""")
        val body = JSONObject().put("id", "restore-0").put("kind", "PHOTO").put("state", "BROWSABLE")
            .put("fileName", fileName(0)).put("favorite", false).put("takenAt", "2026-09-01T00:00:00Z")
            .put("contentHash", "a".repeat(64)).put("width", 48).put("height", 32)
            .put("tags", if (removed.get()) JSONArray() else JSONArray().put(
                JSONObject().put("id", 7).put("name", "风景").put("source", "RULE")))
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        mutations.incrementAndGet()
        if (request.method != "DELETE" || request.requestUrl!!.encodedPath != "/api/v1/assets/restore-0/tags/7") {
            return MockResponse().setResponseCode(405)
        }
        removed.set(true); failDetail.set(true)
        return MockResponse().setResponseCode(204)
    }
    private fun openTags() {
        rule.onNodeWithTag("asset-restore-0").performClick(); waitTag("media-viewer")
        rule.onNodeWithContentDescription("照片与视频信息").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("编辑标签").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("编辑标签").performClick()
        waitTag("remove-photo-tag-7")
    }
    @Test fun tagPickerRestoresAndCancellingDoesNotAttachTag() {
        openTags()
        rule.onNodeWithText("添加标签", substring = false).performClick()
        waitTag("pick-tag-7")
        rule.activityRule.scenario.recreate()
        waitTag("pick-tag-7")
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("pick-tag-7").fetchSemanticsNodes().isEmpty() }
        waitTag("remove-photo-tag-7")
        assertEquals(0, mutations.get())
    }
    @Test fun committedRemovalWithFailedRefreshStaysReadOnlyUntilReload() {
        openTags()
        // Synthetic fixture only: generated colour-block photo and the local tag named 风景.
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val screenshot=requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null),"mock-photo-tags-controls.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        var darkPixels=0
        for(y in screenshot.height-60 until screenshot.height-15) {
            for(x in screenshot.width*35/100 until screenshot.width*65/100) {
                val pixel=screenshot.getPixel(x,y)
                if(android.graphics.Color.red(pixel)<100 && android.graphics.Color.green(pixel)<100 && android.graphics.Color.blue(pixel)<100) darkPixels++
            }
        }
        assertTrue("Light management page must have a dark gesture handle",darkPixels>100)
        screenshot.recycle()
        rule.onNodeWithTag("remove-photo-tag-7").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("重新读取标签").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("照片标签").assertExists()
        rule.onNodeWithText("标签变更已提交，请刷新后继续编辑。").assertExists()
        rule.onNodeWithText("重新读取标签").assertExists()
        rule.onNodeWithTag("remove-photo-tag-7").assertIsNotEnabled()
        rule.onNodeWithText("添加标签", substring = false).assertIsNotEnabled()
        assertEquals(1, mutations.get())
        failDetail.set(false)
        rule.onNodeWithText("重新读取标签").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("这张照片还没有标签").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("添加标签", substring = false).assertIsEnabled()
        assertEquals("Reloading must not repeat the deletion", 1, mutations.get())
    }
}
