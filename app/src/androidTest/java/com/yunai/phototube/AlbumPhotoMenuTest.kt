package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Local synthetic album and bundled images. No production requests. */
class AlbumPhotoMenuTest : RemoteAppTest() {
    override val photographicFixtures = true
    private val removed = CopyOnWriteArrayList<String>()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("DELETE",request.method)
        assertEquals("/api/v1/albums/restore-album/assets",request.requestUrl!!.encodedPath)
        val ids=JSONObject(request.body.readUtf8()).getJSONArray("assetIds")
        assertEquals(1,ids.length());removed.add(ids.getString(0))
        return MockResponse().setResponseCode(503).setHeader("Content-Type","application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"合成移除失败，请稍后重试"}""")
    }
    private fun back() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)

    @Test fun longPressAnchorsMenuAndOnlyExplicitRemovalWrites() {
        for ((index,mode) in listOf(ThemeMode.LIGHT,ThemeMode.DARK).withIndex()) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithTag("album-restore-album").performClick();waitTag("album-media-restore-0")
            val photo=rule.onNodeWithTag("album-media-restore-0")
            val before=photo.fetchSemanticsNode().boundsInWindow
            val photoCoordinates=photo.fetchSemanticsNode().layoutInfo.coordinates
            val screenBottom=photoCoordinates.localToScreen(androidx.compose.ui.geometry.Offset(0f,photoCoordinates.size.height.toFloat())).y
            photo.performTouchInput {longClick()}
            waitTag("glass-action-menu")
            rule.onNodeWithTag("glass-confirmation").assertDoesNotExist()
            rule.onNodeWithTag("media-viewer").assertDoesNotExist()
            rule.onNodeWithText("设为相册封面").assertIsDisplayed()
            rule.onNodeWithText("从相册移除（保留原文件）").assertIsDisplayed()
            // Popup and dialog have different window origins; compare screen coordinates.
            val popupCoordinates=rule.onNodeWithTag("glass-action-menu").fetchSemanticsNode().layoutInfo.coordinates
            val screenTop=popupCoordinates.localToScreen(androidx.compose.ui.geometry.Offset.Zero).y
            assertTrue("Menu should be anchored below the touched image: $screenBottom -> $screenTop",screenTop>=screenBottom-1f && screenTop-screenBottom<50f)
            auditShot("album-photo-menu-${mode.name.lowercase()}")
            back()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty()}
            assertEquals(before,photo.fetchSemanticsNode().boundsInWindow)
            assertEquals(index,removed.size)
            rule.onNodeWithTag("album-media-restore-2").performTouchInput {longClick()}
            rule.onNodeWithText("从相册移除（保留原文件）").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成移除失败，请稍后重试").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(index+1,removed.size)
            assertEquals("restore-2",removed.last())
            rule.onNodeWithTag("album-media-restore-2").assertExists()
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
        }
    }
}
