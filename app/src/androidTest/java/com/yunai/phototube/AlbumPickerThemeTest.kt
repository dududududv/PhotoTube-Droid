package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Actual dark appearance entry; bundled media and synthetic local failures only. */
class AlbumPickerThemeTest : RemoteAppTest() {
    override val photographicFixtures=true
    private val writes=AtomicInteger()
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        assertEquals("/api/v1/albums/restore-album/assets",request.requestUrl!!.encodedPath)
        val ids=JSONObject(request.body.readUtf8()).getJSONArray("assetIds")
        assertEquals(1,ids.length());assertEquals("restore-1",ids.getString(0))
        writes.incrementAndGet()
        return MockResponse().setResponseCode(503).setHeader("Content-Type","application/json")
            .setBody("""{"code":"OFFLINE","message":"合成服务暂不可用，请稍后重试"}""")
    }
    @Test fun darkPickersShowVideoSelectionAndKeepFailureRecoverable() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.onNodeWithContentDescription("返回设置").performClick()
        rule.onNodeWithContentDescription("返回相册").performClick()
        rule.onNodeWithTag("album-restore-album").performScrollTo().performClick();waitTag("album-grid")
        for(remove in listOf(false,true)) {
            val verb=if(remove) "移除" else "添加"
            val suffix=if(remove) "remove" else "add"
                rule.onNodeWithText(if(remove) "选择" else "添加").performClick()
            waitTag("album-member-grid")
            rule.onNodeWithTag("album-member-grid").performScrollToNode(hasTestTag("member-select-restore-1"))
            rule.onNode(hasText("▶ 0:10") and hasAnyAncestor(hasTestTag("album-member-grid"))).assertIsDisplayed()
            rule.onNodeWithText("$verb 0 项").assertIsNotEnabled()
            auditShot("album-picker-$suffix-dark-empty")
            val actionTop=rule.onNodeWithText("$verb 0 项").fetchSemanticsNode().boundsInWindow.top
            rule.onNodeWithTag("member-select-restore-1").performClick().assertIsSelected()
            rule.onNodeWithText("$verb 1 项").assertIsEnabled()
            assertEquals(actionTop,rule.onNodeWithText("$verb 1 项").fetchSemanticsNode().boundsInWindow.top,1f)
            auditShot("album-picker-$suffix-dark-selected")
            val before=writes.get()
            rule.onNodeWithText("$verb 1 项").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成服务暂不可用",substring=true).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("合成服务暂不可用",substring=true).assertIsDisplayed()
            rule.onNodeWithText("$verb 1 项").assertIsEnabled()
            auditShot("album-picker-$suffix-dark-failure")
            assertEquals(before+1,writes.get())
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("album-member-grid").fetchSemanticsNodes().isEmpty()}
            assertEquals(before+1,writes.get())
        }
    }
}
