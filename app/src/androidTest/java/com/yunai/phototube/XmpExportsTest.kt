package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.XmpExportsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class XmpExportsTest {
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
    private fun run(state:String="SUCCEEDED")=JSONObject().put("id","run-one").put("state",state).put("libraryIds",JSONArray().put("library-one")).put("includePrivate",false)
        .put("matchedCount",7).put("succeededCount",6).put("failedCount",1).put("attemptCount",1).put("snapshotId","snapshot-one")
        .put("snapshotAt","2026-09-17T00:00:00Z").put("logicalPath","xmp/snapshot-one").put("errorCode",JSONObject.NULL).put("createdAt","2026-09-17T00:00:00Z").put("finishedAt",JSONObject.NULL)
    private fun preview()=JSONObject().put("resolvedLibraryIds",JSONArray().put("library-one")).put("includePrivate",false).put("estimatedAssetCount",10).put("estimatedAt","2026-09-17T00:00:00Z")
    @Test fun previewResolvesScopeAndCreationUsesExactIdsWithoutPaths()=runBlocking {
        val repository=XmpRepository(server)
        host.enqueue(json(preview()))
        val estimate=repository.preview(XmpScope())
        val request=host.takeRequest();val body=JSONObject(request.body.readUtf8())
        Assert.assertEquals("/api/v1/xmp-exports/preview",request.requestUrl!!.encodedPath)
        Assert.assertEquals(false,body.get("includePrivate"));Assert.assertFalse(body.has("libraryIds"))
        host.enqueue(json(run("PENDING")).setResponseCode(202))
        repository.create(estimate.scope)
        val create=JSONObject(host.takeRequest().body.readUtf8())
        Assert.assertEquals(setOf("includePrivate","libraryIds"),create.keys().asSequence().toSet())
        Assert.assertEquals("library-one",create.getJSONArray("libraryIds").getString(0))
        host.enqueue(json(JSONObject().put("items",JSONArray().put(run("PARTIAL"))).put("nextCursor","opaque+/=")))
        val page=repository.list(null);host.takeRequest()
        Assert.assertEquals("PARTIAL",page.items.single().state);Assert.assertFalse(page.items.single().active)
        host.enqueue(json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        repository.list(page.cursor)
        Assert.assertEquals("opaque+/=",host.takeRequest().requestUrl!!.queryParameter("cursor"))
        Assert.assertTrue(kotlin.runCatching {XmpScope(emptySet()).body()}.isFailure)
    }
    @Test fun previewRejectsSilentPrivateScopeChanges()=runBlocking {
        host.enqueue(json(preview()))
        try {XmpRepository(server).preview(XmpScope(includePrivate=true));Assert.fail()} catch(_:IllegalArgumentException) {}
        val sent=JSONObject(host.takeRequest().body.readUtf8())
        Assert.assertTrue(sent.getBoolean("includePrivate"))
        Assert.assertEquals(1,host.requestCount)
    }
    @Test fun privateExportRequiresUnlockAndExplicitConfirmation() {
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                val path=request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("/auth/private-access") -> MockResponse().setResponseCode(204)
                    path.endsWith("/auth/session") -> json("""{"authenticated":true,"privateAccessUnlocked":true,"privateAccessExpiresAt":"${java.time.Instant.now().plusSeconds(600)}"}""")
                    path.endsWith("/health") -> json("""{"xmpExport":{"available":true}}""")
                    path.endsWith("/preview") -> json(preview().put("includePrivate",true))
                    request.method=="POST" -> json(run("PENDING").put("includePrivate",true)).setResponseCode(202)
                    path.endsWith("/run-one") -> json(run().put("includePrivate",true))
                    else -> json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
                }
            }
        }
        val owner=object:LifecycleOwner {val registry=LifecycleRegistry.createUnsafe(this);override val lifecycle:Lifecycle get()=registry}
        rule.runOnIdle {owner.registry.currentState=Lifecycle.State.RESUMED}
        rule.setContent {CompositionLocalProvider(LocalLifecycleOwner provides owner) {PhotoTubeTheme {XmpExportsScreen(server,{},initialLibraries=setOf("library-one"))}}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("包含私密内容的导出…").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("包含私密内容的导出…").performClick()
        Assert.assertTrue(requests.none {it.method=="POST"})
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("本次包含普通和私密照片，并非仅导出私密照片。").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("xmp-preview-private").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("xmp-preview-private").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("确认创建导出？").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("包含私密照片的元数据。确认后，即使锁定私密空间，已创建的后台导出仍会继续。").assertExists()
        Assert.assertTrue(requests.none {it.method=="POST" && it.requestUrl!!.encodedPath=="/api/v1/xmp-exports"})
        rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("private-password").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("确认创建导出？").assertDoesNotExist()
        Assert.assertTrue(requests.none {it.method=="POST" && it.requestUrl!!.encodedPath=="/api/v1/xmp-exports"})
        rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)}
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("xmp-preview-private").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("确认创建导出？").assertDoesNotExist()
        rule.onNodeWithTag("xmp-preview-private").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("确认创建导出？").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("创建导出").performClick()
        rule.waitUntil(10000) {requests.any {it.method=="POST" && it.requestUrl!!.encodedPath=="/api/v1/xmp-exports"}}
        val writes=requests.filter {it.method=="POST" && it.requestUrl!!.encodedPath.startsWith("/api/v1/xmp-exports")}
        Assert.assertEquals(3,writes.size)
        writes.forEach {
            val body=JSONObject(it.body.clone().readUtf8())
            Assert.assertTrue(body.getBoolean("includePrivate"))
            Assert.assertEquals("library-one",body.getJSONArray("libraryIds").getString(0))
        }
        val created=JSONObject(writes.last().body.clone().readUtf8())
        Assert.assertEquals("library-one",created.getJSONArray("libraryIds").getString(0))
    }
    @Test fun unconfiguredStorageDisablesPreviewButKeepsHistoryVisible() {
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        val configured=java.util.concurrent.atomic.AtomicBoolean(false)
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return if(request.requestUrl!!.encodedPath.endsWith("/health")) json(JSONObject().put("xmpExport",JSONObject().put("available",configured.get())))
                else json(JSONObject().put("items",JSONArray().put(run())).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {XmpExportsScreen(server,{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("服务器尚未配置导出存储。历史仍可查看。").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("预估导出").assertIsNotEnabled()
        rule.waitUntil(10000) {rule.onAllNodesWithText("导出完成").fetchSemanticsNodes().isNotEmpty()}
        configured.set(true)
        rule.onNodeWithText("重新检查配置").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("xmp-preview-normal").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("服务器尚未配置导出存储。历史仍可查看。").assertDoesNotExist()
        Assert.assertEquals(2,requests.count {it.requestUrl!!.encodedPath.endsWith("/health")})
        Assert.assertTrue(requests.all {it.method=="GET"})
    }
    @Test fun estimateNeverCreatesUntilConfirmedAndTerminalPollingStops() {
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return when {
                    request.requestUrl!!.encodedPath.endsWith("/health") -> json("""{"xmpExport":{"available":true}}""")
                    request.requestUrl!!.encodedPath.endsWith("/preview") -> json(preview())
                    request.method=="POST" -> json(run("PENDING")).setResponseCode(202)
                    request.requestUrl!!.encodedPath.endsWith("/run-one") -> json(run("PARTIAL"))
                    else -> json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
                }
            }
        }
        rule.setContent {PhotoTubeTheme {XmpExportsScreen(server,{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("预估导出").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(10000) {requests.any {it.requestUrl!!.encodedPath.endsWith("/health")}}
        rule.waitForIdle()
        rule.onNodeWithText("预估导出").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("确认创建导出？").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertFalse(requests.any {it.method=="POST" && it.requestUrl!!.encodedPath=="/api/v1/xmp-exports"})
        rule.onNodeWithText("创建导出").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("部分完成").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("匹配 7 · 成功 6 · 失败 1").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(10000);rule.waitForIdle()
        Assert.assertEquals(1,requests.count {it.requestUrl!!.encodedPath.endsWith("/run-one")})
        val create=requests.single {it.method=="POST" && it.requestUrl!!.encodedPath=="/api/v1/xmp-exports"}
        Assert.assertEquals("library-one",JSONObject(create.body.readUtf8()).getJSONArray("libraryIds").getString(0))
    }
}
