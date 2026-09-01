package com.yunai.phototube.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.memory.MemoryExclusion
import com.yunai.phototube.data.memory.MemoryExclusionGateway
import com.yunai.phototube.data.memory.MemoryExclusionRepository
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MemoryExclusionViewModel(
    private val repository: MemoryExclusionGateway,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MemoryExclusionUiState())
    private val mutableChanges = Channel<Unit>(capacity = Channel.BUFFERED)
    private var draftRevision = 0L

    val state: StateFlow<MemoryExclusionUiState> = mutableState.asStateFlow()
    val changes: Flow<Unit> = mutableChanges.receiveAsFlow()
    val exclusions: Flow<PagingData<MemoryExclusion>> = repository
        .pagedExclusions()
        .cachedIn(viewModelScope)

    fun setDateFrom(value: LocalDate?) {
        draftRevision += 1
        mutableState.update { it.copy(dateFrom = value, validationMessage = null, actionError = null) }
    }

    fun setDateTo(value: LocalDate?) {
        draftRevision += 1
        mutableState.update { it.copy(dateTo = value, validationMessage = null, actionError = null) }
    }

    fun createDateRange() {
        val snapshot = mutableState.value
        if (snapshot.busyTarget != null) return
        val from = snapshot.dateFrom
        val to = snapshot.dateTo
        val validation = when {
            from == null || to == null -> "请选择开始日期和结束日期"
            from.isAfter(to) -> "开始日期不能晚于结束日期"
            else -> null
        }
        if (validation != null) {
            mutableState.update { it.copy(validationMessage = validation) }
            return
        }
        val submittedDraftRevision = draftRevision
        mutableState.update {
            it.copy(busyTarget = CREATE_TARGET, validationMessage = null, actionError = null)
        }
        viewModelScope.launch {
            try {
                repository.createDateRange(requireNotNull(from), requireNotNull(to))
                mutableState.update {
                    it.copy(
                        dateFrom = if (draftRevision == submittedDraftRevision) null else it.dateFrom,
                        dateTo = if (draftRevision == submittedDraftRevision) null else it.dateTo,
                        busyTarget = null,
                        actionError = null,
                        feedback = "已添加回忆屏蔽",
                    )
                }
                mutableChanges.trySend(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(busyTarget = null, actionError = failure.toTimelineError())
                }
            }
        }
    }

    fun delete(exclusion: MemoryExclusion) {
        if (mutableState.value.busyTarget != null) return
        mutableState.update {
            it.copy(busyTarget = exclusion.id, actionError = null, validationMessage = null)
        }
        viewModelScope.launch {
            try {
                repository.delete(exclusion.id)
                mutableState.update {
                    it.copy(
                        busyTarget = null,
                        actionError = null,
                        feedback = "已删除回忆屏蔽",
                    )
                }
                mutableChanges.trySend(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(busyTarget = null, actionError = failure.toTimelineError())
                }
            }
        }
    }

    fun consumeFeedback() {
        mutableState.update { it.copy(feedback = null) }
    }

    companion object {
        private const val CREATE_TARGET = "create"

        fun factory(repository: MemoryExclusionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemoryExclusionViewModel(repository) }
        }
    }
}

data class MemoryExclusionUiState(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val validationMessage: String? = null,
    val busyTarget: String? = null,
    val actionError: TimelineError? = null,
    val feedback: String? = null,
)
