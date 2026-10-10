package com.yunai.phototube

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.PrivateSpace
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.yunai.phototube.ui.remote.BulkAssetsScreen
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PrivateAccessTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var host: MockWebServer
    private lateinit var server: PhotoTubeServer
    private val expired = AtomicInteger()
    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)
    private fun session(unlocked: Boolean = true) = json("""{"authenticated":true,"privateAccessUnlocked":$unlocked,"privateAccessExpiresAt":"${Instant.now().plusSeconds(600)}"}""")
    private fun asset() = """{"id":"private-one","kind":"PHOTO","state":"BROWSABLE","takenAt":"2026-09-01T00:00:00Z","takenAtOffsetMinutes":0,"fileName":"private.jpg","contentHash":null,"width":800,"height":600,"favorite":false,"durationSec":null}"""
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host = MockWebServer().apply { useHttps(tls.sslSocketFactory(), false); start(java.net.InetAddress.getLoopbackAddress(), 0) }
        server = PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext, host.url("/").newBuilder().host("localhost").build().toString(), { expired.incrementAndGet() }, OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .addInterceptor {chain ->
                check(chain.request().url.host=="localhost")
                chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
            }.build())
        server.cookies.saveFromResponse(server.origin, listOf(Cookie.Builder().name("phototube_session").value("fixture").hostOnlyDomain("localhost").path("/").secure().build()))
    }
    @After fun cleanup() { server.cookies.clear(); server.dispose(); host.shutdown() }
    @Test fun privateContractUsesSharedCookieAndTypedPrivateFilterWithoutArchive() = runBlocking {
        val repository = PrivateRepository(server)
        host.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"INVALID_CREDENTIALS","message":"错误口令"}"""))
        try { repository.unlock(" wrong "); Assert.fail() } catch (_: ServerError) {}
        Assert.assertEquals(0, expired.get())
        val wrong = host.takeRequest()
        Assert.assertEquals(" wrong ", JSONObject(wrong.body.readUtf8()).getString("password"))
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        host.enqueue(MockResponse().setResponseCode(204)); repository.unlock("fixture")
        Assert.assertEquals("POST", host.takeRequest().method)
        host.enqueue(session()); Assert.assertTrue(repository.expiry().isAfter(Instant.now())); host.takeRequest()
        val filter = WallFilters(privateOnly = true, archived = true)
        Assert.assertFalse(filter.query().containsKey("archived")); Assert.assertTrue(filter.json().getBoolean("private"))
        host.enqueue(json("""{"matchedCount":1000,"succeededCount":999,"failedCount":1}"""))
        AssetActions(server).executeFilter(AssetAction.PRIVATE, filter, false)
        val operation = host.takeRequest()
        Assert.assertEquals("/api/v1/assets/private", operation.path)
        val body = JSONObject(operation.body.readUtf8())
        Assert.assertFalse(body.getBoolean("private")); Assert.assertTrue(body.getJSONObject("filter").getBoolean("private")); Assert.assertFalse(body.has("assetIds"))
        host.enqueue(MockResponse().setResponseCode(204)); repository.lock()
        val lock = host.takeRequest(); Assert.assertEquals("DELETE", lock.method); Assert.assertEquals(0L, lock.bodySize)
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
    }
    @Test fun serverSessionFactIsRequiredAndLockDuringUnlockCannotResurrectLease() = runBlocking<Unit> {
        val controller = PrivateAccessController(server)
        host.enqueue(MockResponse().setResponseCode(204)); host.enqueue(session(false))
        try { controller.unlock("fixture"); Assert.fail() } catch (_: ServerError) {}
        Assert.assertNull(controller.lease.value)
        host.takeRequest(); host.takeRequest()
        host.enqueue(MockResponse().setResponseCode(204).setHeadersDelay(300, TimeUnit.MILLISECONDS))
        host.enqueue(session()); host.enqueue(MockResponse().setResponseCode(204))
        val pending = async { controller.unlock("fixture") }
        withContext(Dispatchers.IO) { host.takeRequest(5, TimeUnit.SECONDS) ?: error("unlock missing") }
        withContext(Dispatchers.Main) { controller.lock() }
        pending.await()
        Assert.assertNull(controller.lease.value)
        Assert.assertEquals("GET", host.takeRequest(5, TimeUnit.SECONDS)!!.method)
        Assert.assertEquals("DELETE", host.takeRequest(5, TimeUnit.SECONDS)!!.method)
        host.enqueue(MockResponse().setResponseCode(204))
        withContext(Dispatchers.Main) { controller.close() }
        host.takeRequest(5, TimeUnit.SECONDS)
    }
    @Test fun revokedForkCannotFetchOrRetainImagesAndDoesNotCancelOrdinaryClient() = runBlocking {
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "POST" || request.method == "DELETE" -> MockResponse().setResponseCode(204)
                request.path == "/api/v1/auth/session" -> session()
                else -> json("""{"items":[],"nextCursor":null}""")
            }
        }
        val controller = PrivateAccessController(server)
        controller.unlock("fixture")
        val isolated = controller.lease.value!!.server
        Assert.assertNull(isolated.imageLoader.memoryCache)
        Assert.assertNull(isolated.imageLoader.diskCache)
        Assert.assertNotSame(server.client.dispatcher, isolated.client.dispatcher)
        Assert.assertSame(server.cookies, isolated.cookies)
        isolated.assets(null, WallFilters(privateOnly = true))
        withContext(Dispatchers.Main) { controller.lock() }
        Assert.assertNull(controller.lease.value)
        try { isolated.assets(null, WallFilters(privateOnly = true)); Assert.fail() } catch (_: java.io.IOException) {}
        server.assets(null)
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        withContext(Dispatchers.Main) { controller.close() }
    }
    @Test fun forbiddenPrivateRequestRevokesLeaseWithoutLoggingOut() = runBlocking {
        val controller = PrivateAccessController(server)
        host.enqueue(MockResponse().setResponseCode(204)); host.enqueue(session())
        controller.unlock("fixture")
        host.takeRequest(); host.takeRequest()
        val isolated = controller.lease.value!!.server
        host.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"PRIVATE_ACCESS_REQUIRED","message":"重新解锁"}"""))
        host.enqueue(MockResponse().setResponseCode(204))
        try { isolated.assets(null, WallFilters(privateOnly = true)); Assert.fail() } catch (_: java.io.IOException) {}
        withTimeout(5000) { controller.lease.first { it == null } }
        Assert.assertEquals(0, expired.get())
        Assert.assertTrue(server.cookies.loadForRequest(server.origin).isNotEmpty())
        host.takeRequest(); Assert.assertEquals("DELETE", host.takeRequest(5, TimeUnit.SECONDS)!!.method)
        host.enqueue(MockResponse().setResponseCode(204))
        withContext(Dispatchers.Main) { controller.close() }
        host.takeRequest(5, TimeUnit.SECONDS)
        Unit
    }
    @Test fun hidingRotatesOldImageCacheAndLateOrdinaryPageCannotUnredactThumbnail() = runBlocking {
        val item = RemoteAsset("private-one", "PHOTO", "BROWSABLE", "2026-09-01T00:00:00Z", 0, "private.jpg", "a".repeat(64), 800, 600, false, null)
        Assert.assertNotNull(server.thumbnail(item))
        val cache = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "shared-media").apply { mkdirs() }
        val shared = java.io.File(cache, "private-one.jpg").apply { writeText("fixture") }
        val originalLoader = server.imageLoader
        host.enqueue(json("""{"items":[${asset()}],"nextCursor":null}""").setBodyDelay(500, TimeUnit.MILLISECONDS))
        val oldPage = async { server.assets(null) }
        withContext(Dispatchers.IO) { host.takeRequest(5, TimeUnit.SECONDS) ?: error("page missing") }
        host.enqueue(json("""{"succeeded":["private-one"],"failed":[]}"""))
        AssetActions(server).execute(AssetAction.PRIVATE, setOf(item.id), true)
        Assert.assertNotSame(originalLoader, server.imageLoader)
        Assert.assertFalse(shared.exists())
        Assert.assertNull(server.thumbnail(item))
        oldPage.await()
        Assert.assertNull(server.thumbnail(item))
    }
    @Test fun uncertainHideResponseStillRedactsOldThumbnail() = runBlocking {
        val item = RemoteAsset("private-one", "PHOTO", "BROWSABLE", "2026-09-01T00:00:00Z", 0, "private.jpg", "a".repeat(64), 800, 600, false, null)
        Assert.assertNotNull(server.thumbnail(item))
        host.enqueue(MockResponse().setResponseCode(503).setBody("""{"code":"INTERNAL_ERROR","message":"unknown"}"""))
        try { AssetActions(server).execute(AssetAction.PRIVATE, setOf(item.id), true); Assert.fail() } catch (_: ServerError) {}
        Assert.assertNull(server.thumbnail(item))
        Assert.assertEquals(1, host.requestCount)
    }
    @Test fun bulkHideRequiresPasswordAndPreservesFrozenSelection() {
        val writes = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/private-access" -> MockResponse().setResponseCode(204)
                request.path == "/api/v1/auth/session" -> session()
                request.path == "/api/v1/assets/private" -> {
                    writes.add(JSONObject(request.body.readUtf8()))
                    json("""{"succeeded":["private-one"],"failed":[]}""")
                }
                else -> json("""{"items":[${asset()}],"nextCursor":null}""")
            }
        }
        rule.setContent { PhotoTubeTheme { BulkAssetsScreen(server, WallFilters(), 1, {}, {}) } }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("bulk-asset-private-one").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("bulk-scope").performClick()
        rule.onNodeWithText("选择已加载").performClick()
        rule.onNodeWithTag("bulk-actions").performClick()
        rule.onNodeWithText("移入私密空间").performClick()
        rule.onNodeWithText("确认移入私密空间").performClick()
        Assert.assertTrue(writes.isEmpty())
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("成功 1 项，0 项未完成。").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertEquals(1, writes.size)
        Assert.assertTrue(writes.single().getBoolean("private"))
        Assert.assertEquals("private-one", writes.single().getJSONArray("assetIds").getString(0))
        Assert.assertFalse(writes.single().has("filter"))
    }
    @Test fun privateViewerReusesOriginalAndMovesItemBackToLibrary() {
        val hidden = java.util.concurrent.atomic.AtomicBoolean(true)
        val originals = AtomicInteger()
        val png = java.io.ByteArrayOutputStream().apply {
            android.graphics.Bitmap.createBitmap(48, 32, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/auth/private-access" -> MockResponse().setResponseCode(204)
                request.path == "/api/v1/auth/session" -> session()
                request.path == "/api/v1/assets/private" -> {
                    val body = JSONObject(request.body.readUtf8())
                    Assert.assertFalse(body.getBoolean("private"))
                    Assert.assertEquals("private-one", body.getJSONArray("assetIds").getString(0))
                    hidden.set(false)
                    json("""{"succeeded":["private-one"],"failed":[]}""")
                }
                request.path!!.startsWith("/api/v1/assets/timeline-summary") -> json("""{"granularity":"MONTH","groups":[]}""")
                request.path == "/api/v1/assets/private-one/original" -> {
                    originals.incrementAndGet()
                    MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(png))
                }
                request.path == "/api/v1/assets/private-one" -> json(asset())
                else -> json("""{"items":[${if (hidden.get()) asset() else ""}],"nextCursor":null}""")
            }
        }
        rule.setContent { PhotoTubeTheme { PrivateSpace(server, {}, {}) } }
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("private-asset-private-one").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("private-asset-private-one").performClick()
        rule.waitUntil(10000) { originals.get() > 0 }
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("移出私密空间").performClick()
        rule.onNodeWithText("移出后，此内容将恢复为普通可见内容。").assertExists()
        rule.onNodeWithText("确认").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("还没有私密内容").fetchSemanticsNodes().isNotEmpty() }
        Assert.assertFalse(hidden.get())
        rule.onNodeWithContentDescription("关闭照片").assertDoesNotExist()
    }
    @Test fun emptyPrivateLibraryDisablesSelectionAndManualLockClearsContent() {
        val locks = AtomicInteger()
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "DELETE" && request.path == "/api/v1/auth/private-access" -> {
                    locks.incrementAndGet(); MockResponse().setResponseCode(204)
                }
                request.method == "POST" && request.path == "/api/v1/auth/private-access" -> MockResponse().setResponseCode(204)
                request.path == "/api/v1/auth/session" -> session()
                request.path!!.startsWith("/api/v1/assets/timeline-summary") -> json("""{"granularity":"MONTH","groups":[]}""")
                request.path!!.startsWith("/api/v1/assets?") -> json("""{"items":[],"nextCursor":null}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        rule.setContent { PhotoTubeTheme { PrivateSpace(server, {}, {}) } }
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("还没有私密内容").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("选择").assertIsNotEnabled()
        rule.onNodeWithText("重复照片").assertHasClickAction()
        rule.onNodeWithText("XMP 导出").assertHasClickAction()
        rule.onNodeWithText("立即锁定").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("private-grid").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("private-password").assertExists()
        rule.onNodeWithText("解锁").assertIsNotEnabled()
        rule.waitUntil(5000) { locks.get() > 0 }
    }

    @Test fun backgroundDuringViewerExitClearsHandoffAndRequiresUnlock() {
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse = when {
                request.method=="POST" || request.method=="DELETE" -> MockResponse().setResponseCode(204)
                request.path=="/api/v1/auth/session" -> session()
                request.path!!.startsWith("/api/v1/assets/timeline-summary") -> json("""{"granularity":"MONTH","groups":[{"key":"2026-09","startsAt":"2026-09-01T00:00:00Z","count":1}]}""")
                request.requestUrl!!.encodedPath=="/api/v1/assets/private-one" -> json(asset())
                request.requestUrl!!.encodedPath.endsWith("/original") -> MockResponse().setResponseCode(404)
                else -> json("""{"items":[${asset()}],"nextCursor":null}""")
            }
        }
        val owner=object:LifecycleOwner {val registry=LifecycleRegistry.createUnsafe(this);override val lifecycle:Lifecycle get()=registry}
        rule.runOnIdle {owner.registry.currentState=Lifecycle.State.RESUMED}
        var handoff:com.yunai.phototube.ui.media.MediaSourceHandoff?=null
        var activity:android.app.Activity?=null
        rule.setContent {
            var context=LocalContext.current
            while(context is android.content.ContextWrapper && context !is android.app.Activity) context=context.baseContext
            activity=context as android.app.Activity
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {PhotoTubeTheme {
                handoff=com.yunai.phototube.ui.media.LocalMediaSourceHandoff.current
                PrivateSpace(server,{}, {})
            }}
        }
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("private-asset-private-one").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithTag("private-asset-private-one").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("media-viewer").fetchSemanticsNodes().isNotEmpty()}
        rule.runOnIdle {
            Assert.assertNotNull(handoff!!.source)
            Assert.assertNotNull(handoff!!.opacity)
            Assert.assertTrue(activity!!.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        rule.mainClock.autoAdvance=false
        try {
            rule.onNodeWithContentDescription("关闭照片").performClick()
            rule.mainClock.advanceTimeBy(32)
            rule.runOnIdle {owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)}
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.onNodeWithTag("media-viewer").assertDoesNotExist()
            rule.onNodeWithTag("private-grid").assertDoesNotExist()
            rule.runOnIdle {
                Assert.assertNull(handoff!!.source)
                Assert.assertNull(handoff!!.opacity)
                Assert.assertTrue(activity!!.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
                owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            rule.mainClock.advanceTimeBy(400)
            rule.onNodeWithTag("private-password").assertIsDisplayed()
            rule.onNodeWithText("解锁").assertIsNotEnabled()
            rule.onNodeWithTag("media-viewer").assertDoesNotExist()
        } finally {rule.mainClock.autoAdvance=true}
    }

    @Test fun backgroundRemovesPrivateGridAndRequiresNewPassword() {
        val requests = java.util.Collections.synchronizedList(mutableListOf<RecordedRequest>())
        host.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return when {
                    request.method == "POST" || request.method == "DELETE" -> MockResponse().setResponseCode(204)
                    request.path == "/api/v1/auth/session" -> session()
                    request.path!!.startsWith("/api/v1/assets/timeline-summary") -> json("""{"granularity":"MONTH","groups":[{"key":"2026-09","startsAt":"2026-09-01T00:00:00Z","count":1}]}""")
                    else -> json("""{"items":[${asset()}],"nextCursor":null}""")
                }
            }
        }
        val owner = object : LifecycleOwner { val registry = LifecycleRegistry.createUnsafe(this); override val lifecycle: Lifecycle get() = registry }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        var context: android.content.Context? = null
        rule.setContent { context = LocalContext.current; CompositionLocalProvider(LocalLifecycleOwner provides owner) { PhotoTubeTheme { PrivateSpace(server, {}, {}) } } }
        rule.onNodeWithTag("private-grid").assertDoesNotExist()
        Assert.assertEquals(0, requests.size)
        rule.onNodeWithTag("private-password").performTextInput("fixture")
        rule.onNodeWithText("解锁").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("private-asset-private-one").fetchSemanticsNodes().isNotEmpty() }
        val photoBounds=rule.onNodeWithTag("private-asset-private-one").fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(800f/600f,photoBounds.width/photoBounds.height,.02f)
        rule.runOnIdle {
            var c = context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
            Assert.assertTrue((c as android.app.Activity).window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        rule.waitUntil(10000) { rule.onAllNodesWithTag("private-grid").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("private-password").assertExists()
        rule.onNodeWithText("解锁").assertIsNotEnabled()
        rule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        rule.onNodeWithTag("private-grid").assertDoesNotExist()
        Assert.assertEquals(1, requests.count { it.method == "POST" })
        rule.waitUntil(5000) { requests.any { it.method == "DELETE" } }
        Assert.assertTrue(requests.filter { it.path!!.startsWith("/api/v1/assets") }.all { it.requestUrl!!.queryParameter("private") == "true" && it.requestUrl!!.queryParameter("archived") == null })
    }
}
