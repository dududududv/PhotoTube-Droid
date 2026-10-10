package com.yunai.phototube

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.remote.ThumbnailPlaceholder
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure layout fixture: no files, sessions or network. */
class ThumbnailPlaceholderTest {
    @get:Rule val rule=createComposeRule()
    @Test fun narrowAndShortTilesKeepRetryInsideBoundsAtLargeFont() {
        var retries=0
        rule.setContent {
            val density=LocalDensity.current
            PhotoTubeTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    Column(Modifier.safeDrawingPadding()) {
                        Box(Modifier.size(48.dp,120.dp).testTag("narrow")) {
                            ThumbnailPlaceholder("缩略图暂不可用","narrow") {retries++}
                        }
                        Box(Modifier.size(160.dp,64.dp).testTag("short")) {
                            ThumbnailPlaceholder("缩略图暂不可用","short") {retries++}
                        }
                    }
                }
            }
        }
        listOf("narrow","short").forEach {id ->
            val tile=rule.onNodeWithTag(id).fetchSemanticsNode().boundsInRoot
            val retry=rule.onNodeWithTag("thumbnail-retry-$id")
            retry.assertIsDisplayed().assertHasClickAction()
            val bounds=retry.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left>=tile.left && bounds.right<=tile.right && bounds.top>=tile.top && bounds.bottom<=tile.bottom)
            retry.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        }
        rule.runOnIdle {assertEquals(2,retries)}
    }
}
