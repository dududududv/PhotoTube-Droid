package com.yunai.phototube.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineGranularity
import com.yunai.phototube.data.timeline.TimelineGateway
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.data.timeline.TimelineSummaryResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TimelineViewModel(
    private val repository: TimelineGateway,
) : ViewModel() {
    private val mutableFilter = MutableStateFlow(AssetFilter())
    private val mutableSummaryState = MutableStateFlow<TimelineSummaryState>(TimelineSummaryState.Loading)
    private val mutableGranularity = MutableStateFlow(TimelineGranularity.DAY)
    private val mutableLayoutMode = MutableStateFlow(PhotoLayoutMode.TIMELINE)
    private var summaryJob: Job? = null
    private var summaryGeneration = 0L

    val summaryState: StateFlow<TimelineSummaryState> = mutableSummaryState.asStateFlow()
    val granularity: StateFlow<TimelineGranularity> = mutableGranularity.asStateFlow()
    val layoutMode: StateFlow<PhotoLayoutMode> = mutableLayoutMode.asStateFlow()
    val filter: StateFlow<AssetFilter> = mutableFilter.asStateFlow()
    val assets: Flow<PagingData<MediaAsset>> = mutableFilter
        .flatMapLatest(repository::pagedAssets)
        .cachedIn(viewModelScope)
    val serverRoot: ServerRoot = repository.serverRoot()

    init {
        refreshSummary()
    }

    fun refreshSummary() {
        summaryGeneration += 1
        val requestedGranularity = mutableGranularity.value
        val requestedFilter = mutableFilter.value
        val token = TimelineSummaryRequestToken(
            generation = summaryGeneration,
            granularity = requestedGranularity,
            filter = requestedFilter,
        )
        summaryJob?.cancel()
        mutableSummaryState.value = TimelineSummaryState.Loading
        summaryJob = viewModelScope.launch {
            try {
                val summary = repository.getSummary(requestedFilter, requestedGranularity)
                if (token.matches(summaryGeneration, mutableGranularity.value, mutableFilter.value)) {
                    mutableSummaryState.value = TimelineSummaryState.Ready(summary)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (token.matches(summaryGeneration, mutableGranularity.value, mutableFilter.value)) {
                    mutableSummaryState.value = TimelineSummaryState.Error(failure.toTimelineError())
                }
            }
        }
    }

    fun setGranularity(granularity: TimelineGranularity) {
        if (mutableGranularity.value == granularity) return
        mutableGranularity.value = granularity
        refreshSummary()
    }

    fun applyFilter(filter: AssetFilter) {
        if (mutableFilter.value == filter) return
        mutableFilter.value = filter
        refreshSummary()
    }

    fun removeDeletedTag(tagId: Long) {
        applyFilter(mutableFilter.value.withoutTag(tagId))
    }

    fun toggleLayoutMode() {
        mutableLayoutMode.value = when (mutableLayoutMode.value) {
            PhotoLayoutMode.TIMELINE -> PhotoLayoutMode.OVERVIEW
            PhotoLayoutMode.OVERVIEW -> PhotoLayoutMode.TIMELINE
        }
    }

    companion object {
        fun factory(repository: TimelineRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TimelineViewModel(repository) }
        }
    }
}

internal data class TimelineSummaryRequestToken(
    val generation: Long,
    val granularity: TimelineGranularity,
    val filter: AssetFilter,
) {
    fun matches(
        currentGeneration: Long,
        currentGranularity: TimelineGranularity,
        currentFilter: AssetFilter,
    ): Boolean = generation == currentGeneration &&
        granularity == currentGranularity &&
        filter == currentFilter
}

enum class PhotoLayoutMode { TIMELINE, OVERVIEW }

sealed interface TimelineSummaryState {
    data object Loading : TimelineSummaryState
    data class Ready(val summary: TimelineSummaryResponse) : TimelineSummaryState
    data class Error(val error: TimelineError) : TimelineSummaryState
}

data class TimelineError(
    val message: String,
    val logId: String? = null,
    val retryable: Boolean = false,
    val code: String? = null,
)

fun Throwable.toTimelineError(): TimelineError = when (this) {
    is ApiFailure -> TimelineError(
        message = error.message,
        logId = error.logId.takeIf(String::isNotBlank),
        retryable = error.retryable,
        code = error.code,
    )
    is IOException -> TimelineError("无法连接 PhotoTube，请检查网络后重试", retryable = true)
    else -> TimelineError(message ?: "时间轴加载失败", retryable = true)
}
