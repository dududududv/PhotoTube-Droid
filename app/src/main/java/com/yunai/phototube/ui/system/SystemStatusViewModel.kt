package com.yunai.phototube.ui.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yunai.phototube.data.system.LocalCacheSnapshot
import com.yunai.phototube.data.system.SystemRepository
import com.yunai.phototube.data.system.SystemStatus
import com.yunai.phototube.data.system.SystemStatusGateway
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SystemStatusViewModel(
    private val repository: SystemStatusGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SystemStatusUiState())
    private var statusJob: Job? = null
    private var cacheJob: Job? = null
    private var statusGeneration = 0L
    private var cacheGeneration = 0L

    val state: StateFlow<SystemStatusUiState> = mutableState.asStateFlow()

    fun refreshAll() {
        refreshStatus()
        refreshLocalCache()
    }

    fun refreshStatus() {
        statusGeneration += 1
        val token = SystemStatusRequestToken(statusGeneration)
        statusJob?.cancel()
        mutableState.update { it.copy(isLoadingStatus = true, statusError = null) }
        statusJob = viewModelScope.launch {
            try {
                val status = repository.getStatus()
                if (token.matches(statusGeneration)) {
                    mutableState.update { it.copy(status = status, isLoadingStatus = false, statusError = null) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (token.matches(statusGeneration)) {
                    mutableState.update {
                        it.copy(isLoadingStatus = false, statusError = failure.toTimelineError())
                    }
                }
            }
        }
    }

    fun refreshLocalCache() {
        if (mutableState.value.isClearingCache) return
        cacheGeneration += 1
        val token = LocalCacheRequestToken(cacheGeneration)
        cacheJob?.cancel()
        mutableState.update { it.copy(isLoadingCache = true, cacheError = null) }
        cacheJob = viewModelScope.launch {
            try {
                val snapshot = repository.getLocalCacheSnapshot()
                if (token.matches(cacheGeneration)) {
                    mutableState.update { it.copy(cache = snapshot, isLoadingCache = false, cacheError = null) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (token.matches(cacheGeneration)) {
                    mutableState.update {
                        it.copy(isLoadingCache = false, cacheError = failure.toTimelineError())
                    }
                }
            }
        }
    }

    fun clearLocalCache() {
        if (mutableState.value.isClearingCache) return
        cacheGeneration += 1
        val token = LocalCacheRequestToken(cacheGeneration)
        cacheJob?.cancel()
        mutableState.update {
            it.copy(isClearingCache = true, cacheError = null, feedback = null)
        }
        cacheJob = viewModelScope.launch {
            try {
                val snapshot = repository.clearLocalMediaCaches()
                if (token.matches(cacheGeneration)) {
                    mutableState.update {
                        it.copy(
                            cache = snapshot,
                            isLoadingCache = false,
                            isClearingCache = false,
                            cacheError = null,
                            feedback = "本机媒体缓存已清除",
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (token.matches(cacheGeneration)) {
                    mutableState.update {
                        it.copy(
                            isLoadingCache = false,
                            isClearingCache = false,
                            cacheError = failure.toTimelineError(),
                        )
                    }
                }
            }
        }
    }

    fun consumeFeedback() {
        mutableState.update { it.copy(feedback = null) }
    }

    companion object {
        fun factory(repository: SystemRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SystemStatusViewModel(repository) }
        }
    }
}

internal data class SystemStatusRequestToken(
    val generation: Long,
) {
    fun matches(currentGeneration: Long): Boolean = generation == currentGeneration
}

internal data class LocalCacheRequestToken(
    val generation: Long,
) {
    fun matches(currentGeneration: Long): Boolean = generation == currentGeneration
}

data class SystemStatusUiState(
    val status: SystemStatus? = null,
    val isLoadingStatus: Boolean = true,
    val statusError: TimelineError? = null,
    val cache: LocalCacheSnapshot? = null,
    val isLoadingCache: Boolean = true,
    val isClearingCache: Boolean = false,
    val cacheError: TimelineError? = null,
    val feedback: String? = null,
)
