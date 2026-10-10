package com.yunai.phototube

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.PhotoTubeContent
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.json.*
import org.junit.*
import java.io.ByteArrayOutputStream

/** Shared real app host backed only by an isolated TLS service. */
abstract class RemoteAppTest {
    @get:Rule val rule = createAndroidComposeRule<UiTestActivity>()
    protected val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var host: MockWebServer
    private var appearanceBackup: Map<String, Any?> = emptyMap()
    private lateinit var ownerStore: androidx.lifecycle.ViewModelStore
    internal lateinit var wall: PhotoWallViewModel
    private val prefs = "fixture_remote_ui_${javaClass.simpleName}"
    protected open fun fileName(i: Int) = "restore-$i.${if (i == 1) "mp4" else "jpg"}"
    protected open val assetCountFixture = 220
    protected open fun favoriteFixture(index: Int): Boolean = false
    protected open fun mutationFixture(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(405)
    protected open fun readFixture(request: RecordedRequest): MockResponse? = null
    private fun asset(i: Int) = JSONObject().put("id", "restore-$i").put("kind", if (i == 1) "VIDEO" else "PHOTO").put("state", "BROWSABLE")
        .put("takenAt", "2026-09-01T00:00:00Z").put("fileName", fileName(i)).put("durationSec", if (i == 1) 10 else JSONObject.NULL).put("favorite", favoriteFixture(i))
        .put("contentHash", "a".repeat(64)).put("width", 48).put("height", 32)
    protected open val albumKindFixture = "NORMAL"
    private fun album() = JSONObject().put("id", "restore-album").put("name", "恢复测试相册").put("kind", albumKindFixture)
        .put("sortMode", "TAKEN_AT_ASC").put("assetCount", assetCountFixture)
        .apply { if (albumKindFixture == "SMART") put("filter", JSONObject().put("archived", false)) }
    private fun directory(library: String?, path: String = "") = JSONObject()
        .put("libraryId", library ?: JSONObject.NULL).put("path", path)
        .put("kind", if (library == null) "ROOT" else if (path.isEmpty()) "LIBRARY" else "DIRECTORY")
        .put("name", if (library == null) "媒体库" else path.ifEmpty { "测试媒体库" })
        .put("online", true).put("directAssetCount", assetCountFixture).put("hasChildren", path.isEmpty())
    private fun matching(keyword: String) = (0 until assetCountFixture).filter { fileName(it).contains(keyword, ignoreCase = true) }
    private fun page(offset: Int, keyword: String = ""): JSONObject {
        val matching = matching(keyword)
        return JSONObject().put("items", JSONArray(matching.drop(offset).take(100).map(::asset)))
            .put("nextCursor", if (offset + 100 < matching.size) (offset + 100).toString() else JSONObject.NULL)
    }
    protected open val fontScaleFixture: Float? = null
    protected open val photographicFixtures = false
    private fun json(body: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
    @Before fun setupRemoteApp() {
        appearanceBackup = app.getSharedPreferences("appearance", 0).all.toMap()
        app.getSharedPreferences(prefs, 0).edit().clear().commit()
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val transport = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .addInterceptor { chain ->
                check(chain.request().url.host == "localhost")
                // The audit device stays offline. Coil's cache-only policy must not
                // prevent requests to this explicitly injected, local fixture.
                chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
            }.build()
        val png = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
                .compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        val images = if (photographicFixtures) listOf(R.drawable.mountains, R.drawable.lake, R.drawable.forest,
            R.drawable.flowers, R.drawable.meadow, R.drawable.street).map { id -> app.resources.openRawResource(id).use { it.readBytes() } } else emptyList()
        val video = app.resources.openRawResource(R.raw.glass_video).use { it.readBytes() }
        host = MockWebServer().apply {
            useHttps(tls.sslSocketFactory(), false)
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method != "GET") return mutationFixture(request)
                    readFixture(request)?.let { return it }
                    val url = request.requestUrl!!
                    val path = url.encodedPath
                    return when {
                        path.endsWith("/health") -> json("""{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":false}}""")
                        path.endsWith("/auth/session") -> json("""{"authenticated":true,"passwordSet":true}""")
                        path.endsWith("/timeline-summary") -> {
                            val count = matching(url.queryParameter("keyword").orEmpty()).size
                            json(JSONObject().put("granularity", "MONTH").put("groups", if (count == 0) JSONArray() else
                                JSONArray().put(JSONObject().put("key", "2026-09").put("startsAt", "2026-09-01T00:00:00Z").put("count", count))))
                        }
                        path.endsWith("/albums") -> json(JSONObject().put("items", JSONArray().put(album())).put("nextCursor", JSONObject.NULL))
                        path == "/api/v1/tags" -> json(JSONObject().put("items", JSONArray().put(
                            JSONObject().put("id", 7).put("name", "风景").put("assetCount", 12))).put("nextCursor", JSONObject.NULL))
                        path == "/api/v1/source-folders" -> json(JSONObject().put("libraryId", "test-library")
                            .put("items", if (url.queryParameter("path").isNullOrEmpty()) JSONArray().put(
                                JSONObject().put("name", "旅行").put("path", "旅行")) else JSONArray()))
                        path == "/api/v1/home" -> json(JSONObject().put("onThisDay", JSONArray((0..2).map(::asset)))
                            .put("recentPhotos", JSONArray()).put("recentImports", JSONArray())
                            .put("frequentAlbums", JSONArray().put(album())).put("jobSummary", JSONObject().put("items", JSONArray())))
                        path == "/api/v1/folders" -> {
                            val library = url.queryParameter("libraryId")
                            val folder = url.queryParameter("path").orEmpty()
                            val children = if (library == null) listOf(directory("test-library"))
                                else if (folder.isEmpty()) listOf(directory(library, "旅行")) else emptyList()
                            json(JSONObject().put("current", directory(library, folder)).put("children",
                                JSONObject().put("items", JSONArray(children)).put("nextCursor", JSONObject.NULL)))
                        }
                        path == "/api/v1/duplicates" -> json(JSONObject().put("items", JSONArray().put(JSONObject()
                            .put("contentHash", "a".repeat(64)).put("copyCount", assetCountFixture).put("reclaimableBytes", 2000)
                            .put("latestChangeAt", "2026-09-01T00:00:00Z").put("reviewedAt", JSONObject.NULL)
                            .put("representative", JSONObject().put("assetId", "restore-0").put("fileName", fileName(0)))))
                            .put("nextCursor", JSONObject.NULL))
                        path == "/api/v1/duplicates/${"a".repeat(64)}" -> json(page(url.queryParameter("cursor")?.toInt() ?: 0))
                        path.endsWith("/assets") -> {
                            val offset = url.queryParameter("cursor")?.toInt() ?: 0
                            val page = page(offset, url.queryParameter("keyword").orEmpty())
                            json(if (path.contains("/albums/")) JSONObject().put("album", album()).put("assets", page) else page)
                        }
                        path == "/api/v1/assets/restore-1/original" -> {
                            val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                            if (start >= video.size) MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${video.size}")
                            else MockResponse().setResponseCode(if (request.getHeader("Range") != null) 206 else 200)
                                .setHeader("Content-Type", "video/mp4").setHeader("Content-Range", "bytes $start-${video.size - 1}/${video.size}")
                                .setBody(Buffer().write(video, start, video.size - start))
                        }
                        path.endsWith("/thumbnail") || path.endsWith("/original") -> {
                            val index = path.substringBeforeLast('/').substringAfterLast('-').toIntOrNull() ?: 0
                            MockResponse().setHeader("Content-Type", if (images.isEmpty()) "image/png" else "image/jpeg")
                                .setBody(Buffer().write(if (images.isEmpty()) png else images[index % images.size]))
                        }
                        path.matches(Regex("/api/v1/assets/restore-\\d+")) -> json(asset(path.substringAfterLast('-').toInt()))
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start(java.net.InetAddress.getLoopbackAddress(), 0)
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PhotoWallViewModel(app,
                { url, expired -> PhotoTubeServer(app, url, expired, transport) }, prefs).also { wall = it } as T
        }
        if(fontScaleFixture!=null) {
            UiTestActivity.fontScaleOverride=fontScaleFixture
            rule.activityRule.scenario.recreate()
        }
        rule.runOnUiThread { ownerStore = rule.activity.viewModelStore; UiTestActivity.content = { PhotoTubeTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density,fontScaleFixture ?: density.fontScale)) {
                PhotoTubeContent(viewModel(factory = factory))
            }
        } } }
        rule.waitForIdle()
        rule.runOnIdle { wall.connect(host.url("/").newBuilder().host("localhost").build().toString()) }
        waitTag("asset-restore-0")
    }
    @After fun cleanupRemoteApp() {
        UiTestActivity.fontScaleOverride=null
        rule.runOnUiThread { UiTestActivity.content = null; if (::ownerStore.isInitialized) ownerStore.clear() }
        if (::host.isInitialized) host.shutdown()
        app.getSharedPreferences(prefs, 0).edit().clear().commit()
        val appearance = app.getSharedPreferences("appearance", 0)
        if (appearance.all != appearanceBackup) {
            val editor = appearance.edit().clear()
            appearanceBackup.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
            } }
            editor.commit()
            com.yunai.phototube.ui.theme.ThemeController(app).syncNativeMode()
        }
    }
    protected fun auditShot(name: String) {
        rule.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.getExternalFilesDir(null), "audit-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    protected fun waitTag(tag: String) = rule.waitUntil(15000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
