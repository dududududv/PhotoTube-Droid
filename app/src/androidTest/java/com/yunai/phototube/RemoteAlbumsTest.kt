package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.RemoteAlbums
import com.yunai.phototube.ui.remote.AlbumScanDialog
import com.yunai.phototube.ui.remote.AlbumMemberPicker
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class RemoteAlbumsTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private lateinit var repository:AlbumRepository
    private fun album(name:String="旅行")=JSONObject().put("id","album-fixture").put("name",name).put("kind","NORMAL").put("sortMode","TAKEN_AT_DESC").put("assetCount",0).put("cover",JSONObject.NULL).put("coverFocalPoint",JSONObject.NULL)
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
        repository=AlbumRepository(server)
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun albumContractPaginationAndWrites()=runBlocking {
        host.enqueue(json(JSONObject().put("items",JSONArray().put(album())).put("nextCursor","opaque+/=")))
        val page=repository.list(null)
        Assert.assertNull(page.items.single().cover)
        host.takeRequest()
        host.enqueue(json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        repository.list(page.cursor)
        Assert.assertEquals("opaque+/=",host.takeRequest().requestUrl!!.queryParameter("cursor"))
        val create=JSONObject().put("name","家庭").put("kind","PATH_SYNC").put("paths",JSONArray().put(JSONObject().put("libraryId","library-fixture").put("relativePath","Photos/Family").put("enabled",true)))
        host.enqueue(json(album("家庭")))
        repository.create(create)
        val creation=host.takeRequest()
        Assert.assertEquals("/api/v1/albums",creation.path)
        Assert.assertEquals("Photos/Family",JSONObject(creation.body.readUtf8()).getJSONArray("paths").getJSONObject(0).getString("relativePath"))
        Assert.assertEquals(3,host.requestCount) // creation never starts a scan
        host.enqueue(json(album("改名")))
        repository.update("album-fixture",JSONObject().put("name","改名").put("sortMode","TAKEN_AT_ASC"))
        val update=host.takeRequest();Assert.assertEquals("PATCH",update.method)
        Assert.assertEquals("TAKEN_AT_ASC",JSONObject(update.body.readUtf8()).getString("sortMode"))
        host.enqueue(MockResponse().setResponseCode(204));repository.delete("album-fixture")
        Assert.assertEquals("DELETE",host.takeRequest().method)
        host.enqueue(json(JSONObject().put("id","run-fixture").put("state","PENDING")))
        repository.scan("album-fixture")
        val scan=host.takeRequest();Assert.assertEquals("/api/v1/albums/album-fixture/syncs",scan.path);Assert.assertEquals("INCREMENTAL",JSONObject(scan.body.readUtf8()).getString("mode"))
    }
    @Test fun scanModeAndExclusiveCleanupAreExplicit()=runBlocking {
        host.enqueue(json(JSONObject().put("id","run-fixture").put("state","PENDING")))
        repository.scan("album-fixture","FULL")
        Assert.assertEquals("FULL",JSONObject(host.takeRequest().body.readUtf8()).getString("mode"))
        host.enqueue(MockResponse().setResponseCode(204))
        repository.delete("album-fixture",true)
        Assert.assertEquals("true",host.takeRequest().requestUrl!!.queryParameter("cleanupExclusiveAssets"))
        host.enqueue(MockResponse().setResponseCode(204))
        repository.delete("album-fixture")
        Assert.assertEquals("false",host.takeRequest().requestUrl!!.queryParameter("cleanupExclusiveAssets"))
    }
    @Test fun batchRemovalKeepsFailedSelectionAndNeverTrashesAssets() {
        val removals=java.util.Collections.synchronizedList(mutableListOf<List<String>>())
        var closed=false
        fun asset(id:String)=JSONObject().put("id",id).put("kind","PHOTO").put("state","BROWSABLE")
            .put("takenAt","2026-09-16T12:00:00Z").put("takenAtOffsetMinutes",0).put("fileName","$id.jpg")
            .put("contentHash",JSONObject.NULL).put("width",100).put("height",100).put("favorite",false).put("durationSec",JSONObject.NULL)
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                Assert.assertEquals("/api/v1/albums/album-fixture/assets",request.requestUrl!!.encodedPath)
                if(request.method=="DELETE") {
                    val ids=JSONObject(request.body.readUtf8()).getJSONArray("assetIds")
                    removals.add((0 until ids.length()).map(ids::getString))
                    return if(removals.size==1) json(JSONObject().put("succeeded",JSONArray().put("a"))
                        .put("failed",JSONArray().put(JSONObject().put("target","b").put("code","CONFLICT").put("message","稍后重试"))))
                    else json(JSONObject().put("succeeded",JSONArray().put("b")).put("failed",JSONArray()))
                }
                return json(JSONObject().put("album",album()).put("assets",JSONObject().put("items",JSONArray().put(asset("a")).put(asset("b"))).put("nextCursor",JSONObject.NULL)))
            }
        }
        rule.setContent {PhotoTubeTheme {AlbumMemberPicker(repository,remoteAlbum(album()),{},onClose={closed=true},remove=true)}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("member-select-b").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("member-select-a").performClick();rule.onNodeWithTag("member-select-b").performClick()
        rule.onNodeWithText("移除 2 项").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("1 项已移除；稍后重试").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("移除 1 项").performClick()
        rule.waitUntil(10000) {closed}
        Assert.assertEquals(setOf("a","b"),removals[0].toSet());Assert.assertEquals(listOf("b"),removals[1])
    }
    @Test fun scanHistoryPreservesCursorAndOpeningOldRunCannotStartScan() {
        val oldRun=JSONObject().put("id","old-run").put("state","SUCCEEDED").put("succeededPaths",1)
        kotlinx.coroutines.runBlocking {
            host.enqueue(json(JSONObject().put("items",JSONArray().put(oldRun)).put("nextCursor","opaque+/=")))
            val first=repository.scanHistory("album-fixture",null)
            Assert.assertEquals("old-run",first.items.single().getString("id"))
            Assert.assertEquals("GET",host.takeRequest().method)
            host.enqueue(json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
            repository.scanHistory("album-fixture",first.cursor)
            Assert.assertEquals("opaque+/=",host.takeRequest().requestUrl!!.queryParameter("cursor"))
        }
        host.enqueue(json(JSONObject().put("run",oldRun).put("pathResults",JSONArray())))
        rule.setContent {PhotoTubeTheme {AlbumScanDialog(repository,remoteAlbum(album().put("kind","PATH_SYNC")),onClose={},historyRun=oldRun)}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("状态：已完成",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("开始扫描").assertDoesNotExist()
        Assert.assertEquals("/api/v1/albums/album-fixture/syncs/old-run",host.takeRequest().path)
        Assert.assertEquals(3,host.requestCount)
    }
    @Test fun pathPreviewAndApplyAreIdenticalAndCoverIsFocalOnly()=runBlocking {
        val change=JSONObject().put("action","UPDATE").put("expectedGeneration",7).put("pathId","path-fixture")
            .put("path",JSONObject().put("libraryId","library-fixture").put("relativePath","Photos/New").put("enabled",true))
        host.enqueue(json(JSONObject().put("configGeneration",7).put("membersToRemove",2)))
        repository.previewPath("album-fixture",change)
        val preview=host.takeRequest()
        host.enqueue(json(JSONObject().put("configGeneration",8)))
        repository.applyPath("album-fixture",change)
        val apply=host.takeRequest()
        Assert.assertEquals(preview.body.readUtf8(),apply.body.readUtf8())
        Assert.assertTrue(preview.path!!.endsWith("/path-change-previews"))
        Assert.assertTrue(apply.path!!.endsWith("/path-changes"))
        host.enqueue(json(album()))
        repository.update("album-fixture",JSONObject().put("coverAssetId","asset-fixture").put("coverFocalPoint",JSONObject().put("x",.2).put("y",.8)))
        val cover=JSONObject(host.takeRequest().body.readUtf8())
        Assert.assertEquals(.2,cover.getJSONObject("coverFocalPoint").getDouble("x"),.001)
        Assert.assertEquals(2,cover.length())
        host.enqueue(json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray().put(JSONObject().put("target","b").put("code","NOT_FOUND").put("message","已移除")))))
        val result=repository.changeMembers("album-fixture",setOf("a","b"))
        Assert.assertEquals("b",result.getJSONArray("failed").getJSONObject(0).getString("target"))
        Assert.assertEquals(2,JSONObject(host.takeRequest().body.readUtf8()).getJSONArray("assetIds").length())
        host.enqueue(json(JSONObject().put("succeeded",JSONArray().put("a")).put("failed",JSONArray())))
        repository.changeMembers("album-fixture",setOf("a"),remove=true)
        Assert.assertEquals("DELETE",host.takeRequest().method)
    }
    @Test fun scanNeedsExplicitClickAndPollsToTerminal() {
        val starts=java.util.concurrent.atomic.AtomicInteger()
        val polls=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST") {starts.incrementAndGet();return json(JSONObject().put("id","scan-fixture").put("state","PENDING"))}
                if(request.method=="GET" && request.requestUrl!!.encodedPath.endsWith("/syncs")) return json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
                val state=if(polls.incrementAndGet()==1) "RUNNING" else "SUCCEEDED"
                return json(JSONObject().put("run",JSONObject().put("id","scan-fixture").put("state",state).put("succeededPaths",if(state=="SUCCEEDED") 1 else 0)).put("pathResults",JSONArray()))
            }
        }
        rule.setContent {PhotoTubeTheme {AlbumScanDialog(repository,remoteAlbum(album().put("kind","PATH_SYNC")),onClose={})}}
        rule.waitForIdle();Assert.assertEquals(0,starts.get())
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("状态：已完成",substring=true).fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,starts.get());Assert.assertEquals(2,polls.get())
    }
    @Test fun folderCreationRequiresSelectionAndDoesNotScan() {
        val calls=java.util.Collections.synchronizedList(mutableListOf<String>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                calls+=request.method+" "+request.requestUrl!!.encodedPath
                val url=request.requestUrl!!
                if(url.encodedPath.endsWith("source-folders")) return json(JSONObject().put("libraryId","library-fixture").put("path",url.queryParameter("path")).put("items",JSONArray().apply {if(url.queryParameter("path").isNullOrEmpty()) put(JSONObject().put("name","Photos").put("path","Photos"))}))
                if(request.method=="POST") {
                    val body=JSONObject(request.body.readUtf8());Assert.assertEquals("PATH_SYNC",body.getString("kind"));Assert.assertEquals("Photos",body.getJSONArray("paths").getJSONObject(0).getString("relativePath"))
                    return json(album("家庭").put("kind","PATH_SYNC"))
                }
                if(url.encodedPath.endsWith("/assets")) return json(JSONObject().put("album",album("家庭").put("kind","PATH_SYNC")).put("assets",JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
                return json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.CreateRemoteAlbum(repository,onClose={},onCreated={})}}
        rule.onNode(hasSetTextAction()).performTextReplacement("家庭")
        rule.onNodeWithText("同步目录").performClick()
        rule.onNodeWithText("创建").assertIsNotEnabled()
        rule.waitUntil(10000) {rule.onAllNodesWithText("Photos").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("Photos").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("选择此目录").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("选择此目录").performClick()
        rule.onNodeWithText("创建").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertFalse(calls.any {it.contains("/syncs")})
    }
    @Test fun albumPageZoomsFromCoverAndStaysMountedUntilExitFinishes() {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse =
                if(request.requestUrl!!.encodedPath.endsWith("/assets")) json(JSONObject().put("album",album()).put("assets",JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
                else json(JSONObject().put("items",JSONArray().put(album())).put("nextCursor",JSONObject.NULL))
        }
        rule.setContent {PhotoTubeTheme {RemoteAlbums(server,true,0.dp,onConnect={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-album-fixture").fetchSemanticsNodes().isNotEmpty()}
        rule.mainClock.autoAdvance=false
        rule.onNodeWithTag("album-album-fixture").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(80)
        val entering=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(1200)
        val expanded=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInRoot.width
        Assert.assertTrue("Album should grow from its cover",entering<expanded-1f)
        var rootNode=rule.onNodeWithTag("album-grid").fetchSemanticsNode()
        while(rootNode.parent!=null) rootNode=rootNode.parent!!
        val root=rootNode.boundsInRoot
        Assert.assertEquals("Expanded album must fill the window width",root.width,expanded,1f)
        Assert.assertEquals("Expanded album must fill the window height",root.height,rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInRoot.height,1f)
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("album-detail").assertExists()
        Assert.assertTrue("Album should shrink before disposal",rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInRoot.width<expanded-1f)
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("album-detail").assertDoesNotExist()
        rule.mainClock.autoAdvance=true
    }
    @Test fun albumExitUsesCurrentCoverAndDoesNotReuseDetachedCover() {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = json(JSONObject().put("album",album())
                .put("assets",JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        }
        val opening=androidx.compose.ui.geometry.Rect(20f,120f,220f,320f)
        var current:androidx.compose.ui.geometry.Rect?=opening
        var closed=0
        var generation by androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {PhotoTubeTheme { androidx.compose.runtime.key(generation) {
            com.yunai.phototube.ui.remote.RemoteAlbumDetail(repository,remoteAlbum(album()),opening,{}, {},{closed++},resolveSource={current})
        } }}
        rule.waitForIdle()
        rule.mainClock.autoAdvance=false
        try {
            current=androidx.compose.ui.geometry.Rect(180f,280f,480f,580f)
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            val landed=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInWindow
            Assert.assertEquals(current.left,landed.left,1f)
            Assert.assertEquals(current.top,landed.top,1f)
            Assert.assertEquals(current.width,landed.width,1f)
            Assert.assertEquals(1,closed)
            // Compose a second route, then detach its source before closing.
            rule.runOnUiThread {generation++}
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            val expanded=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInWindow
            Assert.assertTrue("Second route must finish expanding before the fade check",expanded.width>opening.width*2)
            current=null
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.mainClock.advanceTimeBy(128)
            val fading=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInWindow
            Assert.assertEquals("Detached cover must fade in place, not shrink to the old rectangle",expanded,fading)
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            Assert.assertEquals(2,closed)
            rule.runOnUiThread {generation++}
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(64)
            rule.waitForIdle()
            val interrupted=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInWindow
            Assert.assertTrue("Third route must still be entering",interrupted.width<expanded.width)
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val closingFrame=rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInWindow
            Assert.assertTrue("Interrupted dismissal must not expand to full screen",closingFrame.width<expanded.width)
            rule.mainClock.advanceTimeBy(64)
            rule.waitForIdle()
            Assert.assertEquals("Missing source must hold its first dismissal geometry while fading",closingFrame,
                rule.onNodeWithTag("album-grid").fetchSemanticsNode().boundsInWindow)
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            Assert.assertEquals(3,closed)
        } finally {rule.mainClock.autoAdvance=true}
    }

    @Test fun squareAlbumOpensRealEmptyDetailAndRenames() {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            var name="旅行"
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="PATCH") {name=JSONObject(request.body.readUtf8()).getString("name");return json(album(name))}
                if(request.requestUrl!!.encodedPath.endsWith("/assets")) return json(JSONObject().put("album",album(name)).put("assets",JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
                return json(JSONObject().put("items",JSONArray().put(album(name))).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {RemoteAlbums(server,true,0.dp,onConnect={})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-album-fixture").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("空相册").assertExists()
        rule.onNodeWithTag("album-album-fixture").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("相册中还没有内容").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("重命名").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("真实相册")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-confirmation").fetchSemanticsNodes().isEmpty()}
        rule.waitUntil(10000) {rule.onAllNodesWithText("真实相册").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty()}
        rule.waitUntil(10000) {rule.onAllNodesWithText("真实相册").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("真实相册").assertExists()
    }
}
