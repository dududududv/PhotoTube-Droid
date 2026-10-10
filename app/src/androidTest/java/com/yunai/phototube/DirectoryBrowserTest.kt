package com.yunai.phototube

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.DirectoryBrowser
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.json.*
import org.junit.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class DirectoryBrowserTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private val requests=CopyOnWriteArrayList<RecordedRequest>()
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},
            OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager)
                .addInterceptor { chain ->
                    check(chain.request().url.host == "localhost")
                    chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
                }.build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    private fun node(library:String?=null,path:String="",count:Long=0,children:Boolean=false,online:Boolean=true):JSONObject =
        JSONObject().put("kind",if(library==null) "ROOT" else if(path.isEmpty()) "LIBRARY" else "DIRECTORY")
            .put("name",if(library==null) "媒体库" else if(path.isEmpty()) library else path.substringAfterLast('/'))
            .put("path",path).put("online",online).put("directAssetCount",count).put("hasChildren",children)
            .apply {library?.let {put("libraryId",it)}}
    private fun listing(current:JSONObject,children:List<JSONObject>,cursor:String?=null)=JSONObject().put("current",current)
        .put("children",JSONObject().put("items",JSONArray(children)).put("nextCursor",cursor ?: JSONObject.NULL))
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun asset()=JSONObject().put("id","photo").put("fileName","旅行照片.png").put("takenAt","2026-09-17T00:00:00Z")
        .put("kind","PHOTO").put("state","BROWSABLE").put("favorite",false).put("width",48).put("height",32).put("contentHash","a".repeat(64))

    @Test fun aggregationKeepsLibraryIdentityCountsAndOpaqueCursor()=runBlocking {
        val repository=DirectoryRepository(server)
        host.enqueue(json(listing(node(),listOf(node("library-a",count=9007199254740993L)),"roots+/=")))
        val root=repository.list(null,"",null)
        Assert.assertEquals(9007199254740993L,root.children.items.single().directAssetCount)
        Assert.assertEquals("roots+/=",root.children.cursor)
        Assert.assertNull(host.takeRequest().requestUrl!!.queryParameter("libraryId"))
        host.enqueue(json(listing(node("library-a","旅行"),listOf(node("library-a","旅行/海边",count=1)),"children+/=")))
        repository.list("library-a","旅行","cursor+/=")
        val request=host.takeRequest().requestUrl!!
        Assert.assertEquals("library-a",request.queryParameter("libraryId"))
        Assert.assertEquals("旅行",request.queryParameter("path"))
        Assert.assertEquals("cursor+/=",request.queryParameter("cursor"))
        host.enqueue(json(listing(node("library-b","旅行"),listOf(node("library-b","旅行/海边",count=7)))))
        val other=repository.list("library-b","旅行",null)
        Assert.assertEquals("library-b",other.children.items.single().libraryId)
        Assert.assertEquals(7L,other.children.items.single().directAssetCount)
    }
    @Test fun offlineAndWrongScopeAreErrorsNotEmptyDirectories()=runBlocking {
        val repository=DirectoryRepository(server)
        host.enqueue(json(JSONObject().put("code","LIBRARY_OFFLINE").put("message","媒体库离线")).setResponseCode(503))
        val offline=runCatching {repository.list("library-a","",null)}.exceptionOrNull()
        Assert.assertTrue(offline is ServerError)
        host.enqueue(json(listing(node("library-b"),emptyList())))
        Assert.assertTrue(runCatching {repository.list("library-a","",null)}.isFailure)
        host.enqueue(json(listing(node("library-a"),listOf(node("library-b","旅行")))))
        Assert.assertTrue(runCatching {repository.list("library-a","",null)}.isFailure)
        host.enqueue(json(listing(node("library-a"),listOf(node("library-a","旅行/海边")))))
        Assert.assertTrue(runCatching {repository.list("library-a","",null)}.isFailure)
    }
    private fun serve(failRootOnce:Boolean=false) {
        val failure=AtomicBoolean(failRootOnce)
        val png=ByteArrayOutputStream().apply {Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(100,140,155))}.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        host.dispatcher=object:Dispatcher() {override fun dispatch(request:RecordedRequest):MockResponse {
            requests+=request
            Assert.assertEquals("GET",request.method)
            val url=request.requestUrl!!
            return when(url.encodedPath) {
                "/api/v1/folders" -> {
                    val library=url.queryParameter("libraryId")
                    val path=url.queryParameter("path").orEmpty()
                    if(library==null && failure.compareAndSet(true,false)) json(JSONObject().put("code","UNAVAILABLE").put("message","目录暂时不可用")).setResponseCode(503)
                    else if(library==null) json(listing(node(children=true),listOf(node("library-a",count=2,children=true),node("library-b",online=false))))
                    else if(path.isEmpty()) json(listing(node(library,count=2,children=true),listOf(node(library,"旅行",count=1,children=true))))
                    else if(path=="旅行") json(listing(node(library,path,count=1,children=true),listOf(node(library,"旅行/海边",count=2))))
                    else json(listing(node(library,path,count=2),emptyList()))
                }
                "/api/v1/assets/timeline-summary", "/api/v1/assets" -> {
                    Assert.assertEquals("library-a",url.queryParameter("libraryId"))
                    Assert.assertEquals("旅行",url.queryParameter("folderPath"))
                    Assert.assertEquals("false",url.queryParameter("archived"))
                    Assert.assertNull(url.queryParameter("private"))
                    if(url.encodedPath.endsWith("timeline-summary")) json(JSONObject().put("granularity","MONTH").put("groups",JSONArray().put(JSONObject().put("key","2026-09").put("startsAt","2026-09-01T00:00:00Z").put("count",3))))
                    else json(JSONObject().put("items",JSONArray().put(asset())).put("nextCursor",JSONObject.NULL))
                }
                "/api/v1/assets/photo" -> json(asset())
                "/api/v1/assets/photo/thumbnail", "/api/v1/assets/photo/original" -> MockResponse().setHeader("Content-Type","image/png").setBody(Buffer().write(png))
                else -> MockResponse().setResponseCode(404)
            }
        }}
    }
    private fun waitTag(tag:String) {rule.waitUntil(10000) {rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun browseHierarchyUsesRecursivePhotoScopeAndExistingViewer() {
        serve()
        rule.setContent {PhotoTubeTheme {DirectoryBrowser(server,{}, {})}}
        waitTag("directory-library-a:")
        rule.onNodeWithTag("directory-library-b:").assertIsNotEnabled()
        rule.onNodeWithTag("directory-library-a:").performClick()
        waitTag("directory-library-a:旅行")
        rule.onNodeWithTag("directory-library-a:旅行").performClick()
        waitTag("directory-library-a:旅行/海边")
        rule.onNodeWithText("本层直接包含 1 项").assertExists()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"directory-browser.png"))
        rule.onNodeWithTag("browse-directory-photos").performClick()
        waitTag("directory-photos")
        rule.waitUntil(10000) {rule.onAllNodesWithText("3 项 · 包含子目录 · 不含归档与私密内容").fetchSemanticsNodes().isNotEmpty()}
        waitTag("asset-photo")
        rule.onNodeWithTag("asset-photo").performClick()
        waitTag("media-viewer")
        rule.onNodeWithContentDescription("关闭照片").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isEmpty()}
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("directory-photos").fetchSemanticsNodes().isEmpty()}
        waitTag("directory-library-a:旅行/海边")
        rule.onNodeWithContentDescription("返回上一级").performClick()
        waitTag("directory-library-a:旅行")
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        waitTag("directory-library-a:")
        Assert.assertTrue(requests.none {it.method!="GET"})
    }
    @Test fun rootFailureCanRetryWithoutShowingEmptyState() {
        serve(true)
        rule.setContent {PhotoTubeTheme {DirectoryBrowser(server,{}, {})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("目录暂时不可用").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("没有可浏览的媒体库").assertDoesNotExist()
        rule.onNodeWithText("重试").performClick()
        waitTag("directory-library-a:")
    }
}
