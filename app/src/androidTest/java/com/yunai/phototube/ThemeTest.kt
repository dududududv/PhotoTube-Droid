package com.yunai.phototube

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    @After fun resetPreferences() {
        rule.activity.getSharedPreferences("appearance", 0).edit().clear().commit()
        rule.activity.getSystemService(android.app.UiModeManager::class.java).setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO)
    }
    private fun appearance() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
    }
    private fun mode(name: String) = rule.onNodeWithTag("theme-mode-$name").performScrollTo().performClick().assertIsSelected()
    private fun capture(name: String): Bitmap {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = automation.takeScreenshot()!!
        val output = File(rule.activity.getExternalFilesDir(null), "$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shell("mkdir -p /sdcard/Download/phototube-theme")
        shell("cp ${output.absolutePath} /sdcard/Download/phototube-theme/$name.png")
        return bitmap
    }
    private fun assertBackground(light: Boolean, name: String) {
        val bitmap = capture(name)
        val channel = Color.red(bitmap.getPixel(12, bitmap.height / 2))
        assertTrue("$name background must follow theme (red=$channel)", if(light) channel > 220 else channel < 40)
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
        rule.onNodeWithContentDescription("山间来信").performClick()
        assertBackground(false, "light-photo-viewer")
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
