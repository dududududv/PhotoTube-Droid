package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yunai.phototube.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Only localhost synthetic filter catalogs; no private library queries. */
class WallFilterLayoutTest : RemoteAppTest() {
    @Test fun applyRemainsVisibleWhileConditionsScrollInBothThemes() {
        for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithContentDescription("图库标签").performClick()
            rule.onNodeWithContentDescription("筛选").performClick()
            rule.onNodeWithText("日期、目录与标签").performClick()
            waitTag("apply-wall-filters")
            val action=rule.onNodeWithTag("apply-wall-filters")
            action.assertIsDisplayed().assertIsEnabled()
            val top=action.fetchSemanticsNode().boundsInRoot.top
            rule.onNodeWithText("开始日期").assertIsDisplayed()
            auditShot("wall-conditions-top-${mode.name.lowercase()}")
            rule.onNodeWithTag("wall-filter-conditions").performTouchInput { swipeUp() }
            rule.onNodeWithTag("wall-filter-tags-exclude").assertIsDisplayed()
            rule.onNodeWithText("三组条件同时生效，每组最多 100 个标签。").assertIsDisplayed()
            action.assertIsDisplayed()
            assertEquals(top,action.fetchSemanticsNode().boundsInRoot.top,1f)
            rule.onNodeWithText("重置条件").assertIsDisplayed()
            auditShot("wall-conditions-bottom-${mode.name.lowercase()}")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("apply-wall-filters").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithContentDescription("关闭筛选菜单").performClick()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("filter-menu").fetchSemanticsNodes().isEmpty()}
        }
    }
    @Test fun landscapeKeepsConditionsAndActionsReachable() {
        rule.onNodeWithContentDescription("筛选").performClick()
        rule.onNodeWithText("日期、目录与标签").performClick()
        waitTag("apply-wall-filters")
        try {
            rule.runOnUiThread {rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            waitTag("wall-filter-conditions")
            rule.onNodeWithTag("wall-filter-conditions").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(80f))
            rule.onNodeWithTag("wall-filter-tags-exclude").performScrollTo().assertIsDisplayed()
            rule.onNodeWithTag("apply-wall-filters").performScrollTo().assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithText("重置条件").assertIsDisplayed()
            auditShot("wall-conditions-landscape")
        } finally {
            rule.runOnUiThread {rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
        }
    }

}
