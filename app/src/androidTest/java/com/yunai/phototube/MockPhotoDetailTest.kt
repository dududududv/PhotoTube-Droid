package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import java.io.File

/** Test-only injected gallery and bundled sample images. */
class MockPhotoDetailTest : LocalFixtureAppTest() {
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
    @Test fun informationAndTagsShareOnePanelAndReturnInOrder() {
        waitFor(hasTestTag("asset-mock-0"))
        rule.onNodeWithTag("asset-mock-0").performClick()
        waitFor(hasText("风景"))
        waitFor(hasContentDescription("照片与视频信息"))
        rule.onNodeWithContentDescription("照片与视频信息").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("media-info").assertIsDisplayed()
        rule.onNodeWithTag("media-filmstrip").assertDoesNotExist()
        rule.onNodeWithContentDescription("关闭照片").assertDoesNotExist()
        rule.onNodeWithTag("detail-actions").assertIsDisplayed()
        rule.onNodeWithText("照片信息").assertDoesNotExist()
        rule.onNodeWithContentDescription("关闭信息面板").assertDoesNotExist()
        screenshot("panel-info")
        rule.onNodeWithText("编辑标签").performClick()
        rule.onNodeWithTag("photo-tags-panel").assertIsDisplayed()
        screenshot("panel-tags")
        rule.onNodeWithText("添加标签").performClick()
        rule.onNodeWithTag("detail-tag-picker").assertIsDisplayed()
        screenshot("panel-picker")
        rule.onNodeWithTag("media-details-panel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Expand)
        screenshot("panel-expanded")
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressBack()
        rule.onNodeWithTag("photo-tags-panel").assertIsDisplayed()
        device.pressBack()
        rule.onNodeWithTag("media-info").assertIsDisplayed()
        rule.onNodeWithTag("media-details-panel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Collapse)
        rule.waitForIdle()
        rule.onNodeWithTag("media-info").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(center.x, 20f), androidx.compose.ui.geometry.Offset(center.x, 950f), 400)
        }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("media-details-panel").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithContentDescription("关闭照片").assertIsDisplayed()
        rule.onNode(currentPhoto("山间.jpg")).assertIsDisplayed()
    }

}
