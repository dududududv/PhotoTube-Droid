package com.yunai.phototube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.ui.media.MediaViewerHeading
import com.yunai.phototube.ui.theme.MediaColors
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pure white generated backdrop, no video/account/network. */
class MediaHeadingContrastTest {
    @get:Rule val rule=createComposeRule()
    @Test fun videoDemoLabelHasReadableContrastOnWhiteBackdrop() {
        rule.setContent {PhotoTubeTheme {
            val backdrop=rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                com.yunai.phototube.ui.VideoPreviewLabel("拖动看看",backdrop,Modifier.testTag("demo-label"))
            }
        }}
        rule.onNodeWithText("拖动看看").assertIsDisplayed()
        val pixels=rule.onNodeWithTag("demo-label").captureToImage().toPixelMap()
        val surface=pixels[pixels.width/2,pixels.height-4].toArgb()
        val contrast=androidx.core.graphics.ColorUtils.calculateContrast(MediaColors.text.toArgb(),surface)
        assertTrue("Demo text contrast was $contrast",contrast>=4.5)
    }
    @Test fun filenameHasReadableContrastOnWhiteBackdrop() {
        rule.setContent {PhotoTubeTheme {
            val backdrop=rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                Box(Modifier.width(280.dp)) {MediaViewerHeading("2026年10月9日","synthetic-bright-video.mp4",backdrop,true)}
            }
        }}
        rule.onNodeWithText("synthetic-bright-video.mp4").assertIsDisplayed()
        val pixels=rule.onNodeWithTag("media-heading").captureToImage().toPixelMap()
        // Bottom central padding is inside the glass but outside glyphs and rounded edge.
        val surface=pixels[pixels.width/2,pixels.height-4].toArgb()
        val contrast=androidx.core.graphics.ColorUtils.calculateContrast(MediaColors.secondary.toArgb(),surface)
        assertTrue("Small filename contrast was $contrast",contrast>=4.5)
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(inst.uiAutomation.takeScreenshot())
        try {
            java.io.File(inst.targetContext.getExternalFilesDir(null),"mock-media-heading-white.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        } finally {bitmap.recycle()}
    }
}
