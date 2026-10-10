package com.yunai.phototube.ui.remote

import androidx.compose.runtime.*
import androidx.paging.*
import com.yunai.phototube.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flatMapLatest

/** Owns only metadata, the API client, and a bounded paging cache; no UI/window references. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AlbumDetailState(repository: AlbumRepository, initial: RemoteAlbum) : AutoCloseable {
    val album = mutableStateOf(initial)
    val generation = mutableIntStateOf(0)
    val busy = mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val members = snapshotFlow { generation.intValue }.flatMapLatest { revision ->
        Pager(PagingConfig(100, initialLoadSize = 100, prefetchDistance = 15, maxSize = 500, enablePlaceholders = false)) {
            AlbumPagingSource { cursor ->
                val result = repository.members(initial.id, cursor)
                withContext(Dispatchers.Main.immediate) {
                    if (revision == generation.intValue && !busy.value) album.value = result.first
                }
                result.second
            }
        }.flow
    }.cachedIn(scope)

    override fun close() { scope.cancel() }
}
