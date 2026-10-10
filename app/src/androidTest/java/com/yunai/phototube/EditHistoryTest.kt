package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.EditHistoryScreen
import com.yunai.phototube.ui.media.RemoteMedia
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class EditHistoryTest {
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
    private val hash="a".repeat(64)
    private fun active(id:String)=JSONObject().put("editVersionId",id).put("sourceState","CURRENT").put("renderState","READY").put("displayWidth",50).put("displayHeight",50)
    private fun version(state:String="CURRENT")=JSONObject().put("id","v2").put("sourceContentHash",hash).put("active",false).put("sourceState",state).put("renderState","READY")
        .put("outputWidth",50).put("outputHeight",50).put("createdAt","2026-09-17T00:00:00Z").put("transform",JSONObject())
    private fun page(state:String="CURRENT")=JSONObject().put("items",JSONArray().put(version(state))).put("nextCursor",JSONObject.NULL)
    private fun source():RemoteMedia=RemoteMedia(RemoteAsset("photo","PHOTO","BROWSABLE","2026-09-17T00:00:00Z",0,"photo.jpg",hash,100,100,false,null),server).apply {
        detail=JSONObject().put("state","BROWSABLE").put("contentHash",hash).put("activeEdit",active("v1"))
    }
    @Test fun renderUsesSourceHashAndRestoreParsesJsonNullWithExpectedFence()=runBlocking {
        val source=source();val url=source.displayUrl().toHttpUrl()
        Assert.assertEquals(hash,url.queryParameter("v"));Assert.assertEquals("PREVIEW",url.queryParameter("size"))
        source.detail!!.getJSONObject("activeEdit").put("sourceState","STALE")
        Assert.assertEquals(server.original("photo"),source.displayUrl())
        host.enqueue(json("null"))
        Assert.assertNull(EditVersionsRepository(server).select("photo","v1",null))
        val request=host.takeRequest();val body=JSONObject(request.body.readUtf8())
        Assert.assertEquals("PATCH",request.method);Assert.assertEquals("v1",body.getString("expectedActiveEditVersionId"));Assert.assertTrue(body.isNull("targetEditVersionId"))
        host.enqueue(json(active("v2")))
        EditVersionsRepository(server).select("photo",null,"v2")
        val next=JSONObject(host.takeRequest().body.readUtf8());Assert.assertTrue(next.isNull("expectedActiveEditVersionId"));Assert.assertEquals("v2",next.getString("targetEditVersionId"))
    }
    @Test fun conflictRequiresRefreshAndRestoreKeepsHistory() {
        val source=source();var changed=0;var writes=0
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return when {
                    request.method=="PATCH" -> {writes++;when(writes) {1->json("""{"code":"CONFLICT","message":"当前版本已变化，请刷新"}""").setResponseCode(409);2->json(active("v2"));else->json("null")}}
                    request.requestUrl!!.encodedPath.endsWith("/edit-versions") -> json(page())
                    request.requestUrl!!.encodedPath.endsWith("/render") -> MockResponse().setResponseCode(503)
                    else -> json(JSONObject().put("state","BROWSABLE").put("contentHash",hash).put("activeEdit",active("v3")))
                }
            }
        }
        rule.setContent {PhotoTubeTheme {EditHistoryScreen(source,{changed++},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("使用此版本").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("使用此版本").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("当前版本已变化，请刷新").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("使用此版本").assertIsNotEnabled();Assert.assertEquals(0,changed)
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("刷新当前版本"))
        rule.onNodeWithText("刷新当前版本").performClick()
        rule.waitUntil(10000) {source.detail?.optJSONObject("activeEdit")?.optString("editVersionId")=="v3"}
        rule.onNodeWithText("使用此版本").performScrollTo().performClick()
        rule.waitUntil(10000) {changed==1}
        rule.onNodeWithTag("edit-history-list").performScrollToNode(hasText("恢复原图"))
        rule.onNodeWithText("恢复原图").performClick();rule.onNodeWithTag("glass-confirmation").assertIsDisplayed();rule.onNodeWithText("确认恢复").performClick()
        rule.waitUntil(10000) {changed==2}
        Assert.assertNull(source.detail!!.optJSONObject("activeEdit"))
        rule.onNodeWithText("正在显示原图").assertIsDisplayed().assertHasNoClickAction()
        val bodies=requests.filter {it.method=="PATCH"}.map {JSONObject(it.body.readUtf8())}
        Assert.assertEquals(listOf("v1","v3","v2"),bodies.map {it.getString("expectedActiveEditVersionId")})
        Assert.assertTrue(bodies.last().isNull("targetEditVersionId"))
        Assert.assertFalse(requests.any {it.method=="DELETE"})
    }
    @Test fun longErrorKeepsRefreshRestoreAndHistoryReachable() {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return if (request.requestUrl!!.encodedPath.endsWith("/edit-versions")) json(page("STALE"))
                else MockResponse().setResponseCode(503)
            }
        }
        val media = source().apply { error = "读取暂时失败，请稍后重试。".repeat(120) }
        rule.setContent { PhotoTubeTheme { EditHistoryScreen(media, {}, {}) } }
        val list = rule.onNodeWithTag("edit-history-list")
        list.performScrollToNode(hasText("刷新当前版本"))
        rule.onNodeWithText("刷新当前版本").assertIsDisplayed().assertIsEnabled()
        list.performScrollToNode(hasText("恢复原图"))
        rule.onNodeWithText("恢复原图").assertIsDisplayed().performClick()
        rule.onNodeWithText("取消", substring = false).performClick()
        rule.waitUntil(10000) { requests.any { it.requestUrl!!.encodedPath.endsWith("/edit-versions") } }
        list.performScrollToNode(hasText("使用此版本"))
        rule.onNodeWithText("使用此版本").assertIsDisplayed().assertIsNotEnabled()
        Assert.assertTrue(requests.all { it.method == "GET" })
    }

    @Test fun staleHistoryIsVisibleButCannotBeActivatedOrRendered() {
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {requests.add(request);return json(page("STALE"))}
        }
        rule.setContent {PhotoTubeTheme {EditHistoryScreen(source(),{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("原文件已变化，此版本不能重新应用").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("使用此版本").assertIsNotEnabled()
        Assert.assertTrue(requests.all {it.method=="GET" && it.requestUrl!!.encodedPath.endsWith("/edit-versions")})
    }
}
