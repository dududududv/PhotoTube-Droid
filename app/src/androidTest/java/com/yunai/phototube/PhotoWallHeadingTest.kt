package com.yunai.phototube

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.remote.PhotoWallHeading
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic layout only; no media, account or network. */
class PhotoWallHeadingTest {
    @get:Rule val rule = createComposeRule()
    @Test fun largeFontMovesSelectionBelowTitleAndPreservesFilterSpace() {
        var clicks = 0
        rule.setContent {
            val density = LocalDensity.current
            PhotoTubeTheme {
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.safeDrawingPadding().width(284.dp)) {
                        PhotoWallHeading { clicks++ }
                    }
                }
            }
        }
        val title = rule.onNodeWithTag("wall-title").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val action = rule.onNodeWithTag("wall-select").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val button = action.fetchSemanticsNode().boundsInRoot
        val heading = rule.onNodeWithTag("wall-heading").fetchSemanticsNode().boundsInRoot
        assertTrue(button.top >= title.bottom)
        assertTrue(button.right <= heading.right)
        assertTrue(title.right <= heading.right)
        action.performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }
    @Test fun hiddenSelectionPreservesHeadingGeometryAndRestoresInteraction() {
        val visible = mutableStateOf(true)
        var clicks = 0
        rule.setContent {
            PhotoTubeTheme {
                Box(Modifier.safeDrawingPadding().width(284.dp)) {
                    PhotoWallHeading(actionVisible=visible.value) { clicks++ }
                }
            }
        }
        val before = rule.onNodeWithTag("wall-heading").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { visible.value = false }
        rule.onNodeWithTag("wall-select").assertDoesNotExist()
        assertEquals(before, rule.onNodeWithTag("wall-heading").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithTag("wall-select").assertIsDisplayed().performClick()
        assertEquals(before, rule.onNodeWithTag("wall-heading").fetchSemanticsNode().boundsInRoot)
        rule.runOnIdle { assertEquals(1, clicks) }
    }

}
