package com.yunai.phototube.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.job.Job
import com.yunai.phototube.data.job.JobFilter
import com.yunai.phototube.data.job.JobKind
import com.yunai.phototube.data.job.JobRepository
import com.yunai.phototube.data.job.JobState
import com.yunai.phototube.data.job.JobSummary
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.rethrowCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class JobCenterViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(JobFilter())
    private val mutableUiState = MutableStateFlow(JobCenterUiState())
    private var routeGeneration = 0L
    private var routeActive = false
    private var hasEnteredRoute = false
    private var summaryGeneration = 0L
    private var mutationGeneration = 0L
    private var inFlightMutationTarget: String? = null
    val uiState: StateFlow<JobCenterUiState> = mutableUiState.asStateFlow()
    val jobs: Flow<PagingData<Job>> = filter
        .flatMapLatest(repository::pagedJobs)
        .cachedIn(viewModelScope)

    fun selectState(state: JobState?) {
        if (filter.value.state == state) return
        filter.value = filter.value.copy(state = state)
        mutableUiState.update { it.copy(selectedState = state) }
    }

    fun selectKind(kind: JobKind?) {
        val target = kind.takeUnless { filter.value.kind == kind }
        filter.value = filter.value.copy(kind = target)
        mutableUiState.update { it.copy(selectedKind = target) }
    }

    fun refreshSummary() {
        if (!routeActive) return
        summaryGeneration += 1
        val token = JobSummaryRequestToken(
            routeGeneration = routeGeneration,
            requestGeneration = summaryGeneration,
        )
        mutableUiState.update { it.copy(isLoadingSummary = true, summaryError = null) }
        viewModelScope.launch {
            runCatching { repository.getSummary() }
                .onSuccess { response ->
                    if (!token.matches(routeGeneration, summaryGeneration) || !routeActive) return@onSuccess
                    mutableUiState.update {
                        it.copy(isLoadingSummary = false, summaries = response.items, summaryError = null)
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.matches(routeGeneration, summaryGeneration) || !routeActive) return@onFailure
                    mutableUiState.update {
                        it.copy(isLoadingSummary = false, summaryError = failure.toTimelineError())
                    }
                }
        }
    }

    fun setQueuePaused(summary: JobSummary, paused: Boolean) {
        if (!summary.kind.canControl || inFlightMutationTarget != null) return
        mutate("queue-${summary.kind.name}") {
            if (paused) repository.pauseQueue(summary.kind) else repository.resumeQueue(summary.kind)
        }
    }

    fun cancel(job: Job) {
        if (!job.canCancel || inFlightMutationTarget != null) return
        mutate("job-${job.id}") { repository.cancel(job) }
    }

    private fun mutate(target: String, operation: suspend () -> Unit) {
        if (inFlightMutationTarget != null) return
        mutationGeneration += 1
        inFlightMutationTarget = target
        val token = JobMutationRequestToken(
            requestGeneration = mutationGeneration,
            target = target,
        )
        mutableUiState.update { it.copy(busyTarget = target, actionError = null) }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess {
                    if (!token.matches(mutationGeneration, inFlightMutationTarget)) return@onSuccess
                    inFlightMutationTarget = null
                    if (!routeActive) return@onSuccess
                    mutableUiState.update {
                        it.copy(
                            busyTarget = null,
                            refreshRevision = it.refreshRevision + 1,
                            actionError = null,
                        )
                    }
                    refreshSummary()
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.matches(mutationGeneration, inFlightMutationTarget)) return@onFailure
                    inFlightMutationTarget = null
                    if (!routeActive) return@onFailure
                    mutableUiState.update {
                        it.copy(busyTarget = null, actionError = failure.toTimelineError())
                    }
                }
        }
    }

    fun onRouteEntered() {
        val shouldRefreshPaging = hasEnteredRoute
        hasEnteredRoute = true
        routeActive = true
        routeGeneration += 1
        mutableUiState.update {
            it.copy(
                busyTarget = inFlightMutationTarget,
                actionError = null,
                summaryError = null,
                refreshRevision = it.refreshRevision + if (shouldRefreshPaging) 1 else 0,
            )
        }
        refreshSummary()
    }

    fun onRouteLeft() {
        routeActive = false
        routeGeneration += 1
        summaryGeneration += 1
        mutableUiState.update {
            it.copy(
                isLoadingSummary = false,
                summaryError = null,
                actionError = null,
                busyTarget = null,
            )
        }
    }

    companion object {
        fun factory(repository: JobRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { JobCenterViewModel(repository) }
        }
    }
}

data class JobCenterUiState(
    val summaries: List<JobSummary> = emptyList(),
    val isLoadingSummary: Boolean = true,
    val summaryError: TimelineError? = null,
    val actionError: TimelineError? = null,
    val selectedState: JobState? = null,
    val selectedKind: JobKind? = null,
    val busyTarget: String? = null,
    val refreshRevision: Int = 0,
)

internal data class JobSummaryRequestToken(
    val routeGeneration: Long,
    val requestGeneration: Long,
) {
    fun matches(
        currentRouteGeneration: Long,
        currentRequestGeneration: Long,
    ): Boolean = routeGeneration == currentRouteGeneration && requestGeneration == currentRequestGeneration
}

internal data class JobMutationRequestToken(
    val requestGeneration: Long,
    val target: String,
) {
    fun matches(
        currentRequestGeneration: Long,
        currentTarget: String?,
    ): Boolean = requestGeneration == currentRequestGeneration && target == currentTarget
}
