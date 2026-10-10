package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/** Full mock UI at a locally injected 2x font scale; no system setting or private data. */
class NavigationLargeFontTest : RemoteAppTest() {
    override val fontScaleFixture = 2f
    @Test fun expandedLabelsFitAndSearchKeepsMatchingHeight() {
        rule.onNodeWithTag("bottom-tabs").assertHeightIsAtLeast(64.dp)
        val expandedHeight=rule.onNodeWithTag("bottom-tabs").fetchSemanticsNode().boundsInRoot.height
        assertEquals(expandedHeight,rule.onNodeWithTag("search-button").fetchSemanticsNode().boundsInRoot.height,1f)
        for (label in listOf("图库","相册")) {
            // The later duplicate row is an invisible backdrop capture, not the interactive label.
            val node=rule.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag("bottom-tabs")),useUnmergedTree=true).onFirst()
            node.assertIsDisplayed()
            val layouts=mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertTrue(layouts.isNotEmpty())
            assertFalse("$label overflow: size=${layouts.first().size}, bottom=${layouts.first().getLineBottom(0)}, widthOverflow=${layouts.first().didOverflowWidth}, heightOverflow=${layouts.first().didOverflowHeight}",layouts.first().hasVisualOverflow)
            assertTrue(node.fetchSemanticsNode().boundsInRoot.height >= layouts.first().size.height - 1f)
        }
        rule.onNodeWithTag("remote-photo-wall").performTouchInput {swipeUp(durationMillis=600)}
        rule.waitForIdle()
        rule.onNodeWithTag("bottom-tabs").assertHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("search-button").assertHeightIsEqualTo(48.dp)
        rule.onNodeWithContentDescription("相册标签").performClick().assertIsSelected()
        assertEquals(expandedHeight,rule.onNodeWithTag("bottom-tabs").fetchSemanticsNode().boundsInRoot.height,1f)
    }
}
