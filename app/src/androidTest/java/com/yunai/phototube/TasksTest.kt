package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.TasksScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class TasksTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun summary()=JSONObject().put("items",JSONArray().put(JSONObject().put("kind","TAG_SCAN").put("pending",1).put("paused",0).put("running",0)
        .put("succeeded",0).put("failed",2).put("cancelled",0).put("discovered",3).put("total",JSONObject.NULL).put("lastFinishedAt",JSONObject.NULL)))
    private fun run(id:String="ai",state:String="CANCELLED")=JSONObject().put("id",id).put("state",state).put("scanMode","INCREMENTAL").put("dryRun",false)
        .put("processedAssets",0).put("totalAssets",0).put("cachedAssets",0).put("providerAttemptCount",0).put("skippedCount",0).put("conflictCount",0).put("batchSize",8)
        .put("createdAt","2026-09-16T00:00:00Z").put("error",JSONObject.NULL).put("report",JSONArray()).put("mediaScope","VIDEO")
    private fun page(cursor:String?=null)=JSONObject().put("items",JSONArray().put(JSONObject().put("albumId","album").put("albumName","旅行").put("run",run()))).put("nextCursor",cursor ?: JSONObject.NULL)
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun groupingPrioritizesActiveAndDoesNotTreatCancellationAsFailure() {
        val jobs=listOf(JobSummary("TAG_SCAN",1,0,0,0,2,0,3,null,null),JobSummary("XMP_EXPORT",0,0,0,0,1,0,1,1,null))
        val rows=mergedTasks(jobs,listOf(AiTask("album","旅行",albumAiRun(run()))))
        Assert.assertEquals(listOf(0,1,2),rows.map {it.section})
        Assert.assertEquals(3,rows.map {it.key}.toSet().size)
        Assert.assertEquals("CANCELLED",rows.last().ai!!.run.state)
    }
    @Test fun readsWrappedAiPageAndKindBoundHistory()=runBlocking {
        val repository=TasksRepository(server)
        host.enqueue(json(page("next+/=")))
        val ai=repository.ai("opaque+/=")
        Assert.assertEquals("旅行",ai.items.single().albumName);Assert.assertEquals(AiMediaScope.VIDEO,ai.items.single().run.mediaScope)
        val request=host.takeRequest();Assert.assertEquals("/api/v1/ai-tag-tasks",request.requestUrl!!.encodedPath);Assert.assertEquals("opaque+/=",request.requestUrl!!.queryParameter("cursor"))
        host.enqueue(json(summary()));Assert.assertNull(repository.summary().single().total);host.takeRequest()
        host.enqueue(json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        repository.jobs("TAG_SCAN","job+/=")
        val history=host.takeRequest();Assert.assertEquals("TAG_SCAN",history.requestUrl!!.queryParameter("kind"));Assert.assertEquals("job+/=",history.requestUrl!!.queryParameter("cursor"))
    }
    @Test fun partialFailureRetainsJobsAndUnknownTotalsWithoutControls() {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                Assert.assertEquals("GET",request.method)
                return if(request.requestUrl!!.encodedPath.endsWith("summary")) json(summary()) else MockResponse().setResponseCode(503).setBody("""{"code":"UNAVAILABLE","message":"AI 历史不可用"}""")
            }
        }
        rule.setContent {PhotoTubeTheme {TasksScreen(server,{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("列表不完整",substring=true).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("标签处理").assertExists();rule.onNodeWithText("已发现 3 项").assertExists()
        rule.onNodeWithText("暂无任务").assertDoesNotExist();rule.onNodeWithText("暂停").assertDoesNotExist();rule.onNodeWithText("开始打标").assertDoesNotExist()
    }
    @Test fun historyUsesNextCursorAndDoesNotPollHistoricalPages() {
        val cursors=java.util.Collections.synchronizedList(mutableListOf<String?>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                Assert.assertEquals("GET",request.method)
                return if(request.requestUrl!!.encodedPath.endsWith("summary") || request.requestUrl!!.encodedPath.endsWith("scan-batches")) json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)) else {
                    val cursor=request.requestUrl!!.queryParameter("cursor");cursors.add(cursor);json(page(if(cursor==null) "next+/=" else null))
                }
            }
        }
        rule.setContent {PhotoTubeTheme {TasksScreen(server,{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("视频 · 已取消").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("更多历史").performClick()
        rule.waitUntil(10000) {cursors.contains("next+/=")}
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(6500)
        Assert.assertEquals(1,cursors.count {it=="next+/="})
        rule.onNodeWithText("更多历史").assertIsNotEnabled()
    }
}
