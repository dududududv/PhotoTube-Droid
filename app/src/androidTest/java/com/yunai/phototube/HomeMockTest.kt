package com.yunai.phototube

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeMockTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        // Window enter/exit animations run outside the Compose test clock.
        if (name.startsWith("settings")) android.os.SystemClock.sleep(400)
        val bitmap = if (name.startsWith("settings") || name == "search-keyboard")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        else rule.onRoot().captureToImage().asAndroidBitmap()
        val output = File(rule.activity.getExternalFilesDir(null), "$name.png")
        output.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Keep QA artifacts after the Gradle runner uninstalls the test app.
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("mkdir -p /sdcard/Download/phototube-ui").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        automation.executeShellCommand("cp ${output.absolutePath} /sdcard/Download/phototube-ui/$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    @Test fun navigationGeometryAndDrag() {
        val tabs = rule.onNodeWithTag("bottom-tabs")
        val search = rule.onNodeWithTag("search-button")
        tabs.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
        search.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
        rule.onNodeWithTag("filter-button").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
        rule.onNodeWithTag("profile-button").assertDoesNotExist()
        rule.onNodeWithTag("settings-button").assertDoesNotExist()
        capture("home")
        rule.onNodeWithContentDescription("走过老街").performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        tabs.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
        search.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
        capture("collapsed")
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        tabs.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
        search.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
        capture("expanded")
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(durationMillis = 600) }
        rule.waitForIdle()
        tabs.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
        // The narrowed capsule must still use the current tab width during a drag.
        tabs.performTouchInput { swipe(start = centerLeft + androidx.compose.ui.geometry.Offset(20f, 0f), end = centerRight - androidx.compose.ui.geometry.Offset(20f, 0f), durationMillis = 600) }
        rule.onNodeWithTag("settings-button").assertIsDisplayed()
        rule.onNodeWithTag("filter-button").assertDoesNotExist()
        rule.onNodeWithText("我的相册").assertIsDisplayed()
        listOf("山野之间", "漫步日常", "花与风").forEach { name ->
            val bounds = rule.onNodeWithTag("album-cover-$name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertEquals(bounds.width, bounds.height, 1f)
        }
        capture("albums")
        rule.onNodeWithContentDescription("图库标签").performClick()
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeDown(durationMillis = 600) }
        rule.waitForIdle()
        tabs.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
        search.assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
        capture("expanded")
    }

    @Test fun filteringSearchAndGlassSettings() {
        rule.onNodeWithContentDescription("筛选").performClick()
        rule.onNodeWithText("只看收藏").performClick()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithText("我的收藏").assertIsDisplayed()
        rule.onNodeWithContentDescription("走过老街").assertDoesNotExist()
        rule.onNodeWithContentDescription("筛选").performClick()
        rule.onNodeWithText("只看收藏").performClick()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithTag("search-button").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("老街")
        capture("search-keyboard")
        rule.onNodeWithTag("search-input").performImeAction()
        rule.onNodeWithContentDescription("走过老街").assertIsDisplayed()
        rule.onNodeWithContentDescription("山间来信").assertDoesNotExist()
        capture("search-dock")
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithText("搜索相册").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("花")
        rule.onNodeWithTag("search-input").performImeAction()
        rule.onNodeWithText("花与风").assertIsDisplayed()
        rule.onNodeWithText("山野之间").assertDoesNotExist()
        capture("album-search")
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        rule.onNodeWithContentDescription("走过老街").assertIsDisplayed()
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("appearance-opacity").performScrollTo()
        rule.onNodeWithTag("appearance-opacity").performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
        capture("settings")
    }
    @Test fun settingsStateAndBackNavigation() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("触感反馈"))).assertIsOn().performClick().assertIsOff()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("只看收藏"))).performClick().assertIsOn()
        capture("settings")
        rule.onNodeWithTag("settings-list").performScrollToNode(hasText("视频预览"))
        capture("settings-scrolled")
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        rule.onNodeWithText("我的收藏").assertIsDisplayed()
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNode(isToggleable() and hasAnyAncestor(hasContentDescription("触感反馈"))).assertIsOff()
        rule.onNodeWithContentDescription("返回相册").performClick()
    }

    @Test fun appearanceSliderThumbFollowsValueAndDrag() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        val slider = rule.onNodeWithTag("appearance-opacity").performScrollTo()
        val thumb = rule.onNode(hasTestTag("liquid-slider-thumb") and hasAnyAncestor(hasTestTag("appearance-opacity")), useUnmergedTree = true)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.15f) }
        rule.waitForIdle()
        val left = thumb.fetchSemanticsNode().boundsInRoot.center.x
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.65f) }
        rule.waitForIdle()
        val right = thumb.fetchSemanticsNode().boundsInRoot.center.x
        org.junit.Assert.assertTrue("Thumb must follow external value", right - left > 100f)
        thumb.performTouchInput { swipe(center, center - androidx.compose.ui.geometry.Offset(150f, 0f), 500) }
        rule.waitForIdle()
        org.junit.Assert.assertTrue("Dragging must move thumb", thumb.fetchSemanticsNode().boundsInRoot.center.x < right - 50f)
        capture("settings-appearance")
        rule.onNodeWithContentDescription("色散").performScrollTo()
        rule.onNode(isToggleable()).performTouchInput { click() }
        rule.onNode(isToggleable()).assertIsOn()
        rule.onNode(isToggleable()).performTouchInput { click() }
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText("恢复默认玻璃效果").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        slider.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(.4f, .1f.. .7f)))
    }

    @Test fun filterMenuCascadesAndDismisses() {
        rule.onNodeWithContentDescription("筛选").performClick()
        rule.onNodeWithTag("filter-menu").assertIsDisplayed()
        capture("filter-menu")
        rule.onNodeWithText("排列顺序").performClick()
        rule.onNodeWithText("最新在前").assertIsDisplayed()
        capture("filter-sort-menu")
        rule.onNodeWithText("最早在前").performClick()
        rule.onNodeWithText("最早",substring=false).assertIsDisplayed()
        rule.onNodeWithText("排列顺序").performClick()
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithText("只看收藏").assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭筛选菜单").performClick()
        rule.onNodeWithTag("filter-menu").assertDoesNotExist()
        rule.onNodeWithContentDescription("筛选").assertIsDisplayed()
    }

    @Test fun searchDockClearsAndBackDismisses() {
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("不存在的照片")
        rule.onNodeWithText("没有找到照片").assertIsDisplayed()
        rule.onNodeWithContentDescription("清除搜索").performClick()
        rule.onNodeWithContentDescription("山间来信").assertExists()
        rule.onNodeWithTag("search-input").performImeAction()
        rule.onNodeWithTag("search-dock").assertIsDisplayed()
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithTag("search-button").assertIsDisplayed()
        rule.onNodeWithContentDescription("图库标签").assertIsDisplayed()
    }

    @Test fun searchRestoresScrollPosition() {
        rule.onNodeWithContentDescription("走过老街").performTouchInput {swipeUp(durationMillis=600)}
        fun offset()=rule.onNode(hasScrollToIndexAction()).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
        val before=offset()
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("花")
        rule.onNodeWithText("取消").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(before,offset(),1f)
        rule.onNodeWithTag("search-button").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
    }

    @Test fun searchFollowsKeyboardAndBackClosesInStages() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=automation.executeShellCommand(command).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()}
        val original=shell("settings get secure show_ime_with_hard_keyboard")
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            rule.onNodeWithTag("search-button").performClick()
            rule.waitUntil(10000) {rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true}
            rule.onNodeWithTag("search-input").performTextInput("老街")
            rule.onNodeWithTag("search-dock").assertIsDisplayed()
            capture("search-keyboard")
            val device=androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            device.pressBack()
            rule.waitUntil(5000) {rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==false}
            rule.onNodeWithTag("search-dock").assertIsDisplayed()
            device.pressBack()
            rule.onNodeWithTag("search-button").assertIsDisplayed()
        } finally {
            shell(if(original=="null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $original")
        }
    }

}
