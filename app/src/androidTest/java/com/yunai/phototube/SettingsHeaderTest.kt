package com.yunai.phototube

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual settings scrolling, synthetic transport, no accounts or external media. */
class SettingsHeaderTest : RemoteAppTest() {
    @Test fun scrolledNavigationHasReadableBackingInBothThemes() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        for(mode in listOf("LIGHT","DARK")) {
            rule.onNodeWithText("液态玻璃").performScrollTo().performClick()
            rule.onNodeWithTag("theme-mode-$mode").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("theme-mode-$mode") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithText("视频预览").performScrollTo()
            rule.onNodeWithTag("settings-toolbar-title").assertIsDisplayed()
            rule.onNodeWithContentDescription("返回相册").assertIsDisplayed().assertIsEnabled()
            val pixels=rule.onNodeWithTag("settings-toolbar").captureToImage().toPixelMap()
            // Inside the solid backing, clear of the centered label and back button.
            val pixel=pixels[pixels.width*3/4,pixels.height*3/4]
            val brightness=(pixel.red+pixel.green+pixel.blue)/3
            assertTrue("Toolbar backing must follow $mode: $brightness",if(mode=="DARK") brightness<.1f else brightness>.9f)
            val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                java.io.File(app.getExternalFilesDir(null),"mock-settings-header-${mode.lowercase()}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            } finally {bitmap.recycle()}
        }
    }
}
