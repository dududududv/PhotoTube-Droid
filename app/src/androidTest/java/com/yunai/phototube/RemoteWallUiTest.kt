package com.yunai.phototube

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.RemotePhotoWall
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.json.*
import org.junit.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class RemoteWallUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun virtualizedWallPaginatesAndRendersThumbnails() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        app.getSharedPreferences("fixture_wall",0).edit().clear().commit()
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val transport=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
        val pages=AtomicInteger();val images=AtomicInteger()
        val bytes=ByteArrayOutputStream().apply {Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        fun json(text:String)=MockResponse().setHeader("Content-Type","application/json").setBody(text)
        val host=MockWebServer().apply {
            useHttps(tls.sslSocketFactory(),false)
            dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val url=request.requestUrl!!
                    return when {
                        url.encodedPath.endsWith("/health") -> json("""{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":false}}""")
                        url.encodedPath.endsWith("/auth/session") -> json("""{"authenticated":true,"passwordSet":true}""")
                        url.encodedPath.endsWith("/timeline-summary") -> if(url.queryParameter("kind")=="VIDEO") json("""{"granularity":"MONTH","groups":[]}""") else json("""{"granularity":"MONTH","groups":[{"key":"2026-09","startsAt":"2026-09-01T00:00:00Z","count":105}]}""")
                        url.encodedPath.endsWith("/thumbnail") -> {images.incrementAndGet();MockResponse().setHeader("Content-Type","image/png").setBody(Buffer().write(bytes))}
                        url.encodedPath.endsWith("/assets") -> {
                            pages.incrementAndGet()
                            if(url.queryParameter("kind")=="VIDEO") return json("""{"items":[],"nextCursor":null}""")
                            val first=url.queryParameter("cursor")==null
                            val items=JSONArray()
                            for(i in if(first) 0..99 else 100..104) items.put(JSONObject().put("id","fixture-$i").put("kind","PHOTO").put("state","BROWSABLE")
                                .put("takenAt","2026-09-10T00:00:00Z").put("takenAtOffsetMinutes",0).put("fileName","fixture-$i.jpg").put("contentHash","hash-$i")
                                .put("width",when(i) {0->320;1->960;else->640}).put("height",480).put("favorite",false))
                            json(JSONObject().put("items",items).put("nextCursor",if(first) "page-two" else JSONObject.NULL).toString())
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        val store=ViewModelStore()
        val vm=PhotoWallViewModel(app,{url,expired -> PhotoTubeServer(app,url,expired,transport)},"fixture_wall")
        store.put("wall",vm)
        try {
            rule.setContent {PhotoTubeTheme {RemotePhotoWall(vm,0.dp,Modifier)}}
            rule.runOnIdle {vm.connect(host.url("/").toString())}
            rule.waitUntil(10000) {rule.onAllNodesWithTag("asset-fixture-0").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("remote-photo-wall").assertIsDisplayed()
            rule.waitUntil(10000) {images.get()>0}
            val portrait=rule.onNodeWithTag("asset-fixture-0").fetchSemanticsNode().boundsInRoot
            val landscape=rule.onNodeWithTag("asset-fixture-1").fetchSemanticsNode().boundsInRoot
            Assert.assertEquals(portrait.top,landscape.top,1f)
            Assert.assertEquals(portrait.bottom,landscape.bottom,1f)
            Assert.assertEquals(portrait.width*3f,landscape.width,3f)
            rule.onNode(hasScrollToIndexAction()).performScrollToIndex(45)
            rule.waitUntil(10000) {pages.get()>=2}
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("asset-fixture-104"))
            rule.onNodeWithTag("asset-fixture-104").assertIsDisplayed()
            Assert.assertEquals(2,pages.get())
            rule.runOnIdle {vm.updateFilters(WallFilters(WallKind.VIDEO,true))}
            rule.waitUntil(10000) {rule.onAllNodesWithText("没有符合条件的照片或视频").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithTag("asset-fixture-104").assertDoesNotExist()
            rule.onNodeWithText("清除筛选").performClick()
            rule.waitUntil(10000) {rule.onAllNodesWithTag("asset-fixture-0").fetchSemanticsNodes().isNotEmpty()}
            Assert.assertFalse(vm.filters.active)
        } finally {
            rule.runOnIdle {store.clear()}
            host.shutdown()
            app.getSharedPreferences("fixture_wall",0).edit().clear().commit()
        }
    }
}
