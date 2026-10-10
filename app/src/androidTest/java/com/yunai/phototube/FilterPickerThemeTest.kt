package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic directory names and tags only; an isolated localhost failure is intentional. */
class FilterPickerThemeTest : RemoteAppTest() {
    private val failFolder=AtomicBoolean(true)
    private val writes=AtomicInteger()
    private val path="合成旅行/山间风景/2026年秋季照片与视频"
    private fun json(value:JSONObject)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        writes.incrementAndGet();return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request:RecordedRequest):MockResponse? {
        val url=request.requestUrl!!
        if(url.encodedPath=="/api/v1/tags") return json(JSONObject().put("items",JSONArray()
            .put(JSONObject().put("id",7).put("name","合成风景").put("assetCount",12)))
            .put("nextCursor",JSONObject.NULL))
        if(url.encodedPath!="/api/v1/folders") return null
        if(failFolder.getAndSet(false)) return json(JSONObject().put("code","OFFLINE").put("message","合成目录暂不可用")).setResponseCode(503)
        val items=JSONArray()
        fun folder(name:String,p:String)=JSONObject().put("libraryId","synthetic-library").put("path",p).put("name",name).put("online",true)
        if(url.queryParameter("libraryId")==null) items.put(folder("合成媒体库",""))
        else if(url.queryParameter("path").isNullOrEmpty()) items.put(folder("2026年秋季照片与视频",path))
        return json(JSONObject().put("children",JSONObject().put("items",items).put("nextCursor",JSONObject.NULL)))
    }
    @Test fun folderRetryAndTagDraftMatchBothThemes() {
        for(mode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            rule.onNodeWithContentDescription("图库标签").performClick()
            rule.onNodeWithContentDescription("筛选").performClick()
            rule.onNodeWithText("日期、目录与标签").performClick()
            failFolder.set(true)
            rule.onNodeWithTag("wall-filter-folder").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成目录暂不可用").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("重试").assertIsDisplayed()
            auditShot("picker-folder-error-${mode.name.lowercase()}")
            rule.onNodeWithText("重试").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成媒体库").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("合成媒体库").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("2026年秋季照片与视频").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("2026年秋季照片与视频").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("没有子目录").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText(path).assertIsDisplayed()
            rule.onNodeWithText("选择此目录").assertIsDisplayed()
            auditShot("picker-folder-leaf-${mode.name.lowercase()}")
            rule.onNodeWithText("选择此目录").performClick()
            rule.onNodeWithTag("wall-filter-folder").assertTextContains(path)
            assertNull(wall.filters.folderPath)
            rule.onNodeWithTag("wall-filter-tags-all").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithText("合成风景",substring=false).fetchSemanticsNodes().isNotEmpty()}
            val top=rule.onNodeWithText("完成选择").fetchSemanticsNode().boundsInRoot.top
            rule.onNodeWithText("合成风景",substring=false).performClick()
            rule.onNodeWithText("已选 1/100").assertIsDisplayed()
            assertEquals(top,rule.onNodeWithText("完成选择").fetchSemanticsNode().boundsInRoot.top,1f)
            auditShot("picker-tags-selected-${mode.name.lowercase()}")
            rule.onNodeWithText("完成选择").performClick()
            rule.onNodeWithTag("wall-filter-tags-all").assertTextContains("1 个标签")
            assertTrue(wall.filters.tagsAll.isEmpty())
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("apply-wall-filters").fetchSemanticsNodes().isEmpty()}
            rule.onNodeWithContentDescription("关闭筛选菜单").performClick()
            rule.waitUntil(5000) {rule.onAllNodesWithTag("filter-menu").fetchSemanticsNodes().isEmpty()}
            assertNull(wall.filters.folderPath);assertTrue(wall.filters.tagsAll.isEmpty());assertEquals(0,writes.get())
        }
    }
}
