package com.yunai.phototube.ui.system

import com.yunai.phototube.data.system.LocalCacheSnapshot
import com.yunai.phototube.data.system.SystemStatus
import com.yunai.phototube.data.system.SystemStatusGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemStatusViewModelTest {
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
    fun `服务端状态重复刷新拒绝不响应取消的旧结果`() = runTest {
        val gateway = DeferredSystemStatusGateway()
        val model = SystemStatusViewModel(gateway)

        model.refreshStatus()
        model.refreshStatus()

        assertEquals(2, gateway.statusRequests.size)
        gateway.statusRequests[1].complete(SystemStatus("2026-09-01T02:00:00Z", emptyList()))
        assertEquals("2026-09-01T02:00:00Z", model.state.value.status?.generatedAt)

        gateway.statusRequests[0].complete(SystemStatus("2026-09-01T01:00:00Z", emptyList()))
        assertEquals("2026-09-01T02:00:00Z", model.state.value.status?.generatedAt)
    }

    @Test
    fun `清理前的缓存快照不能覆盖清理结果`() = runTest {
        val gateway = DeferredSystemStatusGateway()
        val model = SystemStatusViewModel(gateway)

        model.refreshLocalCache()
        model.clearLocalCache()

        assertEquals(1, gateway.snapshotRequests.size)
        assertEquals(1, gateway.clearRequests.size)
        assertTrue(model.state.value.isClearingCache)
        gateway.clearRequests.single().complete(cache(bytes = 0))
        assertEquals(0L, model.state.value.cache?.storedBytes)
        assertFalse(model.state.value.isClearingCache)

        gateway.snapshotRequests.single().complete(cache(bytes = 99))
        assertEquals(0L, model.state.value.cache?.storedBytes)
        assertEquals("本机媒体缓存已清除", model.state.value.feedback)
    }

    @Test
    fun `服务端状态与缓存分别使用独立 generation`() {
        assertTrue(SystemStatusRequestToken(4).matches(4))
        assertFalse(SystemStatusRequestToken(4).matches(5))
        assertTrue(LocalCacheRequestToken(9).matches(9))
        assertFalse(LocalCacheRequestToken(9).matches(10))
    }

    private fun cache(bytes: Long) = LocalCacheSnapshot(
        memoryBytes = bytes,
        memoryMaxBytes = 100,
        imageDiskBytes = 0,
        httpDiskBytes = 0,
        httpDiskMaxBytes = 100,
    )

    private class DeferredSystemStatusGateway : SystemStatusGateway {
        val statusRequests = mutableListOf<CompletableDeferred<SystemStatus>>()
        val snapshotRequests = mutableListOf<CompletableDeferred<LocalCacheSnapshot>>()
        val clearRequests = mutableListOf<CompletableDeferred<LocalCacheSnapshot>>()

        override suspend fun getStatus(): SystemStatus = deferred(statusRequests)

        override suspend fun getLocalCacheSnapshot(): LocalCacheSnapshot = deferred(snapshotRequests)

        override suspend fun clearLocalMediaCaches(): LocalCacheSnapshot = deferred(clearRequests)

        private suspend fun <T> deferred(target: MutableList<CompletableDeferred<T>>): T {
            val request = CompletableDeferred<T>()
            target += request
            return withContext(NonCancellable) { request.await() }
        }
    }
}
