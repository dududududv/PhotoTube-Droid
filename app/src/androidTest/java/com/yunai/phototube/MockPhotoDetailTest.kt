package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real app entry, in-process LocalMockData and bundled sample images only. */
class MockPhotoDetailTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(15000) {
        rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
    private fun currentPhoto(name: String) = hasTestTag("media-viewer") and
        SemanticsMatcher("Current photo is $name") {
            it.config.getOrNull(SemanticsProperties.StateDescription)?.endsWith(" $name") == true
        }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val inst = InstrumentationRegistry.getInstrumentation()
        UiDevice.getInstance(inst).takeScreenshot(File(inst.targetContext.getExternalFilesDir(null), "mock-detail-$name.png"))
    }
    @Test fun openSwipeInspectCloseAndReopenBundledPhotos() {
        waitFor(hasTestTag("asset-mock-0"))
        rule.onNodeWithTag("asset-mock-0").performClick()
        waitFor(hasText("风景")) // Metadata arrived: this previously crashed composition.
        waitFor(hasContentDescription("关闭照片"))
        rule.onNode(currentPhoto("山间.jpg")).assertIsDisplayed()
        screenshot("opened")
        rule.onNodeWithTag("media-pager").performTouchInput { swipeLeft() }
        waitFor(currentPhoto("湖畔.jpg"))
        rule.onNodeWithContentDescription("照片与视频信息").performClick()
        rule.onNodeWithTag("media-info").assertIsDisplayed()
        screenshot("info")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("asset-mock-1").performClick()
        waitFor(hasText("风景"))
        waitFor(hasContentDescription("关闭照片"))
        rule.onNode(currentPhoto("湖畔.jpg")).assertIsDisplayed()
        screenshot("reopened")
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
    }
}
