package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Two isolated synthetic servers; exercises the real address form and CookieJar. */
class ServerSwitchTest {
    @get:Rule val rule = createEmptyComposeRule()
    private class Fixture(val token: String) : AutoCloseable {
        val receivedCookies = CopyOnWriteArrayList<String>()
        private val host = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val cookie = request.getHeader("Cookie").orEmpty()
                    receivedCookies += cookie
                    fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                    return when(request.requestUrl!!.encodedPath) {
                        "/api/v1/health" -> json("""{"status":"ok","database":{"reachable":true},"xmpExport":{}}""")
                        "/api/v1/auth/session" -> json("""{"authenticated":${cookie.contains("phototube_session=$token")},"passwordSet":true}""")
                        "/api/v1/auth/login" -> MockResponse().setResponseCode(204).setHeader("Set-Cookie", "phototube_session=$token; Path=/; HttpOnly")
                        "/api/v1/assets/timeline-summary" -> json("""{"granularity":"MONTH","groups":[]}""")
                        "/api/v1/assets", "/api/v1/albums" -> json("""{"items":[],"nextCursor":null}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        }
        val address = host.url("/").newBuilder().host("127.0.0.1").build().toString()
        override fun close() { host.shutdown() }
    }
    private fun waitTag(tag: String) = rule.waitUntil(15000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun connect(address: String) {
        waitTag("server-address")
        rule.onNodeWithTag("server-address").performTextReplacement(address)
        rule.onNodeWithTag("server-connect").performClick()
    }
    private fun login() {
        waitTag("local-login-password")
        rule.onNodeWithTag("local-login-password").performTextReplacement("synthetic-test-password")
        rule.onNodeWithTag("local-login-submit").performClick()
        waitTag("remote-photo-wall")
    }
    private fun switchServer() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("切换服务器").performScrollTo().performClick()
        waitTag("server-address")
    }
    @Test fun independentLoginSwitchAndColdStartRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("server_connection_v2", 0)
        val previous = prefs.getString("base_url", null)
        prefs.edit().remove("base_url").commit()
        Fixture("synthetic-a").use { a -> Fixture("synthetic-b").use { b ->
            try {
                ActivityScenario.launch(MainActivity::class.java).use {
                    connect(a.address + "api/v1/"); login()
                    switchServer(); connect(b.address)
                    waitTag("local-login-password")
                    assertTrue(b.receivedCookies.all { it.isEmpty() })
                    login()
                    switchServer(); connect(a.address)
                    waitTag("remote-photo-wall")
                    rule.onNodeWithTag("local-login-password").assertDoesNotExist()
                }
                ActivityScenario.launch(MainActivity::class.java).use {
                    waitTag("remote-photo-wall")
                    rule.onNodeWithTag("server-address").assertDoesNotExist()
                    rule.onNodeWithTag("local-login-password").assertDoesNotExist()
                }
                assertFalse(a.receivedCookies.any { it.contains(b.token) })
                assertFalse(b.receivedCookies.any { it.contains(a.token) })
                assertEquals(a.address.trimEnd('/'), prefs.getString("base_url", null))
            } finally {
                prefs.edit().apply { if(previous == null) remove("base_url") else putString("base_url", previous) }.commit()
            }
        } }
    }
}
