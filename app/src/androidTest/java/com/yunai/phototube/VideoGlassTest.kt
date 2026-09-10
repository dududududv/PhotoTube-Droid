package com.yunai.phototube

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class VideoGlassTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String): Bitmap {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot()!!
        val file = File(rule.activity.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        automation.executeShellCommand("mkdir -p /sdcard/Download/phototube-video").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/phototube-video/$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        return bitmap
    }

    @Test fun videoBackdropUpdatesAndPlaybackControlsWork() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("视频预览"))
        rule.onNodeWithText("视频预览").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("正在加载视频…").fetchSemanticsNodes().isEmpty() }
        val lens = rule.onNodeWithTag("video-glass")
        val oldBounds = lens.fetchSemanticsNode().boundsInRoot
        lens.performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(80f, 480f), 600) }
        val bounds = lens.fetchSemanticsNode().boundsInRoot
        assertTrue("Glass must actually move", bounds.top > oldBounds.top + 100)
        SystemClock.sleep(500)
        val first = screenshot("playing-1")
        SystemClock.sleep(1500)
        val second = screenshot("playing-2")
        // Only inspect the glass interior, avoiding the label and edges. These captures
        // are QA evidence, never the application's rendering input.
        var difference = 0L
        var count = 0
        for (y in (bounds.top + 16).toInt() until (bounds.top + bounds.height * .3f).toInt() step 4) {
            for (x in (bounds.left + 32).toInt() until (bounds.right - 32).toInt() step 4) {
                val a = first.getPixel(x, y)
                val b = second.getPixel(x, y)
                difference += abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
                count++
            }
        }
        assertTrue("Video pixels inside stationary glass must update: mean=${difference.toFloat() / count}", difference.toFloat() / count > 2f)
        rule.onNodeWithTag("video-play").performClick()
        rule.onNodeWithText("播放").assertIsDisplayed()
        screenshot("paused")
        rule.onNodeWithTag("video-play").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("重新播放").performClick()
        rule.onNodeWithContentDescription("关闭视频").performClick()
        rule.onNodeWithText("我的相册").assertIsDisplayed()
        // Reopening catches released/stale SurfaceTexture reuse.
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("视频预览"))
        rule.onNodeWithText("视频预览").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("暂停").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("关闭视频").performClick()
    }
}
