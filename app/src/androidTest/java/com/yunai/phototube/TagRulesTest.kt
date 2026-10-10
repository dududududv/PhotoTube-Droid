package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.RuleScanScreen
import com.yunai.phototube.ui.remote.RuleScanScopePicker
import com.yunai.phototube.ui.remote.BulkAssetsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class TagRulesTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun scan(id:String="preview",dry:Boolean=true,source:String="RULE")=JSONObject().put("id",id).put("state","SUCCEEDED")
        .put("scanMode","PREVIEW").put("sources",JSONArray().put(source)).put("dryRun",dry).put("processedAssets",10).put("totalAssets",10)
        .put("cachedAssets",0).put("providerAttemptCount",0).put("skippedCount",0).put("conflictCount",0).put("batchSize",8)
        .put("createdAt","2026-09-16T00:00:00Z").put("error",JSONObject.NULL).put("matchedAssets",10).put("proposedAdditions",10)
        .put("matchPercentage",100).put("highMatchWarning",true).put("report",JSONArray().put(JSONObject().put("tagName","假日").put("matchedAssetCount",10).put("sampleFileNames",JSONArray().put("holiday.jpg"))))
    private fun ruleJson()=JSONObject().put("id","rule").put("name","假日").put("pattern","(?i)holiday").put("target","FILE_NAME")
        .put("tagIds",JSONArray().put(9007199254740993L)).put("enabled",true).put("generatedAssetCount",10)
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    private fun captureAudit(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(450)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"audit-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
    @Test fun auditDeleteRuleCancel() {
        val writes=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method!="GET") { writes.incrementAndGet(); return MockResponse().setResponseCode(405) }
                return json(JSONObject().put("items",JSONArray().put(ruleJson())).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.TagRulesScreen(server,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("删除规则").fetchSemanticsNodes().isNotEmpty()}
        captureAudit("rule-list")
        rule.onNodeWithText("删除规则").performClick()
        rule.onNodeWithText("默认只删除规则，已有标签保留。").assertIsDisplayed()
        rule.onAllNodes(isToggleable()).onFirst().performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        captureAudit("repair-rule-delete-confirm")
        rule.onNodeWithText("取消").performClick()
        Assert.assertEquals(0,writes.get())
    }
    @Test fun deleteRuleFailureCanRetryWithExplicitReclaimChoice() {
        val reclaimValues=java.util.concurrent.CopyOnWriteArrayList<String>()
        var changed=0
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="DELETE") {
                    Assert.assertEquals("/api/v1/tag-rules/rule",request.requestUrl!!.encodedPath)
                    reclaimValues += requireNotNull(request.requestUrl!!.queryParameter("reclaimGeneratedTags"))
                    return if(reclaimValues.size==1) json("""{"code":"CONFLICT","message":"本地模拟：规则已变化"}""").setResponseCode(409)
                    else MockResponse().setResponseCode(204)
                }
                return json(JSONObject().put("items",if(reclaimValues.size>=2) JSONArray() else JSONArray().put(ruleJson())).put("nextCursor",JSONObject.NULL))
            }
        }
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.TagRulesScreen(server,{changed++},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("删除规则").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("删除规则").performClick()
        rule.onNode(isToggleable()).assertIsOff()
        rule.onNodeWithText("确认删除规则").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("本地模拟：规则已变化").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("glass-confirmation").assertDoesNotExist()
        Assert.assertEquals(listOf("false"),reclaimValues.toList())
        Assert.assertEquals(0,changed)
        rule.onNodeWithText("删除规则").performClick()
        rule.onNode(isToggleable()).assertIsOff().performClick()
        rule.onNode(isToggleable()).assertIsOn()
        rule.onNodeWithText("确认删除规则").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("还没有标签规则").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(listOf("false","true"),reclaimValues.toList())
        Assert.assertEquals(1,changed)
    }
    @Test fun auditApplyRuleCancel() = auditScanConfirmation(true)
    @Test fun auditUndoRuleCancel() = auditScanConfirmation(false)
    private fun auditScanConfirmation(dry:Boolean) {
        val writes=java.util.concurrent.atomic.AtomicInteger()
        val target=RuleScanTarget.Album("audit-only", "合成审查相册")
        val preferences=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rule-scan-bookmarks",0)
        val bookmark=server.base+"/"+ruleScanBookmark(target,emptySet())
        preferences.edit().putString(bookmark,"audit-run").commit()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method!="GET") {writes.incrementAndGet();return MockResponse().setResponseCode(405)}
                return json(scan("audit-run",dry))
            }
        }
        try {
            rule.setContent {PhotoTubeTheme {RuleScanScreen(server,target,{},{})}}
            val action=if(dry) "应用预览结果" else "撤销本批标签"
            rule.waitUntil(10000) {rule.onAllNodesWithText(action).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText(action).performScrollTo()
            captureAudit(if(dry) "rule-preview" else "rule-applied")
            rule.onNodeWithText(action).performClick()
            rule.onNodeWithText(if(dry) "应用规则预览？" else "撤销本批标签？").assertIsDisplayed()
            captureAudit(if(dry) "rule-apply-confirm" else "rule-undo-confirm")
            rule.onNodeWithText("取消").performClick()
            Assert.assertEquals(0,writes.get())
        } finally {preferences.edit().remove(bookmark).commit()}
    }
    @Test fun auditSampleAnalysisCancel() {
        val selections=java.util.concurrent.atomic.AtomicInteger()
        val unexpected=java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST" && request.requestUrl!!.encodedPath=="/api/v1/albums/audit-only/ai-tag-sample") {
                    selections.incrementAndGet()
                    return json(JSONObject().put("items",JSONArray().put(JSONObject().put("assetId","sample-1").put("kind","PHOTO").put("fileName","mock-sample.jpg"))))
                }
                unexpected.incrementAndGet();return MockResponse().setResponseCode(405)
            }
        }
        val album=remoteAlbum(JSONObject().put("id","audit-only").put("name","合成审查相册").put("kind","NORMAL").put("sortMode","TAKEN_AT_DESC").put("assetCount",1).put("cover",JSONObject.NULL))
        rule.setContent {PhotoTubeTheme {com.yunai.phototube.ui.remote.AlbumAiSampleScreen(server,album,"","",3,{})}}
        rule.onNodeWithText("随机抽样").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("当前保留 1 项；修改上方选项后重新抽样才会改变这批样本。").fetchSemanticsNodes().isNotEmpty()}
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("分析这批样本"))
        captureAudit("sample-selected")
        rule.onNodeWithText("分析这批样本").performClick()
        rule.onNodeWithText("分析 1 项样本？").assertIsDisplayed()
        captureAudit("sample-confirm")
        rule.onNodeWithText("取消").performClick()
        Assert.assertEquals(1,selections.get());Assert.assertEquals(0,unexpected.get())
    }
    @Test fun saveDoesNotScanAndReclaimRequiresExplicitFlag()=runBlocking {
        val repository=TagRuleRepository(server)
        host.enqueue(json(ruleJson()))
        repository.save(null," 假日 ","(?i)holiday","FILE_NAME",setOf(9007199254740993L),true)
        val create=host.takeRequest();Assert.assertEquals("/api/v1/tag-rules",create.path)
        Assert.assertEquals(9007199254740993L,JSONObject(create.body.readUtf8()).getJSONArray("tagIds").getLong(0))
        Assert.assertEquals(1,host.requestCount)
        for(reclaim in listOf(false,true)) {
            host.enqueue(MockResponse().setResponseCode(204));repository.delete("rule",reclaim)
            val request=host.takeRequest();Assert.assertEquals("DELETE",request.method);Assert.assertEquals(reclaim.toString(),request.requestUrl!!.queryParameter("reclaimGeneratedTags"))
        }
    }
    @Test fun previewIsRuleDryRunAndApplyCannotAcceptAiPreview()=runBlocking {
        val repository=TagRuleRepository(server)
        host.enqueue(json(scan()))
        val preview=repository.preview(JSONObject().put("type","ALBUM").put("albumId","album"),setOf("rule"))
        val body=JSONObject(host.takeRequest().body.readUtf8())
        Assert.assertTrue(body.getBoolean("dryRun"));Assert.assertFalse(body.has("scanMode"));Assert.assertEquals("RULE",body.getJSONArray("sources").getString(0))
        host.enqueue(json(scan("applied",false)));val applied=repository.apply(preview)
        val apply=host.takeRequest();Assert.assertEquals("/api/v1/tag-scans/preview/apply",apply.path);Assert.assertEquals(0L,apply.bodySize)
        host.enqueue(MockResponse().setResponseCode(204));repository.undo(applied);Assert.assertEquals("DELETE",host.takeRequest().method)
        try {repository.apply(ruleScan(scan(source="AI")));Assert.fail()} catch(_:IllegalArgumentException) {}
        Assert.assertEquals(3,host.requestCount)
    }
    @Test fun scopeContractsAreExactAndSelectionBookmarksAreStable()=runBlocking {
        val targets=listOf(
            RuleScanTarget.Directory("library","Trips/2026 100%","旅行"),
            RuleScanTarget.TimeRange("2026-09-01T00:00:00+08:00","2026-09-02T23:59:59+08:00"),
            RuleScanTarget.Selection(setOf("b","a")),
        )
        val keys=listOf(setOf("type","libraryId","directoryPath"),setOf("type","takenFrom","takenTo"),setOf("type","assetIds"))
        targets.forEachIndexed {i,target ->
            host.enqueue(json(scan()));TagRuleRepository(server).preview(target.json())
            val body=JSONObject(host.takeRequest().body.readUtf8())
            Assert.assertTrue(body.getBoolean("dryRun"));Assert.assertEquals("RULE",body.getJSONArray("sources").getString(0))
            val actual=body.getJSONObject("scope")
            Assert.assertEquals(keys[i],actual.keys().asSequence().toSet())
            Assert.assertEquals(target.json().toString(),actual.toString())
        }
        Assert.assertEquals(targets.last().bookmark,RuleScanTarget.Selection(setOf("a","b")).bookmark)
        Assert.assertNotEquals(targets.last().bookmark,RuleScanTarget.Selection(setOf("a")).bookmark)
        val mutable=mutableSetOf("a");val frozen=RuleScanTarget.Selection(mutable);mutable.add("b")
        Assert.assertEquals(1,frozen.ids.size)
        Assert.assertTrue(runCatching {RuleScanTarget.Directory("library","","root")}.isFailure)
        Assert.assertTrue(runCatching {RuleScanTarget.Directory("library","../secret","secret")}.isFailure)
        Assert.assertTrue(runCatching {RuleScanTarget.TimeRange("2026-09-02T00:00:00Z","2026-09-01T00:00:00Z")}.isFailure)
        Assert.assertTrue(runCatching {RuleScanTarget.Selection((0..500).map {it.toString()}.toSet())}.isFailure)
    }
    @Test fun directoryPickerRejectsRootAndPreviewUsesSelectedSubdirectory() {
        val writes=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val url=request.requestUrl!!
                if(url.encodedPath=="/api/v1/folders") {
                    val folder=when {
                        url.queryParameter("libraryId")==null -> JSONObject().put("libraryId","library").put("path","").put("name","图库源").put("online",true)
                        url.queryParameter("path")=="" -> JSONObject().put("libraryId","library").put("path","Trips/2026 100%").put("name","旅行目录").put("online",true)
                        else -> null
                    }
                    return json(JSONObject().put("children",JSONObject().put("items",JSONArray().apply {folder?.let {put(it)}}).put("nextCursor",JSONObject.NULL)))
                }
                if(request.method=="POST") writes.add(JSONObject(request.body.readUtf8()))
                return json(scan())
            }
        }
        rule.setContent {PhotoTubeTheme {RuleScanScopePicker(server,{},{})}}
        rule.onNodeWithText("继续核对范围").assertIsNotEnabled()
        rule.onNodeWithText("选择目录").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("图库源").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("图库源").performClick()
        rule.onNodeWithText("选择此目录").performClick()
        rule.onNodeWithText("继续核对范围").assertIsNotEnabled()
        rule.onNodeWithText("图库源").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("图库源").fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodesWithText("图库源").onLast().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("旅行目录").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("旅行目录").performClick()
        rule.onNodeWithText("选择此目录").performClick()
        rule.onNodeWithText("继续核对范围").performClick()
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithText("开始规则预览").performClick()
        rule.waitUntil(10000) {writes.size==1}
        val scope=writes.single().getJSONObject("scope")
        Assert.assertEquals("DIRECTORY",scope.getString("type"));Assert.assertEquals("Trips/2026 100%",scope.getString("directoryPath"))
        Assert.assertFalse(scope.has("albumId"))
        rule.waitUntil(10000) {rule.onAllNodesWithText("应用预览结果").fetchSemanticsNodes().isNotEmpty()}
        rule.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File(context.getExternalFilesDir(null),"rule-scan-directory.png"))
    }
    @Test fun datePickerUsesLocalFullDaysAndDoesNotStartOnContinue() {
        val writes=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST") writes.add(JSONObject(request.body.readUtf8()))
                return json(scan())
            }
        }
        val first=java.time.YearMonth.now().atDay(1);val last=first.plusDays(1)
        rule.setContent {PhotoTubeTheme {RuleScanScopePicker(server,{},{})}}
        rule.onNodeWithText("拍摄时间").performClick()
        rule.onNodeWithText("选择开始日期").performClick()
        rule.onNodeWithTag("filter-day-$first").performScrollTo().performClick()
        rule.onNodeWithText("选择",useUnmergedTree=true).performClick()
        rule.onNodeWithText("选择结束日期").performClick()
        rule.onNodeWithTag("filter-day-$last").performScrollTo().performClick()
        rule.onNodeWithText("选择",useUnmergedTree=true).performClick()
        rule.onNodeWithText("继续核对范围").performClick()
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithText("开始规则预览").performClick()
        rule.waitUntil(10000) {writes.size==1}
        val scope=writes.single().getJSONObject("scope");val zone=java.time.ZoneId.systemDefault()
        Assert.assertEquals("TIME_RANGE",scope.getString("type"))
        Assert.assertEquals(first.atStartOfDay(zone).toInstant(),java.time.Instant.parse(scope.getString("takenFrom")))
        Assert.assertEquals(last.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1),java.time.Instant.parse(scope.getString("takenTo")))
    }
    @Test fun bulkSelectionPreviewsOnlyChosenIds() {
        val writes=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.requestUrl!!.encodedPath=="/api/v1/assets") return json("""{"items":[{"id":"chosen","kind":"PHOTO","state":"BROWSABLE","takenAt":"2026-09-01T00:00:00Z","fileName":"chosen.jpg","favorite":false}],"nextCursor":null}""")
                if(request.method=="POST") writes.add(JSONObject(request.body.readUtf8()))
                return json(scan())
            }
        }
        rule.setContent {PhotoTubeTheme {BulkAssetsScreen(server,WallFilters(),1,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("bulk-asset-chosen").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("bulk-asset-chosen").performClick()
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("规则预览").performClick()
        rule.onNodeWithText("选中的 1 项内容").assertExists()
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithText("开始规则预览").performClick()
        rule.waitUntil(10000) {writes.size==1}
        val scope=writes.single().getJSONObject("scope")
        Assert.assertEquals("SELECTION",scope.getString("type"));Assert.assertEquals("chosen",scope.getJSONArray("assetIds").getString(0));Assert.assertFalse(scope.has("filter"))
    }
    @Test fun chosenRulesAreSentAndDoNotReuseAllRulesBookmark() {
        val writes=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.requestUrl!!.encodedPath=="/api/v1/tag-rules") return json(JSONObject().put("items",JSONArray().put(ruleJson())).put("nextCursor",JSONObject.NULL))
                if(request.method=="POST") writes.add(JSONObject(request.body.readUtf8()))
                return json(scan())
            }
        }
        val target=RuleScanTarget.Selection(setOf("a"))
        rule.setContent {PhotoTubeTheme {RuleScanScreen(server,target,{},{})}}
        rule.onNodeWithText("全部已启用规则").performClick()
        rule.waitUntil(10000) {rule.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodes(isToggleable()).onFirst().performClick()
        rule.onNodeWithText("使用所选规则").performClick()
        rule.onNodeWithText("已选 1 条规则").assertExists()
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithText("开始规则预览").performClick()
        rule.waitUntil(10000) {writes.size==1}
        Assert.assertEquals("rule",writes.single().getJSONArray("ruleIds").getString(0))
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rule-scan-bookmarks",android.content.Context.MODE_PRIVATE)
        val key=server.base+"/"+ruleScanBookmark(target,setOf("rule"))
        rule.waitUntil(10000) {prefs.getString(key,null)=="preview"}
        Assert.assertNull(prefs.getString(server.base+"/"+target.bookmark,null))
        prefs.edit().remove(key).commit()
    }
    @Test fun highMatchPreviewNeedsConfirmationAndRestoresSavedRun() {
        val writes=java.util.Collections.synchronizedList(mutableListOf<String>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.method=="POST") writes.add(request.requestUrl!!.encodedPath)
                return if(request.requestUrl!!.encodedPath.contains("applied") || request.requestUrl!!.encodedPath.endsWith("apply")) json(scan("applied",false)) else json(scan())
            }
        }
        val album=remoteAlbum(JSONObject().put("id","album").put("name","旅行").put("kind","NORMAL").put("sortMode","TAKEN_AT_DESC").put("assetCount",10).put("cover",JSONObject.NULL))
        var visible by mutableStateOf(true)
        rule.setContent {PhotoTubeTheme {if(visible) RuleScanScreen(server,album,{},{})}}
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithText("开始规则预览").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("应用预览结果").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("本次命中范围较广（100.0%），请核对标签与样本后再应用。").assertExists()
        Assert.assertEquals(listOf("/api/v1/tag-scans"),writes)
        rule.onNodeWithText("应用预览结果").performScrollTo().performClick()
        Assert.assertEquals(1,writes.size)
        rule.onNodeWithText("确认应用").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("撤销本批标签").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals("/api/v1/tag-scans/preview/apply",writes.last())
        rule.onNodeWithText("本次命中范围较广（100.0%），请核对标签与样本后再应用。").assertDoesNotExist()
        rule.onNodeWithText("应用预览结果").assertDoesNotExist()
        rule.onNodeWithText("应用结果").performScrollTo().assertIsDisplayed()
        captureAudit("rule-applied-repaired")
        val preferences=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("rule-scan-bookmarks",android.content.Context.MODE_PRIVATE)
        Assert.assertEquals("applied",preferences.getString(server.base+"/album",null))
        rule.runOnIdle {visible=false};rule.waitForIdle();rule.runOnIdle {visible=true}
        rule.waitUntil(10000) {rule.onAllNodesWithText("撤销本批标签").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(2,writes.size)
        rule.onNodeWithText("本次命中范围较广（100.0%），请核对标签与样本后再应用。").assertDoesNotExist()
        rule.onNodeWithText("应用预览结果").assertDoesNotExist()
        preferences.edit().remove(server.base+"/album").commit()
    }
    @Test fun rulePickerCapsSelectionAndAllowsRemovingDisabledSelection() {
        val requests=java.util.concurrent.CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                val entries=JSONArray()
                listOf("disabled", "first", "second", "off").forEach { id ->
                    entries.put(ruleJson().put("id",id).put("name",id).put("enabled",id=="first" || id=="second"))
                }
                return json(JSONObject().put("items",entries).put("nextCursor",JSONObject.NULL))
            }
        }
        val initial=(1..98).map { "selected-$it" }.toSet()+"disabled"
        var result:Set<String>?=null
        rule.setContent {PhotoTubeTheme {
            com.yunai.phototube.ui.remote.ScanRulePicker(server,initial,{}, {result=it})
        }}
        rule.waitUntil(10000) {rule.onAllNodesWithContentDescription("first").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("off").assertIsNotEnabled().performTouchInput {click()}
        rule.onNodeWithText("已选 99/100 条").assertExists()
        rule.onNodeWithContentDescription("first").performClick().assertIsOn()
        rule.onNodeWithContentDescription("second").assertIsNotEnabled().performTouchInput {click()}
        rule.onNodeWithText("已选 100/100 条").assertExists()
        rule.onNodeWithContentDescription("disabled").assertIsEnabled().performClick().assertIsOff().assertIsNotEnabled()
        rule.onNodeWithContentDescription("second").assertIsEnabled().performClick()
        Assert.assertNull(result)
        rule.onNodeWithText("使用所选规则").performClick()
        Assert.assertEquals(100,result!!.size)
        Assert.assertFalse(result!!.contains("disabled"));Assert.assertFalse(result!!.contains("off"))
        Assert.assertTrue(result!!.containsAll(listOf("first","second")))
        Assert.assertTrue(requests.all {it.method=="GET"})
    }

}
