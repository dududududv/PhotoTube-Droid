package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Bundled photographs, loopback API. A draft cannot write until Create is pressed. */
class AlbumCreationFlowTest : RemoteAppTest() {
    override val photographicFixtures = true
    override val assetCountFixture = 8
    private val writes = CopyOnWriteArrayList<Pair<String, JSONObject>>()
    private var partial = false
    private fun album() = JSONObject().put("id","created-draft").put("name","周末相册")
        .put("kind","NORMAL").put("sortMode","TAKEN_AT_ASC").put("assetCount",2)
    private fun json(body:JSONObject) = MockResponse().setHeader("Content-Type","application/json").setBody(body.toString())
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        val path=request.requestUrl!!.encodedPath
        val body=JSONObject(request.body.readUtf8())
        writes += "${request.method} $path" to body
        return when {
            path=="/api/v1/albums" -> json(album()).setResponseCode(201)
            path.endsWith("/created-draft/assets") -> json(JSONObject()
                .put("succeeded",JSONArray(if(partial) listOf("restore-0") else listOf("restore-0","restore-2")))
                .put("failed",JSONArray()))
            path.endsWith("/created-draft") -> json(album().put("coverAssetId",body.getString("coverAssetId")))
            else -> MockResponse().setResponseCode(405)
        }
    }
    override fun readFixture(request:RecordedRequest):MockResponse? =
        if(request.requestUrl!!.encodedPath=="/api/v1/albums/created-draft/assets")
            json(JSONObject().put("album",album()).put("assets",JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        else null
    private fun draft() {
        rule.onNodeWithContentDescription("相册标签").performClick();waitTag("album-restore-album")
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("普通相册").assertIsSelected()
        rule.onNodeWithText("名称").performTextInput("周末相册")
        rule.onNodeWithText("选择照片").performScrollTo().performClick();waitTag("member-select-restore-0")
        rule.onNodeWithTag("member-select-restore-0").performClick()
        rule.onNodeWithTag("member-select-restore-2").performClick()
        rule.onNodeWithText("完成选择").performClick();waitTag("draft-cover-restore-2")
        rule.onNodeWithTag("draft-cover-restore-2").performClick()
        assertTrue(writes.isEmpty())
    }
    @Test fun photosAndCoverStayInDraftUntilCreateAndWriteInOrder() {
        draft()
        rule.activityRule.scenario.recreate()
        waitTag("draft-cover-restore-2")
        rule.onNodeWithTag("draft-cover-restore-2").assertIsSelected()
        rule.onNodeWithText("已选 2 项 · 更改").assertExists()
        auditShot("repair-create-with-cover")
        assertTrue(writes.isEmpty())
        rule.onNodeWithText("创建").performScrollTo().performClick();waitTag("album-detail")
        assertEquals(listOf("POST /api/v1/albums","POST /api/v1/albums/created-draft/assets","PATCH /api/v1/albums/created-draft"),writes.map {it.first})
        assertEquals("NORMAL",writes[0].second.getString("kind"))
        assertEquals("restore-2",writes[2].second.getString("coverAssetId"))
    }
    @Test fun cancellingPhotoDraftCreatesNothing() {
        draft()
        rule.onNodeWithText("取消").performScrollTo().performClick();waitTag("album-restore-album")
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("周末相册").assertDoesNotExist()
        rule.onNodeWithTag("draft-cover-restore-2").assertDoesNotExist()
        assertTrue(writes.isEmpty())
    }
    @Test fun partialMembershipCannotDuplicateAlbumOrSetUnconfirmedCover() {
        partial=true;draft()
        rule.onNodeWithText("创建").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("其余 1 项未确认",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("创建").assertIsNotEnabled()
        rule.onNodeWithText("进入已建相册").performScrollTo().performClick();waitTag("album-detail")
        assertEquals(2,writes.size)
        assertEquals(1,writes.count {it.first=="POST /api/v1/albums"})
        assertFalse(writes.any {it.first.startsWith("PATCH")})
    }
}
