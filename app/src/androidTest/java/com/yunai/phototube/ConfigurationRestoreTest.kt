package com.yunai.phototube

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test

/** Exercises saved state and retained cursor windows in the real route tree. */
class ConfigurationRestoreTest : RemoteAppTest() {
    @Test fun bulkSelectionAndActionsRemainReachableInLandscape() {
        rule.onNodeWithText("选择", substring = false).performClick()
        waitTag("bulk-grid")
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        try {
            waitTag("bulk-grid")
            Assert.assertTrue(rule.onNodeWithTag("bulk-grid").fetchSemanticsNode().boundsInRoot.height > 0)
            rule.onNodeWithTag("bulk-grid").performScrollToNode(hasTestTag("bulk-asset-restore-0"))
            rule.onNodeWithTag("bulk-asset-restore-0").assertIsDisplayed().performClick()
            rule.onNodeWithTag("bulk-actions").assertIsDisplayed().assertIsEnabled()
            capture("bulk-landscape")
            rule.onNodeWithTag("bulk-actions").performClick()
            rule.onNodeWithText("批量操作").assertIsDisplayed()
            rule.onNodeWithText("取消", substring = false).performClick()
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        }
    }
    @Test fun duplicateGroupAndMembersRemainReachableInLandscape() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("完全重复").performClick()
        waitTag("duplicate-groups")
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        try {
            waitTag("duplicate-groups")
            Assert.assertTrue(rule.onNodeWithTag("duplicate-groups").fetchSemanticsNode().boundsInRoot.height > 0)
            val group = "duplicate-group-${"a".repeat(64)}"
            rule.onNodeWithTag("duplicate-groups").performScrollToNode(hasTestTag(group))
            rule.onNodeWithTag(group).assertIsDisplayed().performClick()
            waitTag("duplicate-members")
            Assert.assertTrue(rule.onNodeWithTag("duplicate-members").fetchSemanticsNode().boundsInRoot.height > 0)
            rule.onNodeWithTag("duplicate-members").performScrollToNode(hasTestTag("duplicate-media-restore-0"))
            rule.onNodeWithTag("duplicate-media-restore-0").assertIsDisplayed()
            rule.onNodeWithText("保留全部副本").assertIsDisplayed()
            capture("duplicate-landscape")
            rule.onNodeWithTag("duplicate-media-restore-0").performClick(); current(0)
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null), "route-restore-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun current(i: Int) = rule.waitUntil(15000) {
        rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().any {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.endsWith(" ${fileName(i)}") == true
        }
    }
    private fun select(i: Int) {
        rule.onNodeWithTag("media-filmstrip").performScrollToNode(hasContentDescription("缩略图：${fileName(i)}"))
        rule.onNodeWithContentDescription("缩略图：${fileName(i)}").performClick()
        current(i)
    }
    @Test fun rotatingPhotoViewerReturnsToVisibleReopenableSource() {
        try {
            for ((orientation, expected, label) in listOf(
                Triple(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE, "photo-close-landscape"),
                Triple(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT, "photo-close-portrait")
            )) {
                rule.onNodeWithTag("asset-restore-0").performClick(); current(0)
                rule.runOnUiThread { rule.activity.requestedOrientation=orientation }
                rule.waitUntil(15000) { rule.activity.resources.configuration.orientation==expected }
                current(0)
                rule.onNodeWithContentDescription("关闭照片").performClick()
                rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty()}
                rule.onNodeWithTag("asset-restore-0").assertIsDisplayed()
                capture(label)
            }
            rule.onNodeWithTag("asset-restore-0").performClick(); current(0)
            rule.onNodeWithContentDescription("关闭照片").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty()}
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        }
    }

    @Test fun memoryViewerKeepsSelectedPhotoAndReturnsToFeedAfterRecreation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("回忆与精选").performClick()
        waitTag("memory-往年今日-restore-0")
        rule.onNodeWithTag("memory-往年今日-restore-0").performClick(); current(0)
        select(2)
        rule.activityRule.scenario.recreate(); current(2)
        select(0)
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        waitTag("memory-往年今日-restore-0")
    }
    @Test fun directoryTrailAndPagedViewerSurviveRecreation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("文件夹").performClick()
        waitTag("directory-test-library:")
        rule.onNodeWithTag("directory-test-library:").performClick()
        waitTag("directory-test-library:旅行")
        rule.onNodeWithTag("directory-test-library:旅行").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("browse-directory-photos").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("没有子目录，可查看本目录的照片与视频。").assertExists()
        capture("directory")
        rule.onNodeWithTag("browse-directory-photos").performClick()
        waitTag("asset-restore-0")
        rule.onNodeWithTag("asset-restore-0").performClick(); current(0)
        select(99); select(100)
        rule.activityRule.scenario.recreate(); current(100)
        rule.onNodeWithText("列表已变化，请返回后重新打开").assertDoesNotExist()
        select(101)
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("directory-photos").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("返回上一级").performClick()
        waitTag("directory-test-library:旅行")
    }
    @Test fun duplicateSelectionAndPagedViewerSurviveRecreation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("完全重复").performClick()
        waitTag("duplicate-group-${"a".repeat(64)}")
        rule.onNodeWithTag("duplicate-group-${"a".repeat(64)}").performClick()
        waitTag("duplicate-media-restore-0")
        rule.activityRule.scenario.recreate()
        waitTag("duplicate-media-restore-0")
        capture("duplicates")
        rule.onNodeWithTag("duplicate-media-restore-0").performClick(); current(0)
        select(99); select(100)
        rule.activityRule.scenario.recreate(); current(100)
        rule.onNodeWithText("列表已变化，请返回后重新打开").assertDoesNotExist()
        select(101)
    }
    @Test fun pausedVideoKeepsSeekAndSoundAcrossLandscapeRotation() {
        rule.onNodeWithTag("asset-restore-1").performClick(); current(1)
        rule.waitUntil(15000) { rule.onAllNodes(hasContentDescription("暂停视频") or hasContentDescription("播放视频")).fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithContentDescription("暂停视频").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithContentDescription("暂停视频").performClick()
        if (rule.onAllNodesWithContentDescription("开启声音").fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithContentDescription("开启声音").performClick()
        rule.onNodeWithTag("video-timeline").performSemanticsAction(SemanticsActions.SetProgress) { it(2000f) }
        fun position() = rule.onNodeWithTag("video-timeline").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        rule.waitUntil(5000) { kotlin.math.abs(position() - 2000f) < 100f }
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        current(1)
        rule.onNodeWithContentDescription("播放视频").assertExists()
        rule.onNodeWithContentDescription("静音").assertExists()
        rule.waitUntil(10000) { kotlin.math.abs(position() - 2000f) < 150f }
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        current(1)
        rule.onNodeWithContentDescription("播放视频").assertExists()
        rule.waitUntil(10000) { kotlin.math.abs(position() - 2000f) < 150f }
    }
    @Test fun libraryViewerSurvivesRecreationBeyondFirstPageAndStillNavigates() {
        rule.onNodeWithTag("asset-restore-0").performClick(); current(0)
        select(99); select(100)
        val original = wall
        rule.activityRule.scenario.recreate()
        current(100)
        Assert.assertSame(original, wall)
        rule.onNodeWithText("列表已变化，请返回后重新打开").assertDoesNotExist()
        select(101); select(99)
    }
    @Test fun albumAndItsOpenViewerSurviveRecreation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick()
        waitTag("album-media-restore-0")
        rule.activityRule.scenario.recreate()
        waitTag("album-media-restore-0")
        rule.onNodeWithTag("album-media-restore-0").performClick(); current(0)
        select(99); select(100)
        rule.activityRule.scenario.recreate()
        current(100)
        rule.onNodeWithText("列表已变化，请返回后重新打开").assertDoesNotExist()
        select(101)
    }
}
