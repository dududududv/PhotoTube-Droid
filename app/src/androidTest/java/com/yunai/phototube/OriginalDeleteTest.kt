package com.yunai.phototube

import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.OriginalDeleteScreen
import com.yunai.phototube.ui.remote.TrashScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class OriginalDeleteTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private val expired=AtomicInteger()
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun confirmation(token:String="fixture-token",expired:Boolean=false)=JSONObject()
        .put("confirmationToken",token).put("confirmationPhrase","DELETE 2 FILES")
        .put("expiresAt",Instant.now().plusSeconds(if(expired) -60 else 600).toString())
        .put("assetCount",2).put("fileCount",2).put("totalBytes",9007199254741093L)
        .put("items",JSONArray().put(JSONObject().put("assetId","a").put("fileName","first.jpg").put("relativePath","trip/first.jpg").put("fileSize",100))
            .put(JSONObject().put("assetId","b").put("fileName","second.jpg").put("relativePath","family/second.jpg").put("fileSize",9007199254740993L)))
    private fun partial()="""{"succeeded":["a"],"failed":[{"target":"b","code":"FILE_CHANGED","message":"文件已变化"}]}"""
    private fun done()="""{"succeeded":["a","b"],"failed":[]}"""
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(), 0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{expired.incrementAndGet()},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
        server.cookies.saveFromResponse(server.origin,listOf(Cookie.Builder().name("phototube_session").value("fixture").hostOnlyDomain("localhost").path("/").secure().build()))
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun contractPreservesPasswordAndReturnedBatchAndRequiresExactPhrase()=runBlocking {
        val repository=OriginalDeleteRepository(server)
        try {repository.prepare((1..21).map {it.toString()}.toSet(),"password");Assert.fail()} catch(_:IllegalArgumentException) {}
        Assert.assertEquals(0,host.requestCount)
        host.enqueue(json("""{"code":"INVALID_CREDENTIALS","message":"错误"}""").setResponseCode(401))
        try {repository.prepare(setOf("a")," wrong ");Assert.fail()} catch(_:ServerError) {}
        Assert.assertEquals(0,expired.get());Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        Assert.assertEquals(" wrong ",JSONObject(host.takeRequest().body.readUtf8()).getString("password"))
        host.enqueue(json(confirmation()))
        val frozen=repository.prepare(setOf("a"),"fixture");host.takeRequest()
        Assert.assertEquals(listOf("a","b"),frozen.items.map {it.assetId})
        Assert.assertEquals(9007199254741093L,frozen.totalBytes)
        Assert.assertFalse(frozen.toString().contains("fixture-token"))
        try {repository.execute(frozen,"DELETE 2 FILES ");Assert.fail()} catch(_:IllegalArgumentException) {}
        Assert.assertEquals(2,host.requestCount)
        host.enqueue(json(partial()));val result=repository.execute(frozen,frozen.phrase)
        Assert.assertEquals(setOf("a"),result.succeeded);Assert.assertEquals("FILE_CHANGED",result.failed.single().code)
        val execution=host.takeRequest();val body=JSONObject(execution.body.readUtf8())
        Assert.assertEquals("/api/v1/assets/delete-original",execution.path)
        Assert.assertEquals(setOf("confirmationToken","confirmationPhrase"),body.keys().asSequence().toSet())
        Assert.assertEquals("fixture-token",body.getString("confirmationToken"))
        host.enqueue(json(confirmation(expired=true)));val old=repository.prepare(setOf("b"),"fixture");host.takeRequest()
        val before=host.requestCount
        try {repository.execute(old,old.phrase);Assert.fail()} catch(_:IllegalArgumentException) {}
        Assert.assertEquals(before,host.requestCount)
    }
    @Test fun disabledDeploymentHidesTrashEntryAndCannotPrepare() {
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return if(request.path=="/api/v1/auth/session") json("""{"authenticated":true,"originalDeleteEnabled":false}""")
                else json("""{"items":[],"nextCursor":null}""")
            }
        }
        rule.setContent {PhotoTubeTheme {TrashScreen(server,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("回收站是空的").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("删除所选 NAS 原文件…").assertDoesNotExist()
        Assert.assertTrue(requests.all {it.method=="GET"})
    }
    private fun dispatch(execute:(Int)->MockResponse):CopyOnWriteArrayList<RecordedRequest> {
        val requests=CopyOnWriteArrayList<RecordedRequest>();val executions=AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return when {
                    request.path=="/api/v1/auth/session"->json("""{"authenticated":true,"originalDeleteEnabled":true}""")
                    request.path!!.endsWith("/prepare")->json(confirmation())
                    else->execute(executions.incrementAndGet())
                }
            }
        }
        return requests
    }
    private fun prepareUi() {
        rule.waitUntil(10000) {rule.onAllNodesWithTag("delete-original-password").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("delete-original-password").performScrollTo().performTextInput("fixture")
        rule.onNodeWithText("验证并查看文件清单").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("delete-original-phrase").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun executeUi() {
        rule.onNodeWithTag("delete-original-phrase").performScrollTo().performTextReplacement("DELETE 2 FILES")
        rule.onNodeWithText("永久删除清单中的原文件").performScrollTo().performClick()
    }
    @Test fun completeReturnedListIsShownAndReplayingDoesNotAccumulateCounts() {
        val requests=dispatch {json(partial())};val results=CopyOnWriteArrayList<AssetActionResult>()
        rule.setContent {PhotoTubeTheme {OriginalDeleteScreen(server,setOf("a"),{results.add(it)},{})}}
        prepareUi()
        rule.onNodeWithText("family/second.jpg").assertExists()
        rule.onNodeWithText("这是服务端返回的完整待处理批次，与刚才的选择不同。请核对全部文件。").assertExists()
        Assert.assertEquals(0,requests.count {it.path=="/api/v1/assets/delete-original"})
        rule.onNodeWithTag("delete-original-phrase").performScrollTo().performTextInput("DELETE 2 FILES ")
        rule.onNodeWithText("永久删除清单中的原文件").assertIsNotEnabled()
        executeUi()
        rule.waitUntil(10000) {results.size==1}
        rule.onNodeWithText("成功 1 项 · 失败 1 项").assertExists()
        executeUi()
        rule.waitUntil(10000) {results.size==2}
        rule.onNodeWithText("成功 1 项 · 失败 1 项").assertExists()
        rule.onNodeWithText("成功 2 项 · 失败 2 项").assertDoesNotExist()
        Assert.assertTrue(requests.none {it.path!!.contains("purge")})
    }
    @Test fun lostResponseCanBeExplicitlyReplayedWithSameToken() {
        val requests=dispatch {if(it==1) MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else json(done())}
        rule.setContent {PhotoTubeTheme {OriginalDeleteScreen(server,setOf("a"),{},{})}}
        prepareUi();executeUi()
        rule.waitUntil(15000) {rule.onAllNodesWithText("执行结果未完全确认",substring=true).fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,requests.count {it.path=="/api/v1/assets/delete-original"})
        executeUi()
        rule.waitUntil(10000) {rule.onAllNodesWithText("本批次已完成").fetchSemanticsNodes().isNotEmpty()}
        val calls=requests.filter {it.path=="/api/v1/assets/delete-original"}
        Assert.assertEquals(2,calls.size)
        Assert.assertEquals(calls[0].body.clone().readUtf8(),calls[1].body.clone().readUtf8())
    }
    @Test fun expiredServerConfirmationAndBackgroundBothRequireFreshPreparation() {
        val requests=dispatch {json("""{"code":"CONFIRMATION_REQUIRED","message":"删除确认已过期"}""").setResponseCode(409)}
        val owner=object:LifecycleOwner {val registry=LifecycleRegistry.createUnsafe(this);override val lifecycle:Lifecycle get()=registry}
        rule.runOnIdle {owner.registry.currentState=Lifecycle.State.RESUMED}
        rule.setContent {CompositionLocalProvider(LocalLifecycleOwner provides owner) {PhotoTubeTheme {OriginalDeleteScreen(server,setOf("b"),{},{})}}}
        prepareUi();executeUi()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("delete-original-password").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("delete-original-phrase").assertDoesNotExist()
        rule.onNodeWithText("删除确认已过期。请重新验证密码并核对完整清单。").assertExists()
        prepareUi()
        rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("delete-original-phrase").fetchSemanticsNodes().isEmpty()}
        rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)}
        rule.onNodeWithText("验证并查看文件清单").assertIsNotEnabled()
        Assert.assertEquals(2,requests.count {it.path!!.endsWith("/prepare")})
        Assert.assertEquals(1,requests.count {it.path=="/api/v1/assets/delete-original"})
    }
    @Test fun reopeningAfterLostLocalStateRequiresPasswordAndNewPreparation() {
        val requests=dispatch {json(done())}
        var open by mutableStateOf(true)
        rule.setContent {PhotoTubeTheme {if(open) OriginalDeleteScreen(server,setOf("b"),{},{})}}
        prepareUi()
        rule.runOnIdle {open=false};rule.waitForIdle();rule.runOnIdle {open=true}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("delete-original-password").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("delete-original-phrase").assertDoesNotExist()
        rule.onNodeWithText("验证并查看文件清单").assertIsNotEnabled()
        Assert.assertEquals(1,requests.count {it.path!!.endsWith("/prepare")})
        prepareUi()
        rule.onNodeWithText("trip/first.jpg").assertExists();rule.onNodeWithText("family/second.jpg").assertExists()
        Assert.assertEquals(2,requests.count {it.path!!.endsWith("/prepare")})
        Assert.assertEquals(0,requests.count {it.path=="/api/v1/assets/delete-original"})
    }
}
