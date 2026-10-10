package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class BulkRouteRestoreTest : RemoteAppTest() {
    private val mutationAttempts = java.util.concurrent.atomic.AtomicInteger()
    private val writes = CopyOnWriteArrayList<JSONObject>()
    private val firstPageReads=java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var succeedSelection=false
    override fun readFixture(request:RecordedRequest):MockResponse? {
        if(request.requestUrl!!.encodedPath=="/api/v1/assets" && request.requestUrl!!.queryParameter("cursor")==null) firstPageReads.incrementAndGet()
        return null
    }
    @Test fun openingSelectionReusesHomePageWithoutFetchingFirstPageAgain() {
        rule.onNodeWithTag("wall-select").assertIsDisplayed().assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        val before=firstPageReads.get()
        assertTrue(before>0)
        open()
        rule.waitForIdle()
        assertEquals("Entering selection must reuse the cached home generation",before,firstPageReads.get())
        rule.onNodeWithTag("bulk-month-2026-09").assertExists()
        rule.onNodeWithTag("bulk-done").assertIsDisplayed()
        rule.onNodeWithText("已选 0 项").assertIsDisplayed()
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("选择范围").performClick()
        rule.onNodeWithText("选择全部匹配项").assertIsDisplayed()
        rule.onNodeWithText("选择已加载").assertIsDisplayed()
        rule.onNodeWithText("取消",substring=false).performClick()
        assertEquals(0,mutationAttempts.get())
    }


    @Test fun enteringAndLeavingSelectionKeepsFirstPhotoPosition() {
        val before=rule.onNodeWithTag("asset-restore-0").fetchSemanticsNode().boundsInWindow
        open()
        rule.waitForIdle()
        val selecting=rule.onNodeWithTag("bulk-asset-restore-0").fetchSemanticsNode().boundsInWindow
        assertEquals("First photo top must not move",before.top,selecting.top,1f)
        assertEquals(before.left,selecting.left,1f)
        assertEquals(before.width,selecting.width,1f)
        assertEquals(before.height,selecting.height,1f)
        val bitmap=requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            java.io.File(app.getExternalFilesDir(null),"mock-bulk-aligned.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        } finally {bitmap.recycle()}

        rule.onNodeWithTag("bulk-done").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty()}
        val after=rule.onNodeWithTag("asset-restore-0").fetchSemanticsNode().boundsInWindow
        assertEquals(before,after)
        assertEquals(0,mutationAttempts.get())
    }

    @Test fun selectionTransitionDoesNotScaleOrMoveThePhotoWall() {
        val source=rule.onNodeWithTag("asset-restore-0").fetchSemanticsNode().boundsInWindow
        rule.mainClock.autoAdvance=false
        try {
            rule.onNodeWithTag("wall-select").performClick()
            rule.mainClock.advanceTimeByFrame()
            rule.waitUntil(10000) {
                rule.mainClock.advanceTimeByFrame()
                rule.onAllNodesWithTag("bulk-asset-restore-0").fetchSemanticsNodes().isNotEmpty()
            }
            fun assertSourcePosition() {
                val current=rule.onNodeWithTag("bulk-asset-restore-0").fetchSemanticsNode().boundsInWindow
                assertEquals(source.left,current.left,1f)
                assertEquals(source.top,current.top,1f)
                assertEquals(source.width,current.width,1f)
                assertEquals(source.height,current.height,1f)
            }
            for(step in listOf(16L,32L,64L,96L,160L)) {
                rule.mainClock.advanceTimeBy(step)
                rule.waitForIdle()
                assertSourcePosition()
            }
            rule.onNodeWithTag("bulk-done").performClick()
            for(step in listOf(16L,32L,64L)) {
                rule.mainClock.advanceTimeBy(step)
                rule.waitForIdle()
                assertSourcePosition()
            }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.onNodeWithTag("bulk-grid").assertDoesNotExist()
            assertEquals(source,rule.onNodeWithTag("asset-restore-0").fetchSemanticsNode().boundsInWindow)
        } finally {rule.mainClock.autoAdvance=true}
    }

    @Test fun longPressSelectsScrolledPhotoWithoutOpeningViewerOrMovingIt() {
        rule.onNodeWithTag("remote-photo-wall").performScrollToNode(hasTestTag("asset-restore-120"))
        val source=rule.onNodeWithTag("asset-restore-120").fetchSemanticsNode().boundsInWindow
        val sourceLayout=rule.onNodeWithTag("asset-restore-120").getUnclippedBoundsInRoot()
        rule.onNodeWithTag("asset-restore-120").performTouchInput {longClick()}
        waitTag("bulk-asset-restore-120")
        rule.onNodeWithTag("bulk-asset-restore-120").assertIsSelected()
        rule.onNodeWithTag("media-viewer").assertDoesNotExist()
        val selected=rule.onNodeWithTag("bulk-asset-restore-120").fetchSemanticsNode().boundsInWindow
        assertEquals(source.left,selected.left,1f)
        assertEquals(source.top,selected.top,1f)
        assertEquals(source.width,selected.width,1f)
        // Floating controls must not truncate the source row at their upper edge.
        assertEquals(source.height,selected.height,1f)
        val footer=rule.onNodeWithTag("bulk-footer").fetchSemanticsNode().boundsInWindow
        val viewport=rule.onNodeWithTag("bulk-grid").fetchSemanticsNode().boundsInWindow
        assertTrue("Photos continue beneath the floating controls",viewport.bottom>footer.top)
        // Layout size remains independent of viewport clipping.
        val selectedLayout=rule.onNodeWithTag("bulk-asset-restore-120").getUnclippedBoundsInRoot()
        assertEquals((sourceLayout.bottom-sourceLayout.top).value,(selectedLayout.bottom-selectedLayout.top).value,.5f)
        assertEquals((sourceLayout.right-sourceLayout.left).value,(selectedLayout.right-selectedLayout.left).value,.5f)
        rule.onNodeWithTag("bulk-actions").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithTag("bulk-done").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty()}
        assertEquals(source,rule.onNodeWithTag("asset-restore-120").fetchSemanticsNode().boundsInWindow)
        rule.onNodeWithTag("asset-restore-120").performClick()
        waitTag("media-viewer")
        assertEquals(0,mutationAttempts.get())
    }

    @Test fun lastPhotoCanScrollAboveFloatingActions() {
        open()
        rule.onNodeWithTag("bulk-grid").performScrollToNode(hasTestTag("bulk-asset-restore-219"))
        rule.onNodeWithTag("bulk-grid").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX,height*.65f),androidx.compose.ui.geometry.Offset(centerX,height*.15f))
        }
        rule.waitForIdle()
        val photo=rule.onNodeWithTag("bulk-asset-restore-219").fetchSemanticsNode().boundsInWindow
        val footer=rule.onNodeWithTag("bulk-footer").fetchSemanticsNode().boundsInWindow
        auditShot("bulk-floating-last-photo")
        assertTrue("Last photo $photo must scroll clear of footer $footer",photo.bottom<footer.top)
        rule.onNodeWithTag("bulk-asset-restore-219").performTouchInput {click()}
        rule.onNodeWithTag("bulk-asset-restore-219").assertIsSelected()
        assertEquals(0,mutationAttempts.get())
    }

    @Test fun favoriteFromLaterPageReturnsToOriginalPhotoPosition() {
        succeedSelection=true
        rule.onNodeWithTag("remote-photo-wall").performScrollToNode(hasTestTag("asset-restore-120"))
        val source=rule.onNodeWithTag("asset-restore-120").fetchSemanticsNode().boundsInWindow
        val originalFlow=wall.pageFlow
        rule.onNodeWithTag("asset-restore-120").performTouchInput {longClick()}
        waitTag("bulk-asset-restore-120")
        favoriteConfirmation()
        rule.onNodeWithText("确认收藏",substring=false).performClick()
        rule.waitUntil(10000) {writes.size==1 && !wall.busy && rule.onAllNodesWithTag("bulk-result").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("成功 1 项，0 项未完成。").assertExists()
        rule.onNodeWithTag("bulk-done").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithTag("asset-restore-120").assertIsDisplayed()
        assertEquals(source,rule.onNodeWithTag("asset-restore-120").fetchSemanticsNode().boundsInWindow)
        assertSame(originalFlow,wall.pageFlow)
        assertEquals("restore-120",writes.single().getJSONArray("assetIds").getString(0))
    }

    override fun mutationFixture(request: RecordedRequest): MockResponse {
        mutationAttempts.incrementAndGet()
        if (request.method != "POST" || request.requestUrl!!.encodedPath != "/api/v1/assets/favorite") {
            return MockResponse().setResponseCode(405)
        }
        val body = JSONObject(request.body.readUtf8())
        writes += body
        val result = if (body.has("filter")) {
            JSONObject().put("matchedCount", 220).put("succeededCount", 220).put("failedCount", 0)
        } else if(succeedSelection) {
            JSONObject().put("succeeded",body.getJSONArray("assetIds")).put("failed",JSONArray())
        } else {
            JSONObject().put("succeeded", JSONArray().put("restore-0"))
                .put("failed", JSONArray().put(JSONObject().put("target", "restore-2")
                    .put("code", "OFFLINE").put("message", "目录离线")))
        }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(result.toString())
    }

    private fun open() {
        rule.onNodeWithText("选择", substring = false).performClick()
        waitTag("bulk-asset-restore-0")
    }
    private fun favoriteConfirmation() {
        rule.onNodeWithTag("bulk-favorite").performClick()
        rule.onNodeWithText("收藏？").assertExists()
    }

    @Test fun selectionKeepsSourceRatioAndAlignedRowsWhilePaging() {
        open()
        val first=rule.onNodeWithTag("bulk-asset-restore-0").fetchSemanticsNode().boundsInRoot
        val second=rule.onNodeWithTag("bulk-asset-restore-1").fetchSemanticsNode().boundsInRoot
        assertEquals(1.5f,first.width/first.height,.02f)
        assertEquals(first.top,second.top,1f)
        assertEquals(first.bottom,second.bottom,1f)
        rule.onNodeWithTag("bulk-asset-restore-0").performClick().assertIsSelected()
        assertEquals(first,rule.onNodeWithTag("bulk-asset-restore-0").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("bulk-grid").performScrollToNode(hasTestTag("bulk-asset-restore-120"))
        rule.onNodeWithTag("bulk-asset-restore-120").performClick().assertIsSelected()
        rule.onNodeWithTag("bulk-grid").performScrollToIndex(0)
        rule.onNodeWithText("已选 2 项").assertExists()
        assertTrue(writes.isEmpty())
    }

    @Test fun scrolledSelectionRevealsPartialResultInPortraitAndLandscape() {
        try {
            for (orientation in listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                open()
                rule.onNodeWithTag("bulk-asset-restore-0").performClick()
                rule.onNodeWithTag("bulk-asset-restore-2").performClick()
                rule.runOnUiThread { rule.activity.requestedOrientation = orientation }
                val expected = if (orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
                rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == expected }
                waitTag("bulk-grid")
                rule.onNodeWithTag("bulk-grid").performScrollToNode(hasTestTag("bulk-asset-restore-70"))
                favoriteConfirmation()
                rule.onNodeWithText("确认收藏").performClick()
                rule.waitUntil(10000) { rule.onAllNodesWithTag("bulk-result").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("bulk-result").assertIsDisplayed()
                rule.onNodeWithText("目录离线（OFFLINE）").assertIsDisplayed()
                rule.onNodeWithTag("bulk-actions").assertIsDisplayed().assertIsEnabled()
                rule.onNodeWithTag("bulk-done").performClick()
                rule.waitUntil(10000) { rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty() }
            }
            assertEquals(2, writes.size)
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }

    @Test fun selectionAndPartialResultRestoreButConfirmationDoesNotReplay() {
        open()
        rule.onNodeWithTag("bulk-asset-restore-0").performClick()
        rule.onNodeWithTag("bulk-asset-restore-2").performClick()
        favoriteConfirmation()
        rule.activityRule.scenario.recreate()
        waitTag("bulk-grid")
        rule.onNodeWithText("收藏？").assertDoesNotExist()
        rule.onNodeWithText("已选 2 项").assertExists()
        assertTrue(writes.isEmpty())
        favoriteConfirmation()
        rule.onNodeWithText("确认收藏").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("成功 1 项，1 项未完成。").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, writes.size)
        val ids = writes.single().getJSONArray("assetIds")
        assertEquals(setOf("restore-0", "restore-2"), (0 until ids.length()).map(ids::getString).toSet())
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("已选 1 项").assertExists()
        rule.onNodeWithText("成功 1 项，1 项未完成。").assertExists()
        rule.onNodeWithText("目录离线（OFFLINE）").assertExists()
        assertEquals(1, writes.size)
        rule.onNodeWithTag("bulk-done").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("bulk-grid").fetchSemanticsNodes().isEmpty() }
        open()
        rule.onNodeWithText("已选 0 项").assertExists()
        rule.onNodeWithText("成功 1 项，1 项未完成。").assertDoesNotExist()
    }

    @Test fun cancelledGlassActionsDoNotSubmitAndKeepSelection() {
        open()
        rule.onNodeWithTag("bulk-asset-restore-0").performClick()
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithTag("glass-action-menu").assertExists()
        auditShot("bulk-glass-actions")
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithText("已选 1 项").assertExists()
        rule.onNodeWithTag("bulk-trash").performClick()
        rule.onNodeWithText("确认移入回收站").assertExists()
        rule.onNodeWithText("范围：选中的 1 项。", substring = true).assertExists()
        auditShot("bulk-glass-trash-confirm")
        rule.onNodeWithText("取消", substring = false).performClick()
        rule.onNodeWithText("已选 1 项").assertExists()
        assertEquals(0, mutationAttempts.get())
    }

    @Test fun allMatchingScopeSurvivesRecreationWithoutExecuting() {
        open()
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("选择范围").performClick()
        rule.onNodeWithText("选择全部匹配项").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("已选择全部匹配项").assertExists()
        assertTrue(writes.isEmpty())
        favoriteConfirmation()
        rule.onNodeWithText("确认收藏").performClick()
        rule.waitUntil(10000) { writes.size == 1 }
        assertFalse(writes.single().has("assetIds"))
        assertFalse(writes.single().getJSONObject("filter").getBoolean("archived"))
        assertTrue(writes.single().getBoolean("favorite"))
    }
}
