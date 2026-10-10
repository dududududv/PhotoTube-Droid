package com.yunai.phototube

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.ui.PhotoTubeContent
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.*
import org.junit.Test

/** Synthetic API records and bundled sample photographs only. */
class SearchSurfaceTest : RemoteAppTest() {
    override val photographicFixtures = true
    @Test fun lightSearchOverPhotographs() = exercise("search-photo-light")
    @Test fun darkSearchOverPhotographs() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("theme-mode-DARK") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithContentDescription("图库标签").performClick()
        exercise("search-photo-dark")
    }
    @Test fun narrowSearchWithLargeTypeKeepsInputAndActionsAccessible() {
        val model=wall
        rule.runOnUiThread {UiTestActivity.content={PhotoTubeTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Box(Modifier.width(280.dp).fillMaxHeight()) {PhotoTubeContent(model)}
            }
        }}}
        rule.waitForIdle()
        exercise("search-photo-narrow-large")
    }
    private fun exercise(name:String) {
        rule.onNodeWithTag("search-button").performClick()
        rule.onNodeWithTag("search-input").performTextInput("restore")
        rule.waitUntil(10000) {!wall.busy && wall.filters.keyword=="restore"}
        waitTag("asset-restore-0")
        rule.onNodeWithTag("search-input").performImeAction()
        rule.waitUntil(5000) {rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==false}
        rule.waitForIdle()
        rule.onNodeWithTag("search-input").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("清除搜索").assertIsDisplayed()
        val layouts=mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("取消").assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.isNotEmpty())
        val layout=layouts.first()
        assertFalse("Cancel height overflow: ${layout.size}, ${layout.getLineBottom(0)}",layout.didOverflowHeight)
        assertTrue("Cancel must remain one line",layout.lineCount==1)
        assertTrue("Cancel width overflow: ${layout.size}, ${layout.getLineRight(0)}",layout.getLineRight(0)<=layout.size.width+1f)
        val inst=InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(inst.uiAutomation.takeScreenshot())
        try {java.io.File(app.getExternalFilesDir(null),"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
        rule.onNodeWithContentDescription("清除搜索").performClick()
        rule.onNodeWithTag("search-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        rule.onNodeWithText("取消").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("search-button").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("图库标签").assertIsSelected()
    }
}
