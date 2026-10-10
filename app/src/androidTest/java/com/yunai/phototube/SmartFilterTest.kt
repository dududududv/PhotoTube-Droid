package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.SmartAlbumEditor
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class SmartFilterTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun fullRuleRoundTripPreservesOffsetsIdsAndFalseValues() {
        val filter=JSONObject("""{"archived":false,"favorite":false,"kind":"PHOTO","keyword":"海边假日","libraryId":"lib","folderPath":"照片/旅行","takenFrom":"2026-01-01T00:00:00+08:00","takenTo":"2026-09-16T23:59:59.999999999+08:00","tagsAll":[9007199254740993],"tagsAny":[2,3],"tagsExclude":[4]}""")
        val draft=SmartFilterDraft.from(filter)
        Assert.assertNull(draft.validationError())
        val result=draft.json()
        filter.keys().forEach { key -> Assert.assertEquals(key,filter.get(key).toString(),result.get(key).toString()) }
        Assert.assertEquals(9007199254740993L,draft.all.single())
        Assert.assertNotNull(draft.copy(keyword="ab").validationError())
        Assert.assertNotNull(draft.copy(takenFrom="2027-01-01T00:00:00Z").validationError())
        val cleared=draft.copy(library=null,folder=null,takenFrom=null,all=emptySet(),favorite=null).json()
        Assert.assertFalse(cleared.has("folderPath"));Assert.assertFalse(cleared.has("libraryId"));Assert.assertFalse(cleared.has("takenFrom"));Assert.assertFalse(cleared.has("tagsAll"));Assert.assertFalse(cleared.has("favorite"))
    }
    @Test fun catalogUsesLibraryBoundCursorAndLongTagIds()=runBlocking {
        val catalog=FilterCatalog(server)
        host.enqueue(json(JSONObject().put("items",JSONArray().put(JSONObject().put("id",9007199254740993L).put("name","海边").put("assetCount",3))).put("nextCursor","tag+/=")))
        Assert.assertEquals(9007199254740993L,catalog.tags("海边",null).items.single().id)
        Assert.assertEquals("海边",host.takeRequest().requestUrl!!.queryParameter("keyword"))
        host.enqueue(json(JSONObject().put("children",JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))))
        catalog.folders("library-fixture","旅行/海边","cursor+/=")
        val request=host.takeRequest()
        Assert.assertEquals("/api/v1/folders",request.requestUrl!!.encodedPath)
        Assert.assertEquals("library-fixture",request.requestUrl!!.queryParameter("libraryId"))
        Assert.assertEquals("旅行/海边",request.requestUrl!!.queryParameter("path"))
        Assert.assertEquals("cursor+/=",request.requestUrl!!.queryParameter("cursor"))
    }
    @Test fun editorRejectsShortKeywordAndKeepsDraftOnSaveFailure() {
        val album=remoteAlbum(JSONObject().put("id","album").put("name","旅行").put("kind","SMART").put("sortMode","TAKEN_AT_DESC").put("assetCount",0).put("cover",JSONObject.NULL).put("filter",JSONObject().put("archived",false)))
        var saved=false
        rule.setContent {PhotoTubeTheme {SmartAlbumEditor(AlbumRepository(server),album,{saved=true},{})}}
        rule.onNodeWithTag("smart-keyword").performScrollTo().performTextReplacement("ab")
        rule.onNodeWithText("保存规则").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("smart-keyword").performScrollTo().performTextReplacement("海边假日")
        host.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"CONFLICT","message":"规则保存冲突"}"""))
        rule.onNodeWithText("保存规则").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("规则保存冲突").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertFalse(saved)
        rule.onNodeWithTag("smart-keyword").performScrollTo().assertTextContains("海边假日")
        Assert.assertEquals("海边假日",JSONObject(host.takeRequest().body.readUtf8()).getJSONObject("filter").getString("keyword"))
    }
    @Test fun offlineDirectoryCannotNavigateOrSelect() {
        host.enqueue(json(JSONObject("""{"children":{"items":[{"libraryId":"offline-fixture","path":"","name":"合成离线库","online":false}],"nextCursor":null}}""")))
        var selected:LibraryFolder?=null
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.IndexedFolderPicker(FilterCatalog(server),{}, {selected=it})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("合成离线库 · 离线").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("合成离线库 · 离线").assertIsNotEnabled().performTouchInput {click()}
        rule.onNodeWithText("选择此目录").assertDoesNotExist()
        Assert.assertNull(selected)
        Assert.assertEquals(1,host.requestCount)
    }
    @Test fun wallEditorUsesTagPickerAndOnlyAppliesOnConfirmation() {
        val initial=WallFilters(WallKind.VIDEO,true,keyword="holiday",libraryId="library",folderPath="旅行",
            takenFrom="2026-01-01T00:00:00+08:00",tagsAll=setOf(7L),tagsExclude=setOf(9L))
        var applied:WallFilters?=null
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.WallFilterEditor(server,initial,{applied=it},{})}}
        rule.waitForIdle()
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"wall-filter-preview.png"))
        Assert.assertEquals(0,host.requestCount)
        host.enqueue(json(JSONObject().put("items",JSONArray().put(JSONObject().put("id",9007199254740993L).put("name","海边").put("assetCount",3))).put("nextCursor",JSONObject.NULL)))
        rule.onNodeWithTag("wall-filter-tags-any").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("海边").fetchSemanticsNodes().isNotEmpty()}
        val before=rule.onNodeWithText("完成选择").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithText("海边").performClick()
        rule.waitForIdle()
        Assert.assertEquals(before,rule.onNodeWithText("完成选择").fetchSemanticsNode().boundsInRoot.top,1f)
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"mock-tag-picker-selected.png"))
        Assert.assertNull(applied)
        rule.onNodeWithText("完成选择").performScrollTo().performClick()
        Assert.assertNull(applied)
        rule.onNodeWithTag("apply-wall-filters").assertIsDisplayed().performClick()
        Assert.assertEquals(initial.copy(tagsAny=setOf(9007199254740993L)),applied)
        Assert.assertEquals("/api/v1/tags",host.takeRequest().requestUrl!!.encodedPath)
        Assert.assertEquals(1,host.requestCount)
    }
    @Test fun wallEditorRejectsInvertedDatesAndResetsScopeWithoutClearingSearch() {
        val initial=WallFilters(WallKind.VIDEO,true,keyword="holiday",libraryId="library",folderPath="旅行",
            takenFrom="2026-09-17T00:00:00Z",takenTo="2026-01-01T00:00:00Z",tagsAll=setOf(7L))
        var applied:WallFilters?=null
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.WallFilterEditor(server,initial,{applied=it},{})}}
        rule.onNodeWithTag("apply-wall-filters").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("结束日期不能早于开始日期").assertIsDisplayed()
        rule.onNodeWithText("重置条件").assertIsDisplayed().performClick()
        Assert.assertNull(applied)
        rule.onNodeWithTag("apply-wall-filters").assertIsDisplayed().performClick()
        Assert.assertEquals(WallFilters(WallKind.VIDEO,true,keyword="holiday"),applied)
        Assert.assertEquals(0,host.requestCount)
    }
    @Test fun assetActionsRequireExplicitTargetsAndDetectPartialFailure()=runBlocking {
        val actions=AssetActions(server)
        for(action in AssetAction.entries) {
            host.enqueue(json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray().put(JSONObject().put("target","b").put("code","NOT_FOUND").put("message","已移除")))))
            val result=actions.execute(action,setOf("a","b"),if(action.flag!=null) false else null)
            val request=host.takeRequest();val body=JSONObject(request.body.readUtf8())
            Assert.assertEquals("/api/v1/assets/${action.path}",request.requestUrl!!.encodedPath)
            Assert.assertEquals(2,body.getJSONArray("assetIds").length())
            action.flag?.let {Assert.assertFalse(body.getBoolean(it))}
            result.requireSuccess("a")
            try {result.requireSuccess("b");Assert.fail()} catch(error:ServerError) {Assert.assertEquals("NOT_FOUND",error.code)}
        }
    }
}
