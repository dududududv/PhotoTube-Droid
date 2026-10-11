package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import com.yunai.phototube.ui.media.MediaDetailsPanel
import com.yunai.phototube.ui.media.Photo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.RemoteMedia
import com.yunai.phototube.ui.remote.PhotoTagEditor
import com.yunai.phototube.ui.remote.TagsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class TagsTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun tag(name:String="海边")=JSONObject().put("id",9007199254740993L).put("name",name).put("assetCount",2)
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getByName("127.0.0.1"),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("127.0.0.1").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun tagCrudAndAssetRelationsUseDistinctEndpoints()=runBlocking {
        val repository=TagRepository(server)
        host.enqueue(json(tag()));val result=repository.create(" 海边 ")
        Assert.assertEquals(9007199254740993L,result.id)
        Assert.assertEquals("海边",JSONObject(host.takeRequest().body.readUtf8()).getString("name"))
        host.enqueue(json(tag("海岸")));repository.rename(result.id,"海岸")
        val rename=host.takeRequest();Assert.assertEquals("PATCH",rename.method);Assert.assertEquals("/api/v1/tags/9007199254740993",rename.path)
        host.enqueue(json(JSONObject().put("id",result.id).put("name","海岸").put("source","MANUAL").put("confirmed",false)))
        repository.attach("photo",result.id)
        val attach=host.takeRequest();Assert.assertEquals("/api/v1/assets/photo/tags",attach.path)
        val body=JSONObject(attach.body.readUtf8());Assert.assertEquals(1,body.length());Assert.assertEquals(result.id,body.getLong("tagId"))
        host.enqueue(MockResponse().setResponseCode(204));repository.detach("photo",result.id)
        val detach=host.takeRequest();Assert.assertEquals("DELETE",detach.method);Assert.assertEquals("/api/v1/assets/photo/tags/9007199254740993",detach.path)
        host.enqueue(json(JSONObject().put("affectedAssetCount",2)));repository.delete(result.id)
        Assert.assertEquals("/api/v1/tags/9007199254740993",host.takeRequest().path)
    }
    @Test fun libraryRenameAndDeleteRequireExplicitActions() {
        var name="海边";var deleted=false
        val writes=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="PATCH") {writes.incrementAndGet();name=JSONObject(request.body.readUtf8()).getString("name");return json(tag(name))}
                if(request.method=="DELETE") {writes.incrementAndGet();deleted=true;return json(JSONObject().put("affectedAssetCount",2))}
                return json(JSONObject().put("items",if(deleted) JSONArray() else JSONArray().put(tag(name))).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {TagsScreen(server,{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("海边").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(0,writes.get())
        rule.onNodeWithTag("tag-actions-9007199254740993").performClick()
        rule.onNodeWithText("重命名").performClick()
        rule.onNode(hasSetTextAction() and hasText("海边")).performTextReplacement("海岸")
        rule.onNodeWithText("保存标签").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("海岸").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("tag-actions-9007199254740993").performClick()
        rule.onNodeWithText("删除标签").performClick();Assert.assertEquals(1,writes.get())
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        rule.onNodeWithText("取消",substring=false).performClick()
        Assert.assertEquals(1,writes.get())
        rule.onNodeWithTag("tag-actions-9007199254740993").performClick()
        rule.onNodeWithText("删除标签").performClick()
        rule.onNodeWithText("确认删除标签").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("没有匹配的标签").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(2,writes.get())
    }
    @Test fun detachSuccessAndRefreshFailureRetriesOnlyRead() {
        val deletes=java.util.concurrent.atomic.AtomicInteger()
        val reads=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="DELETE") {deletes.incrementAndGet();return MockResponse().setResponseCode(204)}
                if(reads.incrementAndGet()==1) return MockResponse().setResponseCode(503).setBody("""{"code":"OFFLINE","message":"暂时离线"}""")
                return json(JSONObject().put("tags",JSONArray()))
            }
        }
        val source=RemoteMedia(RemoteAsset("photo","PHOTO","BROWSABLE","2026-09-16T00:00:00Z",0,"海边.jpg",null,10,10,false,null),server)
        source.detail=JSONObject().put("tags",JSONArray().put(JSONObject().put("id",7).put("name","海边").put("source","AI").put("confirmed",false)))
        rule.setContent {PhotoTubeTheme {PhotoTagEditor(source,{},{})}}
        rule.onNodeWithTag("remove-photo-tag-7").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("操作已提交，刷新标签失败：暂时离线").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("重新读取标签").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("这张照片还没有标签").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,deletes.get());Assert.assertEquals(2,reads.get())
    }
    @Test fun unconfirmedAutomaticTagsRemainVisible() {
        val result=photoTags(JSONObject().put("tags",JSONArray().put(JSONObject().put("id",4).put("name","海边").put("source","AI").put("confirmed",false))))
        Assert.assertEquals("海边",result.single().name)
    }

    @Test fun externalUpdatesRefreshOnForegroundAndPullWithoutClearingSearch() {
        val current = java.util.concurrent.atomic.AtomicReference("海边")
        val count = java.util.concurrent.atomic.AtomicInteger(2)
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val queries = java.util.concurrent.CopyOnWriteArrayList<String>()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Assert.assertEquals("GET", request.method)
                Assert.assertEquals("/api/v1/tags", request.requestUrl!!.encodedPath)
                reads.incrementAndGet()
                queries += request.requestUrl!!.queryParameter("keyword").orEmpty()
                return json(JSONObject().put("items",JSONArray().put(tag(current.get()).put("assetCount",count.get())))
                    .put("nextCursor",JSONObject.NULL))
            }
        }
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        rule.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        rule.setContent { androidx.compose.runtime.CompositionLocalProvider(
            androidx.lifecycle.compose.LocalLifecycleOwner provides owner
        ) { PhotoTubeTheme { TagsScreen(server,{}) } } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("海边").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction() and hasText("搜索标签")).performTextReplacement("海")
        rule.waitUntil(10000) { queries.lastOrNull() == "海" }
        rule.waitForIdle()
        val before = reads.get()
        rule.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.STARTED }
        current.set("海岸"); count.set(7)
        rule.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        rule.waitUntil(10000) { rule.onAllNodesWithText("海岸").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("7 项内容").assertIsDisplayed()
        Assert.assertTrue(reads.get() > before)
        current.set("海港"); count.set(9)
        rule.onNodeWithTag("tag-list").performScrollToIndex(0).performTouchInput { swipeDown(durationMillis=600) }
        rule.waitUntil(10000) { rule.onAllNodesWithText("海港").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("9 项内容").assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText("海",substring=false)).assertExists()
        Assert.assertEquals("海",queries.last())
    }
    @Test fun detailPanelRetriesAttachmentWithoutCreatingTagTwice() {
        val creates=java.util.concurrent.atomic.AtomicInteger()
        val attaches=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                return when {
                    request.method=="POST" && request.requestUrl!!.encodedPath=="/api/v1/tags" -> {
                        creates.incrementAndGet(); json(tag("新标签"))
                    }
                    request.method=="POST" -> {
                        if(attaches.incrementAndGet()==1) MockResponse().setResponseCode(503)
                            .setBody("""{"code":"OFFLINE","message":"暂时离线"}""")
                        else json(JSONObject())
                    }
                    request.requestUrl!!.encodedPath=="/api/v1/tags" -> json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
                    else -> json(JSONObject().put("tags",JSONArray().put(tag("新标签").put("source","MANUAL"))))
                }
            }
        }
        val source=RemoteMedia(RemoteAsset("photo","PHOTO","BROWSABLE","2026-09-16T00:00:00Z",0,"海边.jpg",null,10,10,false,null),server)
        source.detail=JSONObject().put("tags",JSONArray())
        rule.setContent { PhotoTubeTheme { BoxWithConstraints(Modifier.fillMaxSize()) {
            MediaDetailsPanel(Photo(1,"海边.jpg","2026年9月16日",remote=source),null,
                constraints.maxHeight.toFloat(),remember { Animatable(.88f) },{}, {})
        } } }
        rule.onNodeWithText("编辑标签").performClick()
        rule.onNodeWithText("添加标签").performClick()
        rule.onNodeWithTag("detail-tag-search").performTextInput("新标签")
        rule.waitUntil(10000) {rule.onAllNodesWithText("新建“新标签”并添加").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("新建“新标签”并添加").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("暂时离线").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("新建“新标签”并添加").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("photo-tags-panel").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("新标签").assertIsDisplayed()
        Assert.assertEquals(1,creates.get()); Assert.assertEquals(2,attaches.get())
    }

}
