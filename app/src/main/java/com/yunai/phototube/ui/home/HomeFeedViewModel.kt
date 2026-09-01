package com.yunai.phototube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.home.HomeFeed
import com.yunai.phototube.data.home.HomeFeedGateway
import com.yunai.phototube.data.home.HomeRepository
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeFeedViewModel(
    private val repository: HomeFeedGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeFeedState>(HomeFeedState.Loading)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    val state: StateFlow<HomeFeedState> = mutableState.asStateFlow()
    val serverRoot: ServerRoot = repository.serverRoot()

    init {
        refresh()
    }

    fun refresh() {
        refreshGeneration += 1
        val token = HomeRefreshRequestToken(refreshGeneration)
        refreshJob?.cancel()
        mutableState.value = HomeFeedState.Loading
        refreshJob = viewModelScope.launch {
            try {
                val feed = repository.getHomeFeed()
                if (token.matches(refreshGeneration)) {
                    mutableState.value = HomeFeedState.Ready(feed)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (token.matches(refreshGeneration)) {
                    mutableState.value = HomeFeedState.Error(failure.toTimelineError())
                }
            }
        }
    }

    companion object {
        fun factory(repository: HomeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeFeedViewModel(repository) }
        }
    }
}

internal data class HomeRefreshRequestToken(
    val generation: Long,
) {
    fun matches(currentGeneration: Long): Boolean = generation == currentGeneration
}

sealed interface HomeFeedState {
    data object Loading : HomeFeedState
    data class Ready(val feed: HomeFeed) : HomeFeedState
    data class Error(val error: TimelineError) : HomeFeedState
}
