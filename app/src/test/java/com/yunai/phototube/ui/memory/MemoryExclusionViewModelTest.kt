package com.yunai.phototube.ui.memory

import androidx.paging.PagingData
import com.yunai.phototube.data.memory.MemoryExclusion
import com.yunai.phototube.data.memory.MemoryExclusionGateway
import com.yunai.phototube.data.memory.MemoryExclusionKind
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryExclusionViewModelTest {
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
    fun `旧创建成功不能清空提交后修改的新日期草稿`() = runTest {
        val gateway = DeferredMemoryExclusionGateway()
        val model = MemoryExclusionViewModel(gateway)
        val submittedFrom = LocalDate.of(2026, 3, 1)
        val submittedTo = LocalDate.of(2026, 3, 31)
        model.setDateFrom(submittedFrom)
        model.setDateTo(submittedTo)
        model.createDateRange()
        assertEquals(1, gateway.createRequests.size)

        val newerFrom = LocalDate.of(2026, 4, 1)
        val newerTo = LocalDate.of(2026, 4, 30)
        model.setDateFrom(newerFrom)
        model.setDateTo(newerTo)
        gateway.createRequests.single().result.complete(dateRule(submittedFrom, submittedTo))

        assertEquals(newerFrom, model.state.value.dateFrom)
        assertEquals(newerTo, model.state.value.dateTo)
        assertNull(model.state.value.busyTarget)
    }

    @Test
    fun `未订阅期间的成功变更会保留到页面重新收集`() = runTest {
        val gateway = DeferredMemoryExclusionGateway()
        val model = MemoryExclusionViewModel(gateway)
        val from = LocalDate.of(2026, 3, 1)
        val to = LocalDate.of(2026, 3, 31)
        model.setDateFrom(from)
        model.setDateTo(to)
        model.createDateRange()

        gateway.createRequests.single().result.complete(dateRule(from, to))

        assertEquals(Unit, model.changes.first())
        assertNull(model.state.value.dateFrom)
        assertNull(model.state.value.dateTo)
    }

    @Test
    fun `忙碌期间不会重复提交创建或删除`() = runTest {
        val gateway = DeferredMemoryExclusionGateway()
        val model = MemoryExclusionViewModel(gateway)
        val from = LocalDate.of(2026, 3, 1)
        val to = LocalDate.of(2026, 3, 31)
        model.setDateFrom(from)
        model.setDateTo(to)

        model.createDateRange()
        model.createDateRange()
        model.delete(dateRule(from, to))

        assertEquals(1, gateway.createRequests.size)
        assertEquals(0, gateway.deleteRequests.size)
        gateway.createRequests.single().result.complete(dateRule(from, to))
    }

    private fun dateRule(from: LocalDate, to: LocalDate) = MemoryExclusion(
        id = "11111111-1111-4111-8111-111111111111",
        kind = MemoryExclusionKind.DATE_RANGE,
        dateFrom = from.toString(),
        dateTo = to.toString(),
        personId = null,
        createdAt = "2026-09-01T08:00:00Z",
    )

    private class DeferredMemoryExclusionGateway : MemoryExclusionGateway {
        val createRequests = mutableListOf<CreateRequest>()
        val deleteRequests = mutableListOf<DeleteRequest>()

        override fun pagedExclusions(): Flow<PagingData<MemoryExclusion>> =
            flowOf(PagingData.empty())

        override suspend fun createDateRange(
            dateFrom: LocalDate,
            dateTo: LocalDate,
        ): MemoryExclusion {
            val request = CreateRequest(dateFrom, dateTo, CompletableDeferred())
            createRequests += request
            return request.result.await()
        }

        override suspend fun delete(exclusionId: String) {
            val request = DeleteRequest(exclusionId, CompletableDeferred())
            deleteRequests += request
            request.result.await()
        }
    }

    private data class CreateRequest(
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
        val result: CompletableDeferred<MemoryExclusion>,
    )

    private data class DeleteRequest(
        val id: String,
        val result: CompletableDeferred<Unit>,
    )
}
