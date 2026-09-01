package com.yunai.phototube.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.session.PrivateAccessGateway
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineRepository
import com.yunai.phototube.data.timeline.TimelineGateway
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.rethrowCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryCollectionMode { ARCHIVED, PRIVATE }

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryAssetsViewModel(
    private val mode: LibraryCollectionMode,
    private val timelineRepository: TimelineGateway,
    private val sessionRepository: PrivateAccessGateway,
) : ViewModel() {
    private val accessGranted = MutableStateFlow(mode == LibraryCollectionMode.ARCHIVED)
    private val mutableUiState = MutableStateFlow(
        LibraryAssetsUiState(isCheckingAccess = mode == LibraryCollectionMode.PRIVATE),
    )
    val uiState: StateFlow<LibraryAssetsUiState> = mutableUiState.asStateFlow()
    val serverRoot: ServerRoot = timelineRepository.serverRoot()
    private var routeActive = false
    private var routeGeneration = 0L
    private var privateCheckGeneration = 0L
    private var privateAuthGeneration = 0L
    val assets: Flow<PagingData<MediaAsset>> = accessGranted
        .flatMapLatest { granted ->
            if (!granted) {
                flowOf(PagingData.empty())
            } else {
                timelineRepository.pagedAssets(
                    when (mode) {
                        LibraryCollectionMode.ARCHIVED -> AssetFilter(archived = true)
                        LibraryCollectionMode.PRIVATE -> AssetFilter(private = true)
                    },
                )
            }
        }
        .cachedIn(viewModelScope)

    fun onRouteEntered() {
        if (mode != LibraryCollectionMode.PRIVATE || routeActive) return
        routeActive = true
        routeGeneration += 1
        privateCheckGeneration += 1
        privateAuthGeneration += 1
        accessGranted.value = false
        mutableUiState.value = LibraryAssetsUiState(isCheckingAccess = true)
        checkPrivateAccess()
    }

    fun onRouteLeft() {
        if (mode != LibraryCollectionMode.PRIVATE || !routeActive) return
        routeActive = false
        routeGeneration += 1
        privateCheckGeneration += 1
        privateAuthGeneration += 1
        accessGranted.value = false
        mutableUiState.value = LibraryAssetsUiState()
    }

    fun updatePassword(password: String) {
        mutableUiState.update { it.copy(password = password.take(1024)) }
    }

    fun unlock() {
        if (mode != LibraryCollectionMode.PRIVATE || !routeActive) return
        val password = mutableUiState.value.password
        if (password.isEmpty() || mutableUiState.value.isUnlocking) return
        privateAuthGeneration += 1
        privateCheckGeneration += 1
        val token = LibraryPrivateAuthToken(routeGeneration, privateAuthGeneration)
        mutableUiState.update { it.copy(isUnlocking = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.unlockPrivateAccess(password) }
                .mapCatching { session ->
                    check(session.privateAccessUnlocked) { "PhotoTube 没有确认私密访问已解锁" }
                    session
                }
                .onSuccess { session ->
                    if (!token.matchesCurrent()) return@onSuccess
                    accessGranted.value = true
                    mutableUiState.update {
                        it.copy(
                            isCheckingAccess = false,
                            isUnlocked = true,
                            isUnlocking = false,
                            password = "",
                            expiresAt = session.privateAccessExpiresAt,
                            error = null,
                        )
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.matchesCurrent()) return@onFailure
                    mutableUiState.update {
                        it.copy(isCheckingAccess = false, isUnlocking = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    fun lock() {
        if (mode != LibraryCollectionMode.PRIVATE || !routeActive || mutableUiState.value.isLocking) return
        privateAuthGeneration += 1
        privateCheckGeneration += 1
        val token = LibraryPrivateAuthToken(routeGeneration, privateAuthGeneration)
        accessGranted.value = false
        mutableUiState.update {
            it.copy(
                isUnlocked = false,
                isUnlocking = false,
                isLocking = true,
                password = "",
                expiresAt = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { sessionRepository.lockPrivateAccess() }
                .onSuccess {
                    if (!token.matchesCurrent()) return@onSuccess
                    mutableUiState.update {
                        it.copy(isLocking = false, closeRequested = true)
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.matchesCurrent()) return@onFailure
                    mutableUiState.update { it.copy(isLocking = false, error = failure.toTimelineError()) }
                }
        }
    }

    fun expirePrivateAccess() {
        if (mode != LibraryCollectionMode.PRIVATE) return
        privateAuthGeneration += 1
        privateCheckGeneration += 1
        accessGranted.value = false
        mutableUiState.update {
            it.copy(
                isCheckingAccess = false,
                isUnlocked = false,
                isUnlocking = false,
                isLocking = false,
                password = "",
                expiresAt = null,
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { sessionRepository.lockPrivateAccess() }
                .exceptionOrNull()
                ?.rethrowCancellation()
        }
    }

    fun consumeCloseRequest() {
        mutableUiState.update { it.copy(closeRequested = false) }
    }

    private fun checkPrivateAccess() {
        if (!routeActive || mode != LibraryCollectionMode.PRIVATE) return
        privateCheckGeneration += 1
        val token = LibraryPrivateCheckToken(
            routeGeneration = routeGeneration,
            requestGeneration = privateCheckGeneration,
            authGeneration = privateAuthGeneration,
        )
        viewModelScope.launch {
            runCatching { sessionRepository.getSessionInfo() }
                .onSuccess { session ->
                    if (!token.matchesCurrent()) return@onSuccess
                    accessGranted.value = session.privateAccessUnlocked
                    mutableUiState.update {
                        it.copy(
                            isCheckingAccess = false,
                            isUnlocked = session.privateAccessUnlocked,
                            expiresAt = session.privateAccessExpiresAt,
                            error = null,
                        )
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.matchesCurrent()) return@onFailure
                    mutableUiState.update {
                        it.copy(isCheckingAccess = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    private fun LibraryPrivateAuthToken.matchesCurrent(): Boolean = matches(
        currentRouteActive = routeActive,
        currentRouteGeneration = routeGeneration,
        currentAuthGeneration = privateAuthGeneration,
    )

    private fun LibraryPrivateCheckToken.matchesCurrent(): Boolean = matches(
        currentRouteActive = routeActive,
        currentRouteGeneration = routeGeneration,
        currentRequestGeneration = privateCheckGeneration,
        currentAuthGeneration = privateAuthGeneration,
    )

    companion object {
        fun factory(
            mode: LibraryCollectionMode,
            timelineRepository: TimelineRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryAssetsViewModel(mode, timelineRepository, sessionRepository) }
        }
    }
}

internal data class LibraryPrivateAuthToken(
    val routeGeneration: Long,
    val authGeneration: Long,
) {
    fun matches(
        currentRouteActive: Boolean,
        currentRouteGeneration: Long,
        currentAuthGeneration: Long,
    ): Boolean = currentRouteActive &&
        routeGeneration == currentRouteGeneration &&
        authGeneration == currentAuthGeneration
}

internal data class LibraryPrivateCheckToken(
    val routeGeneration: Long,
    val requestGeneration: Long,
    val authGeneration: Long,
) {
    fun matches(
        currentRouteActive: Boolean,
        currentRouteGeneration: Long,
        currentRequestGeneration: Long,
        currentAuthGeneration: Long,
    ): Boolean = currentRouteActive &&
        routeGeneration == currentRouteGeneration &&
        requestGeneration == currentRequestGeneration &&
        authGeneration == currentAuthGeneration
}

data class LibraryAssetsUiState(
    val isCheckingAccess: Boolean = false,
    val isUnlocked: Boolean = false,
    val isUnlocking: Boolean = false,
    val isLocking: Boolean = false,
    val password: String = "",
    val expiresAt: String? = null,
    val error: TimelineError? = null,
    val closeRequested: Boolean = false,
)
