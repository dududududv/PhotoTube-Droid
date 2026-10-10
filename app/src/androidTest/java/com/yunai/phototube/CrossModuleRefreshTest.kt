package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.PhotoTubeServer
import com.yunai.phototube.ui.remote.RemoteAlbums
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CrossModuleRefreshTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private val removed=AtomicBoolean(false)
    private val notifications=AtomicInteger()
    private val albumReads=AtomicInteger()
    private val writes=AtomicInteger()
    private val hash="a".repeat(64)
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun page(vararg items:JSONObject)=JSONObject().put("items",JSONArray(items.toList())).put("nextCursor",JSONObject.NULL)
    private fun asset(id:String)=JSONObject().put("id",id).put("kind","PHOTO").put("state","BROWSABLE").put("fileName","$id.jpg")
        .put("takenAt","2026-09-17T00:00:00Z").put("favorite",false).put("contentHash",hash)
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start()}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").toString(),{},
            OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
        host.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            val path=request.requestUrl!!.encodedPath
            if(request.method=="POST") {
                Assert.assertEquals("/api/v1/assets/trash",path)
                Assert.assertEquals("a",JSONObject(request.body.readUtf8()).getJSONArray("assetIds").getString(0))
                removed.set(true);writes.incrementAndGet()
                return json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray()))
            }
            Assert.assertEquals("GET",request.method)
            return when(path) {
                "/api/v1/albums" -> {
                    albumReads.incrementAndGet()
                    json(page(JSONObject().put("id","album").put("name","相册计数").put("kind","NORMAL").put("sortMode","TAKEN_AT_DESC").put("assetCount",if(removed.get()) 0 else 1)))
                }
                "/api/v1/duplicates" -> json(if(removed.get()) page() else page(JSONObject().put("contentHash",hash).put("copyCount",2).put("reclaimableBytes",100)
                    .put("latestChangeAt","2026-09-17T00:00:00Z").put("reviewedAt",JSONObject.NULL)
                    .put("representative",JSONObject().put("assetId","a").put("fileName","重复组入口").put("contentHash",hash))))
                "/api/v1/duplicates/$hash" -> json(if(removed.get()) page(asset("b")) else page(asset("a"),asset("b")))
                "/api/v1/assets/a" -> json(asset("a"))
                else -> MockResponse().setResponseCode(404)
            }
        }}
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun trashFromDuplicateViewerRefreshesOuterAlbumCountsAndLibrary() {
        rule.setContent {PhotoTubeTheme {RemoteAlbums(server,true,0.dp,onLibraryChanged={notifications.incrementAndGet()},onConnect={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("1 项 · 普通相册").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("完全重复").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("重复组入口").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("重复组入口").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("a.jpg").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("a.jpg").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("删除").performClick()
        rule.onNodeWithText("删除").performClick()
        rule.waitUntil(10000) {notifications.get()>0 && albumReads.get()>1}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty()}
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("同组文件内容完全一致；保留操作只关闭提醒。有新副本时会再次提醒。").fetchSemanticsNodes().isEmpty()}
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("0 项 · 普通相册").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,writes.get())
        Assert.assertEquals(1,notifications.get())
    }
}
