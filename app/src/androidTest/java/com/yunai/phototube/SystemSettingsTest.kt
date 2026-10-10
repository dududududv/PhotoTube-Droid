package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.AiProviderSettings
import com.yunai.phototube.ui.remote.SystemSettings
import com.yunai.phototube.ui.remote.ChangePasswordScreen
import com.yunai.phototube.ui.remote.DiagnosticsScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*

class SystemSettingsTest {
    @Test fun auditProviderFormWithoutSaving() {
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                Assert.assertEquals("GET", request.method)
                Assert.assertEquals("/api/v1/system/ai-settings", request.path)
                return json(settings())
            }
        }
        rule.setContent { PhotoTubeTheme { AiProviderSettings(SystemRepository(server), {}) } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("ai-provider-model").fetchSemanticsNodes().isNotEmpty() }
        fun capture(name: String) {
            rule.waitForIdle()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(java.io.File(context.getExternalFilesDir(null), "audit-provider-$name.png"))
        }
        capture("top")
        rule.onNodeWithText("替换密钥").performScrollTo().performClick()
        rule.onNodeWithTag("ai-provider-key").performScrollTo().assertIsDisplayed()
        capture("replace")
        rule.onNodeWithText("清除密钥").performScrollTo().performClick()
        rule.onNodeWithText("保存配置").performScrollTo().assertIsDisplayed()
        capture("clear")
        Assert.assertEquals(1, host.requestCount)
    }
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    private fun settings()=JSONObject().put("baseUrl","https://vision.example/v1/chat/completions").put("model","vision").put("thinkingEnabled",true).put("apiKeyConfigured",true).put("configured",true).put("updatedAt",JSONObject.NULL)
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start(java.net.InetAddress.getLoopbackAddress(),0)}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").newBuilder().host("localhost").build().toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    @Test fun keyModesHaveDistinctPayloadsAndPreserveThinking()=runBlocking {
        val repository=SystemRepository(server)
        for(mode in KeyUpdate.entries) {
            val body=aiSettingsBody("https://vision.example/responses","vision",true,mode,"fixture-key")
            host.enqueue(json(settings()));repository.saveAi(body)
            val request=host.takeRequest();Assert.assertEquals("PUT",request.method)
            val sent=JSONObject(request.body.readUtf8());Assert.assertTrue(sent.getBoolean("thinkingEnabled"))
            when(mode) {KeyUpdate.KEEP->Assert.assertFalse(sent.has("apiKey"));KeyUpdate.CLEAR->Assert.assertTrue(sent.isNull("apiKey"));KeyUpdate.REPLACE->Assert.assertEquals("fixture-key",sent.getString("apiKey"))}
        }
    }
    @Test fun logoutFailureDoesNotClearCookieAndSuccessDoes()=runBlocking {
        val repository=SystemRepository(server)
        server.cookies.saveFromResponse(server.origin,listOf(Cookie.Builder().name("phototube_session").value("fixture").hostOnlyDomain("localhost").path("/").secure().build()))
        host.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"OFFLINE","message":"暂时不可用"}"""))
        try {repository.logout();Assert.fail()} catch(_:ServerError) {}
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        host.takeRequest();host.enqueue(MockResponse().setResponseCode(204));repository.logout()
        val request=host.takeRequest();Assert.assertEquals("/api/v1/auth/logout",request.path);Assert.assertEquals("POST",request.method);Assert.assertEquals(0L,request.bodySize)
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isEmpty())
    }
    @Test fun savingModelKeepsExistingKeyAndDoesNotTestProvider() {
        val writes=java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                Assert.assertEquals("/api/v1/system/ai-settings",request.path)
                if(request.method=="PUT") writes.add(JSONObject(request.body.readUtf8()))
                return json(settings())
            }
        }
        rule.setContent {PhotoTubeTheme {AiProviderSettings(SystemRepository(server),{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("ai-provider-model").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("ai-provider-model").performScrollTo().performTextReplacement("new-vision")
        rule.onNodeWithText("保存配置").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("配置已保存").fetchSemanticsNodes().isNotEmpty()}
        Assert.assertEquals(1,writes.size);Assert.assertFalse(writes.single().has("apiKey"));Assert.assertTrue(writes.single().getBoolean("thinkingEnabled"))
        rule.onNodeWithText("测试成功").assertDoesNotExist()
    }
    @Test fun logoutConfirmationCancelAndFailureKeepLocalSession() {
        sessionCookie()
        val posts=java.util.concurrent.atomic.AtomicInteger()
        var signedOut=0
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when(request.requestUrl!!.encodedPath) {
                "/api/v1/auth/session" -> json(JSONObject().put("authenticated",true).put("user",JSONObject().put("username","fixture")).put("privateAccessUnlocked",false).put("originalDeleteEnabled",false))
                "/api/v1/auth/logout" -> {
                    Assert.assertEquals("POST",request.method)
                    posts.incrementAndGet()
                    json("""{"code":"OFFLINE","message":"本地模拟：退出失败"}""").setResponseCode(503)
                }
                else -> MockResponse().setResponseCode(503)
            }
        }
        rule.setContent {PhotoTubeTheme {SystemSettings(server,{signedOut++},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("已登录：fixture").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("退出登录").performScrollTo().performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()
        Assert.assertEquals(0,posts.get())
        rule.onNodeWithText("退出登录").performClick()
        rule.onNodeWithText("确认退出").performClick()
        rule.waitUntil(10000) {posts.get()==1}
        rule.onNodeWithText("本地模拟：退出失败").performScrollTo().assertIsDisplayed()
        Assert.assertEquals(0,signedOut)
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
    }
    @Test fun degradedHealthAndUnavailableAlertsAreNotReportedAsHealthy() {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when(request.requestUrl!!.encodedPath) {
                "/api/v1/auth/session"->json(JSONObject().put("authenticated",true).put("user",JSONObject().put("username","fixture")).put("privateAccessUnlocked",false).put("originalDeleteEnabled",false))
                "/api/v1/health"->json(JSONObject().put("status","degraded").put("database",JSONObject().put("reachable",false)).put("xmpExport",JSONObject().put("available",false)))
                else->MockResponse().setResponseCode(503).setBody("""{"code":"OFFLINE","message":"告警读取失败"}""")
            }
        }
        rule.setContent {PhotoTubeTheme {SystemSettings(server,{},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("服务降级").fetchSemanticsNodes().isNotEmpty()}
        rule.onAllNodes(hasText("AI Worker", substring=true)).assertCountEquals(0)
        rule.onNodeWithText("数据库：不可用").assertExists();rule.onNodeWithText("当前没有告警").assertDoesNotExist()
        rule.waitUntil(10000) {rule.onAllNodesWithText("即时告警：告警读取失败").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun sessionCookie() = server.cookies.saveFromResponse(server.origin, listOf(Cookie.Builder().name("phototube_session").value("fixture").hostOnlyDomain("localhost").path("/").secure().build()))
    @Test fun passwordUnicodePayloadAndVerificationDoNotExpireSession() = runBlocking {
        val repository = SystemRepository(server)
        sessionCookie()
        val newPassword = " " + "🔐".repeat(10) + " "
        host.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_CREDENTIALS","message":"当前密码错误"}"""))
        try { repository.changePassword(" old ", newPassword); Assert.fail() } catch (error: ServerError) { Assert.assertEquals("INVALID_CREDENTIALS", error.code) }
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        val request = host.takeRequest()
        Assert.assertEquals("/api/v1/auth/password", request.path)
        val body = JSONObject(request.body.readUtf8())
        Assert.assertEquals(" old ", body.getString("currentPassword"))
        Assert.assertEquals(newPassword, body.getString("newPassword"))
        Assert.assertEquals(2, body.length())
        try { repository.changePassword("old", "🔐".repeat(11)); Assert.fail() } catch (_: IllegalArgumentException) {}
        Assert.assertEquals(1, host.requestCount)
        host.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED","message":"会话失效"}"""))
        try { repository.changePassword("old", newPassword); Assert.fail() } catch (_: ServerError) {}
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isEmpty())
        host.takeRequest()
        sessionCookie()
        host.enqueue(MockResponse().setResponseCode(204))
        repository.changePassword("old", newPassword)
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isEmpty())
    }
    @Test fun passwordRateLimitPreservesSessionAndDoesNotRetry() = runBlocking {
        sessionCookie()
        host.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120").setBody("""{"code":"RATE_LIMITED","message":"稍后再试"}"""))
        try { SystemRepository(server).changePassword("old", "new-password-12"); Assert.fail() }
        catch (limited: PasswordRateLimit) { Assert.assertEquals(120_000L, limited.waitMillis) }
        Assert.assertEquals(1, host.requestCount)
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
    }
    @Test fun passwordFormRetainsDraftOnRejectionAndOnlySignsOutOn204() {
        val writes = java.util.concurrent.atomic.AtomicInteger()
        val signedOut = java.util.concurrent.atomic.AtomicInteger()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (writes.incrementAndGet() == 1)
                MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_CREDENTIALS","message":"错误"}""") else MockResponse().setResponseCode(204)
        }
        rule.setContent { PhotoTubeTheme { ChangePasswordScreen(SystemRepository(server), { signedOut.incrementAndGet() }, {}) } }
        rule.onNodeWithTag("password-current").performScrollTo().performTextInput("wrong")
        rule.onNodeWithTag("password-new").performScrollTo().performTextInput("new-password-12")
        rule.onNodeWithTag("password-confirm").performScrollTo().performTextInput("mismatch")
        rule.onNodeWithText("修改并退出全部设备").assertIsNotEnabled()
        rule.onNodeWithTag("password-confirm").performTextReplacement("new-password-12")
        rule.onNodeWithText("修改并退出全部设备").performScrollTo().performClick()
        Assert.assertEquals(0, writes.get())
        rule.onNodeWithText("确认修改").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("当前密码不正确，请重新输入").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals(0, signedOut.get())
        rule.onNodeWithTag("password-current").performScrollTo().performTextReplacement("correct")
        // The unchanged confirmation and new password still form a valid draft.
        rule.onNodeWithText("修改并退出全部设备").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText("确认修改").performClick()
        rule.waitUntil(10000) { signedOut.get() == 1 }
        Assert.assertEquals(2, writes.get())
    }
    @Test fun uncertainPasswordResultDoesNotRepeatWriteAndOffersReauthentication() {
        val writes = java.util.concurrent.atomic.AtomicInteger()
        val signedOut = java.util.concurrent.atomic.AtomicInteger()
        sessionCookie()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                writes.incrementAndGet()
                return MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR","message":"unknown"}""")
            }
        }
        rule.setContent { PhotoTubeTheme { ChangePasswordScreen(SystemRepository(server), { signedOut.incrementAndGet() }, {}) } }
        rule.onNodeWithTag("password-current").performScrollTo().performTextInput("fixture")
        rule.onNodeWithTag("password-new").performScrollTo().performTextInput("new-password-12")
        rule.onNodeWithTag("password-confirm").performScrollTo().performTextInput("new-password-12")
        rule.onNodeWithText("修改并退出全部设备").performScrollTo().performClick()
        rule.onNodeWithText("确认修改").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("返回登录核对").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("修改并退出全部设备").assertIsNotEnabled()
        Assert.assertEquals(1, writes.get())
        Assert.assertEquals(0, signedOut.get())
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        rule.onNodeWithText("返回登录核对").performScrollTo().performClick()
        rule.waitUntil(10000) { signedOut.get() == 1 }
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isEmpty())
        Assert.assertEquals(1, writes.get())
    }
    @Test fun missingDiagnosticsExplainsDeploymentAndRefreshCanRecover() {
        host.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"NOT_FOUND","message":"接口路径不存在"}"""))
        host.enqueue(json(JSONObject("""{"generatedAt":"2026-09-17T00:00:00Z","version":"updated","schema":{"version":47,"dirty":false},"cache":{"thumbnailBytes":0,"videoPreviewBytes":0,"limitBytes":null},"capabilities":{"aiConfigured":false,"originalDeleteEnabled":false,"xmpExportAvailable":false}}""")))
        rule.setContent { PhotoTubeTheme { DiagnosticsScreen(SystemRepository(server), {}) } }
        val message = "当前服务器未提供系统诊断，请更新后端后重试。"
        rule.waitUntil(10000) { rule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("构建与数据库").assertDoesNotExist()
        rule.onNodeWithText("刷新诊断").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("构建版本：updated").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(message).assertDoesNotExist()
        repeat(2) {
            val request = host.takeRequest()
            Assert.assertEquals("GET", request.method)
            Assert.assertEquals("/api/v1/system/diagnostics", request.path)
        }
        Assert.assertEquals(2, host.requestCount)
    }

    @Test fun diagnosticsPreservesUnknownStatisticsAndNeverProbesAi() {
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request.method + " " + request.path)
                return json(JSONObject("""{"generatedAt":"2026-09-17T00:00:00Z","version":"dev","schema":{"version":null,"dirty":true},"cache":{"thumbnailBytes":null,"videoPreviewBytes":0,"limitBytes":null},"capabilities":{"aiConfigured":true,"originalDeleteEnabled":false,"xmpExportAvailable":true},"privatePath":"must-not-render"}"""))
            }
        }
        rule.setContent { PhotoTubeTheme { DiagnosticsScreen(SystemRepository(server), {}) } }
        rule.waitUntil(10000) { rule.onAllNodesWithText("缩略图与编辑预览：统计不可用").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("视频 AI 拼图：0.0 MiB").assertExists()
        rule.onNodeWithText("缓存上限：未配置").assertExists()
        rule.onNodeWithText("数据库版本：空库").assertExists()
        rule.onNodeWithText("must-not-render").assertDoesNotExist()
        Assert.assertEquals(listOf("GET /api/v1/system/diagnostics"), requests.toList())
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(java.io.File(context.getExternalFilesDir(null), "diagnostics-preview.png"))
    }

}
