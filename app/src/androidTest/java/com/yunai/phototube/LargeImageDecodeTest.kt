package com.yunai.phototube

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.PhotoTubeServer
import com.yunai.phototube.data.RemoteAsset
import com.yunai.phototube.ui.media.RemoteArtwork
import com.yunai.phototube.ui.media.RemoteMedia
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LargeImageDecodeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun originalCameraAndPanoramaDecodesStayBounded() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtures = listOf("large-camera", "large-panorama").associateWith {
            instrumentation.context.assets.open("$it.png").use { stream -> stream.readBytes() }
        }
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        val host = MockWebServer().apply {
            useHttps(tls.sslSocketFactory(), false)
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    val id = path.split('/').getOrNull(4)
                    val image = fixtures[id] ?: return MockResponse().setResponseCode(404)
                    return if (path.endsWith("/original")) MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(image))
                    else MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        val server = PhotoTubeServer(context, host.url("/").toString(), {}, client)
        fun media(id: String) = RemoteMedia(RemoteAsset(id, "PHOTO", "BROWSABLE", "2026-09-01T00:00:00Z", 0,
            "$id.png", "c".repeat(64), if (id == "large-camera") 8192 else 16384, if (id == "large-camera") 6144 else 2048, false, null), server)
            .apply { detail = JSONObject().put("activeEdit", JSONObject.NULL) }
        val current = mutableStateOf(media("large-camera"))
        try {
            rule.setContent { PhotoTubeTheme { RemoteArtwork(current.value, Modifier.fillMaxSize(), ContentScale.Fit, null, true) } }
            for (id in fixtures.keys) {
                rule.runOnIdle { current.value = media(id) }
                val cache = requireNotNull(server.imageLoader.memoryCache)
                rule.waitUntil(15000) { cache.keys.any { it.key.contains("/$id/original") } }
                val image = cache.keys.first { it.key.contains("/$id/original") }.let { cache[it]!!.image }
                println("$id decoded ${image.width} x ${image.height}, ${image.size} bytes")
                assertTrue("$id decoded too wide: ${image.width}", image.width <= 3072)
                assertTrue("$id decoded too tall: ${image.height}", image.height <= 3072)
                assertTrue("$id should retain detail beyond an MD thumbnail", image.width >= 2048)
                assertTrue(image.size <= 3072L * 3072 * 4)
            }
        } finally { rule.runOnIdle { server.dispose() }; host.shutdown() }
    }
}
