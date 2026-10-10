package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Generated media and loopback JSON only; no real service or stored account. */
class ManagementRetryTest : RemoteAppTest() {
    override val albumKindFixture = "SMART"
    private val failAlbums = AtomicBoolean(true)
    private val failTags = AtomicBoolean(true)
    private val failWall = AtomicBoolean(false)
    private val writes = AtomicInteger()
    private val failThumbnail = AtomicBoolean(true)
    private val thumbnailReads = AtomicInteger()
    override fun readFixture(request: RecordedRequest): MockResponse? {
        val path=request.requestUrl!!.encodedPath
        if(path=="/api/v1/assets/restore-0/thumbnail") {
            thumbnailReads.incrementAndGet()
            if(failThumbnail.get()) return MockResponse().setResponseCode(503)
        }
        if(path=="/api/v1/albums" && failAlbums.get() || path=="/api/v1/tags" && failTags.get() || path=="/api/v1/assets/timeline-summary" && failWall.get())
            return MockResponse().setResponseCode(503).setHeader("Content-Type","application/json")
                .setBody("""{"code":"UNAVAILABLE","message":"本地合成加载失败"}""")
        return null
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet();return MockResponse().setResponseCode(405)
    }
    @Test fun thumbnailRetryRecoversWithoutOpeningViewerOrWriting() {
        waitTag("thumbnail-retry-restore-0")
        val attempts=thumbnailReads.get()
        failThumbnail.set(false)
        rule.onNodeWithTag("thumbnail-retry-restore-0").performClick()
        rule.waitUntil(10000) {thumbnailReads.get()>attempts}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("thumbnail-retry-restore-0").fetchSemanticsNodes().isEmpty() &&
            rule.onAllNodesWithText("正在加载",substring=false).fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("remote-photo-wall").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("关闭").assertCountEquals(0)
        assertEquals(0,writes.get())
    }
    @Test fun wallSummaryFailureCanRetryWithoutWriting() {
        failWall.set(true)
        rule.runOnIdle {wall.refresh()}
        rule.waitUntil(10000) {rule.onAllNodesWithText("本地合成加载失败").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("server-error").assertIsDisplayed()
        failWall.set(false)
        rule.onNodeWithText("重试",substring=false).performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("server-error").fetchSemanticsNodes().isEmpty()}
        waitTag("asset-restore-0")
        assertEquals(0,writes.get())
    }
    @Test fun albumAndTagRetryRecoverWithoutChangingDraftOrWriting() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("本地合成加载失败").fetchSemanticsNodes().isNotEmpty()}
        failAlbums.set(false)
        rule.onNodeWithText("重试",substring=false).performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("智能相册规则").performClick()
        waitTag("smart-keyword")
        rule.onNodeWithTag("smart-keyword").performTextInput("本地草稿")
        rule.onNodeWithText("全部包含 · 不限").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("本地合成加载失败").fetchSemanticsNodes().isNotEmpty()}
        failTags.set(false)
        rule.onNodeWithText("重试",substring=false).performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("风景",substring=false).fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithContentDescription("返回",substring=false).onLast().performClick()
        rule.onNodeWithTag("smart-keyword").assertTextContains("本地草稿")
        assertEquals(0,writes.get())
    }
}
