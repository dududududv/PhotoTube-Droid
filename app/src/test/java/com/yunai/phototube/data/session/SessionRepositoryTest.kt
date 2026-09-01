package com.yunai.phototube.data.session

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.connection.ServerStore
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.remote.PhotoTubeServiceFactory
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var serverRoot = ServerRoot.parse("http://localhost").getOrThrow()
    private lateinit var serverStore: RecordingServerStore
    private lateinit var cookieJar: RecordingCookieJar
    private var cacheClearCount = 0
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        serverRoot = ServerRoot.parse(server.url("/").toString()).getOrThrow()
        serverStore = RecordingServerStore(serverRoot)
        cookieJar = RecordingCookieJar()
        cacheClearCount = 0
        repository = SessionRepository(
            serverPreferences = serverStore,
            cookieJar = cookieJar,
            serviceFactory = PhotoTubeServiceFactory(
                cookieJar = cookieJar,
                sessionEventBus = SessionEventBus(),
                cacheDirectory = temporaryFolder.newFolder("http-cache"),
            ),
            onSessionCleared = { cacheClearCount += 1 },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun logoutRequiresServerConfirmationBeforeClearingLocalSession() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(204)
                .addHeader(
                    "Set-Cookie",
                    "phototube_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0",
                )
                .build(),
        )

        repository.logout()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
        assertEquals(1, cookieJar.clearCount)
        assertEquals(1, cacheClearCount)
        assertEquals(serverRoot, serverStore.getServerRoot())
    }

    @Test
    fun failedLogoutDoesNotPretendThatTheServerSessionWasRevoked() = runTest {
        server.enqueue(internalErrorResponse())

        val failure = runCatching { repository.logout() }.exceptionOrNull()

        assertTrue(failure is ApiFailure)
        assertEquals(0, cookieJar.clearCount)
        assertEquals(0, cacheClearCount)
        assertEquals(serverRoot, serverStore.getServerRoot())
    }

    @Test
    fun switchServerConfirmsRemoteLogoutAndAlwaysForgetsLocalBoundary() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(204)
                .addHeader(
                    "Set-Cookie",
                    "phototube_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0",
                )
                .build(),
        )

        val result = repository.switchServer()

        assertTrue(result.serverLogoutConfirmed)
        assertEquals("/api/v1/auth/logout", server.takeRequest().url.encodedPath)
        assertNull(serverStore.getServerRoot())
        assertEquals(1, cookieJar.clearCount)
        assertEquals(1, cacheClearCount)
    }

    @Test
    fun switchServerStillClearsLocalBoundaryWhenOldServerRejectsLogout() = runTest {
        server.enqueue(internalErrorResponse())

        val result = repository.switchServer()

        assertFalse(result.serverLogoutConfirmed)
        assertNotNull(server.takeRequest())
        assertNull(serverStore.getServerRoot())
        assertEquals(1, cookieJar.clearCount)
        assertEquals(1, cacheClearCount)
    }

    @Test
    fun connectToDifferentServerClearsOldCookieBoundaryBeforeSessionProbe() = runTest {
        val nextServer = MockWebServer()
        nextServer.start()
        try {
            nextServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body(
                        """{"status":"ok","version":"1.0","database":{"reachable":true,"migrationVersion":1,"dirty":false},"aiWorker":null,"xmpExport":{"available":false}}""",
                    )
                    .build(),
            )
            nextServer.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Type", "application/json")
                    .body(
                        """{"authenticated":false,"passwordSet":true,"privateAccessUnlocked":false,"privateAccessExpiresAt":null,"user":null}""",
                    )
                    .build(),
            )

            val result = repository.connect(nextServer.url("/").toString())

            assertFalse(result.sessionInfo.authenticated)
            assertEquals(1, cookieJar.clearCount)
            assertEquals(1, cacheClearCount)
            assertEquals(result.serverRoot, serverStore.getServerRoot())
            assertEquals("/api/v1/health", nextServer.takeRequest().url.encodedPath)
            assertEquals("/api/v1/auth/session", nextServer.takeRequest().url.encodedPath)
        } finally {
            nextServer.close()
        }
    }

    private fun internalErrorResponse() = MockResponse.Builder()
        .code(500)
        .addHeader("Content-Type", "application/json")
        .body(
            """{"code":"INTERNAL_ERROR","message":"服务端没有删除会话","retryable":true,"logId":"log-session"}""",
        )
        .build()

    private class RecordingServerStore(initial: ServerRoot?) : ServerStore {
        private var root = initial

        override fun getServerRoot(): ServerRoot? = root

        override fun setServerRoot(serverRoot: ServerRoot) {
            root = serverRoot
        }

        override fun clear() {
            root = null
        }
    }

    private class RecordingCookieJar : ClearableCookieJar {
        var clearCount = 0
            private set

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()

        override fun clear() {
            clearCount += 1
        }
    }
}
