package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.DuplicatesScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class DuplicatesTest {
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
    private val hash="a".repeat(64)
    private fun group(reviewed:Boolean=false)=JSONObject().put("contentHash",hash).put("copyCount",2).put("reclaimableBytes",9007199254740993L)
        .put("latestChangeAt","2026-09-17T00:00:00Z").put("reviewedAt",if(reviewed) "2026-09-17T00:00:00Z" else JSONObject.NULL)
        .put("representative",JSONObject().put("assetId","a").put("fileName","holiday.jpg").put("contentHash",hash))
    private fun page(items:JSONArray,cursor:String?=null)=JSONObject().put("items",items).put("nextCursor",cursor ?: JSONObject.NULL)
    @Test fun contractKeepsOpaqueCursorCountsAndBodylessReview()=runBlocking {
        val repository=DuplicatesRepository(server)
        host.enqueue(json(page(JSONArray().put(group()),"opaque+/=")))
        val result=repository.groups(true,null)
        Assert.assertEquals(9007199254740993L,result.items.single().bytes)
        Assert.assertEquals("true",host.takeRequest().requestUrl!!.queryParameter("includeReviewed"))
        host.enqueue(json(page(JSONArray())))
        repository.groups(true,result.cursor)
        Assert.assertEquals("opaque+/=",host.takeRequest().requestUrl!!.queryParameter("cursor"))
        host.enqueue(json(page(JSONArray())))
        Assert.assertTrue(repository.members(hash,null).items.isEmpty())
        Assert.assertEquals("/api/v1/duplicates/$hash",host.takeRequest().requestUrl!!.encodedPath)
        listOf(true,false).forEach {keep ->
            host.enqueue(MockResponse().setResponseCode(204));repository.review(hash,keep)
            val request=host.takeRequest()
            Assert.assertEquals(if(keep) "PUT" else "DELETE",request.method)
            Assert.assertEquals("/api/v1/duplicates/$hash/review",request.requestUrl!!.encodedPath)
            Assert.assertEquals(0L,request.bodySize)
        }
    }
    @Test fun privateScopeIsSentForGroupsMembersAndBothReviewActions() = runBlocking {
        val isolated=server.privateFork({true},{})
        try {
            val repository=DuplicatesRepository(isolated)
            host.enqueue(json(page(JSONArray())))
            repository.groups(false,"private-cursor+/=")
            val list=host.takeRequest()
            Assert.assertEquals("true",list.requestUrl!!.queryParameter("private"))
            Assert.assertEquals("private-cursor+/=",list.requestUrl!!.queryParameter("cursor"))
            host.enqueue(json(page(JSONArray())))
            repository.members(hash,null)
            Assert.assertEquals("true",host.takeRequest().requestUrl!!.queryParameter("private"))
            for(keep in listOf(true,false)) {
                host.enqueue(MockResponse().setResponseCode(204));repository.review(hash,keep)
                val request=host.takeRequest()
                Assert.assertEquals("true",request.requestUrl!!.queryParameter("private"))
                Assert.assertEquals(if(keep) "PUT" else "DELETE",request.method)
                Assert.assertEquals(0L,request.bodySize)
            }
        } finally {isolated.dispose()}
    }
    @Test fun browseDoesNotMutateAndReviewFailureRemainsRetryable() {
        val requests=CopyOnWriteArrayList<RecordedRequest>();var writes=0
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return when {
                    request.method=="PUT" -> {writes++;if(writes==1) json("""{"code":"CONFLICT","message":"组已变化，请重试"}""").setResponseCode(409) else MockResponse().setResponseCode(204)}
                    request.method=="DELETE" -> MockResponse().setResponseCode(204)
                    request.requestUrl!!.encodedPath.endsWith("/thumbnail") -> MockResponse().setResponseCode(404)
                    request.requestUrl!!.encodedPath=="/api/v1/duplicates" -> json(page(JSONArray().put(group())))
                    else -> json(page(JSONArray().put(JSONObject().put("id","a").put("kind","PHOTO").put("state","PROCESSING").put("takenAt","2026-09-17T00:00:00Z").put("fileName","holiday.jpg").put("favorite",false))))
                }
            }
        }
        rule.setContent {PhotoTubeTheme {DuplicatesScreen(server,{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("holiday.jpg").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("holiday.jpg").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("保留全部副本").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("保留全部副本").assertIsEnabled()
        Assert.assertTrue(requests.all {it.method=="GET"})
        rule.onNodeWithText("保留全部副本").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("组已变化，请重试").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("保留全部副本").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("恢复重复提醒").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("恢复重复提醒").performClick()
        rule.waitUntil(10000) {requests.any {it.method=="DELETE"}}
        rule.onNodeWithText("保留全部副本").assertIsDisplayed()
        Assert.assertTrue(requests.filter {it.method!="GET"}.all {it.requestUrl!!.encodedPath.endsWith("/review")})
    }
}
