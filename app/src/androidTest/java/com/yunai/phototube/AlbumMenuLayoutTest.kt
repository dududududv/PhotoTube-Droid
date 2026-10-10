package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real app routes, synthetic records and bundled landscapes only. */
class AlbumMenuLayoutTest : RemoteAppTest() {
    override val photographicFixtures = true
    private var writes = 0
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes++
        return MockResponse().setResponseCode(409)
    }

    @Test fun groupedMenuRetainsDisabledActionAndSafeDismissalInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithTag("album-restore-album").performClick()
            waitTag("album-grid")
            rule.onNodeWithContentDescription("相册选项").performClick()
            waitTag("glass-action-menu")
            rule.onNodeWithText("显示").assertIsDisplayed()
            rule.onNodeWithText("清除自定义封面").performScrollTo().assertIsNotEnabled()
            auditShot("album-menu-groups-${mode.name.lowercase()}")
            rule.onNodeWithText("删除相册").performScrollTo().assertIsDisplayed()
            auditShot("album-menu-delete-${mode.name.lowercase()}")
            rule.onNodeWithText("删除相册").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("取消").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("album-grid").assertExists()
            assertEquals("Cancel must never issue a mutation",0,writes)
            rule.onNodeWithContentDescription("相册选项").performClick()
            rule.onNodeWithText("放大缩略图").performClick()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("相册选项").performClick()
            rule.onNodeWithText("缩小缩略图").assertIsDisplayed()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            rule.waitUntil(10000) { rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty() }
        }
        assertEquals(0,writes)
    }
}
