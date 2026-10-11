package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.PhotoTubeServer
import com.yunai.phototube.data.TrashRepository
import com.yunai.phototube.data.PhotoWallViewModel
import com.yunai.phototube.ui.PhotoTubeContent
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in read-only reproduction against the authorized generated-media service. */
class LocalTrashReadTest {
    @get:Rule val rule = createComposeRule()

    @Test fun authenticatedTrashLoadsAndRenders(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localIntegrationSmoke") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.scheme == "http" && request.url.host == "127.0.0.1" && request.url.port == 18474)
            check(request.method == "GET" || request.method == "POST" && request.url.encodedPath == "/api/v1/auth/login")
            chain.proceed(request)
        }.build()
        val server = PhotoTubeServer(context, "http://127.0.0.1:18474", {}, transport)
        val store = androidx.lifecycle.ViewModelStore()
        val preferenceName = "local_trash_read_test"
        try {
            server.login(requireNotNull(args.getString("localUsername")), requireNotNull(args.getString("localPassword")))
            val page = TrashRepository(server).list(null)
            page.items.forEach { it.remainingDays() }
            val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    PhotoWallViewModel(context.applicationContext as android.app.Application,
                        { url, expired -> PhotoTubeServer(context, url, expired, transport) }, preferenceName) as T
            }
            val wall = androidx.lifecycle.ViewModelProvider(store, factory)[PhotoWallViewModel::class.java]
            rule.setContent { PhotoTubeTheme { PhotoTubeContent(wall) } }
            rule.runOnIdle { wall.connect("http://127.0.0.1:18474") }
            rule.waitUntil(20000) { rule.onAllNodesWithTag("remote-photo-wall").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("相册标签").performClick()
            repeat(2) {
                rule.onNodeWithText("回收站").performScrollTo().performClick()
                rule.waitUntil(20000) {
                    if (page.items.isEmpty()) rule.onAllNodesWithText("回收站是空的").fetchSemanticsNodes().isNotEmpty()
                    else rule.onAllNodesWithTag("trash-${page.items.first().asset.id}").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithTag("trash-list").assertIsDisplayed()
                page.items.lastOrNull()?.let { last ->
                    rule.onNodeWithTag("trash-list").performScrollToNode(hasTestTag("trash-${last.asset.id}"))
                    rule.onNodeWithTag("trash-${last.asset.id}").assertIsDisplayed()
                }
                rule.onNodeWithContentDescription("返回", useUnmergedTree = true).performClick()
                rule.waitUntil(10000) { rule.onAllNodesWithTag("trash-list").fetchSemanticsNodes().isEmpty() }
            }
        } finally {
            rule.runOnUiThread { store.clear() }
            context.getSharedPreferences(preferenceName, 0).edit().clear().commit()
            server.dispose()
        }
    }
}
