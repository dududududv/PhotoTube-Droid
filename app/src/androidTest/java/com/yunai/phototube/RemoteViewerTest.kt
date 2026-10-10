package com.yunai.phototube

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.junit.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RemoteViewerTest {
    @get:Rule val rule=createComposeRule()
    @Test fun originalAndVideoUseAuthenticatedExistingViewer() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager)
            .addInterceptor { chain ->
                check(chain.request().url.host == "localhost")
                // Allow explicit loopback fixtures on the offline audit emulator.
                chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
            }.build()
        val png=ByteArrayOutputStream().apply {Bitmap.createBitmap(48,32,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        val video=context.resources.openRawResource(R.raw.glass_video).use {it.readBytes()}
        val favoriteWrites=AtomicInteger();val archiveWrites=AtomicInteger();val trashWrites=AtomicInteger();
        val ranges=AtomicInteger();val originals=AtomicInteger();val videos=AtomicInteger();val unauthorized=AtomicInteger()
        val host=MockWebServer().apply {
            useHttps(tls.sslSocketFactory(),false)
            dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    if(request.getHeader("Cookie")!="phototube_session=viewer-fixture") {unauthorized.incrementAndGet();return MockResponse().setResponseCode(401)}
                    val path=request.requestUrl!!.encodedPath
                    return when {
                        path=="/api/v1/assets/favorite" || path=="/api/v1/assets/archive" || path=="/api/v1/assets/trash" -> {
                            val body=org.json.JSONObject(request.body.readUtf8())
                            Assert.assertEquals("POST",request.method)
                            val id=body.getJSONArray("assetIds").getString(0)
                            when { path.endsWith("favorite") -> {Assert.assertTrue(body.getBoolean("favorite"));favoriteWrites.incrementAndGet()}
                                path.endsWith("archive") -> {Assert.assertTrue(body.getBoolean("archived"));archiveWrites.incrementAndGet()}
                                else -> trashWrites.incrementAndGet() }
                            MockResponse().setHeader("Content-Type","application/json").setBody(org.json.JSONObject().put("succeeded",org.json.JSONArray().put(id)).put("failed",org.json.JSONArray()).toString())
                        }
                        path.endsWith("/thumbnail") -> MockResponse().setHeader("Content-Type","image/png").setBody(Buffer().write(png))
                        path=="/api/v1/assets/photo/original" -> {originals.incrementAndGet();MockResponse().setHeader("Content-Type","image/png").setBody(Buffer().write(png))}
                        path=="/api/v1/assets/video/original" -> {
                            videos.incrementAndGet();if(request.getHeader("Range")!=null) ranges.incrementAndGet()
                            val start=request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                            if(start>=video.size) return MockResponse().setResponseCode(416).setHeader("Content-Range","bytes */${video.size}")
                            MockResponse().setResponseCode(if(request.getHeader("Range")!=null) 206 else 200).setHeader("Content-Type","video/mp4")
                                .setHeader("Content-Range","bytes $start-${video.size-1}/${video.size}").setBody(Buffer().write(video,start,video.size-start))
                        }
                        else -> MockResponse().setHeader("Content-Type","application/json").setBody("""{"state":"BROWSABLE","width":48,"height":32,"fileSize":1234,"relativePath":"fixture/image.png","activeEdit":null}""")
                    }
                }
            }
            start(java.net.InetAddress.getLoopbackAddress(),0)
        }
        val api=PhotoTubeServer(context,host.url("/").newBuilder().host("localhost").build().toString(),{},client)
        api.cookies.saveFromResponse(api.origin,listOf(Cookie.Builder().name("phototube_session").value("viewer-fixture").hostOnlyDomain("localhost").path("/").secure().build()))
        fun photo(id:String,index:Int)=Photo(index,"$id.${if(id=="video") "mp4" else "png"}","2026年9月11日",video=id=="video",remote=RemoteMedia(RemoteAsset(id,if(id=="video") "VIDEO" else "PHOTO","BROWSABLE","2026-09-11T00:00:00Z",0,id,"hash",48,32,false,5.0),api))
        var closed=false
        try {
            val media=listOf(photo("photo",-1),photo("video",-2))
            rule.setContent {PhotoTubeTheme {MediaViewer(ViewerRequest(media,-1,emptyMap()),emptySet(),{},{},{closed=true})}}
            rule.waitUntil(15000) {originals.get()>0}
            rule.onNodeWithContentDescription("收藏").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("取消收藏").fetchSemanticsNodes().isNotEmpty()}
            Assert.assertEquals(1,favoriteWrites.get())
            rule.onNodeWithContentDescription("更多操作").performClick()
            rule.onNodeWithText("归档").performClick()
            rule.waitUntil(10000) {archiveWrites.get()==1}
            rule.onNodeWithContentDescription("照片与视频信息").performClick()
            rule.onNodeWithTag("media-info").assertIsDisplayed()
            rule.onNodeWithText("fixture/image.png").assertExists()
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
            rule.waitForIdle()
            rule.onNodeWithContentDescription("缩略图：video.mp4").performClick()
            rule.waitUntil(15000) {videos.get()>0}
            rule.onNodeWithTag("viewer-video-surface").assertIsDisplayed()
            rule.waitUntil(15000) {rule.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"已显示视频画面")).fetchSemanticsNodes().isNotEmpty()}
            val dataSource=androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(api.client).createDataSource()
            try {
                dataSource.open(androidx.media3.datasource.DataSpec.Builder().setUri(api.original("video")).setPosition(100).build())
                val bytes=ByteArray(16)
                Assert.assertEquals(16,dataSource.read(bytes,0,16))
                Assert.assertArrayEquals(video.copyOfRange(100,116),bytes)
            } finally {dataSource.close()}
            val invalidRange=androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(api.client).createDataSource()
            try {
                val failure=runCatching {
                    invalidRange.open(androidx.media3.datasource.DataSpec.Builder().setUri(api.original("video")).setPosition(video.size.toLong()+1L).build())
                }.exceptionOrNull()
                Assert.assertTrue(failure is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                Assert.assertEquals(416,(failure as androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException).responseCode)
            } finally {invalidRange.close()}
            Assert.assertTrue(ranges.get()>0)
            Assert.assertEquals(0,unauthorized.get())
            rule.onNodeWithContentDescription("删除").performClick()
            rule.onNodeWithText("移入回收站后可以恢复，NAS 原文件会保留。").assertExists()
            rule.onNodeWithText("删除").performClick()
            rule.waitUntil(5000) {trashWrites.get()==1}
            rule.waitForIdle()
            rule.waitUntil(5000) {closed || rule.onAllNodesWithText("操作未完成").fetchSemanticsNodes().isNotEmpty()}
            if(!closed) throw AssertionError(rule.onAllNodes(isRoot()).printToString(maxDepth=12))
            Assert.assertEquals(1,trashWrites.get())
        } finally {api.cookies.clear();api.dispose();host.shutdown()}
    }
}
