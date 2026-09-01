package com.yunai.phototube.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.album.AlbumDetailGateway
import com.yunai.phototube.data.album.AlbumKind
import com.yunai.phototube.data.album.AlbumPath
import com.yunai.phototube.data.album.AlbumPathChangeRequest
import com.yunai.phototube.data.album.AlbumPathInput
import com.yunai.phototube.data.album.AlbumPathSyncRunDetail
import com.yunai.phototube.data.album.AlbumPathSyncRun
import com.yunai.phototube.data.album.AlbumRepository
import com.yunai.phototube.data.album.AlbumSortMode
import com.yunai.phototube.data.album.PreviewedAlbumPathChange
import com.yunai.phototube.data.album.SourceFolderListing
import com.yunai.phototube.data.remote.BatchOperationResult
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.data.timeline.TimelineGateway
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModel(
    private val repository: AlbumDetailGateway,
    timelineRepository: TimelineGateway,
) : ViewModel() {
    private val albumId = MutableStateFlow<String?>(null)
    private val pathSyncAlbumId = MutableStateFlow<String?>(null)
    private val mutableUiState = MutableStateFlow(AlbumDetailUiState())
    private val mutableChanges = Channel<Unit>(capacity = Channel.BUFFERED)
    val uiState: StateFlow<AlbumDetailUiState> = mutableUiState.asStateFlow()
    val changes: Flow<Unit> = mutableChanges.receiveAsFlow()
    val assets: Flow<PagingData<MediaAsset>> = albumId
        .filterNotNull()
        .flatMapLatest(repository::pagedAlbumAssets)
        .cachedIn(viewModelScope)
    val syncRuns: Flow<PagingData<AlbumPathSyncRun>> = pathSyncAlbumId
        .filterNotNull()
        .flatMapLatest(repository::pagedPathSyncRuns)
        .cachedIn(viewModelScope)
    val candidateAssets: Flow<PagingData<MediaAsset>> = timelineRepository
        .pagedAssets(AssetFilter())
        .cachedIn(viewModelScope)
    val serverRoot = repository.serverRoot()

    private var detailJob: Job? = null
    private var syncJob: Job? = null
    private var syncDetailJob: Job? = null
    private var pathsJob: Job? = null
    private var folderJob: Job? = null
    private var previewJob: Job? = null
    private val busyAlbumIds = mutableSetOf<String>()
    private var albumGeneration = 0L
    private var detailRequestGeneration = 0L
    private var pathRequestGeneration = 0L
    private var folderRequestGeneration = 0L
    private var previewRequestGeneration = 0L
    private var syncRequestGeneration = 0L
    private var syncDetailRequestGeneration = 0L
    private var observedSyncRequest: AlbumSyncRequestToken? = null

    fun open(targetAlbumId: String) {
        if (albumId.value == targetAlbumId && mutableUiState.value.album != null) return
        albumGeneration += 1
        albumId.value = targetAlbumId
        pathSyncAlbumId.value = null
        detailJob?.cancel()
        pathsJob?.cancel()
        folderJob?.cancel()
        previewJob?.cancel()
        syncJob?.cancel()
        syncDetailJob?.cancel()
        detailRequestGeneration += 1
        pathRequestGeneration += 1
        folderRequestGeneration += 1
        previewRequestGeneration += 1
        syncRequestGeneration += 1
        syncDetailRequestGeneration += 1
        observedSyncRequest = null
        mutableUiState.value = AlbumDetailUiState(isBusy = targetAlbumId in busyAlbumIds)
        refreshAlbum()
    }

    fun refreshAlbum() {
        val route = currentRouteToken() ?: return
        detailJob?.cancel()
        detailRequestGeneration += 1
        val request = AlbumRequestToken(route, detailRequestGeneration)
        mutableUiState.update { it.copy(isLoading = true, error = null) }
        detailJob = viewModelScope.launch {
            runCatching { repository.getAlbumListing(route.albumId).album }
                .mapCatching { album ->
                    check(album.id == route.albumId) {
                        "PhotoTube 返回了其它图集的详情"
                    }
                    album
                }
                .onSuccess { album ->
                    if (request.matchesCurrent(detailRequestGeneration)) {
                        mutableUiState.update { it.copy(album = album, isLoading = false) }
                        if (album.kind == AlbumKind.PATH_SYNC) {
                            pathSyncAlbumId.value = album.id
                            refreshPaths()
                            resumeLatestSyncIfNeeded(album)
                        } else {
                            pathSyncAlbumId.value = null
                        }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (request.matchesCurrent(detailRequestGeneration)) {
                        mutableUiState.update {
                            it.copy(isLoading = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    fun refreshPaths() {
        val album = mutableUiState.value.album ?: return
        if (album.kind != AlbumKind.PATH_SYNC) return
        val route = currentRouteToken()?.takeIf { it.albumId == album.id } ?: return
        pathsJob?.cancel()
        pathRequestGeneration += 1
        val request = AlbumRequestToken(route, pathRequestGeneration)
        pathsJob = viewModelScope.launch {
            runCatching { repository.getAllPaths(album) }
                .mapCatching { paths ->
                    check(paths.all { it.albumId == album.id }) {
                        "PhotoTube 返回了其它图集的路径"
                    }
                    paths
                }
                .onSuccess { paths ->
                    if (request.matchesCurrent(pathRequestGeneration)) {
                        mutableUiState.update { it.copy(paths = paths) }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (request.matchesCurrent(pathRequestGeneration)) {
                        mutableUiState.update { it.copy(error = failure.toTimelineError()) }
                    }
                }
        }
    }

    fun browseFolder(path: String = "") {
        val route = currentRouteToken() ?: return
        folderJob?.cancel()
        folderRequestGeneration += 1
        val request = AlbumRequestToken(route, folderRequestGeneration)
        mutableUiState.update { it.copy(isBrowsingFolders = true, error = null) }
        folderJob = viewModelScope.launch {
            runCatching { repository.browseSourceFolders(path) }
                .onSuccess { listing ->
                    if (request.matchesCurrent(folderRequestGeneration)) {
                        mutableUiState.update {
                            it.copy(sourceFolderListing = listing, isBrowsingFolders = false)
                        }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (request.matchesCurrent(folderRequestGeneration)) {
                        mutableUiState.update {
                            it.copy(isBrowsingFolders = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    fun addAssets(assetIds: List<String>) = mutateAlbum(
        operation = { album -> repository.addAssets(album, assetIds) },
        onSuccess = { _, result ->
            mutableUiState.update {
                it.copy(
                    notice = batchNotice("已添加", result),
                    assetRefreshRevision = it.assetRefreshRevision + 1,
                )
            }
            refreshAlbum()
        },
    )

    fun removeAsset(assetId: String) = mutateAlbum(
        operation = { album -> repository.removeAssets(album, listOf(assetId)) },
        onSuccess = { _, result ->
            mutableUiState.update {
                it.copy(
                    notice = batchNotice("已移除", result),
                    assetRefreshRevision = it.assetRefreshRevision + 1,
                )
            }
            refreshAlbum()
        },
    )

    fun updateSettings(name: String, sortMode: AlbumSortMode) = mutateAlbum(
        operation = { album -> repository.updateSettings(album, name, sortMode) },
        onSuccess = { album, updated ->
            check(updated.id == album.id) { "PhotoTube 返回了其它图集的设置结果" }
            mutableUiState.update {
                it.copy(
                    album = updated,
                    notice = "图集信息已更新",
                    assetRefreshRevision = it.assetRefreshRevision + 1,
                )
            }
        },
    )

    fun setCover(assetId: String) = mutateAlbum(
        operation = { album -> repository.setCover(album, assetId) },
        onSuccess = { album, updated ->
            check(updated.id == album.id) { "PhotoTube 返回了其它图集的封面结果" }
            mutableUiState.update {
                it.copy(album = updated, notice = "已设置图集封面")
            }
        },
    )

    fun clearCover() = mutateAlbum(
        operation = repository::clearCover,
        onSuccess = { album, updated ->
            check(updated.id == album.id) { "PhotoTube 返回了其它图集的封面结果" }
            mutableUiState.update {
                it.copy(album = updated, notice = "已清除图集封面")
            }
        },
    )

    fun previewAddPath(path: AlbumPathInput) {
        val album = mutableUiState.value.album ?: return
        val generation = album.pathSync?.configGeneration ?: return
        previewPathChange(album, AlbumPathChangeRequest.add(generation, path))
    }

    fun previewRemovePath(path: AlbumPath) {
        val album = mutableUiState.value.album ?: return
        val generation = album.pathSync?.configGeneration ?: return
        previewPathChange(album, AlbumPathChangeRequest.remove(generation, path.id))
    }

    fun dismissPathPreview() {
        mutableUiState.update { it.copy(pathPreview = null) }
    }

    fun applyPathPreview() {
        val previewed = mutableUiState.value.pathPreview ?: return
        mutateAlbum(
            requirePathSync = true,
            operation = { album -> repository.applyPreviewedPathChange(album, previewed) },
            onSuccess = { album, accepted ->
                mutableUiState.update {
                    it.copy(
                        pathPreview = null,
                        notice = if (previewed.preview.requiresSync) {
                            "目录配置已保存，尚未扫描。请明确点击“开始扫描”。"
                        } else {
                            "目录配置已更新"
                        },
                        requiresManualSync = previewed.preview.requiresSync,
                    )
                }
                refreshAlbum()
                if (accepted.syncRunId != null) {
                    mutableUiState.update {
                        it.copy(syncHistoryRevision = it.syncHistoryRevision + 1)
                    }
                    observeSync(album.id, accepted.syncRunId, refreshOnTerminal = true)
                }
            },
        )
    }

    fun triggerManualSync() {
        mutateAlbum(
            requirePathSync = true,
            operation = repository::triggerManualSync,
            onSuccess = { album, run ->
                check(run.albumId == album.id) { "PhotoTube 返回了其它图集的同步任务" }
                mutableUiState.update {
                    it.copy(
                        requiresManualSync = false,
                        notice = "扫描已由你手动启动",
                        syncHistoryRevision = it.syncHistoryRevision + 1,
                    )
                }
                observeSync(album.id, run.id, refreshOnTerminal = true)
            },
        )
    }

    fun openSyncRun(run: AlbumPathSyncRun) {
        val album = mutableUiState.value.album ?: return
        if (album.kind != AlbumKind.PATH_SYNC || run.albumId != album.id) return
        if (run.state.isTerminal) {
            loadSyncRunDetail(album.id, run.id)
        } else {
            observeSync(album.id, run.id, refreshOnTerminal = true)
        }
    }

    fun deleteAlbum() {
        mutateAlbum(
            operation = { album -> repository.delete(album) },
            onSuccess = { _, _ -> mutableUiState.update { it.copy(deleted = true) } },
        )
    }

    private fun previewPathChange(album: Album, request: AlbumPathChangeRequest) {
        if (mutableUiState.value.isBusy) return
        val route = currentRouteToken()?.takeIf { it.albumId == album.id } ?: return
        previewJob?.cancel()
        previewRequestGeneration += 1
        val requestToken = AlbumRequestToken(route, previewRequestGeneration)
        mutableUiState.update { it.copy(isBusy = true, error = null) }
        previewJob = viewModelScope.launch {
            runCatching { repository.previewPathChange(album, request) }
                .onSuccess { preview ->
                    if (requestToken.matchesCurrent(previewRequestGeneration)) {
                        mutableUiState.update { it.copy(isBusy = false, pathPreview = preview) }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (requestToken.matchesCurrent(previewRequestGeneration)) {
                        mutableUiState.update {
                            it.copy(isBusy = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    private fun resumeLatestSyncIfNeeded(album: Album) {
        val summary = album.pathSync ?: return
        val lastRunId = summary.lastRunId ?: return
        val lastRunState = summary.lastRunState ?: return
        if (!lastRunState.isTerminal && observedSyncRequest?.syncRunId != lastRunId) {
            observeSync(album.id, lastRunId, refreshOnTerminal = true)
        }
    }

    private fun observeSync(
        targetAlbumId: String,
        syncRunId: String,
        refreshOnTerminal: Boolean,
    ) {
        val route = currentRouteToken()?.takeIf { it.albumId == targetAlbumId } ?: return
        syncJob?.cancel()
        syncRequestGeneration += 1
        val request = AlbumSyncRequestToken(route, syncRunId, syncRequestGeneration)
        observedSyncRequest = request
        mutableUiState.update {
            it.copy(
                selectedSyncRunId = syncRunId,
                isLoadingSyncDetail = true,
                syncDetail = null,
                error = null,
            )
        }
        syncJob = viewModelScope.launch {
            try {
                repository.observeSync(targetAlbumId, syncRunId).collect { detail ->
                    if (request.matchesCurrent(syncRequestGeneration)) {
                        check(detail.run.albumId == targetAlbumId && detail.run.id == syncRunId) {
                            "PhotoTube 返回了其它图集的同步详情"
                        }
                        if (mutableUiState.value.selectedSyncRunId == syncRunId) {
                            mutableUiState.update {
                                it.copy(syncDetail = detail, isLoadingSyncDetail = false)
                            }
                        }
                        if (detail.run.state.isTerminal && refreshOnTerminal) {
                            mutableUiState.update {
                                it.copy(
                                    assetRefreshRevision = it.assetRefreshRevision + 1,
                                    syncHistoryRevision = it.syncHistoryRevision + 1,
                                )
                            }
                            refreshAlbum()
                            mutableChanges.send(Unit)
                        }
                    }
                }
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (request.matchesCurrent(syncRequestGeneration)) {
                    mutableUiState.update {
                        it.copy(
                            isLoadingSyncDetail = false,
                            error = failure.toTimelineError(),
                        )
                    }
                }
            } finally {
                if (observedSyncRequest == request) observedSyncRequest = null
            }
        }
    }

    private fun loadSyncRunDetail(targetAlbumId: String, syncRunId: String) {
        val route = currentRouteToken()?.takeIf { it.albumId == targetAlbumId } ?: return
        syncDetailJob?.cancel()
        syncDetailRequestGeneration += 1
        val request = AlbumSyncRequestToken(route, syncRunId, syncDetailRequestGeneration)
        mutableUiState.update {
            it.copy(
                selectedSyncRunId = syncRunId,
                isLoadingSyncDetail = true,
                syncDetail = null,
                error = null,
            )
        }
        syncDetailJob = viewModelScope.launch {
            runCatching { repository.getSyncRunDetail(targetAlbumId, syncRunId) }
                .mapCatching { detail ->
                    check(detail.run.albumId == targetAlbumId && detail.run.id == syncRunId) {
                        "PhotoTube 返回了其它图集的同步详情"
                    }
                    detail
                }
                .onSuccess { detail ->
                    if (request.matchesCurrent(syncDetailRequestGeneration) &&
                        mutableUiState.value.selectedSyncRunId == syncRunId
                    ) {
                        mutableUiState.update {
                            it.copy(syncDetail = detail, isLoadingSyncDetail = false)
                        }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (request.matchesCurrent(syncDetailRequestGeneration)) {
                        mutableUiState.update {
                            it.copy(
                                isLoadingSyncDetail = false,
                                error = failure.toTimelineError(),
                            )
                        }
                    }
                }
        }
    }

    private fun <T> mutateAlbum(
        requirePathSync: Boolean = false,
        operation: suspend (Album) -> T,
        onSuccess: (Album, T) -> Unit,
    ) {
        val album = mutableUiState.value.album ?: return
        if (requirePathSync && album.kind != AlbumKind.PATH_SYNC) return
        if (mutableUiState.value.isBusy) return
        val route = currentRouteToken()?.takeIf { it.albumId == album.id } ?: return
        if (!busyAlbumIds.add(album.id)) return
        mutableUiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = operation(album)
                if (route.matchesCurrent()) onSuccess(album, result)
                mutableChanges.send(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (route.matchesCurrent()) {
                    mutableUiState.update { it.copy(error = failure.toTimelineError()) }
                }
            } finally {
                busyAlbumIds.remove(album.id)
                if (albumId.value == album.id) {
                    mutableUiState.update { it.copy(isBusy = album.id in busyAlbumIds) }
                }
            }
        }
    }

    private fun currentRouteToken(): AlbumRouteToken? = albumId.value?.let {
        AlbumRouteToken(albumId = it, generation = albumGeneration)
    }

    private fun AlbumRouteToken.matchesCurrent(): Boolean = matches(
        currentAlbumId = this@AlbumDetailViewModel.albumId.value,
        currentGeneration = albumGeneration,
    )

    private fun AlbumRequestToken.matchesCurrent(currentRequestGeneration: Long): Boolean =
        route.matchesCurrent() && requestGeneration == currentRequestGeneration

    private fun AlbumSyncRequestToken.matchesCurrent(currentRequestGeneration: Long): Boolean =
        route.matchesCurrent() && requestGeneration == currentRequestGeneration

    private fun batchNotice(prefix: String, result: BatchOperationResult): String {
        if (result.failed.isEmpty()) return "$prefix ${result.succeeded.size} 项"
        val reasons = result.failed.take(3).joinToString("；") { it.message }
        return "$prefix ${result.succeeded.size} 项，${result.failed.size} 项失败：$reasons"
    }

    companion object {
        fun factory(
            repository: AlbumRepository,
            timelineRepository: TimelineRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AlbumDetailViewModel(repository, timelineRepository) }
        }
    }
}

internal data class AlbumRouteToken(
    val albumId: String,
    val generation: Long,
) {
    fun matches(currentAlbumId: String?, currentGeneration: Long): Boolean =
        albumId == currentAlbumId && generation == currentGeneration
}

internal data class AlbumRequestToken(
    val route: AlbumRouteToken,
    val requestGeneration: Long,
)

internal data class AlbumSyncRequestToken(
    val route: AlbumRouteToken,
    val syncRunId: String,
    val requestGeneration: Long,
)

data class AlbumDetailUiState(
    val album: Album? = null,
    val paths: List<AlbumPath> = emptyList(),
    val pathPreview: PreviewedAlbumPathChange? = null,
    val syncDetail: AlbumPathSyncRunDetail? = null,
    val selectedSyncRunId: String? = null,
    val isLoadingSyncDetail: Boolean = false,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val requiresManualSync: Boolean = false,
    val assetRefreshRevision: Int = 0,
    val syncHistoryRevision: Int = 0,
    val notice: String? = null,
    val error: TimelineError? = null,
    val sourceFolderListing: SourceFolderListing? = null,
    val isBrowsingFolders: Boolean = false,
    val deleted: Boolean = false,
)
