package com.yunai.phototube

import android.graphics.Bitmap
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.*
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Buffer
import org.json.*
import org.junit.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class PagedViewerTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var host: MockWebServer
    private lateinit var server: PhotoTubeServer
    private val cursors = CopyOnWriteArrayList<Int>()
    private val failOnce = AtomicBoolean(false)
    private var largestWindow = 0
    private var refresh: () -> Unit = {}
    private val filters = WallFilters(keyword="picture", favorite=true, tagsAll=setOf(12,18))

    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},
            OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager)
                .addInterceptor {chain ->
                    check(chain.request().url.host=="localhost")
                    chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
                }.build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    private fun asset(index:Int)=JSONObject().put("id","asset-$index").put("kind","PHOTO").put("state","BROWSABLE")
        .put("takenAt","2026-09-01T00:00:00Z").put("fileName","picture-$index.jpg").put("favorite",true)
        .put("contentHash","a".repeat(64)).put("width",48).put("height",32)
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun serve(album:Boolean) {
        val png=ByteArrayOutputStream().apply { Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
        host.dispatcher=object:Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                Assert.assertEquals("GET",request.method)
                val url=request.requestUrl!!
                val path=url.encodedPath
                val expected=if(album) "/api/v1/albums/album-fixture/assets" else "/api/v1/assets"
                if(path==expected) {
                    if(!album) filters.query().forEach {(key,value)-> Assert.assertEquals(value,url.queryParameter(key))}
                    val offset=url.queryParameter("cursor")?.removeSuffix("+/=")?.toInt() ?: 0
                    cursors+=offset
                    if(offset==8 && failOnce.compareAndSet(true,false)) return json(JSONObject().put("code","UNAVAILABLE").put("message","fixture failure")).setResponseCode(503)
                    val list=JSONArray((offset until minOf(offset+4,24)).map(::asset))
                    val result=JSONObject().put("items",list).put("nextCursor",if(offset+4<24) "${offset+4}+/=" else JSONObject.NULL)
                    val body=if(album) JSONObject().put("assets",result).put("album",JSONObject().put("id","album-fixture").put("name","旅行")
                        .put("kind","NORMAL").put("sortMode","TAKEN_AT_ASC").put("assetCount",24)) else result
                    return json(body)
                }
                if(path.endsWith("/original") || path.endsWith("/thumbnail")) return MockResponse().setHeader("Content-Type","image/png").setBody(Buffer().write(png))
                if(path.matches(Regex("/api/v1/assets/asset-\\d+"))) return json(asset(path.substringAfterLast('-').toInt()))
                return MockResponse().setResponseCode(404)
            }
        }
    }
    private fun open(album:Boolean) {
        val config=PagingConfig(pageSize=4,initialLoadSize=4,prefetchDistance=1,maxSize=12,enablePlaceholders=false)
        val flow=if(album) Pager(config) { AlbumPagingSource {cursor->AlbumRepository(server).members("album-fixture",cursor).second} }.flow
            else Pager(config) {AssetPagingSource(server,filters)}.flow
        rule.setContent {PhotoTubeTheme {
            if(album) ViewerHost(flow) {it}
            else ViewerHost(remember(flow) {flow.map {data -> data.map<RemoteAsset,WallEntry> {WallEntry.Asset(it)}.insertSeparators {before,after ->
                if(after is WallEntry.Asset && (before==null || after.value.id.removePrefix("asset-").toInt()%4==0)) WallEntry.Month("2026-09",24,after.value.id) else null
            }}}) {(it as? WallEntry.Asset)?.value}
        }}
        rule.waitUntil(10000) {rule.onAllNodesWithText("打开详情").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("打开详情").performClick()
        current(0)
    }
    @Composable private fun <T:Any> ViewerHost(flow:Flow<PagingData<T>>, asset:(T)->RemoteAsset?) {
        val items=flow.collectAsLazyPagingItems()
        refresh=items::refresh
        largestWindow=maxOf(largestWindow,items.itemSnapshotList.items.count {asset(it)!=null})
        var session by remember {mutableStateOf<PagedViewerSession?>(null)}
        if(session==null && items.itemCount>0) Button({session=PagedViewerSession.create(items,asset,server,items.itemSnapshotList.items.firstNotNullOf(asset),emptyMap())}) {Text("打开详情")}
        session?.let {PagedMediaViewer(it,{session=null},{})}
    }
    private fun current(index:Int) {
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().any {
                it.config.getOrNull(SemanticsProperties.StateDescription)?.endsWith(" picture-$index.jpg")==true
            }
        }
    }
    private fun select(index:Int) {
        rule.onNodeWithTag("media-filmstrip").performScrollToNode(hasContentDescription("缩略图：picture-$index.jpg"))
        rule.onNodeWithContentDescription("缩略图：picture-$index.jpg").performClick()
        current(index)
    }
    private fun loaded(offset:Int) {rule.waitUntil(10000) {cursors.contains(offset)};rule.waitForIdle()}

    @Test fun filteredWallContinuesRetriesAndRetainsCurrentAcrossPageDrops() {
        serve(false);failOnce.set(true);open(false)
        select(3);loaded(4);current(3)
        select(7)
        rule.waitUntil(10000) {rule.onAllNodesWithText("加载更多失败 · 重试").fetchSemanticsNodes().isNotEmpty()}
        current(7)
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"viewer-paging-retry.png"))
        rule.onNodeWithText("加载更多失败 · 重试").performClick()
        rule.waitUntil(10000) {cursors.count {it==8}==2};rule.waitForIdle();current(7)
        select(11);loaded(12);current(11)
        select(15);loaded(16);current(15)
        select(19);loaded(20);current(19)
        select(23);current(23)
        Assert.assertTrue("Paging window must remain bounded: $largestWindow",largestWindow<=12)
        // Return through discarded pages using the original opaque cursor chain.
        select(16);select(12);select(8);select(4);select(0)
        Assert.assertTrue(cursors.count {it==4}>=2)
    }
    @Test fun photoSwipeCrossesPageBoundaryWithoutRestartingViewer() {
        serve(false);open(false)
        select(2)
        rule.onNodeWithTag("media-pager").performTouchInput {swipeLeft(durationMillis=450)}
        current(3);loaded(4)
        rule.onNodeWithTag("media-pager").performTouchInput {swipeLeft(durationMillis=450)}
        current(4)
        rule.onNodeWithTag("media-pager").performTouchInput {swipeRight(durationMillis=450)}
        current(3)
    }
    @Test fun albumScopeContinuesAndInvalidationDoesNotJumpToFirstPhoto() {
        serve(true);open(true)
        select(3);loaded(4)
        select(7);loaded(8)
        select(11);loaded(12);current(11)
        rule.runOnIdle {refresh()}
        rule.waitUntil(10000) {rule.onAllNodesWithText("列表已变化，请返回后重新打开").fetchSemanticsNodes().isNotEmpty()}
        current(11)
    }
}
