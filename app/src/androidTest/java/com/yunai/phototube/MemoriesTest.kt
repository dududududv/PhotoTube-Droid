package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.MemoriesScreen
import com.yunai.phototube.ui.remote.MemoryExclusionsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class MemoriesTest {
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
    private fun exclusion()=JSONObject().put("id","rule-one").put("kind","DATE_RANGE").put("dateFrom","2025-01-01").put("dateTo","2025-01-02")
    @Test fun auditKeepDateExclusion() {
        val writes=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method!="GET") {writes.incrementAndGet();return MockResponse().setResponseCode(405)}
                return json(JSONObject().put("items",JSONArray().put(exclusion())).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {MemoryExclusionsScreen(MemoriesRepository(server),{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("取消屏蔽").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("取消屏蔽").performClick()
        rule.onNodeWithText("取消此日期屏蔽？").assertIsDisplayed()
        rule.waitForIdle();android.os.SystemClock.sleep(450)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"audit-memory-keep.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
        rule.onNodeWithText("保留屏蔽").performClick()
        rule.onNodeWithText("2025-01-01 至 2025-01-02").assertIsDisplayed()
        Assert.assertEquals(0,writes.get())
    }
    private fun home()=JSONObject().put("onThisDay",JSONArray()).put("recentPhotos",JSONArray()).put("recentImports",JSONArray()).put("frequentAlbums",JSONArray()).put("jobSummary",JSONObject().put("items",JSONArray()))
    @Test fun dateRulesUseDateOnlyAndOpaquePaginationWithoutAssetMutations()=runBlocking {
        val repository=MemoriesRepository(server)
        host.enqueue(json(exclusion()).setResponseCode(201))
        repository.create(java.time.LocalDate.parse("2025-01-01"),java.time.LocalDate.parse("2025-01-02"))
        val create=host.takeRequest();val body=JSONObject(create.body.readUtf8())
        Assert.assertEquals("/api/v1/memory-exclusions",create.requestUrl!!.encodedPath)
        Assert.assertEquals(setOf("kind","dateFrom","dateTo"),body.keys().asSequence().toSet())
        Assert.assertEquals("DATE_RANGE",body.getString("kind"));Assert.assertEquals("2025-01-01",body.getString("dateFrom"))
        Assert.assertTrue(kotlin.runCatching {repository.create(java.time.LocalDate.parse("2025-02-01"),java.time.LocalDate.parse("2025-01-01"))}.isFailure)
        host.enqueue(json(JSONObject().put("items",JSONArray().put(exclusion())).put("nextCursor","opaque+/=")))
        val page=repository.list(null);host.takeRequest()
        host.enqueue(json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL)))
        repository.list(page.cursor)
        Assert.assertEquals("opaque+/=",host.takeRequest().requestUrl!!.queryParameter("cursor"))
        host.enqueue(MockResponse().setResponseCode(204));repository.remove("rule-one")
        val delete=host.takeRequest();Assert.assertEquals("DELETE",delete.method);Assert.assertEquals("/api/v1/memory-exclusions/rule-one",delete.requestUrl!!.encodedPath)
    }
    @Test fun emptyMemoryBlockIsHiddenAndRemovingRuleRefreshesWithoutDeletingPhotos() {
        val requests=CopyOnWriteArrayList<RecordedRequest>();var removed=false
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return when {
                    request.method=="DELETE" -> {removed=true;MockResponse().setResponseCode(204)}
                    request.requestUrl!!.encodedPath=="/api/v1/home" -> json(home())
                    else -> json(JSONObject().put("items",if(removed) JSONArray() else JSONArray().put(exclusion())).put("nextCursor",JSONObject.NULL))
                }
            }
        }
        rule.setContent {PhotoTubeTheme {MemoriesScreen(server,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("暂无可展示的照片").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("往年今日").assertDoesNotExist()
        rule.onNodeWithText("回忆日期屏蔽").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("2025-01-01 至 2025-01-02").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("取消屏蔽").performClick()
        Assert.assertFalse(requests.any {it.method=="DELETE"})
        rule.onNodeWithText("确认取消屏蔽").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("尚未屏蔽任何日期").fetchSemanticsNodes().isNotEmpty()}
        rule.waitUntil(10000) {requests.count {it.requestUrl!!.encodedPath=="/api/v1/home"}>=2}
        Assert.assertTrue(requests.filter {it.method!="GET"}.all {it.method=="DELETE" && it.requestUrl!!.encodedPath=="/api/v1/memory-exclusions/rule-one"})
    }
    @Test fun datePickerCreateFailureKeepsSelectedRange() {
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return if(request.method=="POST") json("""{"code":"CONFLICT","message":"保存失败，请重试"}""").setResponseCode(409)
                else json(JSONObject().put("items",JSONArray()).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {MemoryExclusionsScreen(MemoriesRepository(server),{},{})}}
        rule.onNodeWithText("屏蔽此日期范围").assertIsNotEnabled()
        fun dayTag(day:Int)="filter-day-${java.time.YearMonth.now().atDay(day)}"
        rule.onNodeWithText("开始日期").performClick();rule.onNodeWithTag(dayTag(1)).performScrollTo().performClick();rule.onNodeWithText("选择日期").performScrollTo().performClick()
        rule.onNodeWithText("结束日期").performClick();rule.onNodeWithTag(dayTag(2)).performScrollTo().performClick();rule.onNodeWithText("选择日期").performScrollTo().performClick()
        rule.onNodeWithText("屏蔽此日期范围").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("保存失败，请重试").fetchSemanticsNodes().isNotEmpty()}
        val body=JSONObject(requests.single {it.method=="POST"}.body.readUtf8())
        Assert.assertTrue(body.getString("dateFrom").endsWith("-01"));Assert.assertTrue(body.getString("dateTo").endsWith("-02"))
        rule.onNodeWithText(body.getString("dateFrom")).assertIsDisplayed();rule.onNodeWithText(body.getString("dateTo")).assertIsDisplayed()
        rule.onNodeWithText("屏蔽此日期范围").assertIsEnabled()
    }
}
