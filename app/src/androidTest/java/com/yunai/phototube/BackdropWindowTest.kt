package com.yunai.phototube

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.*
import com.kyant.backdrop.drawBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic color bands only. No network, account, external media, or stored screenshots. */
class BackdropWindowTest {
    @get:Rule val rule = createComposeRule()
    @Test fun popupSamplesScreenAlignedBandInsteadOfItsOwnWindowOrigin() {
        var center = Offset.Zero
        rule.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    drawRect(Color.Blue)
                    drawRect(Color.Red, topLeft = Offset(0f, size.height / 2), size = Size(size.width, size.height / 2))
                }
                val position = remember { object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset(80, windowSize.height * 3 / 4)
                } }
                Popup(position, properties = PopupProperties(focusable = true)) {
                    Box(Modifier.size(80.dp).onGloballyPositioned {
                        center = it.localToScreen(Offset(it.size.width / 2f, it.size.height / 2f))
                    }.drawBackdrop(backdrop, shape = { androidx.compose.ui.graphics.RectangleShape }, effects = {}, highlight = null, shadow = null))
                }
            }
        }
        rule.waitForIdle()
        android.os.SystemClock.sleep(350)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val pixel = bitmap.getPixel(center.x.toInt(), center.y.toInt())
        bitmap.recycle()
        assertTrue("Popup must sample red lower screen band; got ${Integer.toHexString(pixel)}", android.graphics.Color.red(pixel) > 220 && android.graphics.Color.blue(pixel) < 40)
    }
}
