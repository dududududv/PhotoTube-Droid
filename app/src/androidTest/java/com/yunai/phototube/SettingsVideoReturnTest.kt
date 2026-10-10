package com.yunai.phototube

import androidx.compose.ui.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings route with bundled demo video and synthetic app data only. */
class SettingsVideoReturnTest : RemoteAppTest() {
    private fun openSettingsAtVideo() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("视频预览").performScrollTo().assertIsDisplayed()
    }
    @Test fun closeAndSystemBackReturnToTheSameSettingsPosition() {
        openSettingsAtVideo()
        val before=rule.onNodeWithText("视频预览").fetchSemanticsNode().boundsInWindow
        for(systemBack in listOf(false,true)) {
            rule.onNodeWithText("视频预览").performClick()
            waitTag("video-root")
            rule.onNodeWithTag("video-preview-title").assertIsDisplayed()
            if(!systemBack) {
                val bitmap=requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                try {
                    java.io.File(app.getExternalFilesDir(null),"mock-video-preview-labels.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                    }
                } finally {bitmap.recycle()}
            }
            if(systemBack) androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
            else rule.onNodeWithContentDescription("关闭视频").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("video-root").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithTag("settings-screen").assertIsDisplayed()
            rule.onNodeWithContentDescription("返回相册").assertIsDisplayed()
            assertEquals(before,rule.onNodeWithText("视频预览").fetchSemanticsNode().boundsInWindow)
        }
    }
    @Test fun recreationKeepsVideoAndItsSettingsDestination() {
        openSettingsAtVideo()
        val before=rule.onNodeWithText("视频预览").fetchSemanticsNode().boundsInWindow
        rule.onNodeWithText("视频预览").performClick()
        waitTag("video-root")
        rule.activityRule.scenario.recreate()
        waitTag("video-root")
        rule.onNodeWithContentDescription("关闭视频").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("video-root").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("settings-screen").assertIsDisplayed()
        rule.onNodeWithText("视频预览").assertIsDisplayed()
        assertEquals(before,rule.onNodeWithText("视频预览").fetchSemanticsNode().boundsInWindow)
    }
}
