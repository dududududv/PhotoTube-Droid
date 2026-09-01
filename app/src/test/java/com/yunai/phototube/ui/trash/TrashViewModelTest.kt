package com.yunai.phototube.ui.trash

import androidx.paging.PagingData
import com.yunai.phototube.data.asset.TrashMutationGateway
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.trash.TrashFeedGateway
import com.yunai.phototube.ui.AssetChangeKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
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
    fun `恢复成功即使稍后收集也交付带资产 ID 的 RESTORED 事件`() = runTest {
        val mutation = DeferredTrashMutationGateway()
        val model = TrashViewModel(EmptyTrashFeedGateway(), mutation)

        model.restore("asset-7")
        assertEquals("asset-7", model.uiState.value.busyAssetId)
        mutation.restoreRequests.single().result.complete(Unit)

        assertEquals(
            TrashChange(assetId = "asset-7", kind = AssetChangeKind.RESTORED),
            model.changes.first(),
        )
        assertNull(model.uiState.value.busyAssetId)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `彻底清除成功交付带资产 ID 的 PURGED 事件`() = runTest {
        val mutation = DeferredTrashMutationGateway()
        val model = TrashViewModel(EmptyTrashFeedGateway(), mutation)

        model.purge("asset-9")
        mutation.purgeRequests.single().result.complete(Unit)

        assertEquals(
            TrashChange(assetId = "asset-9", kind = AssetChangeKind.PURGED),
            model.changes.first(),
        )
        assertNull(model.uiState.value.busyAssetId)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `忙碌期间拒绝第二个回收站 mutation`() = runTest {
        val mutation = DeferredTrashMutationGateway()
        val model = TrashViewModel(EmptyTrashFeedGateway(), mutation)

        model.restore("asset-7")
        model.purge("asset-9")

        assertEquals(1, mutation.restoreRequests.size)
        assertTrue(mutation.purgeRequests.isEmpty())
        assertEquals("asset-7", model.uiState.value.busyAssetId)
        mutation.restoreRequests.single().result.complete(Unit)
    }

    @Test
    fun `回收站 mutation 失败会清除忙碌并且不交付成功事件`() = runTest {
        val mutation = DeferredTrashMutationGateway()
        val model = TrashViewModel(EmptyTrashFeedGateway(), mutation)
        val changes = model.changes.produceIn(backgroundScope)

        model.restore("asset-7")
        mutation.restoreRequests.single().result.completeExceptionally(
            IllegalStateException("恢复失败"),
        )

        assertNull(model.uiState.value.busyAssetId)
        assertNotNull(model.uiState.value.error)
        assertTrue(changes.tryReceive().isFailure)
    }

    private class EmptyTrashFeedGateway : TrashFeedGateway {
        override fun pagedTrash(): Flow<PagingData<MediaAsset>> = flowOf(PagingData.empty())

        override fun serverRoot(): ServerRoot = TEST_SERVER_ROOT
    }

    private class DeferredTrashMutationGateway : TrashMutationGateway {
        val restoreRequests = mutableListOf<MutationRequest>()
        val purgeRequests = mutableListOf<MutationRequest>()

        override suspend fun restoreAsset(assetId: String) {
            val request = MutationRequest(assetId, CompletableDeferred())
            restoreRequests += request
            withContext(NonCancellable) { request.result.await() }
        }

        override suspend fun purgeAsset(assetId: String) {
            val request = MutationRequest(assetId, CompletableDeferred())
            purgeRequests += request
            withContext(NonCancellable) { request.result.await() }
        }
    }

    private data class MutationRequest(
        val assetId: String,
        val result: CompletableDeferred<Unit>,
    )

    private companion object {
        val TEST_SERVER_ROOT = ServerRoot.parse("https://photos.example.test").getOrThrow()
    }
}
