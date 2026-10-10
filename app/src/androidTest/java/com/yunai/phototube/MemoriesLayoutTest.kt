package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.ui.theme.ThemeMode
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Only bundled landscape images served by RemoteAppTest's isolated localhost fixture. */
class MemoriesLayoutTest : RemoteAppTest() {
    override val photographicFixtures = true

    override fun readFixture(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/home") return null
        fun asset(i: Int) = org.json.JSONObject().put("id", "restore-$i").put("kind", "PHOTO")
            .put("state", "BROWSABLE").put("fileName", "示例风景-$i.jpg")
            .put("takenAt", "2026-09-01T00:00:00Z").put("favorite", false)
            .put("contentHash", "a".repeat(64)).put("width", 1200).put("height", 800)
        val album = org.json.JSONObject().put("id", "restore-album").put("name", "随包风景相册")
            .put("kind", "NORMAL").put("sortMode", "TAKEN_AT_ASC").put("assetCount", 220).put("cover", asset(4))
        return okhttp3.mockwebserver.MockResponse().setHeader("Content-Type", "application/json")
            .setBody(org.json.JSONObject().put("onThisDay", org.json.JSONArray((0..2).map(::asset)))
                .put("recentPhotos", org.json.JSONArray()).put("recentImports", org.json.JSONArray())
                .put("frequentAlbums", org.json.JSONArray().put(album))
                .put("jobSummary", org.json.JSONObject().put("items", org.json.JSONArray())).toString())
    }

    @Test fun compactCollectionsAndPhotoReturnInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithText("回忆与精选").performClick()
            waitTag("memory-往年今日-restore-0")
            val first = rule.onNodeWithTag("memory-往年今日-restore-0")
            val second = rule.onNodeWithTag("memory-往年今日-restore-1")
            first.assertIsDisplayed(); second.assertIsDisplayed()
            rule.onNodeWithTag("memory-album-restore-album").assertIsDisplayed()
            val before = first.fetchSemanticsNode().boundsInRoot
            assertTrue(second.fetchSemanticsNode().boundsInRoot.left >= before.right)
            auditShot("memories-compact-${mode.name.lowercase()}")
            first.performClick(); waitTag("media-viewer")
            rule.onNodeWithContentDescription("关闭照片").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty() }
            first.assertIsDisplayed()
            assertEquals(before.top,first.fetchSemanticsNode().boundsInRoot.top,1f)
            val album = rule.onNodeWithTag("memory-album-restore-album")
            val albumTop = album.fetchSemanticsNode().boundsInRoot.top
            album.performClick(); waitTag("album-detail")
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty() }
            album.assertIsDisplayed()
            assertEquals(albumTop,album.fetchSemanticsNode().boundsInRoot.top,1f)
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("memory-往年今日-restore-0").fetchSemanticsNodes().isEmpty() }
        }
    }
}
