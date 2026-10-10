package com.yunai.phototube

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.librarySearchMessage
import com.yunai.phototube.ui.remote.RemotePhotoWall
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class LibrarySearchTest {
    @get:Rule val rule=createComposeRule()
    @Test fun shortInputNeverQueriesAndLatestKeywordClearsOldResults() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val pref="fixture_search"
        app.getSharedPreferences(pref,0).edit().clear().commit()
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val transport=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        fun json(value:String)=MockResponse().setHeader("Content-Type","application/json").setBody(value)
        val host=MockWebServer().apply {
            useHttps(tls.sslSocketFactory(),false)
            dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    requests.add(request)
                    return when(request.requestUrl!!.encodedPath) {
                        "/api/v1/health" -> json("""{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":false}}""")
                        "/api/v1/auth/session" -> json("""{"authenticated":true,"passwordSet":true}""")
                        "/api/v1/assets/timeline-summary" -> json("""{"granularity":"MONTH","groups":[]}""")
                        "/api/v1/assets" -> json("""{"items":[],"nextCursor":null}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start(java.net.InetAddress.getLoopbackAddress(),0)
        }
        val store=ViewModelStore()
        val vm=PhotoWallViewModel(app,{url,expired -> PhotoTubeServer(app,url,expired,transport)},pref)
        store.put("wall",vm)
        var query by mutableStateOf("")
        try {
            rule.setContent {PhotoTubeTheme {
                val message=librarySearchMessage(vm,query,true)
                RemotePhotoWall(vm,0.dp,Modifier,searchMessage=message)
            }}
            rule.runOnIdle {vm.connect(host.url("/").newBuilder().host("localhost").build().toString())}
            rule.waitUntil(10000) {requests.any {it.requestUrl!!.encodedPath=="/api/v1/assets"}}
            rule.runOnIdle {query="ab"}
            rule.onNodeWithText("请输入至少 3 个字符，按文件名搜索").assertIsDisplayed()
            Assert.assertFalse(requests.any {it.requestUrl!!.queryParameter("keyword")!=null})
            rule.runOnIdle {query="  holiday & 100%  "}
            rule.waitUntil(10000) {requests.any {it.requestUrl!!.encodedPath=="/api/v1/assets" && it.requestUrl!!.queryParameter("keyword")=="holiday & 100%"}}
            rule.onNodeWithText("没有符合条件的照片或视频").assertIsDisplayed()
            rule.runOnIdle {query="x"}
            rule.onNodeWithTag("remote-photo-wall").assertDoesNotExist()
            rule.onNodeWithText("请输入至少 3 个字符，按文件名搜索").assertIsDisplayed()
            rule.runOnIdle {query=""}
            rule.waitUntil(10000) {vm.filters.keyword.isEmpty() && !vm.busy}
            rule.waitUntil(10000) {rule.onAllNodesWithText("还没有照片").fetchSemanticsNodes().isNotEmpty()}
            val queried=requests.filter {it.requestUrl!!.queryParameter("keyword")!=null}
            Assert.assertEquals(setOf("/api/v1/assets/timeline-summary","/api/v1/assets"),queried.map {it.requestUrl!!.encodedPath}.toSet())
            Assert.assertTrue(queried.all {it.requestUrl!!.queryParameter("keyword")=="holiday & 100%"})
            Assert.assertTrue(requests.all {it.method=="GET"})
        } finally {
            rule.runOnIdle {store.clear()}
            host.shutdown()
            app.getSharedPreferences(pref,0).edit().clear().commit()
        }
    }
}
