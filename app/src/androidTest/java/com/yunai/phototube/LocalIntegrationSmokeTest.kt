package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.LocalConnection
import com.yunai.phototube.data.PhotoTubeServer
import com.yunai.phototube.data.MediaLoadDiagnostics
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in, user-authorized loopback service only. Never records media or credentials. */
class LocalIntegrationSmokeTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun loginReadGalleryAndRestoreSession() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localIntegrationSmoke") == "true")
        val username = requireNotNull(args.getString("localUsername"))
        val password = requireNotNull(args.getString("localPassword"))
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntil(20000) {
                rule.onAllNodesWithTag("local-login-username").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty()
            }
            if(rule.onAllNodesWithTag("local-login-username").fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithTag("local-login-username").performTextReplacement(username)
                rule.onNodeWithTag("local-login-password").performTextReplacement(password)
                rule.onNodeWithTag("local-login-submit").performScrollTo().performClick()
            }
            waitForGallery()
        }
        // New Activity and ViewModel reload the encrypted CookieJar, not in-memory login state.
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForGallery()
            rule.onNodeWithTag("local-login-password").assertDoesNotExist()
        }
    }

    @Test fun authenticatedThumbnailsAndOriginalActuallyDecode() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localIntegrationSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = PhotoTubeServer(context, LocalConnection.BASE, {})
        try {
            server.login(requireNotNull(args.getString("localUsername")), requireNotNull(args.getString("localPassword")))
            assertSame(server.cookies, server.client.cookieJar)
            val assets = mutableListOf<com.yunai.phototube.data.RemoteAsset>()
            var cursor: String? = null
            do {
                val page = server.assets(cursor)
                assets += page.items
                cursor = page.nextCursor
            } while (cursor != null)
            val thumbnails = assets.mapNotNull { server.thumbnail(it) }
            assertTrue("Synthetic local gallery must contain thumbnails", thumbnails.isNotEmpty())
            suspend fun decode(url: String) {
                val result = server.imageLoader.execute(ImageRequest.Builder(context).data(url).size(128)
                    .memoryCachePolicy(CachePolicy.DISABLED).build())
                if (result is ErrorResult) fail("${MediaLoadDiagnostics.safeUrl(url)}: ${MediaLoadDiagnostics.reason(result.throwable)}")
                assertTrue("Must decode pixels, not just receive a successful HTTP response", result is SuccessResult)
                val image = (result as SuccessResult).image
                assertTrue(image.width > 0 && image.height > 0)
            }
            // Use the production loader and client unchanged: no cache-header stripping or fake connectivity.
            repeat(2) { for (url in thumbnails) decode(url) }
            val photo = requireNotNull(assets.firstOrNull { it.kind == "PHOTO" &&
                it.fileName.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "webp", "avif")
            }) { "Local synthetic dataset must include a decodable photo original" }
            decode(server.original(photo.id))
            println("Decoded ${thumbnails.size * 2} local synthetic thumbnails and 1 original; memory cache disabled")
        } finally {
            server.dispose()
        }
    }

    private fun waitForGallery() {
        rule.waitUntil(25000) { rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(25000) {
            rule.onAllNodes(SemanticsMatcher("Loaded gallery item") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("asset-") == true
            }).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("还没有照片").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("server-error").assertCountEquals(0)
    }
}
