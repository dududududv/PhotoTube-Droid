package com.yunai.phototube

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.TrashClearScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TrashClearTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val posts=CopyOnWriteArrayList<List<String>>()
    private val gets=AtomicInteger()
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun entry(i:Int)=JSONObject().put("id","asset-$i").put("kind","PHOTO").put("state","TRASHED").put("fileName","$i.jpg")
        .put("takenAt","2026-01-01T00:00:00Z").put("favorite",false).put("trashedAt","2026-09-01T00:00:00Z").put("retentionDays",30)
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(context,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    private fun serve(total:Int=1101,response:(List<String>)->MockResponse={ids -> json(JSONObject().put("succeeded",JSONArray(ids)).put("failed",JSONArray()))}) {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="GET") {
                    Assert.assertEquals("/api/v1/trash",request.requestUrl!!.encodedPath)
                    Assert.assertEquals("500",request.requestUrl!!.queryParameter("limit"))
                    gets.incrementAndGet()
                    val offset=request.requestUrl!!.queryParameter("cursor")?.substringBefore("+/=")?.toInt() ?: 0
                    val end=minOf(total,offset+500)
                    val rows=JSONArray().apply {for(i in offset until end) put(entry(i));if(offset>0) put(entry(offset-1))}
                    return json(JSONObject().put("items",rows).put("nextCursor",if(end<total) "$end+/=" else JSONObject.NULL))
                }
                Assert.assertEquals("/api/v1/trash/purge",request.requestUrl!!.encodedPath)
                val body=JSONObject(request.body.clone().readUtf8());Assert.assertEquals(setOf("assetIds"),body.keys().asSequence().toSet())
                val array=body.getJSONArray("assetIds");val ids=(0 until array.length()).map(array::getString)
                Assert.assertTrue(ids.size in 1..500);posts.add(ids)
                return response(ids)
            }
        }
    }
    @Test fun cleanupKeepsOldActiveManifestAndDeletesOnlyAbandonedDatabases()=runBlocking {
        val cache=java.io.File(context.cacheDir,"manifest-test-${java.util.UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:android.content.ContextWrapper(context) {override fun getCacheDir()=cache}
        val active=TrashClearPlan.create(isolated)
        val directory=java.io.File(cache,"trash-clear-plans")
        val activeFile=directory.listFiles()!!.single {it.extension=="db"}
        val abandoned=java.io.File(directory,"plan-abandoned.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(abandoned,null).close()
        val journal=java.io.File(directory,"plan-abandoned.db-journal").apply {writeText("fixture")}
        val unrelated=java.io.File(directory,"keep.txt").apply {writeText("fixture")}
        val old=System.currentTimeMillis()-172_800_000L
        listOf(activeFile,abandoned,journal,unrelated).forEach {Assert.assertTrue(it.setLastModified(old))}
        var next:TrashClearPlan?=null
        try {
            next=TrashClearPlan.create(isolated)
            Assert.assertTrue(activeFile.exists())
            Assert.assertFalse(abandoned.exists())
            Assert.assertFalse(journal.exists())
            Assert.assertTrue(unrelated.exists())
            serve(total=1)
            Assert.assertEquals(1L,active.collect(TrashRepository(server)) {}.total)
            Assert.assertTrue(posts.isEmpty())
        } finally {
            next?.close();active.close();active.close();cache.deleteRecursively()
        }
    }
    @Test fun collectsAllPagesBeforeWritingAndHandlesPartialFailureOnce()=runBlocking {
        serve {ids -> json(JSONObject().put("succeeded",JSONArray(ids.filter {it!="asset-500"}+"outside-plan"))
            .put("failed",JSONArray().apply {if("asset-500" in ids) put(JSONObject().put("target","asset-500").put("code","VALIDATION_FAILED").put("message","已恢复，不能清除"))}))}
        val plan=withContext(Dispatchers.IO) {TrashClearPlan.create(context)}
        try {
            val repository=TrashRepository(server)
            Assert.assertEquals(1101L,plan.collect(repository,{}).total)
            Assert.assertEquals(3,gets.get());Assert.assertTrue(posts.isEmpty())
            val result=plan.execute(repository,AtomicBoolean(false),{})
            Assert.assertEquals(TrashClearStats(1101,1100,1,0),result)
            Assert.assertEquals(listOf(500,500,101),posts.map {it.size})
            Assert.assertEquals(1101,posts.flatten().toSet().size)
            Assert.assertEquals(3,gets.get()) // No re-enumeration can add new records during deletion.
            Assert.assertEquals("500.jpg",plan.failures().single().name)
        } finally {plan.close()}
    }
    @Test fun networkFailureDoesNotRepeatOrContinueLaterBatches()=runBlocking {
        serve {MockResponse().setResponseCode(503).setBody("""{"code":"UNAVAILABLE","message":"稍后再试"}""")}
        val plan=withContext(Dispatchers.IO) {TrashClearPlan.create(context)}
        try {
            val repository=TrashRepository(server);plan.collect(repository,{})
            try {plan.execute(repository,AtomicBoolean(false),{});Assert.fail()} catch(_:ServerError) {}
            Assert.assertEquals(1,posts.size);Assert.assertEquals(500L,plan.stats.unknown);Assert.assertEquals(601L,plan.stats.pending)
        } finally {plan.close()}
    }
    @Test fun missingResultsStopAndExplicitStopDoesNotStartAnotherBatch()=runBlocking {
        serve {ids -> json(JSONObject().put("succeeded",JSONArray(ids.dropLast(1))).put("failed",JSONArray()))}
        val plan=withContext(Dispatchers.IO) {TrashClearPlan.create(context)}
        try {
            val repository=TrashRepository(server);plan.collect(repository,{})
            val result=plan.execute(repository,AtomicBoolean(false),{})
            Assert.assertEquals(TrashClearStats(1101,499,0,1),result);Assert.assertEquals(1,posts.size)
        } finally {plan.close()}
        posts.clear();serve()
        val stopped=withContext(Dispatchers.IO) {TrashClearPlan.create(context)}
        try {
            val repository=TrashRepository(server);stopped.collect(repository,{})
            val stop=AtomicBoolean(false)
            val result=stopped.execute(repository,stop) {if(it.processed>=500) stop.set(true)}
            Assert.assertEquals(500L,result.succeeded);Assert.assertEquals(601L,result.pending);Assert.assertEquals(1,posts.size)
        } finally {stopped.close()}
    }
    @Test fun repeatedCursorCannotProduceAnExecutablePartialPlan()=runBlocking {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                Assert.assertEquals("GET",request.method)
                return json(JSONObject().put("items",JSONArray().put(entry(1))).put("nextCursor","repeat+/="))
            }
        }
        val plan=withContext(Dispatchers.IO) {TrashClearPlan.create(context)}
        try {
            val repository=TrashRepository(server)
            try {plan.collect(repository,{});Assert.fail()} catch(_:ServerError) {}
            try {plan.execute(repository,AtomicBoolean(false),{});Assert.fail()} catch(_:IllegalStateException) {}
            Assert.assertEquals(2,host.requestCount)
        } finally {plan.close()}
    }
    @Test fun uiRequiresConfirmationAndBackgroundStopsAfterCurrentBatch() {
        val entered=AtomicBoolean(false)
        val release=java.util.concurrent.CountDownLatch(1)
        serve {ids -> entered.set(true);release.await(10,java.util.concurrent.TimeUnit.SECONDS);json(JSONObject().put("succeeded",JSONArray(ids)).put("failed",JSONArray()))}
        val owner=object:LifecycleOwner {val registry=LifecycleRegistry.createUnsafe(this);override val lifecycle:Lifecycle get()=registry}
        rule.runOnIdle {owner.registry.currentState=Lifecycle.State.RESUMED}
        rule.setContent {CompositionLocalProvider(LocalLifecycleOwner provides owner) {PhotoTubeTheme {TrashClearScreen(server,{},{})}}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("清除这 1101 项记录").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("清除这 1101 项记录").performClick()
        Assert.assertTrue(posts.isEmpty())
        rule.onNodeWithText("取消").performClick();Assert.assertTrue(posts.isEmpty())
        rule.onNodeWithText("清除这 1101 项记录").performClick()
        rule.onNodeWithText("确认清除全部已收集记录").performClick()
        rule.waitUntil(10000) {entered.get()}
        rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)}
        release.countDown()
        rule.waitUntil(10000) {rule.onAllNodesWithText("已停止，剩余记录没有继续处理。").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,posts.size)
        rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)}
        rule.onNodeWithText("已清除 500 · 失败 0 · 未确认 0 · 待处理 601").assertExists()
        rule.waitForIdle()
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File(context.getExternalFilesDir(null),"trash-clear-preview.png"))
    }

    @Test fun emptyCollectionOffersReturnWithoutZeroItemConfirmation() {
        serve(total=0)
        var closed=0
        rule.setContent {PhotoTubeTheme {TrashClearScreen(server,{}, {closed++})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("无需清理").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("回收站是空的").assertIsDisplayed()
        rule.onNodeWithText("清除这 0 项记录").assertDoesNotExist()
        rule.onNodeWithText("返回回收站",substring=false).assertIsEnabled()
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(java.io.File(context.getExternalFilesDir(null),"trash-clear-empty-current.png"))
        rule.onNodeWithText("返回回收站",substring=false).performClick()
        rule.waitUntil(5000) {closed==1}
        Assert.assertTrue(posts.isEmpty())
    }
    @Test fun manualStopDisablesRepeatedRequestsAndReturnUsesExitTransition() {
        val entered=AtomicBoolean(false)
        val release=java.util.concurrent.CountDownLatch(1)
        var closed=0
        serve {ids -> entered.set(true);release.await(10,java.util.concurrent.TimeUnit.SECONDS);json(JSONObject().put("succeeded",JSONArray(ids)).put("failed",JSONArray()))}
        rule.setContent {PhotoTubeTheme {TrashClearScreen(server,{}, {closed++})}}
        try {
            rule.waitUntil(10000) {rule.onAllNodesWithText("清除这 1101 项记录").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("清除这 1101 项记录").performClick()
            rule.onNodeWithText("确认清除全部已收集记录").performClick()
            rule.waitUntil(10000) {entered.get()}
            rule.onNodeWithText("停止后续批次").performClick()
            rule.onNodeWithText("正在结束当前批次…").assertIsNotEnabled()
            release.countDown()
            rule.waitUntil(10000) {rule.onAllNodesWithText("已停止，剩余记录没有继续处理。").fetchSemanticsNodes().isNotEmpty()}
            Assert.assertEquals(1,posts.size)
            rule.mainClock.autoAdvance=false
            rule.onNodeWithText("返回回收站核对").performClick()
            rule.mainClock.advanceTimeByFrame()
            Assert.assertEquals(0,closed)
            rule.onNodeWithText("返回回收站核对").assertIsNotEnabled()
            rule.mainClock.advanceTimeBy(400)
            rule.mainClock.autoAdvance=true
            rule.waitForIdle()
            Assert.assertEquals(1,closed)
        } finally {release.countDown();rule.mainClock.autoAdvance=true}
    }
}
