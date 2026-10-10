package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.BulkAssetsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class BulkAssetsTest {
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
    @Test fun filterBodyUsesTypedValuesAndNeverSendsIdsOrAllowsRestore()=runBlocking {
        val filters=WallFilters(WallKind.VIDEO,true,true,"holiday",libraryId="library",folderPath="旅行",tagsAll=setOf(9007199254740993L))
        host.enqueue(json("""{"matchedCount":1000,"succeededCount":998,"failedCount":2}"""))
        val result=AssetActions(server).executeFilter(AssetAction.ARCHIVE,filters,false)
        Assert.assertEquals(FilterActionResult(1000,998,2),result)
        val request=host.takeRequest();val body=JSONObject(request.body.readUtf8())
        Assert.assertEquals("/api/v1/assets/archive",request.requestUrl!!.encodedPath)
        Assert.assertFalse(body.has("assetIds"));Assert.assertFalse(body.getBoolean("archived"))
        val filter=body.getJSONObject("filter")
        Assert.assertEquals(true,filter.get("archived"));Assert.assertEquals(true,filter.get("favorite"))
        Assert.assertEquals(9007199254740993L,filter.getJSONArray("tagsAll").getLong(0))
        Assert.assertEquals("旅行",filter.getString("folderPath"))
        try {AssetActions(server).executeFilter(AssetAction.RESTORE,filters);Assert.fail()} catch(_:IllegalArgumentException) {}
        Assert.assertEquals(1,host.requestCount)
    }
    private fun page():JSONObject=JSONObject().put("items",JSONArray().apply {
        listOf("a","b").forEach {id -> put(JSONObject().put("id",id).put("kind","PHOTO").put("state","PROCESSING")
            .put("takenAt","2026-09-10T00:00:00Z").put("fileName","$id.jpg").put("favorite",false))}
    }).put("nextCursor",JSONObject.NULL)
    @Test fun selectedPartialFailureRetainsOnlyUnconfirmedItemsAndRefreshes() {
        val requests=CopyOnWriteArrayList<RecordedRequest>();var changed=0
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return if(request.method=="GET") json(page()) else json("""{"succeeded":["a"],"failed":[{"target":"b","code":"OFFLINE","message":"目录离线"}]}""")
            }
        }
        rule.setContent {PhotoTubeTheme {BulkAssetsScreen(server,WallFilters(),2,{changed++},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-asset-a").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("选择范围").performClick()
        rule.onNodeWithText("选择已加载").performClick()
        rule.onNodeWithTag("bulk-trash").performClick()
        rule.onNodeWithText("可以在回收站恢复，不会删除原文件。",substring=true).assertIsDisplayed()
        Assert.assertFalse(requests.any {it.method=="POST"})
        rule.onNodeWithText("确认移入回收站").performClick()
        rule.waitUntil(10000) {changed==1}
        rule.onNodeWithText("已选 1 项").assertIsDisplayed()
        rule.onNodeWithText("成功 1 项，1 项未完成。").assertIsDisplayed()
        rule.onNodeWithText("目录离线（OFFLINE）").assertIsDisplayed()
        val post=requests.single {it.method=="POST"};val body=JSONObject(post.body.readUtf8())
        Assert.assertEquals("/api/v1/assets/trash",post.requestUrl!!.encodedPath)
        Assert.assertEquals(setOf("a","b"),(0 until body.getJSONArray("assetIds").length()).map {body.getJSONArray("assetIds").getString(it)}.toSet())
        Assert.assertFalse(body.has("filter"))
        rule.waitUntil(10000) {requests.count {it.method=="GET"}>=2}
    }
    @Test fun allMatchingRequiresExplicitConfirmationAndReportsCountsWithoutInventingIds() {
        val requests=CopyOnWriteArrayList<RecordedRequest>();var changed=0
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return if(request.method=="GET") json(page()) else json("""{"matchedCount":900,"succeededCount":899,"failedCount":1}""")
            }
        }
        rule.setContent {PhotoTubeTheme {BulkAssetsScreen(server,WallFilters(keyword="holiday"),1000,{changed++},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-asset-a").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("选择范围").performClick()
        rule.onNodeWithText("选择全部匹配项").performClick()
        rule.onNodeWithTag("bulk-favorite").performClick()
        rule.onNodeWithText("范围：当前筛选的全部匹配项，包含未加载的照片。打开页面时共 1000 项，执行时逐批求值，不是固定快照。",substring=true).assertIsDisplayed()
        Assert.assertFalse(requests.any {it.method=="POST"})
        rule.onNodeWithText("确认收藏").performClick()
        rule.waitUntil(10000) {changed==1}
        rule.onNodeWithTag("bulk-result").assertTextContains("匹配 900 项，成功 899 项，失败 1 项。",substring=true)
        rule.onNodeWithText("已选 0 项").assertIsDisplayed()
        val body=JSONObject(requests.single {it.method=="POST"}.body.readUtf8())
        Assert.assertFalse(body.has("assetIds"));Assert.assertEquals("holiday",body.getJSONObject("filter").getString("keyword"))

    }
}
