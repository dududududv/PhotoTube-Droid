package com.yunai.phototube.ui.home

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.home.HomeFeed
import com.yunai.phototube.data.home.HomeFeedGateway
import com.yunai.phototube.data.job.JobKind
import com.yunai.phototube.data.job.JobSummary
import com.yunai.phototube.data.job.JobSummaryResponse
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedViewModelTest {
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
    fun `刷新不会被进行中的旧请求吞掉且旧响应不能覆盖最新首页`() = runTest {
        val gateway = DeferredHomeFeedGateway()
        val model = HomeFeedViewModel(gateway)
        assertEquals(1, gateway.requests.size)

        model.refresh()

        assertEquals(2, gateway.requests.size)
        gateway.requests[1].complete(homeFeed(discovered = 22))
        assertEquals(22, (model.state.value as HomeFeedState.Ready).feed.jobSummary.items.single().discovered)

        gateway.requests[0].complete(homeFeed(discovered = 11))
        assertEquals(22, (model.state.value as HomeFeedState.Ready).feed.jobSummary.items.single().discovered)
    }

    @Test
    fun `首页请求令牌只接受当前 generation`() {
        val token = HomeRefreshRequestToken(generation = 8)

        assertTrue(token.matches(currentGeneration = 8))
        assertTrue(!token.matches(currentGeneration = 9))
    }

    private fun homeFeed(discovered: Int) = HomeFeed(
        onThisDay = emptyList(),
        recentPhotos = emptyList(),
        recentImports = emptyList(),
        frequentAlbums = emptyList(),
        jobSummary = JobSummaryResponse(
            items = listOf(
                JobSummary(
                    kind = JobKind.SCAN_LIBRARY,
                    pending = 0,
                    paused = 0,
                    running = 0,
                    succeeded = discovered,
                    failed = 0,
                    cancelled = 0,
                    queuePaused = false,
                    discovered = discovered,
                    total = discovered,
                    lastFinishedAt = null,
                ),
            ),
        ),
    )

    private class DeferredHomeFeedGateway : HomeFeedGateway {
        val requests = mutableListOf<CompletableDeferred<HomeFeed>>()

        override suspend fun getHomeFeed(): HomeFeed {
            val request = CompletableDeferred<HomeFeed>()
            requests += request
            return withContext(NonCancellable) { request.await() }
        }

        override fun serverRoot(): ServerRoot =
            ServerRoot.parse("https://photos.example.test").getOrThrow()
    }
}
