package com.yunai.phototube

import androidx.compose.runtime.mutableStateOf
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class AlbumSearchTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private val cursors=CopyOnWriteArrayList<Int>()
    private val searchActive=mutableStateOf(true)
    private val query=mutableStateOf("sea")
    private val failOnce=AtomicBoolean(false)
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},
            OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
        host.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            Assert.assertEquals("GET",request.method)
            val url=request.requestUrl!!
            Assert.assertEquals("/api/v1/albums",url.encodedPath)
            Assert.assertTrue(url.queryParameterNames.all {it in setOf("cursor","limit")})
            val offset=url.queryParameter("cursor")?.removeSuffix("+/=")?.toInt() ?: 0
            cursors+=offset
            if(offset==100 && failOnce.compareAndSet(true,false)) return json(JSONObject().put("code","UNAVAILABLE").put("message","相册读取中断")).setResponseCode(503)
            val rows=(offset until minOf(offset+100,1200)).map {i ->
                JSONObject().put("id","album-$i").put("name",when(i) {151->"Sea 旅行";1069->"SEA 回忆";17->"Camera 照片";else->"其他相册 $i"})
                    .put("kind","NORMAL").put("sortMode","TAKEN_AT_DESC").put("assetCount",0)
            }
            return json(JSONObject().put("items",JSONArray(rows)).put("nextCursor",if(offset+100<1200) "${offset+100}+/=" else JSONObject.NULL))
        }}
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    private fun open() {rule.setContent {PhotoTubeTheme {RemoteAlbums(server,true,0.dp,query=query.value,searchActive=searchActive.value,onConnect={})}}}
    private fun waitText(text:String) {rule.waitUntil(15000) {rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun cancellingSearchRestoresBrowsingPosition() {
        searchActive.value=false;query.value="";open();waitText("其他相册 0")
        rule.onNodeWithTag("remote-albums").performScrollToIndex(18)
        val before=rule.onNodeWithTag("album-album-16").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {searchActive.value=true;query.value="sea"}
        waitText("SEA 回忆")
        rule.runOnIdle {searchActive.value=false;query.value=""}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-album-16").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(before,rule.onNodeWithTag("album-album-16").fetchSemanticsNode().boundsInRoot)
    }
    @Test fun sparseNameSearchReadsLaterPagesAndPreservesServerOrder() {
        open()
        waitText("SEA 回忆")
        rule.onNodeWithText("Sea 旅行").assertExists()
        rule.onNodeWithText("其他相册 0").assertDoesNotExist()
        Assert.assertTrue(cursors.containsAll(listOf(0,100,1100)))
        Assert.assertTrue(rule.onNodeWithText("Sea 旅行").fetchSemanticsNode().boundsInRoot.left < rule.onNodeWithText("SEA 回忆").fetchSemanticsNode().boundsInRoot.left)
    }
    @Test fun changingAndClearingNameRestartsAtFirstPageWithoutOldMatches() {
        open();waitText("SEA 回忆")
        val before=cursors.count {it==0}
        rule.runOnIdle {query.value="Camera"}
        waitText("Camera 照片")
        rule.onNodeWithText("Sea 旅行").assertDoesNotExist()
        Assert.assertTrue(cursors.count {it==0}>before)
        rule.runOnIdle {query.value=""}
        waitText("其他相册 0")
    }
    @Test fun noMatchesWaitsForAllPagesAndAppendFailureCanRetry() {
        query.value="不存在的相册";failOnce.set(true);open()
        waitText("相册读取中断")
        rule.onNodeWithText("没有符合名称的相册").assertDoesNotExist()
        rule.onNodeWithText("重试").performClick()
        waitText("没有符合名称的相册")
        Assert.assertEquals(2,cursors.count {it==100})
        Assert.assertTrue(cursors.contains(1100))
    }
}
