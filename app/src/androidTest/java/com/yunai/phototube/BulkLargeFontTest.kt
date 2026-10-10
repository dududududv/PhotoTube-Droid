package com.yunai.phototube

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.Assert.assertEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult

/** Generated local media with injected 2x type; only the isolated mock activity rotates. */
class BulkLargeFontTest : RemoteAppTest() {
    override val fontScaleFixture=2f
    @Test fun landscapeRetainsPhotoViewportAndReachableActions() {
        rule.onNodeWithTag("wall-select").performClick()
        waitTag("bulk-grid")
        try {
            rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            val layouts=mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("完成",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertEquals("Dialog text must actually use the fixture font scale",2f,layouts.single().layoutInput.density.fontScale,.01f)
            rule.onNodeWithTag("bulk-grid").assertHeightIsAtLeast(160.dp)
            rule.onNodeWithTag("bulk-grid").performScrollToNode(hasTestTag("bulk-asset-restore-2"))
            rule.onNodeWithTag("bulk-asset-restore-2").assertIsDisplayed().performClick().assertIsSelected()
            rule.onNodeWithTag("bulk-actions").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithContentDescription("返回").assertIsDisplayed().assertIsEnabled()
            val bitmap=requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                java.io.File(app.getExternalFilesDir(null),"mock-bulk-large-landscape.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            } finally {bitmap.recycle()}
        } finally {
            rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        }
    }
}
