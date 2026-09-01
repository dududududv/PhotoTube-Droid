package com.yunai.phototube.ui.photos

import androidx.paging.PagingData
import com.yunai.phototube.data.connection.ServerRoot
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
import kotlinx.coroutines.flow.flowOf
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
class TimelineViewModelTest {
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
    fun `同筛选重复刷新也会拒绝不响应取消的旧摘要`() = runTest {
        val gateway = DeferredTimelineGateway()
        val model = TimelineViewModel(gateway)
        assertEquals(1, gateway.summaryRequests.size)

        model.refreshSummary()

        assertEquals(2, gateway.summaryRequests.size)
        gateway.summaryRequests[1].complete(summary(totalCount = 22))
        assertEquals(22L, (model.summaryState.value as TimelineSummaryState.Ready).summary.totalCount)

        gateway.summaryRequests[0].complete(summary(totalCount = 11))
        assertEquals(22L, (model.summaryState.value as TimelineSummaryState.Ready).summary.totalCount)
    }

    @Test
    fun `摘要令牌同时匹配 generation 粒度与筛选`() {
        val filter = AssetFilter(favorite = true)
        val token = TimelineSummaryRequestToken(
            generation = 5,
            granularity = TimelineGranularity.MONTH,
            filter = filter,
        )

        assertTrue(token.matches(5, TimelineGranularity.MONTH, filter))
        assertFalse(token.matches(6, TimelineGranularity.MONTH, filter))
        assertFalse(token.matches(5, TimelineGranularity.DAY, filter))
        assertFalse(token.matches(5, TimelineGranularity.MONTH, AssetFilter()))
    }

    private fun summary(totalCount: Long) = TimelineSummaryResponse(
        granularity = TimelineGranularity.DAY,
        groups = emptyList(),
        totalCount = totalCount,
    )

    private class DeferredTimelineGateway : TimelineGateway {
        val summaryRequests = mutableListOf<CompletableDeferred<TimelineSummaryResponse>>()

        override fun pagedAssets(filter: AssetFilter): Flow<PagingData<MediaAsset>> =
            flowOf(PagingData.empty())

        override suspend fun getSummary(
            filter: AssetFilter,
            granularity: TimelineGranularity,
        ): TimelineSummaryResponse {
            val request = CompletableDeferred<TimelineSummaryResponse>()
            summaryRequests += request
            return withContext(NonCancellable) { request.await() }
        }

        override fun serverRoot(): ServerRoot =
            ServerRoot.parse("https://photos.example.test").getOrThrow()
    }
}
