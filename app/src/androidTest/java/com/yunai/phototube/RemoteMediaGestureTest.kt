package com.yunai.phototube

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Device gestures against authenticated HTTP media, never the old local demo routes. */
class RemoteMediaGestureTest : RemoteAppTest() {
    override val photographicFixtures = true
    private val favorites = ConcurrentHashMap.newKeySet<Int>()
    private val mutations = CopyOnWriteArrayList<Pair<String, String>>()
    override fun favoriteFixture(index: Int) = index in favorites
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath
        val body = request.body.readUtf8()
        mutations += path to body
        if (request.method != "POST" || path != "/api/v1/assets/favorite") return MockResponse().setResponseCode(405)
        val json = JSONObject(body)
        val ids = json.getJSONArray("assetIds")
        for (i in 0 until ids.length()) {
            val index = ids.getString(i).removePrefix("restore-").toInt()
            if (json.getBoolean("favorite")) favorites.add(index) else favorites.remove(index)
        }
        return MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("succeeded", ids).put("failed", org.json.JSONArray()).toString())
    }
    private fun current(index: Int) = rule.waitUntil(15000) {
        rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().any {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.endsWith(" ${fileName(index)}") == true
        }
    }
    private fun playing() {
        // Autoplay is off by default; explicitly start playback for gesture tests.
        rule.waitUntil(15000) {
            rule.onAllNodesWithContentDescription("播放视频").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()
        }
        if(rule.onAllNodesWithContentDescription("播放视频").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithContentDescription("播放视频").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun select(index: Int) {
        rule.onNodeWithTag("media-filmstrip").performScrollToNode(hasContentDescription("缩略图：${fileName(index)}"))
        rule.onNodeWithContentDescription("缩略图：${fileName(index)}").performClick()
        current(index)
    }
    private fun open(index: Int) { rule.onNodeWithTag("asset-restore-$index").performClick(); current(index) }
    private fun position() = rule.onNodeWithTag("video-timeline").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    private fun portrait() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    }
    private fun landscape() {
        rule.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(10000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File(app.getExternalFilesDir(null), "remote-media-$name.png"))
    }
    @Test fun photoPagingZoomPinchAndCancelledDismiss() {
        open(0)
        select(2)
        rule.onNodeWithTag("media-pager").performTouchInput { swipeLeft(durationMillis = 450) }
        current(3)
        select(0)
        // The initial cursor window adapts its first item to ID -1; identity is asserted above.
        val photo = rule.onNodeWithTag("photo-gesture--1")
        photo.performTouchInput { doubleClick(center) }
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已放大"))
        photo.performTouchInput { doubleClick(center) }
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "适合屏幕"))
        photo.performTouchInput { pinch(center - Offset(100f, 0f), center + Offset(100f, 0f), center - Offset(300f, 0f), center + Offset(300f, 0f), 500) }
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已放大"))
        capture("zoom")
        photo.performTouchInput { doubleClick(center) }
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "适合屏幕"))
        photo.performTouchInput { swipe(center, center + Offset(0f, 120f), 400) }
        rule.onNodeWithTag("media-viewer").assertExists()
        photo.performTouchInput { swipe(center, center + Offset(0f, 650f), 600) }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("asset-restore-0").assertIsDisplayed()
    }
    @Test fun favoritePersistsAcrossReopenAndCancelledDeleteDoesNotWrite() {
        open(0)
        rule.onNodeWithContentDescription("收藏").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithContentDescription("取消收藏").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, mutations.size)
        assertEquals("/api/v1/assets/favorite", mutations.single().first)
        val request = JSONObject(mutations.single().second)
        assertEquals(listOf("restore-0"), request.getJSONArray("assetIds").let { ids -> (0 until ids.length()).map(ids::getString) })
        assertTrue(request.getBoolean("favorite"))
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        waitTag("asset-restore-0"); open(0)
        rule.onNodeWithContentDescription("取消收藏").assertIsDisplayed().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithContentDescription("收藏").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, mutations.size)
        assertFalse(JSONObject(mutations.last().second).getBoolean("favorite"))
        rule.onNodeWithContentDescription("删除").performClick()
        rule.onNodeWithText("移入回收站后可以恢复，NAS 原文件会保留。").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        current(0)
        rule.waitForIdle()
        assertEquals("Cancel must not send a deletion request", 2, mutations.size)
    }
    @Test fun albumThumbnailSizingAndPhotoInformation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick()
        waitTag("album-media-restore-0")
        val originalWidth = rule.onNodeWithTag("album-media-restore-0").fetchSemanticsNode().boundsInRoot.width
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("放大缩略图").performClick()
        rule.waitUntil(10000) {
            rule.onNodeWithTag("album-media-restore-0").fetchSemanticsNode().boundsInRoot.width > originalWidth * 1.4f
        }
        rule.onNodeWithTag("album-media-restore-0").performClick(); current(0)
        rule.onNodeWithContentDescription("照片与视频信息").performClick()
        waitTag("media-info")
        rule.onNodeWithText("JPG").assertExists()
        capture("photo-info")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-info").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty() }
        waitTag("album-restore-album")
    }
    @Test fun photoShareChooserReturnsAndLandscapeKeepsImage() {
        open(0)
        rule.onNodeWithContentDescription("分享").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        rule.waitUntil(15000) { device.currentPackageName?.let { it == "android" || it.contains("intentresolver") } == true }
        // Open the chooser only; no destination is chosen and nothing is sent.
        device.pressBack(); current(0)
        try {
            landscape(); current(0)
            capture("photo-landscape")
            rule.onNodeWithContentDescription("关闭照片").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        } finally { portrait() }
    }
    @Test fun videoSwipesSeekInsteadOfSwitchingAssets() {
        open(1); playing()
        rule.onNodeWithContentDescription("暂停视频").performClick()
        val before = position()
        rule.onNodeWithTag("viewer-video-surface").performTouchInput { swipe(center, center + Offset(width * .3f, 0f), 500) }
        current(1)
        val forward = position()
        assertTrue("Right swipe advances current video", forward > before + 1000f)
        rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
        rule.onNodeWithTag("viewer-video-surface").performTouchInput { swipe(center, center - Offset(width * .2f, 0f), 500) }
        current(1)
        assertTrue("Left swipe rewinds current video", position() < forward - 500f)
        select(2)
        rule.onNodeWithTag("viewer-video-controls").assertDoesNotExist()
        select(1); playing()
    }
    @Test fun pausedVideoScrubsChangesSpeedAndReopensCleanly() {
        com.yunai.phototube.data.PlaybackPreferences(app).muted = true
        open(1); playing()
        rule.onNodeWithContentDescription("暂停视频").performClick()
        rule.onNodeWithContentDescription("开启声音").performClick()
        rule.onNodeWithContentDescription("静音").performClick()
        rule.onNodeWithTag("video-timeline").performTouchInput { swipe(centerLeft + Offset(15f, 0f), center, 500) }
        assertTrue(position() > 1000f)
        rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("1.5× 播放速度").performClick()
        rule.onNodeWithContentDescription("播放视频").performClick(); playing()
        capture("video-playing")
        rule.onNodeWithContentDescription("关闭视频详情").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        open(1); playing()
        assertTrue("Reopening starts a fresh player", position() < 2000f)
    }
    @Test fun videoGlassMenuVolumeAndSpeedRemainInteractive() {
        open(1)
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithTag("glass-action-menu").assertIsDisplayed()
        rule.onNodeWithTag("viewer-volume").performSemanticsAction(SemanticsActions.SetProgress) { it(.35f) }
        rule.onNodeWithText("音量 35%").assertExists()
        auditShot("video-glass-menu")
        rule.onNodeWithText("1.5× 播放速度").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("✓  1.5× 播放速度").performScrollTo().assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty()}
        assertTrue(mutations.isEmpty())
    }
    @Test fun landscapeVideoControlsRemainVisibleAndInfoIsReachable() {
        open(1)
        // Autoplay defaults off. Start explicitly rather than relying on saved device preferences.
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("播放视频").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithContentDescription("播放视频").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription("播放视频").performClick()
        playing()
        rule.onNodeWithContentDescription("暂停视频").performClick()
        try {
            landscape(); current(1)
            listOf("播放视频", "分享", "删除", "照片与视频信息").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
            rule.onNodeWithTag("media-filmstrip").assertIsDisplayed()
            rule.onNodeWithTag("video-timeline").performSemanticsAction(SemanticsActions.SetProgress) { it(2000f) }
            rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
            capture("video-landscape")
            rule.onNodeWithContentDescription("照片与视频信息").performClick()
            waitTag("media-info")
            rule.onNodeWithText("MP4").assertExists()
            rule.onNodeWithText("关闭", substring = false).assertIsDisplayed()
            capture("video-info-landscape")
            rule.onNodeWithTag("glass-sheet-handle").performSemanticsAction(SemanticsActions.Dismiss) { assertTrue(it()) }
            rule.waitUntil(10000) { rule.onAllNodesWithTag("media-info").fetchSemanticsNodes().isEmpty() }
        } finally { portrait() }
    }
}
