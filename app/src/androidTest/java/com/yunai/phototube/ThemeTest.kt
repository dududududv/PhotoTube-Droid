package com.yunai.phototube

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThemeTest : RemoteAppTest() {
    override val photographicFixtures = true
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun appearance() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
    }
    private fun mode(name: String) {
        rule.onNodeWithTag("theme-mode-$name").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("theme-mode-$name") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = automation.takeScreenshot()!!
        val output = File(rule.activity.getExternalFilesDir(null), "$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
    private fun assertBackground(light: Boolean, name: String, heightFraction: Float = .5f) {
        val bitmap = capture(name)
        val pixel = bitmap.getPixel(12, (bitmap.height * heightFraction).toInt())
        val channels = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        assertTrue("$name background must follow theme (RGB=$channels)", if(light) channels.all { it > 220 } else channels.all { it < 40 })
    }
    @Test fun navigationSelectionSurvivesAppThemeChanges() {
        for (theme in listOf("DARK", "LIGHT")) {
            appearance()
            mode(theme)
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithContentDescription("相册标签").assertIsSelected()
            rule.onNodeWithContentDescription("图库标签").assertIsNotSelected().performClick().assertIsSelected()
            rule.onNodeWithContentDescription("相册标签").assertIsNotSelected()
            rule.onNodeWithTag("bottom-tabs").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(60f))
            rule.onNode(hasText("图库") and hasAnyAncestor(hasTestTag("bottom-tabs"))).assertIsDisplayed()
            capture("navigation-expanded-$theme").recycle()
            if (theme == "DARK") {
                rule.onNodeWithTag("remote-photo-wall").performTouchInput {swipeUp(durationMillis=600)}
                rule.waitForIdle()
                rule.onNodeWithTag("bottom-tabs").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(48f))
                capture("navigation-compact-$theme").recycle()
            }
        }
    }

    @Test fun themeAccentPersistenceAndMediaContrast() {
        appearance()
        mode("LIGHT")
        assertBackground(true, "light-appearance")
        rule.onNodeWithTag("theme-accent-PURPLE").performScrollTo().performClick().assertIsSelected()
        capture("light-purple")
        mode("DARK")
        assertBackground(false, "dark-appearance")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag("theme-mode-DARK").assertIsSelected()
        rule.onNodeWithTag("theme-accent-PURPLE").assertIsSelected()
        mode("LIGHT")
        rule.onNodeWithContentDescription("返回设置").performClick()
        assertBackground(true, "light-settings")
        rule.onNodeWithContentDescription("返回相册").performClick()
        assertBackground(true, "light-albums")
        rule.onNodeWithContentDescription("图库标签").performClick()
        assertBackground(true, "light-library")
        rule.onNodeWithContentDescription("筛选").performClick()
        capture("light-filter")
        rule.onNodeWithText("完成").performClick()
        waitTag("asset-restore-0")
        rule.onNodeWithTag("asset-restore-0").performClick()
        assertBackground(false, "light-photo-viewer", .12f)
        rule.onNodeWithContentDescription("关闭照片").assertIsDisplayed().performClick()
        appearance()
        mode("DARK")
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        assertBackground(false, "dark-library")
    }
    @Test fun followsSystemAndExplicitModeOverridesSystem() {
        val original = Regex("\\b(yes|no|auto|custom)\\b").find(shell("cmd uimode night"))!!.value
        try {
            appearance()
            mode("SYSTEM")
            shell("cmd uimode night yes")
            rule.waitForIdle()
            assertBackground(false, "system-dark")
            shell("cmd uimode night no")
            rule.waitForIdle()
            assertBackground(true, "system-light")
            mode("DARK")
            assertBackground(false, "override-dark")
        } finally { shell("cmd uimode night $original") }
    }
}
