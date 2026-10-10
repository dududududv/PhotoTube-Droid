package com.yunai.phototube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.media.mediaBounds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MediaBoundsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bundledIdsReleaseCoordinatesOnReuseAndRemoval() {
        val bounds = mutableMapOf<Int, Rect>()
        val id = mutableStateOf(1)
        val shown = mutableStateOf(true)
        rule.setContent {
            if (shown.value) Box(Modifier.size(80.dp).mediaBounds(id.value, bounds))
        }
        rule.runOnIdle { assertNotNull(bounds[1]); id.value=2 }
        rule.runOnIdle { assertNull(bounds[1]); assertNotNull(bounds[2]); shown.value=false }
        rule.runOnIdle { assertTrue(bounds.isEmpty()) }
    }

    @Test fun longListReleasesOldCoordinatesAndRebuildsThemOnReturn() {
        val bounds = mutableMapOf<String, Rect>()
        val shown = mutableStateOf(true)
        rule.setContent {
            if (shown.value) LazyColumn(Modifier.size(300.dp, 400.dp).testTag("list")) {
                items(100_000, key = { it }) { index ->
                    Box(Modifier.height(80.dp).mediaBounds("$index", bounds))
                }
            }
        }
        rule.runOnIdle { assertNotNull(bounds["0"]) }
        // Cross many independent windows: an ever-growing coordinate map fails this check.
        for (index in 1..30) {
            rule.onNodeWithTag("list").performScrollToIndex(index * 100)
            rule.runOnIdle {
                assertNotNull(bounds["${index * 100}"])
                assertTrue("Retained ${bounds.size} coordinates", bounds.size < 30)
                assertNull(bounds["0"])
            }
        }
        rule.onNodeWithTag("list").performScrollToIndex(0)
        rule.runOnIdle {
            assertNotNull(bounds["0"])
            assertNull(bounds["3000"])
            shown.value = false
        }
        rule.runOnIdle { assertTrue(bounds.isEmpty()) }
    }
}
