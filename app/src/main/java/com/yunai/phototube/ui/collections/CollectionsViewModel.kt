package com.yunai.phototube.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.album.AlbumPathInput
import com.yunai.phototube.data.album.AlbumRepository
import com.yunai.phototube.data.album.SourceFolderListing
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.rethrowCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectionsViewModel(
    private val repository: AlbumRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = mutableUiState.asStateFlow()
    val albums: Flow<PagingData<Album>> = repository.pagedAlbums().cachedIn(viewModelScope)
    val serverRoot = repository.serverRoot()

    private var folderJob: Job? = null

    fun browseFolder(path: String = "") {
        folderJob?.cancel()
        mutableUiState.update { it.copy(isBrowsingFolders = true, error = null) }
        folderJob = viewModelScope.launch {
            runCatching { repository.browseSourceFolders(path) }
                .onSuccess { listing ->
                    mutableUiState.update { it.copy(folderListing = listing, isBrowsingFolders = false) }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    mutableUiState.update {
                        it.copy(isBrowsingFolders = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    fun createNormal(name: String) = create { repository.createNormal(name) }

    fun createPathSync(name: String, libraryId: String, relativePath: String) = create {
        repository.createPathSync(
            name = name,
            paths = listOf(AlbumPathInput(libraryId, relativePath, enabled = true)),
        )
    }

    fun consumeCreatedAlbum() {
        mutableUiState.update { it.copy(createdAlbum = null) }
    }

    private fun create(operation: suspend () -> Album) {
        if (mutableUiState.value.isCreating) return
        mutableUiState.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess { album ->
                    mutableUiState.update {
                        it.copy(isCreating = false, createdAlbum = album, folderListing = null)
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    mutableUiState.update {
                        it.copy(isCreating = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    companion object {
        fun factory(repository: AlbumRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CollectionsViewModel(repository) }
        }
    }
}

data class CollectionsUiState(
    val folderListing: SourceFolderListing? = null,
    val isBrowsingFolders: Boolean = false,
    val isCreating: Boolean = false,
    val createdAlbum: Album? = null,
    val error: TimelineError? = null,
)
