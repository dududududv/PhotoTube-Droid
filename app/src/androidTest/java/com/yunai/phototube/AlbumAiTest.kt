package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.AlbumAiScreen
import com.yunai.phototube.ui.remote.AlbumPrompts
import com.yunai.phototube.ui.remote.albumPromptChanges
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class AlbumAiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var host: MockWebServer
    private lateinit var server: PhotoTubeServer
    private lateinit var repository: AlbumAiRepository
    private fun run(state: String = "SUCCEEDED", mode: String = "INCREMENTAL") = JSONObject()
        .put("id", "run-fixture").put("state", state).put("scanMode", mode).put("dryRun", false)
        .put("processedAssets", if (state == "SUCCEEDED") 8 else 0).put("totalAssets", 8)
        .put("cachedAssets", 2).put("providerAttemptCount", 1).put("skippedCount", 0)
        .put("conflictCount", 0).put("batchSize", 8).put("createdAt", "2026-09-16T12:00:00Z")
        .put("error", JSONObject.NULL).put("report", JSONArray().put(JSONObject().put("tagName", "海边")
            .put("matchedAssetCount", 8).put("sampleFileNames", JSONArray().put("beach.jpg"))))
    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host = MockWebServer().apply { useHttps(tls.sslSocketFactory(), false); start(java.net.InetAddress.getLoopbackAddress(),0) }
        server = PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext, host.url("/").newBuilder().host("localhost").build().toString(), {},
            OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build())
        repository = AlbumAiRepository(server)
    }
    @After fun cleanup() { server.cookies.clear(); server.dispose(); host.shutdown() }
    @Test fun usesArrayHistoryDirectCreationControlAndSeparateUndo() = runBlocking {
        host.enqueue(json(JSONArray().put(run())))
        Assert.assertEquals("海边", repository.history("album-fixture").single().report.single().name)
        Assert.assertEquals("album-fixture", host.takeRequest().requestUrl!!.queryParameter("albumId"))
        for (mode in AiScanMode.entries) {
            host.enqueue(json(run("PENDING", mode.name)))
            repository.create("album-fixture", mode)
            val request = host.takeRequest()
            Assert.assertEquals("/api/v1/tag-scans", request.path)
            val body = JSONObject(request.body.readUtf8())
            Assert.assertEquals(mode.name, body.getString("scanMode"))
            Assert.assertFalse(body.getBoolean("dryRun"))
            Assert.assertEquals("AI", body.getJSONArray("sources").getString(0))
            Assert.assertEquals("album-fixture", body.getJSONObject("scope").getString("albumId"))
        }
        for (action in AiTaskAction.entries) {
            host.enqueue(json(run("PAUSED")))
            repository.control("run-fixture", action)
            val request = host.takeRequest()
            Assert.assertEquals("/api/v1/tag-scans/run-fixture/control", request.path)
            Assert.assertEquals(action.name, JSONObject(request.body.readUtf8()).getString("action"))
        }
        host.enqueue(MockResponse().setResponseCode(204)); repository.undo("run-fixture")
        val undo = host.takeRequest(); Assert.assertEquals("DELETE", undo.method)
        Assert.assertEquals("/api/v1/tag-scans/run-fixture", undo.path)
    }
    @Test fun mediaScopesAreExplicitAndOlderTasksDefaultToPhotos() = runBlocking {
        Assert.assertEquals(AiMediaScope.PHOTO,albumAiRun(run()).mediaScope)
        for (mode in AiScanMode.entries) for (media in AiMediaScope.entries) {
            host.enqueue(json(run("PENDING",mode.name).put("mediaScope",media.name)))
            val result=repository.create("album-fixture",mode,media)
            Assert.assertEquals(media,result.mediaScope)
            val body=JSONObject(host.takeRequest().body.readUtf8())
            Assert.assertEquals(mode.name,body.getString("scanMode"))
            Assert.assertEquals(media.name,body.optString("mediaScope","PHOTO"))
            Assert.assertFalse(body.getBoolean("dryRun"))
        }
    }
    @Test fun promptUpdatesPreserveUntouchedFieldsAndResetWithNull() {
        val original=JSONObject().put("aiPrompt","照片提示").put("videoAiPrompt","视频提示")
        val changes=albumPromptChanges(original,"照片提示"," 新视频提示 ")
        Assert.assertEquals(1,changes.length());Assert.assertFalse(changes.has("aiPrompt"))
        Assert.assertEquals("新视频提示",changes.getString("videoAiPrompt"))
        Assert.assertTrue(albumPromptChanges(original,"照片提示","  ").isNull("videoAiPrompt"))
        Assert.assertFalse(albumPromptChanges(JSONObject().put("aiPrompt","旧提示"),"新提示","").has("videoAiPrompt"))
        try {albumPromptChanges(original,"x".repeat(4001),"");Assert.fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun videoPromptSaveFailureRetainsDraft() {
        val album=remoteAlbum(JSONObject().put("id","album-fixture").put("name","旅行").put("kind","NORMAL")
            .put("sortMode","TAKEN_AT_DESC").put("assetCount",8).put("cover",JSONObject.NULL).put("aiPrompt","照片提示").put("videoAiPrompt",JSONObject.NULL))
        var saved=false
        rule.setContent {PhotoTubeTheme {AlbumPrompts(AlbumRepository(server),album,{saved=true},{})}}
        rule.onNodeWithTag("video-ai-prompt").performScrollTo().performTextReplacement("描述视频中的动作\n保留场景变化")
        host.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"OFFLINE","message":"暂时无法保存"}"""))
        rule.onNodeWithText("保存提示词").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("暂时无法保存").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertFalse(saved)
        rule.onNodeWithTag("video-ai-prompt").performScrollTo().assertTextContains("描述视频中的动作\n保留场景变化")
        val body=JSONObject(host.takeRequest().body.readUtf8());Assert.assertEquals(1,body.length());Assert.assertEquals("描述视频中的动作\n保留场景变化",body.getString("videoAiPrompt"))
    }
    @Test fun preservesConflictCodeAndLogId() = runBlocking {
        host.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"AI_TASK_CONFLICT","message":"已有运行任务","logId":"log-fixture"}"""))
        try { repository.create("album-fixture", AiScanMode.INCREMENTAL); Assert.fail("Expected conflict") }
        catch (error: ServerError) { Assert.assertEquals("AI_TASK_CONFLICT", error.code); Assert.assertEquals("log-fixture", error.logId) }
        Assert.assertEquals(1, host.requestCount)
    }
    @Test fun fullConfirmationKeepsVideoScopeAndUndoOnlyTargetsCreatedRun() {
        val writes=java.util.concurrent.CopyOnWriteArrayList<Pair<String,String>>()
        val bodies=java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val path=request.requestUrl!!.encodedPath
                if(request.method=="POST") {
                    writes += "POST" to path
                    bodies += JSONObject(request.body.readUtf8())
                    return json(run(mode="FULL").put("mediaScope","VIDEO"))
                }
                if(request.method=="DELETE") {
                    writes += "DELETE" to path
                    return MockResponse().setResponseCode(204)
                }
                return json(if(bodies.isEmpty()) JSONArray() else JSONArray().put(run(mode="FULL").put("mediaScope","VIDEO")))
            }
        }
        val album=remoteAlbum(JSONObject().put("id","album-fixture").put("name","合成相册").put("kind","NORMAL")
            .put("sortMode","TAKEN_AT_DESC").put("assetCount",8).put("cover",JSONObject.NULL))
        rule.setContent {PhotoTubeTheme {AlbumAiScreen(server,album,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("尚无 AI 打标任务").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("ai-scope-VIDEO").performScrollTo().performClick()
        rule.onNodeWithText("全部重新打标").performScrollTo().performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithText("开始打标").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("ai-run-run-fixture").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals("FULL",bodies.single().getString("scanMode"))
        Assert.assertEquals("VIDEO",bodies.single().getString("mediaScope"))
        rule.onNodeWithText("撤销标签",useUnmergedTree=true).performScrollTo().performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        Assert.assertEquals(1,writes.size)
        rule.onNode(hasText("撤销标签") and hasAnyAncestor(hasTestTag("glass-confirmation"))).performClick()
        rule.waitUntil(10000) {writes.size==2}
        Assert.assertEquals(listOf("POST" to "/api/v1/tag-scans","DELETE" to "/api/v1/tag-scans/run-fixture"),writes.toList())
    }
    @Test fun fullAndUndoConfirmationsCancelWithoutMutation() {
        val writes=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method!="GET") {writes.incrementAndGet();return MockResponse().setResponseCode(405)}
                return json(JSONArray().put(run()))
            }
        }
        val album=remoteAlbum(JSONObject().put("id","album-fixture").put("name","合成相册").put("kind","NORMAL")
            .put("sortMode","TAKEN_AT_DESC").put("assetCount",8).put("cover",JSONObject.NULL))
        rule.setContent {PhotoTubeTheme {AlbumAiScreen(server,album,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("ai-run-run-fixture").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("全部重新打标").performScrollTo().performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithTag("ai-run-run-fixture").performScrollTo().performClick()
        rule.onNodeWithText("撤销标签",useUnmergedTree=true).performScrollTo().performClick()
        rule.onNodeWithText("撤销此批次标签？").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        Assert.assertEquals(0,writes.get())
    }
    @Test fun pausedRunResumesAndUndoRemainsSeparateFromCancel() {
        var state = "PAUSED"
        val actions = java.util.Collections.synchronizedList(mutableListOf<String>())
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST") {
                    val action = JSONObject(request.body.readUtf8()).getString("action")
                    actions.add(action)
                    state = if (action == "CANCEL") "CANCELLED" else "RUNNING"
                    return json(run(state))
                }
                if (request.method == "DELETE") { actions.add("UNDO"); return MockResponse().setResponseCode(204) }
                return if (request.requestUrl!!.encodedPath.endsWith("/run-fixture")) json(run(state)) else json(JSONArray().put(run(state)))
            }
        }
        val album = remoteAlbum(JSONObject().put("id", "album-fixture").put("name", "旅行").put("kind", "NORMAL")
            .put("sortMode", "TAKEN_AT_DESC").put("assetCount", 8).put("cover", JSONObject.NULL))
        rule.setContent { PhotoTubeTheme { AlbumAiScreen(server, album, {}, {}) } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("ai-run-run-fixture").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ai-incremental").assertIsNotEnabled()
        rule.onNodeWithTag("ai-run-run-fixture").performScrollTo().performClick()
        rule.onNodeWithText("继续", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(10000) { actions.contains("RESUME") }
        rule.waitUntil(10000) { rule.onAllNodesWithText("暂停", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("取消任务", useUnmergedTree = true).performScrollTo().performClick()
        rule.waitUntil(10000) { actions.contains("CANCEL") }
        Assert.assertFalse(actions.contains("UNDO"))
    }
    @Test fun entryDoesNotStartTaskAndExplicitStartShowsRealReport() {
        val writes = java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST") { writes.incrementAndGet(); return json(run()) }
                return json(if (writes.get() == 0) JSONArray() else JSONArray().put(run()))
            }
        }
        val album = remoteAlbum(JSONObject().put("id", "album-fixture").put("name", "旅行").put("kind", "NORMAL")
            .put("sortMode", "TAKEN_AT_DESC").put("assetCount", 8).put("cover", JSONObject.NULL))
        rule.setContent { PhotoTubeTheme { AlbumAiScreen(server, album, {}, {}) } }
        try {
            rule.waitUntil(10000) { rule.onAllNodesWithText("尚无 AI 打标任务").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            android.util.Log.e("AlbumAiTest", "History requests received: ${host.requestCount}")
            rule.onNodeWithTag("album-ai").printToLog("AlbumAiTest")
            throw failure
        }
        Assert.assertEquals(0, writes.get())
        rule.onNodeWithTag("ai-incremental").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("海边 · 8 张").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals(1, writes.get())
        rule.onNodeWithText("海边 · 8 张").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.getExternalFilesDir(null), "album-ai-preview.png").outputStream().use {
            rule.onNodeWithTag("album-ai").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
