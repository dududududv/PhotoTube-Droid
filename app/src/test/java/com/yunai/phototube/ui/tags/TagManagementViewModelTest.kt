package com.yunai.phototube.ui.tags

import androidx.paging.PagingData
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagDeleteResult
import com.yunai.phototube.data.tag.TagLibraryGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagManagementViewModelTest {
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
    fun `相同关键词重复提交也会重新建立 Paging 查询`() = runTest {
        val gateway = DeferredTagLibraryGateway()
        val model = TagManagementViewModel(gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.tags.collect()
        }

        model.updateQuery("海边")
        model.submitQuery()
        model.submitQuery()
        advanceUntilIdle()

        assertEquals(listOf("", "海边", "海边"), gateway.pagedKeywords)
    }

    @Test
    fun `未订阅期间的标签变更会保留到页面重新收集`() = runTest {
        val gateway = DeferredTagLibraryGateway()
        val model = TagManagementViewModel(gateway)
        val original = tag(id = 7, name = "海边")
        model.rename(original, "海岸")

        gateway.renameRequests.single().result.complete(tag(id = 7, name = "海岸"))

        assertEquals(TagLibraryChange(TagLibraryChangeKind.RENAMED, 7), model.changes.first())
        assertNull(model.state.value.busyTarget)
    }

    @Test
    fun `重命名响应 ID 漂移会暴露错误且不伪造成功`() = runTest {
        val gateway = DeferredTagLibraryGateway()
        val model = TagManagementViewModel(gateway)
        val changes = model.changes.produceIn(backgroundScope)
        model.rename(tag(id = 7, name = "海边"), "海岸")

        gateway.renameRequests.single().result.complete(tag(id = 8, name = "海岸"))

        assertNotNull(model.state.value.actionError)
        assertNull(model.state.value.feedback)
        assertNull(model.state.value.busyTarget)
        assertTrue(changes.tryReceive().isFailure)
    }

    @Test
    fun `忙碌期间不会重复提交其它标签 mutation`() = runTest {
        val gateway = DeferredTagLibraryGateway()
        val model = TagManagementViewModel(gateway)
        model.delete(tag(id = 7, name = "海边"))
        model.create("旅行")
        model.rename(tag(id = 8, name = "城市"), "街景")

        assertEquals(1, gateway.deleteRequests.size)
        assertEquals(0, gateway.createRequests.size)
        assertEquals(0, gateway.renameRequests.size)
        gateway.deleteRequests.single().result.complete(TagDeleteResult(3))
    }

    private fun tag(id: Long, name: String) = Tag(
        id = id,
        name = name,
        assetCount = 3,
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
    )

    private class DeferredTagLibraryGateway : TagLibraryGateway {
        val pagedKeywords = mutableListOf<String>()
        val createRequests = mutableListOf<CreateRequest>()
        val renameRequests = mutableListOf<RenameRequest>()
        val deleteRequests = mutableListOf<DeleteRequest>()

        override fun pagedTags(keyword: String): Flow<PagingData<Tag>> {
            pagedKeywords += keyword
            return flowOf(PagingData.empty())
        }

        override suspend fun create(name: String): Tag {
            val request = CreateRequest(name, CompletableDeferred())
            createRequests += request
            return request.result.await()
        }

        override suspend fun rename(tagId: Long, name: String): Tag {
            val request = RenameRequest(tagId, name, CompletableDeferred())
            renameRequests += request
            return request.result.await()
        }

        override suspend fun delete(tagId: Long): TagDeleteResult {
            val request = DeleteRequest(tagId, CompletableDeferred())
            deleteRequests += request
            return request.result.await()
        }
    }

    private data class CreateRequest(
        val name: String,
        val result: CompletableDeferred<Tag>,
    )

    private data class RenameRequest(
        val tagId: Long,
        val name: String,
        val result: CompletableDeferred<Tag>,
    )

    private data class DeleteRequest(
        val tagId: Long,
        val result: CompletableDeferred<TagDeleteResult>,
    )
}
