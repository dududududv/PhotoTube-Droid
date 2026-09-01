package com.yunai.phototube.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.asset.AssetRepository
import com.yunai.phototube.data.asset.TrashMutationGateway
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.trash.TrashFeedGateway
import com.yunai.phototube.data.trash.TrashRepository
import com.yunai.phototube.ui.AssetChangeKind
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    trashRepository: TrashFeedGateway,
    private val assetRepository: TrashMutationGateway,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TrashUiState())
    private val mutableChanges = Channel<TrashChange>(capacity = Channel.BUFFERED)
    val uiState: StateFlow<TrashUiState> = mutableUiState.asStateFlow()
    val changes: Flow<TrashChange> = mutableChanges.receiveAsFlow()
    val assets: Flow<PagingData<MediaAsset>> = trashRepository.pagedTrash().cachedIn(viewModelScope)
    val serverRoot: ServerRoot = trashRepository.serverRoot()

    fun restore(assetId: String) = mutate(assetId, AssetChangeKind.RESTORED) {
        assetRepository.restoreAsset(assetId)
    }

    fun purge(assetId: String) = mutate(assetId, AssetChangeKind.PURGED) {
        assetRepository.purgeAsset(assetId)
    }

    private fun mutate(
        assetId: String,
        kind: AssetChangeKind,
        operation: suspend () -> Unit,
    ) {
        if (mutableUiState.value.busyAssetId != null) return
        mutableUiState.update { it.copy(busyAssetId = assetId, error = null) }
        viewModelScope.launch {
            try {
                operation()
                mutableUiState.update {
                    it.copy(busyAssetId = null)
                }
                mutableChanges.send(TrashChange(assetId = assetId, kind = kind))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                mutableUiState.update {
                    it.copy(busyAssetId = null, error = failure.toTimelineError())
                }
            }
        }
    }

    companion object {
        fun factory(
            trashRepository: TrashRepository,
            assetRepository: AssetRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TrashViewModel(trashRepository, assetRepository) }
        }
    }
}

data class TrashChange(
    val assetId: String,
    val kind: AssetChangeKind,
)

data class TrashUiState(
    val busyAssetId: String? = null,
    val error: TimelineError? = null,
)
