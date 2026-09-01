package com.yunai.phototube.ui

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.SessionInfo
import com.yunai.phototube.data.remote.SessionUser
import com.yunai.phototube.data.session.AppSessionGateway
import com.yunai.phototube.data.session.BootstrapResult
import com.yunai.phototube.data.session.ServerSwitchResult
import com.yunai.phototube.data.session.SessionEventBus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val serverRoot = ServerRoot.parse("https://photos.example.com").getOrThrow()
    private val user = SessionUser(
        id = "00000000-0000-4000-8000-000000000001",
        username = "admin",
        displayName = "管理员",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulLogoutReturnsToLoginAndKeepsServerAddress() = runTest {
        val gateway = FakeAppSessionGateway(authenticatedSession())
        val model = AppViewModel(gateway, SessionEventBus())
        assertEquals(AppStage.Content, model.uiState.value.stage)

        model.logout()

        val state = model.uiState.value
        assertEquals(1, gateway.logoutCalls)
        assertEquals(AppStage.Login, state.stage)
        assertEquals(serverRoot, state.serverRoot)
        assertNull(state.user)
        assertFalse(state.isBusy)
    }

    @Test
    fun failedLogoutKeepsAuthenticatedScreenAndShowsRetryableError() = runTest {
        val gateway = FakeAppSessionGateway(authenticatedSession()).apply {
            logoutFailure = IOException("offline")
        }
        val model = AppViewModel(gateway, SessionEventBus())

        model.logout()

        val state = model.uiState.value
        assertEquals(AppStage.Content, state.stage)
        assertEquals(user, state.user)
        assertTrue(state.error?.retryable == true)
        assertNotNull(state.error)
        assertFalse(state.isBusy)
    }

    @Test
    fun switchServerContinuesLocallyAndWarnsWhenRemoteLogoutIsUnconfirmed() = runTest {
        val gateway = FakeAppSessionGateway(authenticatedSession()).apply {
            switchResult = ServerSwitchResult(serverLogoutConfirmed = false)
        }
        val model = AppViewModel(gateway, SessionEventBus())

        model.switchServer()

        val state = model.uiState.value
        assertEquals(1, gateway.switchCalls)
        assertEquals(AppStage.ServerSetup, state.stage)
        assertNull(state.serverRoot)
        assertEquals("本机会话与缓存已清除，但旧服务器没有确认登出", state.error?.message)
        assertFalse(state.isBusy)
    }

    @Test
    fun unauthorizedBeforeCollectorIsLatchedAndSkipsStaleBootstrap() = runTest {
        val gateway = FakeAppSessionGateway(authenticatedSession())
        val events = SessionEventBus().apply { notifyUnauthorized() }

        val model = AppViewModel(gateway, events)

        assertEquals(AppStage.Login, model.uiState.value.stage)
        assertEquals("会话已过期，请重新登录", model.uiState.value.error?.message)
        assertEquals(1, gateway.clearExpiredCalls)
        assertEquals(0, gateway.bootstrapCalls)
    }

    @Test
    fun unauthorizedBurstClearsSessionBoundaryOnlyOnce() = runTest {
        val gateway = FakeAppSessionGateway(authenticatedSession())
        val events = SessionEventBus()
        val model = AppViewModel(gateway, events)
        assertEquals(AppStage.Content, model.uiState.value.stage)

        repeat(8) { events.notifyUnauthorized() }

        assertEquals(AppStage.Login, model.uiState.value.stage)
        assertEquals(1, gateway.clearExpiredCalls)
        assertEquals("会话已过期，请重新登录", model.uiState.value.error?.message)
    }

    @Test
    fun staleBootstrapCannotRestoreContentAfterUnauthorized() = runTest {
        val pendingBootstrap = CompletableDeferred<BootstrapResult>()
        val gateway = FakeAppSessionGateway(authenticatedSession()).apply {
            bootstrapDeferred = pendingBootstrap
        }
        val events = SessionEventBus()
        val model = AppViewModel(gateway, events)
        assertTrue(model.uiState.value.isBusy)

        events.notifyUnauthorized()
        pendingBootstrap.complete(authenticatedSession())
        advanceUntilIdle()

        assertEquals(AppStage.Login, model.uiState.value.stage)
        assertEquals(1, gateway.clearExpiredCalls)
        assertEquals("会话已过期，请重新登录", model.uiState.value.error?.message)
        assertNull(model.uiState.value.user)
    }

    private fun authenticatedSession() = BootstrapResult.Session(
        serverRoot = serverRoot,
        sessionInfo = SessionInfo(
            authenticated = true,
            passwordSet = true,
            privateAccessUnlocked = false,
            user = user,
        ),
    )

    private class FakeAppSessionGateway(
        private val initialSession: BootstrapResult.Session,
    ) : AppSessionGateway {
        var logoutCalls = 0
        var switchCalls = 0
        var bootstrapCalls = 0
        var clearExpiredCalls = 0
        var bootstrapDeferred: CompletableDeferred<BootstrapResult>? = null
        var logoutFailure: Throwable? = null
        var switchResult = ServerSwitchResult(serverLogoutConfirmed = true)
        private var server: ServerRoot? = initialSession.serverRoot

        override fun currentServer(): ServerRoot? = server

        override suspend fun bootstrap(): BootstrapResult {
            bootstrapCalls += 1
            return bootstrapDeferred?.await() ?: initialSession
        }

        override suspend fun connect(rawAddress: String): BootstrapResult.Session = initialSession

        override suspend fun login(username: String, password: String): BootstrapResult.Session = initialSession

        override suspend fun logout() {
            logoutCalls += 1
            logoutFailure?.let { throw it }
        }

        override suspend fun switchServer(): ServerSwitchResult {
            switchCalls += 1
            server = null
            return switchResult
        }

        override fun clearExpiredSession() {
            clearExpiredCalls += 1
        }

        override fun forgetServer() {
            server = null
        }
    }
}
