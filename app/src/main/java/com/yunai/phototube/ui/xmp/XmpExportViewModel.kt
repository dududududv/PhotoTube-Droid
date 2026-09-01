package com.yunai.phototube.ui.xmp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.xmp.XmpExportPreview
import com.yunai.phototube.data.xmp.XmpExportRepository
import com.yunai.phototube.data.xmp.XmpExportRequest
import com.yunai.phototube.data.xmp.XmpExportRun
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class XmpExportViewModel(
    private val repository: XmpExportRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val refreshSequence = AtomicLong(0L)
    private var routeGeneration = 0L
    private var environmentRequestGeneration = 0L
    private var previewRequestGeneration = 0L
    private var createRequestGeneration = 0L
    private var runRequestGeneration = 0L
    private var unlockRequestGeneration = 0L
    private var requestAfterUnlock: XmpExportRequest? = null
    private var enablePrivateAfterUnlock = false
    private val mutableUiState = MutableStateFlow(XmpExportUiState())
    val uiState: StateFlow<XmpExportUiState> = mutableUiState.asStateFlow()
    val runs: Flow<PagingData<XmpExportRun>> = repository.pagedRuns().cachedIn(viewModelScope)

    fun refreshEnvironment() {
        val request = XmpRequestToken(routeGeneration, ++environmentRequestGeneration)
        mutableUiState.update { it.copy(capability = XmpCapability.Loading, error = null) }
        viewModelScope.launch {
            runCatching {
                val available = repository.isAvailable()
                val session = if (available) sessionRepository.getSessionInfo() else null
                available to session
            }.onSuccess { (available, session) ->
                if (!request.matches(routeGeneration, environmentRequestGeneration)) return@onSuccess
                mutableUiState.update {
                    it.copy(
                        capability = if (available) XmpCapability.Available else XmpCapability.Unavailable,
                        privateUnlocked = session?.privateAccessUnlocked == true,
                        error = null,
                    )
                }
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                if (!request.matches(routeGeneration, environmentRequestGeneration)) return@onFailure
                mutableUiState.update {
                    it.copy(capability = XmpCapability.Error, error = failure.toTimelineError())
                }
            }
        }
    }

    fun setIncludePrivate(includePrivate: Boolean) {
        val state = mutableUiState.value
        if (includePrivate && !state.privateUnlocked) {
            enablePrivateAfterUnlock = true
            mutableUiState.update {
                it.copy(showPrivateUnlock = true, preview = null, error = null)
            }
            return
        }
        mutableUiState.update {
            it.copy(includePrivate = includePrivate, preview = null, message = null, error = null)
        }
    }

    fun previewAll() = preview(
        XmpExportRequest(
            libraryIds = null,
            includePrivate = mutableUiState.value.includePrivate,
        ),
    )

    fun previewAgain(run: XmpExportRun) {
        val request = XmpExportRequest(
            libraryIds = run.libraryIds,
            includePrivate = run.includePrivate,
        )
        if (run.includePrivate && !mutableUiState.value.privateUnlocked) {
            requestAfterUnlock = request
            mutableUiState.update {
                it.copy(showPrivateUnlock = true, preview = null, error = null)
            }
            return
        }
        mutableUiState.update { it.copy(includePrivate = run.includePrivate) }
        preview(request)
    }

    fun cancelPreview() {
        previewRequestGeneration += 1
        mutableUiState.update {
            it.copy(preview = null, isPreviewing = false, message = null, error = null)
        }
    }

    fun createExport() {
        val preview = mutableUiState.value.preview ?: return
        if (preview.includePrivate && !mutableUiState.value.privateUnlocked) {
            invalidatePrivateScope("当前私密授权已失效，请重新解锁后再次估算")
            return
        }
        val request = runCatching(preview::creationRequest).getOrElse { failure ->
            mutableUiState.update { it.copy(error = failure.toTimelineError()) }
            return
        }
        if (mutableUiState.value.isCreating) return
        val operation = XmpRequestToken(routeGeneration, ++createRequestGeneration)
        mutableUiState.update { it.copy(isCreating = true, error = null, message = "正在创建快照任务…") }
        viewModelScope.launch {
            runCatching { repository.create(request) }
                .onSuccess { run ->
                    if (!operation.matches(routeGeneration, createRequestGeneration)) return@onSuccess
                    mutableUiState.update {
                        it.copy(
                            isCreating = false,
                            preview = null,
                            message = "照片信息备份已开始 · ${run.id.take(8)}",
                            refreshRevision = refreshSequence.incrementAndGet(),
                            selectedRun = run,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!operation.matches(routeGeneration, createRequestGeneration)) return@onFailure
                    handlePrivateAwareFailure(failure)
                }
        }
    }

    fun openRun(runId: String) {
        if (mutableUiState.value.isLoadingRun) return
        val request = XmpRequestToken(routeGeneration, ++runRequestGeneration)
        mutableUiState.update { it.copy(isLoadingRun = true, runError = null) }
        viewModelScope.launch {
            runCatching { repository.getRun(runId) }
                .onSuccess { run ->
                    if (!request.matches(routeGeneration, runRequestGeneration)) return@onSuccess
                    mutableUiState.update {
                        it.copy(isLoadingRun = false, selectedRun = run, runError = null)
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!request.matches(routeGeneration, runRequestGeneration)) return@onFailure
                    mutableUiState.update {
                        it.copy(isLoadingRun = false, runError = failure.toTimelineError())
                    }
                }
        }
    }

    fun refreshSelectedRun() {
        val run = mutableUiState.value.selectedRun ?: return
        if (run.state.isTerminal || mutableUiState.value.isLoadingRun) return
        openRun(run.id)
    }

    fun closeRun() {
        runRequestGeneration += 1
        mutableUiState.update { it.copy(selectedRun = null, runError = null, isLoadingRun = false) }
    }

    fun updatePrivatePassword(password: String) {
        mutableUiState.update { it.copy(privatePassword = password.take(1024), error = null) }
    }

    fun unlockPrivateAccess() {
        val password = mutableUiState.value.privatePassword
        if (password.isEmpty() || mutableUiState.value.isUnlockingPrivate) return
        val request = XmpRequestToken(routeGeneration, ++unlockRequestGeneration)
        mutableUiState.update { it.copy(isUnlockingPrivate = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.unlockPrivateAccess(password) }
                .onSuccess {
                    if (!request.matches(routeGeneration, unlockRequestGeneration)) return@onSuccess
                    val pendingRequest = requestAfterUnlock
                    requestAfterUnlock = null
                    val shouldEnable = enablePrivateAfterUnlock
                    enablePrivateAfterUnlock = false
                    mutableUiState.update {
                        it.copy(
                            privateUnlocked = true,
                            includePrivate = shouldEnable || pendingRequest?.includePrivate == true || it.includePrivate,
                            showPrivateUnlock = false,
                            privatePassword = "",
                            isUnlockingPrivate = false,
                            error = null,
                        )
                    }
                    pendingRequest?.let(::preview)
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!request.matches(routeGeneration, unlockRequestGeneration)) return@onFailure
                    mutableUiState.update {
                        it.copy(isUnlockingPrivate = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    fun dismissPrivateUnlock() {
        unlockRequestGeneration += 1
        requestAfterUnlock = null
        enablePrivateAfterUnlock = false
        mutableUiState.update {
            it.copy(showPrivateUnlock = false, privatePassword = "", isUnlockingPrivate = false)
        }
    }

    fun onRouteLeft() {
        routeGeneration += 1
        requestAfterUnlock = null
        enablePrivateAfterUnlock = false
        mutableUiState.update {
            it.copy(
                privateUnlocked = false,
                includePrivate = false,
                preview = null,
                selectedRun = null,
                showPrivateUnlock = false,
                privatePassword = "",
                isUnlockingPrivate = false,
                isPreviewing = false,
                isCreating = false,
                isLoadingRun = false,
                runError = null,
                message = null,
                error = null,
            )
        }
    }

    private fun preview(request: XmpExportRequest) {
        if (request.includePrivate && !mutableUiState.value.privateUnlocked) {
            requestAfterUnlock = request
            mutableUiState.update { it.copy(showPrivateUnlock = true, preview = null) }
            return
        }
        if (mutableUiState.value.isPreviewing || mutableUiState.value.isCreating) return
        val operation = XmpRequestToken(routeGeneration, ++previewRequestGeneration)
        mutableUiState.update {
            it.copy(
                includePrivate = request.includePrivate,
                isPreviewing = true,
                preview = null,
                message = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.preview(request) }
                .onSuccess { preview ->
                    if (!operation.matches(routeGeneration, previewRequestGeneration)) return@onSuccess
                    mutableUiState.update {
                        it.copy(isPreviewing = false, preview = preview, error = null)
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!operation.matches(routeGeneration, previewRequestGeneration)) return@onFailure
                    handlePrivateAwareFailure(failure)
                }
        }
    }

    private fun handlePrivateAwareFailure(failure: Throwable) {
        if (failure is CancellationException) throw failure
        if (failure is ApiFailure && failure.error.code == "PRIVATE_ACCESS_REQUIRED") {
            invalidatePrivateScope("私密授权已失效，请重新解锁后再次估算")
            return
        }
        mutableUiState.update {
            it.copy(
                isPreviewing = false,
                isCreating = false,
                error = failure.toTimelineError(),
                message = null,
            )
        }
    }

    private fun invalidatePrivateScope(message: String) {
        requestAfterUnlock = null
        enablePrivateAfterUnlock = false
        mutableUiState.update {
            it.copy(
                privateUnlocked = false,
                includePrivate = false,
                preview = null,
                isPreviewing = false,
                isCreating = false,
                showPrivateUnlock = true,
                message = message,
                error = null,
            )
        }
    }

    companion object {
        fun factory(
            repository: XmpExportRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { XmpExportViewModel(repository, sessionRepository) }
        }
    }
}

data class XmpExportUiState(
    val capability: XmpCapability = XmpCapability.Loading,
    val privateUnlocked: Boolean = false,
    val includePrivate: Boolean = false,
    val preview: XmpExportPreview? = null,
    val isPreviewing: Boolean = false,
    val isCreating: Boolean = false,
    val message: String? = null,
    val error: TimelineError? = null,
    val selectedRun: XmpExportRun? = null,
    val isLoadingRun: Boolean = false,
    val runError: TimelineError? = null,
    val refreshRevision: Long = 0L,
    val showPrivateUnlock: Boolean = false,
    val privatePassword: String = "",
    val isUnlockingPrivate: Boolean = false,
) {
    val privateScopeVisible: Boolean
        get() = includePrivate || preview?.includePrivate == true || selectedRun?.includePrivate == true
}

enum class XmpCapability { Loading, Available, Unavailable, Error }

internal data class XmpRequestToken(
    val routeGeneration: Long,
    val requestGeneration: Long,
) {
    fun matches(currentRouteGeneration: Long, currentRequestGeneration: Long): Boolean =
        routeGeneration == currentRouteGeneration && requestGeneration == currentRequestGeneration
}
