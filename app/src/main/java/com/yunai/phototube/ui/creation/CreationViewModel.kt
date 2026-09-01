package com.yunai.phototube.ui.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class CreationViewModel(
    private val repository: TimelineRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(creationAssetFilter(favoriteOnly = false))
    private val mutableUiState = MutableStateFlow(CreationUiState())

    val uiState: StateFlow<CreationUiState> = mutableUiState.asStateFlow()
    val serverRoot: ServerRoot = repository.serverRoot()
    val assets: Flow<PagingData<MediaAsset>> = filter
        .flatMapLatest(repository::pagedAssets)
        .cachedIn(viewModelScope)

    fun toggleFavoriteOnly() {
        val favoriteOnly = !mutableUiState.value.favoriteOnly
        mutableUiState.update { it.copy(favoriteOnly = favoriteOnly) }
        filter.value = creationAssetFilter(favoriteOnly)
    }

    fun toggleGridDensity() {
        mutableUiState.update { it.copy(columns = nextCreationGridColumns(it.columns)) }
    }

    companion object {
        fun factory(repository: TimelineRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreationViewModel(repository) }
        }
    }
}

data class CreationUiState(
    val favoriteOnly: Boolean = false,
    val columns: Int = CREATION_COMFORTABLE_COLUMNS,
)

internal fun creationAssetFilter(favoriteOnly: Boolean): AssetFilter = AssetFilter(
    kind = AssetKind.PHOTO,
    favorite = true.takeIf { favoriteOnly },
)

internal fun nextCreationGridColumns(current: Int): Int = when (current) {
    CREATION_COMFORTABLE_COLUMNS -> CREATION_COMPACT_COLUMNS
    else -> CREATION_COMFORTABLE_COLUMNS
}

internal const val CREATION_COMFORTABLE_COLUMNS = 2
internal const val CREATION_COMPACT_COLUMNS = 3
