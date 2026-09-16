package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Optional read-only check, using the app's existing session without exposing it. */
class RealFilterTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun existingSessionCanFilter() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("verifyNas")=="true")
        lateinit var vm:PhotoWallViewModel
        rule.runOnIdle {vm=ViewModelProvider(rule.activity)[PhotoWallViewModel::class.java]}
        rule.waitUntil(15000) {!vm.busy && vm.server!=null}
        Assume.assumeTrue(vm.stage==ConnectionStage.READY)
        rule.onNodeWithTag("filter-button").performClick()
        rule.onNodeWithText("媒体类型").performClick()
        rule.onNodeWithText("仅视频").performClick()
        rule.onNodeWithText("完成").performClick()
        rule.waitUntil(15000) {!vm.busy}
        Assert.assertNull(vm.error?.let {"${it.javaClass.simpleName}: ${it.message}"},vm.error)
        Assert.assertNotNull(vm.pageFlow)
        Assert.assertEquals(WallKind.VIDEO,vm.filters.kind)
        rule.waitUntil(15000) {rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        java.io.File(rule.activity.getExternalFilesDir(null),"filter-result.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        runBlocking {
            val api=vm.server!!
            for(filter in listOf(WallFilters(WallKind.VIDEO),WallFilters(WallKind.PHOTO,true),WallFilters(archived=true))) {
                try {
                    api.summary(filter)
                    val page=api.assets(null,filter)
                    Assert.assertTrue(page.items.all {filter.kind==null || it.kind==filter.kind.name})
                    if(filter.favoritesOnly) Assert.assertTrue(page.items.all {it.favorite})
                } catch(error:Exception) {throw AssertionError("${filter.label}: ${error.javaClass.simpleName}: ${error.message}")}
            }
        }
    }
}
