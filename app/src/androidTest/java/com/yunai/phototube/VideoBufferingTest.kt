package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class VideoBufferingTest : RemoteAppTest() {
    private lateinit var fixturePreferences:com.yunai.phototube.data.PlaybackPreferences
    private var originalAutoPlay=false
    private var originalMuted=true
    @org.junit.Before fun setDeterministicPlaybackIntent() {
        fixturePreferences=com.yunai.phototube.data.PlaybackPreferences(app)
        originalAutoPlay=fixturePreferences.autoPlay;originalMuted=fixturePreferences.muted
        fixturePreferences.autoPlay=true;fixturePreferences.muted=true
    }
    @org.junit.After fun restorePlaybackIntent() {
        fixturePreferences.autoPlay=originalAutoPlay;fixturePreferences.muted=originalMuted
    }

    @Test fun seekCleanupHonorsCurrentActivityAndPlaybackIntent() {
        rule.runOnUiThread {
            val player=androidx.media3.exoplayer.ExoPlayer.Builder(app).build()
            val playback=com.yunai.phototube.ui.media.VideoPlayback(player)
            try {
                playback.wantsPlay=true;playback.interactionActive=false
                playback.resumeAfterSeek()
                org.junit.Assert.assertFalse(player.playWhenReady)
                playback.interactionActive=true;playback.wantsPlay=false
                playback.resumeAfterSeek()
                org.junit.Assert.assertFalse(player.playWhenReady)
                playback.wantsPlay=true
                playback.resumeAfterSeek()
                org.junit.Assert.assertTrue(player.playWhenReady)
                playback.interactionActive=false
                playback.resumeAfterSeek()
                org.junit.Assert.assertFalse(player.playWhenReady)
            } finally {
                playback.released=true;player.release()
            }
            playback.resumeAfterSeek() // Disposal cleanup must not touch a released player.
        }
    }

    @Test fun firstFrameCoversMismatchedPosterInLetterbox() {
        delaySeconds = 0
        rule.onNodeWithTag("asset-restore-1").performTouchInput {click(androidx.compose.ui.geometry.Offset(12f,12f))}
        waitTag("viewer-video-surface")
        rule.waitUntil(15000) {
            rule.onAllNodes(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已显示视频画面"
            )).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle(); android.os.SystemClock.sleep(600)
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            // The fixture poster is exact opaque blue. None of that source color
            // may remain after the bundled video owns the full display plane.
            var bluePixels = 0
            for(y in bitmap.height/3 until bitmap.height*2/3 step 2) {
                for(x in bitmap.width/4 until bitmap.width*3/4 step 2) {
                    val color=bitmap.getPixel(x,y)
                    if(android.graphics.Color.blue(color)>240 && android.graphics.Color.red(color)<10 && android.graphics.Color.green(color)<10) bluePixels++
                }
            }
            org.junit.Assert.assertTrue("Poster leaked around video: $bluePixels blue pixels", bluePixels < 20)
            java.io.File(app.getExternalFilesDir(null),"repair-video-letterbox.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        } finally {bitmap.recycle()}
    }
    @Test fun cancelledTimelineDragDoesNotSeekAndReleaseKeepsPauseIntent() {
        delaySeconds=0
        rule.onNodeWithTag("asset-restore-1").performClick()
        waitTag("viewer-video-surface")
        rule.waitUntil(15000) {rule.onAllNodes(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已显示视频画面")).fetchSemanticsNodes().isNotEmpty()}
        if(rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithContentDescription("暂停视频").performClick()
        val timeline=rule.onNodeWithTag("video-timeline")
        timeline.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(1000f)}
        fun position()=timeline.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
        rule.waitUntil(5000) {kotlin.math.abs(position()-1000f)<100}
        timeline.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width*.25f,centerY))
            moveTo(androidx.compose.ui.geometry.Offset(width*.75f,centerY),200)
            cancel()
        }
        rule.waitForIdle()
        org.junit.Assert.assertEquals("Cancelled preview must not commit",1000f,position(),100f)
        rule.onNodeWithContentDescription("播放视频").assertExists()
        val duration=timeline.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].range.endInclusive
        timeline.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width*.25f,centerY))
            moveTo(androidx.compose.ui.geometry.Offset(width*.65f,centerY),200)
            up()
        }
        rule.waitUntil(5000) {kotlin.math.abs(position()-duration*.65f)<150}
        rule.onNodeWithContentDescription("播放视频").assertExists()
    }

    private val requests = AtomicInteger()
    @Volatile private var unavailable = false
    @Volatile private var delaySeconds = 3L
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/assets/restore-1/original") return null
        requests.incrementAndGet()
        if (unavailable) return MockResponse().setResponseCode(404)
        val bytes = app.resources.openRawResource(R.raw.glass_video).use { it.readBytes() }
        val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
        if (start >= bytes.size) return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${bytes.size}")
        return MockResponse().setResponseCode(if (request.getHeader("Range") == null) 200 else 206)
            .setHeader("Content-Type", "video/mp4")
            .setHeader("Content-Range", "bytes $start-${bytes.lastIndex}/${bytes.size}")
            .setBody(Buffer().write(bytes, start, bytes.size - start)).setBodyDelay(delaySeconds, TimeUnit.SECONDS)
    }

    @Test fun pauseWhileInitialVideoIsBufferingStaysPausedWhenFrameArrives() {
        rule.onNodeWithTag("asset-restore-1").performClick()
        waitTag("viewer-video-surface")
        rule.waitUntil(10000) { requests.get() > 0 }
        // The request has started, but its delayed body has not delivered a frame.
        rule.onAllNodes(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已显示视频画面"
        )).assertCountEquals(0)
        rule.onNodeWithContentDescription("暂停视频").assertExists().performClick()
        rule.onNodeWithContentDescription("播放视频").assertExists()
        rule.waitUntil(15000) {
            rule.onAllNodes(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已显示视频画面"
            )).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("播放视频").assertExists()
        // Allow multiple real player-position updates; a paused intent must not
        // accidentally start playback when buffering ends.
        android.os.SystemClock.sleep(700)
        rule.onNodeWithTag("video-timeline").assert(SemanticsMatcher("position remains paused") {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current < 100f
        })
        rule.activityRule.scenario.recreate()
        waitTag("viewer-video-surface")
        rule.onNodeWithContentDescription("播放视频").assertExists().performClick()
        rule.onNodeWithContentDescription("暂停视频").assertExists()
    }
    @Test fun failedReloadRetriesAtSavedPositionWithPlaybackPreferences() {
        delaySeconds = 0
        rule.onNodeWithTag("asset-restore-1").performClick()
        waitTag("viewer-video-surface")
        rule.waitUntil(15000) {
            rule.onAllNodes(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已显示视频画面"
            )).fetchSemanticsNodes().isNotEmpty()
        }
        if(rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription("暂停视频").performClick()
        if(rule.onAllNodesWithContentDescription("开启声音").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithContentDescription("开启声音").performClick()
        rule.onNodeWithTag("video-timeline").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2000f) }
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("1.5× 播放速度").performClick()
        fun position() = rule.onNodeWithTag("video-timeline").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
        rule.waitUntil(5000) { kotlin.math.abs(position() - 2000f) < 100f }
        unavailable = true
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15000) { rule.onAllNodesWithText("暂时无法播放这段视频").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("播放视频").assertExists()
        unavailable = false
        val before = requests.get()
        rule.onNodeWithText("重试").performClick()
        rule.waitUntil(15000) {
            requests.get() > before && rule.onAllNodes(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已显示视频画面"
            )).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("暂时无法播放这段视频").assertDoesNotExist()
        rule.onNodeWithContentDescription("播放视频").assertExists()
        rule.onNodeWithContentDescription("静音").assertExists()
        rule.waitUntil(5000) { kotlin.math.abs(position() - 2000f) < 100f }
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("✓  1.5× 播放速度").assertExists()
    }

    @Test fun permanentAudioFocusLossIsNotResumedByReturningToApp() {
        delaySeconds = 0
        rule.onNodeWithTag("asset-restore-1").performClick()
        waitTag("viewer-video-surface")
        rule.waitUntil(15000) {
            rule.onAllNodes(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已显示视频画面"
            )).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("开启声音").performClick()
        val audio = app.getSystemService(android.media.AudioManager::class.java)
        val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener({ }, android.os.Handler(android.os.Looper.getMainLooper()))
            .build()
        try {
            rule.runOnUiThread {
                org.junit.Assert.assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(focus))
            }
            rule.waitUntil(5000) { rule.onAllNodesWithContentDescription("播放视频").fetchSemanticsNodes().isNotEmpty() }
            rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            waitTag("viewer-video-surface")
            rule.onNodeWithContentDescription("播放视频").assertExists()
            rule.onNodeWithContentDescription("静音").assertExists()
            rule.onNodeWithContentDescription("播放视频").performClick()
            rule.onNodeWithContentDescription("暂停视频").assertExists()
        } finally { audio.abandonAudioFocusRequest(focus) }
    }

}
