package com.yunai.phototube

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/** Local synthetic album and bundled photos, with real dialog font scaling. */
class AlbumPickerLayoutTest : RemoteAppTest() {
    override val fontScaleFixture=2f
    override val photographicFixtures=true
    @Test fun selectionRemainsUsableInLargeTextLandscape() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithTag("album-restore-album").performScrollTo().performClick();waitTag("album-grid")
        rule.onNodeWithText("添加").performClick();waitTag("album-member-grid")
        try {
            rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            waitTag("album-member-grid")
            val layouts=mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("添加 0 项",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertEquals(2f,layouts.single().layoutInput.density.fontScale,.01f)
            rule.onNodeWithTag("album-member-grid").assertHeightIsAtLeast(120.dp)
            rule.onNodeWithTag("album-member-grid").performScrollToNode(hasTestTag("member-select-restore-2"))
            rule.onNodeWithTag("member-select-restore-2").performClick().assertIsSelected()
            rule.onNodeWithText("添加 1 项").assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithContentDescription("返回").assertIsDisplayed()
            auditShot("album-picker-large-landscape")
        } finally {
            rule.runOnUiThread {rule.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            rule.waitUntil(15000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        }
    }
}
