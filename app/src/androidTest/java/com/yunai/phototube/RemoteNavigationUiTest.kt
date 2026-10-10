package com.yunai.phototube

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yunai.phototube.data.WallKind
import org.junit.Assert.*
import org.junit.Test

/** Root navigation with local synthetic responses and bundled sample photographs only. */
class RemoteNavigationUiTest : RemoteAppTest() {
    override val photographicFixtures = true
    private fun capture(name: String) {
        rule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            java.io.File(app.getExternalFilesDir(null), "navigation-$name.png"))
    }
    @Test fun scrollResizesBothControlsAndCompactCapsuleStillDragsToAlbums() {
        val tabs = rule.onNodeWithTag("bottom-tabs")
        val search = rule.onNodeWithTag("search-button")
        tabs.assertHeightIsEqualTo(60.dp); search.assertHeightIsEqualTo(60.dp)
        rule.onNodeWithTag("filter-button").assertHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("profile-button").assertDoesNotExist()
        rule.onNodeWithTag("settings-button").assertDoesNotExist()
        rule.onNodeWithTag("remote-photo-wall").performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        tabs.assertHeightIsEqualTo(48.dp); search.assertHeightIsEqualTo(48.dp)
        capture("compact")
        rule.onNodeWithTag("remote-photo-wall").performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        tabs.assertHeightIsEqualTo(60.dp); search.assertHeightIsEqualTo(60.dp)
        rule.onNodeWithTag("remote-photo-wall").performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        tabs.performTouchInput { swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), 600) }
        waitTag("album-restore-album")
        rule.onNodeWithTag("settings-button").assertIsDisplayed()
        rule.onNodeWithTag("filter-button").assertDoesNotExist()
        val cover = rule.onNodeWithTag("remote-album-cover-restore-album", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(cover.width, cover.height, 1f)
        tabs.assertHeightIsEqualTo(60.dp); search.assertHeightIsEqualTo(60.dp)
        val create = rule.onNodeWithContentDescription("新建相册").fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithTag("settings-button").fetchSemanticsNode().boundsInRoot
        assertTrue("Create action must not overlap settings", create.right <= settings.left)
        capture("albums")
        rule.onNodeWithContentDescription("图库标签").performClick()
        rule.onNodeWithTag("filter-button").assertIsDisplayed()
        tabs.assertHeightIsEqualTo(60.dp)
    }
    @Test fun cancelledTabDragKeepsPageAndNextDragStillWorks() {
        val tabs=rule.onNodeWithTag("bottom-tabs")
        tabs.performTouchInput {
            down(Offset(width*.25f,centerY))
            moveTo(Offset(width*.9f,centerY),300)
            cancel()
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("图库标签").assertIsSelected()
        rule.onNodeWithTag("remote-photo-wall").assertIsDisplayed()
        tabs.performTouchInput {swipe(Offset(width*.25f,centerY),Offset(width*.9f,centerY),500)}
        rule.onNodeWithContentDescription("相册标签").assertIsSelected()
        waitTag("album-restore-album")
    }

    @Test fun settingsPreserveTogglesWhenReturningFromLibrary() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("触感反馈"))).assertIsOn().performClick().assertIsOff()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("只看收藏"))).performClick().assertIsOn()
        assertEquals(true, wall.filters.favorite)
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        rule.onNodeWithTag("filter-button").assertIsDisplayed()
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("触感反馈"))).assertIsOff()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("只看收藏"))).assertIsOn()
    }
    @Test fun glassSliderThumbTracksExternalValueDragAndReset() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithText("恢复默认玻璃效果").performScrollTo().performClick()
        val slider = rule.onNodeWithTag("appearance-opacity").performScrollTo()
        val thumb = rule.onNode(hasTestTag("liquid-slider-thumb") and hasAnyAncestor(hasTestTag("appearance-opacity")), useUnmergedTree = true)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.15f) }
        rule.waitForIdle()
        val left = thumb.fetchSemanticsNode().boundsInRoot.center.x
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.65f) }
        rule.waitForIdle()
        val right = thumb.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("Thumb follows the external value", right - left > 100f)
        thumb.performTouchInput { swipe(center, center - Offset(150f, 0f), 500) }
        rule.waitForIdle()
        assertTrue("Dragging moves the thumb", thumb.fetchSemanticsNode().boundsInRoot.center.x < right - 50f)
        rule.onNodeWithContentDescription("色散").performScrollTo()
        rule.onNode(isToggleable()).performTouchInput { click() }
        rule.onNode(isToggleable()).assertIsOn()
        rule.onNode(isToggleable()).performTouchInput { click() }
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText("恢复默认玻璃效果").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.4f, .1f.. .7f)))
        capture("glass-settings")
    }
    @Test fun filterGrowsFromButtonAndCascadesWithoutDismissingRoot() = exerciseFilter("filter")

    @Test fun darkFilterRetainsReadableActionsAndCascade() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("theme-mode-DARK") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        exerciseFilter("filter-dark")
    }

    private fun exerciseFilter(captureName:String) {
        val heading = rule.onNodeWithTag("wall-heading").fetchSemanticsNode().boundsInRoot
        val source = rule.onNodeWithTag("filter-button").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithContentDescription("筛选").performClick()
        val menu = rule.onNodeWithTag("filter-menu")
        menu.assertIsDisplayed().assertWidthIsEqualTo(244.dp)
        rule.onNodeWithTag("wall-select").assertDoesNotExist()
        assertEquals(heading, rule.onNodeWithTag("wall-heading").fetchSemanticsNode().boundsInRoot)
        val expanded = menu.fetchSemanticsNode().boundsInRoot
        assertEquals(source.top, expanded.top, 1f)
        assertEquals(source.right, expanded.right, 1f)
        rule.onNodeWithText("媒体类型").performClick()
        rule.onNodeWithText("仅视频").performClick()
        assertEquals(WallKind.VIDEO, wall.filters.kind)
        rule.onNodeWithText("收藏状态").performClick()
        rule.onNodeWithText("只看未收藏").performClick()
        assertEquals(false, wall.filters.favorite)
        rule.onNodeWithText("收藏状态").performClick()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithText("媒体类型").assertIsDisplayed()
        rule.onNodeWithTag("filter-menu").assertIsDisplayed()
        capture(captureName)
        rule.onNodeWithText("清除筛选").performClick()
        assertFalse(wall.filters.active)
        rule.onNodeWithContentDescription("关闭筛选菜单").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("filter-menu").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("filter-button").assertIsDisplayed()
        rule.onNodeWithTag("wall-select").assertIsDisplayed()
    }
}
