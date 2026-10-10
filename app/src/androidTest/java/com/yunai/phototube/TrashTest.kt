package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.TrashScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.time.Instant

class TrashTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun asset(id:String)=JSONObject().put("id",id).put("kind","PHOTO").put("state","TRASHED")
        .put("takenAt","2020-01-01T00:00:00Z").put("takenAtOffsetMinutes",0).put("fileName","$id.jpg")
        .put("contentHash",JSONObject.NULL).put("width",100).put("height",100).put("favorite",false).put("durationSec",JSONObject.NULL)
        .put("trashedAt","2026-09-16T12:00:00Z").put("retentionDays",30)
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun cursorRetentionAndPurgeContract()=runBlocking {
        val repository=TrashRepository(server)
        host.enqueue(json(JSONObject().put("items",JSONArray().put(asset("a"))).put("nextCursor","trash+/=")))
        val first=repository.list(null);host.takeRequest()
        Assert.assertEquals(30,first.items.single().remainingDays(Instant.parse("2026-09-16T13:00:00Z")))
        Assert.assertEquals(0,first.items.single().remainingDays(Instant.parse("2027-01-01T00:00:00Z")))
        host.enqueue(json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        repository.list(first.cursor)
        Assert.assertEquals("trash+/=",host.takeRequest().requestUrl!!.queryParameter("cursor"))
        host.enqueue(json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray())))
        repository.purge(setOf("a")).requireSuccess("a")
        val purge=host.takeRequest()
        Assert.assertEquals("/api/v1/trash/purge",purge.path)
        Assert.assertEquals(1,JSONObject(purge.body.readUtf8()).length())
    }
    @Test fun purgeNeedsConfirmationAndDoesNotDeleteOriginals() {
        val writes=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST") {
                    Assert.assertEquals("/api/v1/trash/purge",request.path)
                    writes.incrementAndGet()
                    return json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray()))
                }
                return json(JSONObject().put("items",if(writes.get()==0) JSONArray().put(asset("a")) else JSONArray()).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {TrashScreen(server,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("trash-a").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("trash-a").performClick()
        rule.waitForIdle()
        val screenshot=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"trash-preview.png")
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(screenshot)
        rule.onNodeWithText("清除记录").performClick()
        rule.onNodeWithText("这些 PhotoTube 记录和派生缓存将无法恢复。NAS 原文件不会删除；以后扫描相同目录时，文件可能重新入库。").assertExists()
        Assert.assertEquals(0,writes.get())
        rule.onNodeWithText("取消").performClick();Assert.assertEquals(0,writes.get())
        rule.onNodeWithText("清除记录").performClick();rule.onNodeWithText("确认清除记录").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("回收站是空的").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,writes.get())
    }
    @Test fun restorePartialFailureKeepsOnlyFailedTargetsSelected() {
        val batches=java.util.Collections.synchronizedList(mutableListOf<List<String>>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST") {
                    Assert.assertEquals("/api/v1/assets/restore",request.path)
                    val body=JSONObject(request.body.readUtf8());Assert.assertEquals(1,body.length())
                    val ids=body.getJSONArray("assetIds");batches.add((0 until ids.length()).map(ids::getString))
                    return if(batches.size==1) json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray().put(JSONObject().put("target","b").put("code","NOT_FOUND").put("message","请刷新"))))
                    else json(JSONObject().put("succeeded",JSONArray().put("b")).put("failed",JSONArray()))
                }
                val rows=when(batches.size) {0->JSONArray().put(asset("a")).put(asset("b"));1->JSONArray().put(asset("b"));else->JSONArray()}
                return json(JSONObject().put("items",rows).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {TrashScreen(server,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("trash-b").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("选择 2 项").performClick();rule.onNodeWithText("恢复 2 项").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("1 项已恢复").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("恢复 1 项").performClick()
        rule.waitUntil(10000) {batches.size==2}
        Assert.assertEquals(listOf("b"),batches[1])
    }
}
