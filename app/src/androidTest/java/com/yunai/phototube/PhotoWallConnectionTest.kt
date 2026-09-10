package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only smoke against the user-confirmed NAS; never supplies credentials. */
@RunWith(AndroidJUnit4::class)
class PhotoWallConnectionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun healthThenLoginOrExistingSession() {
        if(rule.onAllNodesWithTag("server-address").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("连接服务器").performClick()
        }
        rule.waitUntil(20000) {
            rule.onAllNodesWithTag("server-password").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("server-error").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("server-error").assertDoesNotExist()
        rule.onNodeWithTag("filter-button").assertIsDisplayed()
        rule.onNodeWithTag("search-button").assertIsDisplayed()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap=automation.takeScreenshot()!!
        val file=File(rule.activity.getExternalFilesDir(null),"backend-connection.png")
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        automation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/phototube-backend-connection.png").use {ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
    }
}
