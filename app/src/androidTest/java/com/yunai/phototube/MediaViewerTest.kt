package com.yunai.phototube

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaViewerTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @org.junit.Before fun startPortrait() {
        rule.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
        rule.waitUntil(5000) {rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
    }
    private fun capture(name:String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(400)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()!!
        val output=File(rule.activity.getExternalFilesDir(null),"$name.png")
        output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        automation.executeShellCommand("mkdir -p /sdcard/Download/phototube-viewer").use {ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        automation.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/phototube-viewer/$name.png").use {ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
    }
    private fun viewerName(name:String) = rule.onNodeWithTag("media-viewer").assert(SemanticsMatcher("showing $name") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains(name)==true
    })
    @Test fun pagingZoomDismissAndFavorite() {
        rule.onNodeWithContentDescription("山间来信").performClick()
        viewerName("山间来信")
        capture("photo-detail")
        rule.onNodeWithContentDescription("取消收藏").performClick()
        rule.onNodeWithContentDescription("收藏").assertIsDisplayed().performClick()
        rule.onNodeWithTag("media-pager").performTouchInput {swipeLeft(durationMillis=450)}
        viewerName("走过老街")
        rule.onNodeWithContentDescription("缩略图：山间来信").performClick()
        val photo=rule.onNodeWithTag("photo-gesture-${R.drawable.lake}")
        photo.performTouchInput {doubleClick(center)}
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已放大"))
        capture("photo-zoomed")
        photo.performTouchInput {doubleClick(center)}
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"适合屏幕"))
        photo.performTouchInput {
            pinch(center-Offset(100f,0f),center+Offset(100f,0f),center-Offset(300f,0f),center+Offset(300f,0f),500)
        }
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已放大"))
        photo.performTouchInput {doubleClick(center)}
        photo.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"适合屏幕"))
        // A short vertical pull cancels; a deliberate pull exits.
        photo.performTouchInput {swipe(center,center+Offset(0f,120f),400)}
        rule.onNodeWithTag("media-viewer").assertExists()
        photo.performTouchInput {swipe(center,center+Offset(0f,650f),600)}
        rule.onNodeWithTag("media-viewer").assertDoesNotExist()
        rule.onNodeWithContentDescription("山间来信").assertIsDisplayed()
    }
    @Test fun albumNavigationInformationDeletionAndUndo() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("山野之间").performClick()
        rule.onNodeWithTag("album-detail").assertIsDisplayed()
        capture("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("放大缩略图").performClick()
        rule.onNodeWithTag("album-media-${R.drawable.lake}").performClick()
        rule.onNodeWithContentDescription("照片与视频信息").performClick()
        rule.onNodeWithTag("media-info").assertIsDisplayed()
        rule.onNodeWithText("照片 · JPEG").assertIsDisplayed()
        capture("photo-info")
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithContentDescription("删除").performClick()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithContentDescription("删除").performClick()
        rule.onNodeWithText("删除",useUnmergedTree=true).performClick()
        rule.onNodeWithTag("media-viewer").assertDoesNotExist()
        rule.onNodeWithTag("album-media-${R.drawable.lake}").assertDoesNotExist()
        rule.onNodeWithText("撤销").performClick()
        rule.onNodeWithTag("album-media-${R.drawable.lake}").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.onNodeWithTag("album-detail").assertDoesNotExist()
    }
    @Test fun shareChooserAndLandscape() {
        rule.onNodeWithContentDescription("山间来信").performClick()
        rule.onNodeWithContentDescription("分享").performClick()
        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        rule.waitUntil(10000) {device.currentPackageName != "com.yunai.phototube"}
        org.junit.Assert.assertTrue(device.currentPackageName == "android" || device.currentPackageName.contains("intentresolver"))
        device.pressBack()
        viewerName("山间来信")
        try {
            rule.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            rule.waitUntil(5000) {rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            rule.onNodeWithTag("media-viewer").assertExists()
            capture("photo-landscape")
            rule.onNodeWithContentDescription("关闭照片").performClick()
        } finally {
            rule.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
        }
    }
    @Test fun mixedMediaPagingAndRecreation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("漫步日常").performClick()
        rule.onNodeWithTag("album-media-${R.raw.glass_video}").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("暂停视频").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("media-viewer").assertExists()
        rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
        rule.onNodeWithContentDescription("缩略图：走过老街").performClick()
        viewerName("走过老街")
        rule.onNodeWithTag("viewer-video-controls").assertDoesNotExist()
        rule.onNodeWithContentDescription("缩略图：城市片段").performClick()
        viewerName("城市片段")
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("关闭视频详情").performClick()
    }
    @Test fun videoPlaybackScrubbingAndPageLifecycle() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("视频",substring=false).performClick()
        rule.onNodeWithTag("album-media-${R.raw.glass_video}").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()}
        capture("video-detail")
        rule.onNodeWithContentDescription("暂停视频").performClick()
        rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
        rule.onNodeWithContentDescription("开启声音").performClick()
        rule.onNodeWithContentDescription("静音").assertIsDisplayed().performClick()
        rule.onNodeWithTag("video-timeline").performTouchInput {swipe(centerLeft+Offset(15f,0f),centerRight-Offset(40f,0f),700)}
        capture("video-seek")
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("1.5× 播放速度").performClick()
        rule.onNodeWithContentDescription("播放视频").performClick()
        rule.onNodeWithContentDescription("暂停视频").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭视频详情").performClick()
        rule.onNodeWithTag("media-viewer").assertDoesNotExist()
        rule.onNodeWithTag("album-detail").assertIsDisplayed()
        rule.onNodeWithTag("album-media-${R.raw.glass_video}").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("关闭视频详情").performClick()
    }
    @Test fun landscapeVideoGroupedControls() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("漫步日常").performClick()
        rule.onNodeWithTag("album-media-${R.raw.glass_video}").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("暂停视频").performClick()
        try {
            rule.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            rule.waitUntil(5000) {rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            capture("video-landscape")
            rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
            rule.onNodeWithContentDescription("分享").assertIsDisplayed()
            rule.onNodeWithContentDescription("删除").assertIsDisplayed()
            rule.onNodeWithContentDescription("照片与视频信息").assertIsDisplayed()
            rule.onNodeWithTag("media-filmstrip").assertIsDisplayed()
            rule.onNodeWithTag("video-timeline").performTouchInput {swipe(centerLeft+Offset(15f,0f),center,400)}
            capture("video-landscape")
            rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
            rule.onNodeWithContentDescription("照片与视频信息").performClick()
            rule.onNodeWithTag("media-info").assertIsDisplayed()
        } finally {
            rule.activityRule.scenario.onActivity {it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
        }
    }

    @Test fun videoSwipesSeekWithoutPaging() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("漫步日常").performClick()
        rule.onNodeWithTag("album-media-${R.raw.glass_video}").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("暂停视频").performClick()
        fun position()=rule.onNodeWithTag("video-timeline").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        val before=position()
        rule.onNodeWithTag("viewer-video-surface").performTouchInput {swipe(center,center+Offset(width*.3f,0f),500)}
        viewerName("城市片段")
        val forward=position()
        org.junit.Assert.assertTrue("Right swipe advances the current video",forward>before+1000)
        rule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
        rule.onNodeWithTag("viewer-video-surface").performTouchInput {swipe(center,center-Offset(width*.2f,0f),500)}
        viewerName("城市片段")
        org.junit.Assert.assertTrue("Left swipe rewinds the current video",position()<forward-500)
        rule.onNodeWithContentDescription("缩略图：走过老街").performClick()
        viewerName("走过老街")
    }

}
