package com.yunai.phototube.ui.library

import androidx.paging.PagingData
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.SessionInfo
import com.yunai.phototube.data.remote.SessionUser
import com.yunai.phototube.data.session.PrivateAccessGateway
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineGateway
import com.yunai.phototube.data.timeline.TimelineGranularity
import com.yunai.phototube.data.timeline.TimelineSummaryResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAssetsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `私密路由每次重新进入都关闭本地分页并复核服务端 session`() = runTest {
        val timeline = RecordingTimelineGateway()
        val session = DeferredPrivateAccessGateway()
        val model = LibraryAssetsViewModel(LibraryCollectionMode.PRIVATE, timeline, session)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.assets.collect() }

        model.onRouteEntered()
        assertEquals(1, session.checkRequests.size)
        assertTrue(timeline.filters.isEmpty())
        session.checkRequests[0].complete(sessionInfo(unlocked = true))
        assertEquals(listOf(AssetFilter(private = true)), timeline.filters)

        model.onRouteLeft()
        assertTrue(!model.uiState.value.isUnlocked)
        model.onRouteEntered()
        assertTrue(model.uiState.value.isCheckingAccess)
        assertTrue(!model.uiState.value.isUnlocked)
        assertEquals(2, session.checkRequests.size)
        session.checkRequests[1].complete(sessionInfo(unlocked = false))
        assertTrue(!model.uiState.value.isUnlocked)
    }

    @Test
    fun `旧路由会话检查晚到不能重新打开私密分页`() = runTest {
        val timeline = RecordingTimelineGateway()
        val session = DeferredPrivateAccessGateway()
        val model = LibraryAssetsViewModel(LibraryCollectionMode.PRIVATE, timeline, session)
        model.onRouteEntered()
        model.onRouteLeft()
        model.onRouteEntered()

        session.checkRequests[1].complete(sessionInfo(unlocked = false))
        session.checkRequests[0].complete(sessionInfo(unlocked = true))

        assertTrue(!model.uiState.value.isUnlocked)
        assertTrue(!model.uiState.value.isCheckingAccess)
    }

    @Test
    fun `授权过期会失效在途解锁并提交服务端锁定`() = runTest {
        val session = DeferredPrivateAccessGateway()
        val model = LibraryAssetsViewModel(
            LibraryCollectionMode.PRIVATE,
            RecordingTimelineGateway(),
            session,
        )
        model.onRouteEntered()
        session.checkRequests.single().complete(sessionInfo(unlocked = false))
        model.updatePassword("secret")
        model.unlock()

        model.expirePrivateAccess()
        session.unlockRequests.single().result.complete(sessionInfo(unlocked = true))

        assertTrue(!model.uiState.value.isUnlocked)
        assertEquals(1, session.lockRequests.size)
        session.lockRequests.single().complete(Unit)
    }

    @Test
    fun `显式锁定即使服务端失败也保持本地 fail closed`() = runTest {
        val timeline = RecordingTimelineGateway()
        val session = DeferredPrivateAccessGateway()
        val model = LibraryAssetsViewModel(LibraryCollectionMode.PRIVATE, timeline, session)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.assets.collect() }
        model.onRouteEntered()
        session.checkRequests.single().complete(sessionInfo(unlocked = true))
        assertTrue(model.uiState.value.isUnlocked)

        model.lock()
        assertTrue(!model.uiState.value.isUnlocked)
        session.lockRequests.single().completeExceptionally(IllegalStateException("锁定失败"))

        assertTrue(!model.uiState.value.isUnlocked)
        assertNotNull(model.uiState.value.error)
        assertEquals(1, timeline.filters.size)
    }

    @Test
    fun `离页期间的旧解锁响应不能恢复本地授权`() = runTest {
        val session = DeferredPrivateAccessGateway()
        val model = LibraryAssetsViewModel(
            LibraryCollectionMode.PRIVATE,
            RecordingTimelineGateway(),
            session,
        )
        model.onRouteEntered()
        session.checkRequests.single().complete(sessionInfo(unlocked = false))
        model.updatePassword("secret")
        model.unlock()
        model.onRouteLeft()

        session.unlockRequests.single().result.complete(sessionInfo(unlocked = true))

        assertTrue(!model.uiState.value.isUnlocked)
        assertTrue(model.uiState.value.password.isEmpty())
    }

    private fun sessionInfo(unlocked: Boolean) = SessionInfo(
        authenticated = true,
        passwordSet = true,
        privateAccessUnlocked = unlocked,
        privateAccessExpiresAt = if (unlocked) "2026-09-01T00:15:00Z" else null,
        user = SessionUser("user-1", "admin", "管理员"),
    )

    private class RecordingTimelineGateway : TimelineGateway {
        val filters = mutableListOf<AssetFilter>()

        override fun pagedAssets(filter: AssetFilter): Flow<PagingData<MediaAsset>> {
            filters += filter
            return flowOf(PagingData.empty())
        }

        override suspend fun getSummary(
            filter: AssetFilter,
            granularity: TimelineGranularity,
        ): TimelineSummaryResponse = error("测试未使用 getSummary")

        override fun serverRoot(): ServerRoot = TEST_SERVER_ROOT
    }

    private class DeferredPrivateAccessGateway : PrivateAccessGateway {
        val checkRequests = mutableListOf<CompletableDeferred<SessionInfo>>()
        val unlockRequests = mutableListOf<UnlockRequest>()
        val lockRequests = mutableListOf<CompletableDeferred<Unit>>()

        override suspend fun getSessionInfo(): SessionInfo {
            val request = CompletableDeferred<SessionInfo>()
            checkRequests += request
            return withContext(NonCancellable) { request.await() }
        }

        override suspend fun unlockPrivateAccess(password: String): SessionInfo {
            val request = UnlockRequest(password, CompletableDeferred())
            unlockRequests += request
            return withContext(NonCancellable) { request.result.await() }
        }

        override suspend fun lockPrivateAccess() {
            val request = CompletableDeferred<Unit>()
            lockRequests += request
            withContext(NonCancellable) { request.await() }
        }
    }

    private data class UnlockRequest(
        val password: String,
        val result: CompletableDeferred<SessionInfo>,
    )

    private companion object {
        val TEST_SERVER_ROOT = ServerRoot.parse("https://photos.example.test").getOrThrow()
    }
}
