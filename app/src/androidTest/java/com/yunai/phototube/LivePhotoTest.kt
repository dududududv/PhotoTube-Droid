package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class LivePhotoTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).addInterceptor {chain ->
            check(chain.request().url.host=="localhost")
            chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
        }.build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun recognizedMotionOnlyAndNoEndpointGuessing() {
        val asset=RemoteAsset("photo","PHOTO","BROWSABLE","2026-09-17T00:00:00Z",0,"photo.jpg","hash",48,32,false,null)
        val media=RemoteMedia(asset,server)
        Assert.assertNull(media.motionUrl())
        media.detail=JSONObject("""{"state":"BROWSABLE","motionPhoto":{"format":"OPPO_MOTION_PHOTO_V2","videoMime":"video/mp4"}}""")
        Assert.assertEquals("${server.base}/api/v1/assets/photo/motion-video",media.motionUrl())
        media.detail!!.put("state","OFFLINE");Assert.assertNull(media.motionUrl())
        media.detail!!.put("state","BROWSABLE").getJSONObject("motionPhoto").put("format","UNKNOWN")
        Assert.assertNull(media.motionUrl())
    }
    private fun capture(name:String) {
        rule.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(inst.uiAutomation.takeScreenshot())
        try {java.io.File(inst.targetContext.getExternalFilesDir(null),"mock-live-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }} finally {bitmap.recycle()}
    }
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun motionIsExplicitAuthenticatedAndReturnsToPhoto() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val png=java.io.ByteArrayOutputStream().apply {android.graphics.Bitmap.createBitmap(48,32,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        val video=context.resources.openRawResource(R.raw.glass_video).use {it.readBytes()}
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                if(request.getHeader("Cookie")!="phototube_session=live-fixture") return MockResponse().setResponseCode(401)
                val path=request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("/motion-video") -> {
                        val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                        if(start>=video.size) MockResponse().setResponseCode(416).setHeader("Content-Range","bytes */${video.size}")
                        else MockResponse().setResponseCode(if(request.getHeader("Range")!=null) 206 else 200).setHeader("Content-Type","video/mp4")
                            .setHeader("Content-Range","bytes $start-${video.size-1}/${video.size}").setBody(okio.Buffer().write(video,start,video.size-start))
                    }
                    path.endsWith("/original") || path.endsWith("/thumbnail") -> MockResponse().setHeader("Content-Type","image/png").setBody(okio.Buffer().write(png))
                    else -> json("""{"state":"BROWSABLE","width":48,"height":32,"fileSize":1234,"activeEdit":null,"motionPhoto":{"format":"OPPO_MOTION_PHOTO_V2","videoMime":"video/mp4","width":48,"height":32,"durationSec":4}}""")
                }
            }
        }
        server.cookies.saveFromResponse(server.origin,listOf(Cookie.Builder().name("phototube_session").value("live-fixture").hostOnlyDomain("localhost").path("/").secure().build()))
        val asset=RemoteAsset("photo","PHOTO","BROWSABLE","2026-09-17T00:00:00Z",0,"photo.jpg","hash",48,32,false,null)
        val photo=Photo(-1,"photo.jpg","2026年9月17日",video=false,remote=RemoteMedia(asset,server))
        rule.setContent {PhotoTubeTheme {MediaViewer(ViewerRequest(listOf(photo),-1,emptyMap()),emptySet(),{},{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("live-photo-toggle").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertFalse(requests.any {it.requestUrl!!.encodedPath.endsWith("/motion-video")})
        capture("photo")
        rule.onNodeWithTag("live-photo-toggle").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
            .assert(hasStateDescription("实况照片静态画面")).performClick()
        rule.waitUntil(15000) {rule.onAllNodes(hasStateDescription("已显示视频画面")).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("live-photo-toggle").assert(hasStateDescription("实况照片播放中"))
        capture("playing")
        rule.onNodeWithText("LIVE · 返回照片").performClick()
        rule.onNodeWithTag("photo-gesture--1").assertExists()
        rule.onNodeWithTag("viewer-video-surface").assertDoesNotExist()
        capture("returned")
        rule.onNodeWithTag("photo-gesture--1").performTouchInput {longClick()}
        rule.waitUntil(15000) {rule.onAllNodes(hasStateDescription("已显示视频画面")).fetchSemanticsNodes().isNotEmpty()}
        Assert.assertTrue(requests.filter {it.requestUrl!!.encodedPath.endsWith("/motion-video")}.all {it.getHeader("Cookie")=="phototube_session=live-fixture"})
        val dataSource=androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(server.client).createDataSource()
        try {
            dataSource.open(androidx.media3.datasource.DataSpec.Builder().setUri(server.motionVideo("photo")).setPosition(64).setLength(128).build())
            Assert.assertEquals(128,dataSource.read(ByteArray(128),0,128))
        } finally {dataSource.close()}
        Assert.assertTrue(requests.any {it.requestUrl!!.encodedPath.endsWith("/motion-video") && it.getHeader("Range")=="bytes=64-191"})
        rule.waitUntil(45000) {rule.onAllNodesWithTag("photo-gesture--1").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("photo-gesture--1").assertExists()
        Assert.assertTrue(requests.all {it.method=="GET"})
        rule.mainClock.autoAdvance=false
        try {
            rule.onNodeWithContentDescription("关闭照片").performClick()
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("live-photo-toggle").assertIsNotEnabled()
        } finally {rule.mainClock.autoAdvance=true}
    }
}
