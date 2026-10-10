package com.yunai.phototube

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.RemoteAlbum
import com.yunai.phototube.ui.remote.AlbumCoverPlaceholder
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Generated empty album only. Checks the visible handoff, not just matching text semantics. */
class AlbumPlaceholderTest {
    @get:Rule val rule=createComposeRule()
    @Test fun collapsedPlaceholderMatchesGridColorAndTextSize() {
        val album=RemoteAlbum("empty-fixture","合成空相册","NORMAL","TAKEN_AT_DESC",0,null,.5f,.5f,JSONObject())
        rule.setContent {PhotoTubeTheme {
            val sourceWidth=with(LocalDensity.current) {180.dp.toPx()}
            Column {
                AlbumCoverPlaceholder(album,Modifier.size(180.dp).testTag("reference-cover"))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.size(180.dp).testTag("collapsed-cover")) {
                    AlbumCoverPlaceholder(album,Modifier.wrapContentSize(androidx.compose.ui.Alignment.TopStart,unbounded=true).requiredSize(360.dp)
                        .graphicsLayer {transformOrigin=TransformOrigin(0f,0f);scaleX=.5f;scaleY=.5f},sourceWidth) {0f}
                }
            }
        }}
        fun ink(tag:String):IntArray {
            val pixels=rule.onNodeWithTag(tag).captureToImage().toPixelMap()
            val background=pixels[4,4]
            var minX=pixels.width;var minY=pixels.height;var maxX=-1;var maxY=-1;var count=0
            for(y in 0 until pixels.height) for(x in 0 until pixels.width) {
                val color=pixels[x,y]
                if(abs(color.red-background.red)+abs(color.green-background.green)+abs(color.blue-background.blue)>.2f) {
                    minX=minOf(minX,x);minY=minOf(minY,y);maxX=maxOf(maxX,x);maxY=maxOf(maxY,y);count++
                }
            }
            assertTrue("Placeholder must actually draw readable glyphs",count>30)
            return intArrayOf(minX,minY,maxX,maxY,count)
        }
        val reference=ink("reference-cover");val collapsed=ink("collapsed-cover")
        for(i in 0..3) assertEquals("Glyph bounds $i must match at the handoff",reference[i].toFloat(),collapsed[i].toFloat(),2f)
        val a=rule.onNodeWithTag("reference-cover").captureToImage().toPixelMap()[4,4]
        val b=rule.onNodeWithTag("collapsed-cover").captureToImage().toPixelMap()[4,4]
        assertEquals(a.red,b.red,.01f);assertEquals(a.green,b.green,.01f);assertEquals(a.blue,b.blue,.01f)
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for(tag in listOf("reference-cover","collapsed-cover")) {
            java.io.File(context.getExternalFilesDir(null),"mock-album-empty-$tag.png").outputStream().use {
                rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }
}
