package com.yunai.phototube.ui.collections

import androidx.paging.PagingData
import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.album.AlbumAssetListing
import com.yunai.phototube.data.album.AlbumDetailGateway
import com.yunai.phototube.data.album.AlbumKind
import com.yunai.phototube.data.album.AlbumPath
import com.yunai.phototube.data.album.AlbumPathChangeAccepted
import com.yunai.phototube.data.album.AlbumPathChangeRequest
import com.yunai.phototube.data.album.AlbumPathInput
import com.yunai.phototube.data.album.AlbumPathSyncRun
import com.yunai.phototube.data.album.AlbumPathSyncRunDetail
import com.yunai.phototube.data.album.AlbumPathSyncSummary
import com.yunai.phototube.data.album.AlbumSortMode
import com.yunai.phototube.data.album.PreviewedAlbumPathChange
import com.yunai.phototube.data.album.SourceFolderListing
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.BatchOperationResult
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.MediaAssetPage
import com.yunai.phototube.data.timeline.TimelineGateway
import com.yunai.phototube.data.timeline.TimelineGranularity
import com.yunai.phototube.data.timeline.TimelineSummaryResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
class AlbumDetailViewModelTest {
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
    fun `同一相册重复刷新时旧详情不能覆盖最新响应`() = runTest {
        val gateway = DeferredAlbumGateway()
        val model = AlbumDetailViewModel(gateway, EmptyTimelineGateway)
        model.open("album-a")
        model.refreshAlbum()

        gateway.detailRequests[1].result.complete(listing(album("album-a", "最新名称")))
        gateway.detailRequests[0].result.complete(listing(album("album-a", "旧名称")))

        assertEquals("最新名称", model.uiState.value.album?.name)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `切换相册后旧路径结果不能写入新相册`() = runTest {
        val gateway = DeferredAlbumGateway()
        val model = AlbumDetailViewModel(gateway, EmptyTimelineGateway)
        model.open("album-a")
        gateway.detailRequests.single().result.complete(
            listing(album("album-a", "路径相册", AlbumKind.PATH_SYNC)),
        )
        assertEquals(1, gateway.pathRequests.size)

        model.open("album-b")
        gateway.detailRequests[1].result.complete(listing(album("album-b", "普通相册")))
        gateway.pathRequests.single().result.complete(listOf(path("album-a")))

        assertEquals("album-b", model.uiState.value.album?.id)
        assertTrue(model.uiState.value.paths.isEmpty())
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `旧相册写操作离页成功后只交付失效事件不污染新相册`() = runTest {
        val gateway = DeferredAlbumGateway()
        val model = AlbumDetailViewModel(gateway, EmptyTimelineGateway)
        model.open("album-a")
        gateway.detailRequests.single().result.complete(listing(album("album-a", "相册 A")))
        model.updateSettings("新名称", AlbumSortMode.TAKEN_AT_ASC)

        model.open("album-b")
        gateway.detailRequests[1].result.complete(listing(album("album-b", "相册 B")))
        gateway.settingsRequests.single().result.complete(
            album("album-a", "新名称").copy(sortMode = AlbumSortMode.TAKEN_AT_ASC),
        )

        assertEquals(Unit, model.changes.first())
        assertEquals("album-b", model.uiState.value.album?.id)
        assertEquals("相册 B", model.uiState.value.album?.name)
        assertNull(model.uiState.value.notice)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `设置响应图集 ID 漂移会报错且不发送成功失效`() = runTest {
        val gateway = DeferredAlbumGateway()
        val model = AlbumDetailViewModel(gateway, EmptyTimelineGateway)
        val changes = model.changes.produceIn(backgroundScope)
        model.open("album-a")
        gateway.detailRequests.single().result.complete(listing(album("album-a", "相册 A")))
        model.updateSettings("新名称", AlbumSortMode.TAKEN_AT_ASC)

        gateway.settingsRequests.single().result.complete(album("album-other", "错误相册"))

        assertEquals("相册 A", model.uiState.value.album?.name)
        assertNotNull(model.uiState.value.error)
        assertNull(model.uiState.value.notice)
        assertTrue(changes.tryReceive().isFailure)
    }

    @Test
    fun `路径读取失败不会提前清除同相册写操作忙碌态`() = runTest {
        val gateway = DeferredAlbumGateway()
        val model = AlbumDetailViewModel(gateway, EmptyTimelineGateway)
        model.open("album-a")
        gateway.detailRequests.single().result.complete(
            listing(album("album-a", "路径相册", AlbumKind.PATH_SYNC)),
        )
        model.updateSettings("新名称", AlbumSortMode.TAKEN_AT_ASC)

        gateway.pathRequests.single().result.completeExceptionally(IllegalStateException("路径离线"))

        assertTrue(model.uiState.value.isBusy)
        assertNotNull(model.uiState.value.error)
        gateway.settingsRequests.single().result.complete(
            album("album-a", "新名称", AlbumKind.PATH_SYNC).copy(
                sortMode = AlbumSortMode.TAKEN_AT_ASC,
            ),
        )
        assertTrue(!model.uiState.value.isBusy)
    }

    private fun listing(album: Album) = AlbumAssetListing(
        album = album,
        assets = MediaAssetPage(items = emptyList(), nextCursor = null),
    )

    private fun album(
        id: String,
        name: String,
        kind: AlbumKind = AlbumKind.NORMAL,
    ) = Album(
        id = id,
        userId = "user-1",
        name = name,
        kind = kind,
        sortMode = AlbumSortMode.TAKEN_AT_DESC,
        aiPrompt = null,
        filter = null,
        pathSync = if (kind == AlbumKind.PATH_SYNC) {
            AlbumPathSyncSummary(
                configGeneration = 1,
                totalPathCount = 1,
                enabledPathCount = 1,
                lastRunId = null,
                lastRunState = null,
                lastRunAt = null,
            )
        } else {
            null
        },
        coverAssetId = null,
        coverFocalPoint = null,
        cover = null,
        assetCount = 0,
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
    )

    private fun path(albumId: String) = AlbumPath(
        id = "path-1",
        albumId = albumId,
        libraryId = "library-1",
        relativePath = "Photos/Family",
        enabled = true,
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
        lastSuccessfulSyncAt = null,
        lastRunState = null,
    )

    private class DeferredAlbumGateway : AlbumDetailGateway {
        val detailRequests = mutableListOf<DetailRequest>()
        val pathRequests = mutableListOf<PathRequest>()
        val settingsRequests = mutableListOf<SettingsRequest>()

        override fun pagedAlbumAssets(albumId: String): Flow<PagingData<MediaAsset>> =
            flowOf(PagingData.empty())

        override fun pagedPathSyncRuns(albumId: String): Flow<PagingData<AlbumPathSyncRun>> =
            flowOf(PagingData.empty())

        override suspend fun getAlbumListing(albumId: String): AlbumAssetListing {
            val request = DetailRequest(albumId, CompletableDeferred())
            detailRequests += request
            return withContext(NonCancellable) { request.result.await() }
        }

        override suspend fun getAllPaths(album: Album): List<AlbumPath> {
            val request = PathRequest(album.id, CompletableDeferred())
            pathRequests += request
            return withContext(NonCancellable) { request.result.await() }
        }

        override suspend fun updateSettings(
            album: Album,
            name: String,
            sortMode: AlbumSortMode,
        ): Album {
            val request = SettingsRequest(album.id, name, sortMode, CompletableDeferred())
            settingsRequests += request
            return withContext(NonCancellable) { request.result.await() }
        }

        override suspend fun delete(album: Album) = error("测试未使用 delete")
        override suspend fun setCover(album: Album, assetId: String): Album =
            error("测试未使用 setCover")

        override suspend fun clearCover(album: Album): Album = error("测试未使用 clearCover")
        override suspend fun addAssets(
            album: Album,
            assetIds: List<String>,
        ): BatchOperationResult = error("测试未使用 addAssets")

        override suspend fun removeAssets(
            album: Album,
            assetIds: List<String>,
        ): BatchOperationResult = error("测试未使用 removeAssets")

        override suspend fun browseSourceFolders(path: String): SourceFolderListing =
            error("测试未使用 browseSourceFolders")

        override suspend fun previewPathChange(
            album: Album,
            request: AlbumPathChangeRequest,
        ): PreviewedAlbumPathChange = error("测试未使用 previewPathChange")

        override suspend fun applyPreviewedPathChange(
            album: Album,
            previewed: PreviewedAlbumPathChange,
        ): AlbumPathChangeAccepted = error("测试未使用 applyPreviewedPathChange")

        override suspend fun triggerManualSync(album: Album): AlbumPathSyncRun =
            error("测试未使用 triggerManualSync")

        override fun observeSync(
            albumId: String,
            syncRunId: String,
        ): Flow<AlbumPathSyncRunDetail> = emptyFlow()

        override suspend fun getSyncRunDetail(
            albumId: String,
            syncRunId: String,
        ): AlbumPathSyncRunDetail = error("测试未使用 getSyncRunDetail")

        override fun serverRoot(): ServerRoot = TEST_SERVER_ROOT
    }

    private object EmptyTimelineGateway : TimelineGateway {
        override fun pagedAssets(filter: AssetFilter): Flow<PagingData<MediaAsset>> =
            flowOf(PagingData.empty())

        override suspend fun getSummary(
            filter: AssetFilter,
            granularity: TimelineGranularity,
        ): TimelineSummaryResponse = error("测试未使用 getSummary")

        override fun serverRoot(): ServerRoot = TEST_SERVER_ROOT
    }

    private data class DetailRequest(
        val albumId: String,
        val result: CompletableDeferred<AlbumAssetListing>,
    )

    private data class PathRequest(
        val albumId: String,
        val result: CompletableDeferred<List<AlbumPath>>,
    )

    private data class SettingsRequest(
        val albumId: String,
        val name: String,
        val sortMode: AlbumSortMode,
        val result: CompletableDeferred<Album>,
    )

    private companion object {
        val TEST_SERVER_ROOT = ServerRoot.parse("https://photos.example.test").getOrThrow()
    }
}
