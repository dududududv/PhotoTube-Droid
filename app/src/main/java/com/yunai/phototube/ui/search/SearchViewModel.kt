package com.yunai.phototube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.takeUnicodeCodePoints
import com.yunai.phototube.data.unicodeCodePointCount
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineGranularity
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: TimelineRepository,
) : ViewModel() {
    private val activeRequest = MutableStateFlow<SearchRequest?>(null)
    private val mutableUiState = MutableStateFlow(SearchUiState())
    private var summaryJob: Job? = null
    private var requestGeneration = 0L

    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()
    val serverRoot: ServerRoot = repository.serverRoot()
    val assets: Flow<PagingData<MediaAsset>> = activeRequest
        .flatMapLatest { request ->
            if (request == null) flowOf(PagingData.empty()) else repository.pagedAssets(request.filter)
        }
        .cachedIn(viewModelScope)

    fun updateQuery(value: String) {
        mutableUiState.update {
            it.copy(
                query = value.takeUnicodeCodePoints(MAX_QUERY_CODE_POINTS),
                validationMessage = null,
            )
        }
    }

    fun setKind(kind: AssetKind?) {
        mutableUiState.update { it.copy(kind = if (it.kind == kind) null else kind) }
    }

    fun toggleFavorite() {
        mutableUiState.update { it.copy(favoriteOnly = !it.favoriteOnly) }
    }

    fun setRating(rating: Int?) {
        mutableUiState.update { it.copy(rating = if (it.rating == rating) null else rating) }
    }

    fun search() {
        val state = mutableUiState.value
        val keyword = state.query.trim()
        if (keyword.unicodeCodePointCount() < MIN_QUERY_CODE_POINTS) {
            requestGeneration += 1
            activeRequest.value = null
            summaryJob?.cancel()
            summaryJob = null
            mutableUiState.update {
                it.copy(
                    validationMessage = "请输入至少 3 个字符",
                    submittedKeyword = null,
                    isLoadingSummary = false,
                    totalCount = null,
                    summaryError = null,
                )
            }
            return
        }
        val filter = AssetFilter(
            kind = state.kind,
            keyword = keyword,
            favorite = if (state.favoriteOnly) true else null,
            rating = state.rating,
        )
        requestGeneration += 1
        val request = SearchRequest(
            generation = requestGeneration,
            filter = filter,
        )
        mutableUiState.update {
            it.copy(
                query = keyword,
                submittedKeyword = keyword,
                validationMessage = null,
                isLoadingSummary = true,
                summaryError = null,
                totalCount = null,
            )
        }
        activeRequest.value = request
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            try {
                val summary = repository.getSummary(filter, TimelineGranularity.DAY)
                if (request.matches(activeRequest.value)) {
                    mutableUiState.update {
                        it.copy(isLoadingSummary = false, totalCount = summary.totalCount, summaryError = null)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (request.matches(activeRequest.value)) {
                    mutableUiState.update {
                        it.copy(isLoadingSummary = false, summaryError = failure.toTimelineError())
                    }
                }
            }
        }
    }

    companion object {
        private const val MIN_QUERY_CODE_POINTS = 3
        private const val MAX_QUERY_CODE_POINTS = 255

        fun factory(repository: TimelineRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(repository) }
        }
    }
}

internal data class SearchRequest(
    val generation: Long,
    val filter: AssetFilter,
) {
    fun matches(current: SearchRequest?): Boolean = this == current
}

data class SearchUiState(
    val query: String = "",
    val submittedKeyword: String? = null,
    val kind: AssetKind? = null,
    val favoriteOnly: Boolean = false,
    val rating: Int? = null,
    val validationMessage: String? = null,
    val isLoadingSummary: Boolean = false,
    val totalCount: Long? = null,
    val summaryError: TimelineError? = null,
)
