package com.yunai.phototube.ui.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.duplicate.DuplicateGroup
import com.yunai.phototube.data.duplicate.DuplicateQuery
import com.yunai.phototube.data.duplicate.DuplicateRepository
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.rethrowCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateCenterViewModel(
    private val repository: DuplicateRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val query = MutableStateFlow(DuplicateQuery())
    private val privateAccessGranted = MutableStateFlow(false)
    private val selectedGroup = MutableStateFlow<DuplicateGroup?>(null)
    private val mutableUiState = MutableStateFlow(DuplicateCenterUiState())
    private var routeGeneration = 0L
    private var scopeGeneration = 0L
    private var selectionGeneration = 0L
    private var privateCheckGeneration = 0L
    private var privateAuthGeneration = 0L
    private var reviewGeneration = 0L
    val uiState: StateFlow<DuplicateCenterUiState> = mutableUiState.asStateFlow()
    val serverRoot: ServerRoot = repository.serverRoot()

    val groups: Flow<PagingData<DuplicateGroup>> = combine(query, privateAccessGranted) { current, granted ->
        current to granted
    }.flatMapLatest { (current, granted) ->
        if (current.privateScope && !granted) flowOf(PagingData.empty())
        else repository.pagedGroups(current)
    }.cachedIn(viewModelScope)

    val members: Flow<PagingData<MediaAsset>> = combine(selectedGroup, query, privateAccessGranted) { group, current, granted ->
        Triple(group, current, granted)
    }.flatMapLatest { (group, current, granted) ->
        if (group == null || (current.privateScope && !granted)) flowOf(PagingData.empty())
        else repository.pagedAssets(group.contentHash, current.privateScope)
    }.cachedIn(viewModelScope)

    fun setPrivateScope(privateScope: Boolean) {
        if (query.value.privateScope == privateScope) return
        scopeGeneration += 1
        selectionGeneration += 1
        privateCheckGeneration += 1
        selectedGroup.value = null
        privateAccessGranted.value = false
        query.value = query.value.copy(privateScope = privateScope)
        mutableUiState.update {
            it.copy(
                privateScope = privateScope,
                selectedGroup = null,
                error = null,
                isCheckingPrivateAccess = privateScope,
                isPrivateUnlocked = false,
                isUnlocking = false,
                isLocking = false,
                password = "",
                privateAccessExpiresAt = null,
                busyHash = null,
            )
        }
        if (privateScope) checkPrivateAccess()
    }

    fun setIncludeReviewed(includeReviewed: Boolean) {
        if (query.value.includeReviewed == includeReviewed) return
        selectionGeneration += 1
        selectedGroup.value = null
        query.value = query.value.copy(includeReviewed = includeReviewed)
        mutableUiState.update {
            it.copy(includeReviewed = includeReviewed, selectedGroup = null, error = null)
        }
    }

    fun openGroup(group: DuplicateGroup) {
        selectionGeneration += 1
        selectedGroup.value = group
        mutableUiState.update { it.copy(selectedGroup = group, error = null) }
    }

    fun closeGroup() {
        selectionGeneration += 1
        selectedGroup.value = null
        mutableUiState.update { it.copy(selectedGroup = null, error = null) }
    }

    fun updatePassword(password: String) {
        mutableUiState.update { it.copy(password = password.take(1024)) }
    }

    fun unlockPrivateAccess() {
        val password = mutableUiState.value.password
        if (!query.value.privateScope || password.isEmpty() || mutableUiState.value.isUnlocking) return
        privateAuthGeneration += 1
        privateCheckGeneration += 1
        val token = DuplicatePrivateMutationToken(
            scope = currentScopeToken(),
            authGeneration = privateAuthGeneration,
        )
        mutableUiState.update { it.copy(isUnlocking = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.unlockPrivateAccess(password) }
                .onSuccess { session ->
                    if (!token.isLatest(privateAuthGeneration)) return@onSuccess
                    privateAuthGeneration += 1
                    privateCheckGeneration += 1
                    if (token.scope.matches(routeGeneration, scopeGeneration, query.value.privateScope)) {
                        privateAccessGranted.value = true
                        mutableUiState.update {
                            it.copy(
                                isCheckingPrivateAccess = false,
                                isPrivateUnlocked = true,
                                isUnlocking = false,
                                password = "",
                                privateAccessExpiresAt = session.privateAccessExpiresAt,
                                error = null,
                            )
                        }
                    } else if (query.value.privateScope) {
                        checkPrivateAccess()
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.isLatest(privateAuthGeneration)) return@onFailure
                    privateAuthGeneration += 1
                    privateCheckGeneration += 1
                    if (token.scope.matches(routeGeneration, scopeGeneration, query.value.privateScope)) {
                        mutableUiState.update {
                            it.copy(
                                isCheckingPrivateAccess = false,
                                isUnlocking = false,
                                error = failure.toTimelineError(),
                            )
                        }
                    }
                }
        }
    }

    fun lockPrivateAccess() {
        if (!query.value.privateScope || mutableUiState.value.isLocking) return
        privateAuthGeneration += 1
        privateCheckGeneration += 1
        val token = DuplicatePrivateMutationToken(
            scope = currentScopeToken(),
            authGeneration = privateAuthGeneration,
        )
        mutableUiState.update { it.copy(isLocking = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.lockPrivateAccess() }
                .onSuccess {
                    if (!token.isLatest(privateAuthGeneration)) return@onSuccess
                    privateAuthGeneration += 1
                    privateCheckGeneration += 1
                    privateAccessGranted.value = false
                    selectionGeneration += 1
                    selectedGroup.value = null
                    if (query.value.privateScope) {
                        scopeGeneration += 1
                        query.value = query.value.copy(privateScope = false)
                        mutableUiState.update {
                            it.copy(
                                privateScope = false,
                                isCheckingPrivateAccess = false,
                                isPrivateUnlocked = false,
                                isUnlocking = false,
                                isLocking = false,
                                password = "",
                                privateAccessExpiresAt = null,
                                selectedGroup = null,
                                busyHash = null,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.isLatest(privateAuthGeneration)) return@onFailure
                    privateAuthGeneration += 1
                    privateCheckGeneration += 1
                    if (token.scope.matches(routeGeneration, scopeGeneration, query.value.privateScope)) {
                        mutableUiState.update { it.copy(isLocking = false, error = failure.toTimelineError()) }
                    }
                }
        }
    }

    fun review(group: DuplicateGroup) {
        if (mutableUiState.value.busyHash != null) return
        val privateScope = query.value.privateScope
        reviewGeneration += 1
        val token = DuplicateReviewRequestToken(
            scope = currentScopeToken(),
            requestGeneration = reviewGeneration,
            contentHash = group.contentHash,
            selectionGeneration = selectionGeneration,
        )
        mutableUiState.update { it.copy(busyHash = group.contentHash, error = null) }
        viewModelScope.launch {
            runCatching {
                if (group.reviewedAt == null) repository.review(group.contentHash, privateScope)
                else repository.reopen(group.contentHash, privateScope)
            }.onSuccess {
                if (!token.matchesScope(
                        currentRouteGeneration = routeGeneration,
                        currentScopeGeneration = scopeGeneration,
                        currentPrivateScope = query.value.privateScope,
                        currentRequestGeneration = reviewGeneration,
                    )
                ) return@onSuccess
                mutableUiState.update {
                    it.copy(busyHash = null, refreshRevision = it.refreshRevision + 1, error = null)
                }
                if (token.ownsSelection(selectionGeneration, selectedGroup.value?.contentHash)) {
                    selectionGeneration += 1
                    selectedGroup.value = null
                    mutableUiState.update { it.copy(selectedGroup = null) }
                }
            }.onFailure { failure ->
                failure.rethrowCancellation()
                if (!token.matchesScope(
                        currentRouteGeneration = routeGeneration,
                        currentScopeGeneration = scopeGeneration,
                        currentPrivateScope = query.value.privateScope,
                        currentRequestGeneration = reviewGeneration,
                    )
                ) return@onFailure
                if (failure.isPrivateAccessRequired()) expirePrivateAccess()
                else mutableUiState.update { it.copy(busyHash = null, error = failure.toTimelineError()) }
            }
        }
    }

    fun expirePrivateAccess() {
        if (!query.value.privateScope) return
        privateAuthGeneration += 1
        privateCheckGeneration += 1
        selectionGeneration += 1
        privateAccessGranted.value = false
        selectedGroup.value = null
        mutableUiState.update {
            it.copy(
                isPrivateUnlocked = false,
                isCheckingPrivateAccess = false,
                privateAccessExpiresAt = null,
                selectedGroup = null,
                busyHash = null,
                error = null,
            )
        }
    }

    fun onRouteLeft() {
        routeGeneration += 1
        scopeGeneration += 1
        selectionGeneration += 1
        privateCheckGeneration += 1
        reviewGeneration += 1
        privateAccessGranted.value = false
        selectedGroup.value = null
        query.value = DuplicateQuery()
        mutableUiState.value = DuplicateCenterUiState(
            refreshRevision = mutableUiState.value.refreshRevision + 1,
        )
    }

    private fun checkPrivateAccess() {
        if (!query.value.privateScope) return
        privateCheckGeneration += 1
        val token = DuplicatePrivateCheckToken(
            scope = currentScopeToken(),
            requestGeneration = privateCheckGeneration,
            authGeneration = privateAuthGeneration,
        )
        viewModelScope.launch {
            runCatching { sessionRepository.getSessionInfo() }
                .onSuccess { session ->
                    if (!token.matches(
                            currentRouteGeneration = routeGeneration,
                            currentScopeGeneration = scopeGeneration,
                            currentPrivateScope = query.value.privateScope,
                            currentRequestGeneration = privateCheckGeneration,
                            currentAuthGeneration = privateAuthGeneration,
                        )
                    ) return@onSuccess
                    privateAccessGranted.value = session.privateAccessUnlocked
                    mutableUiState.update {
                        it.copy(
                            isCheckingPrivateAccess = false,
                            isPrivateUnlocked = session.privateAccessUnlocked,
                            privateAccessExpiresAt = session.privateAccessExpiresAt,
                            error = null,
                        )
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!token.matches(
                            currentRouteGeneration = routeGeneration,
                            currentScopeGeneration = scopeGeneration,
                            currentPrivateScope = query.value.privateScope,
                            currentRequestGeneration = privateCheckGeneration,
                            currentAuthGeneration = privateAuthGeneration,
                        )
                    ) return@onFailure
                    mutableUiState.update {
                        it.copy(isCheckingPrivateAccess = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    private fun currentScopeToken() = DuplicateScopeToken(
        routeGeneration = routeGeneration,
        scopeGeneration = scopeGeneration,
        privateScope = query.value.privateScope,
    )

    private fun Throwable.isPrivateAccessRequired(): Boolean =
        this is ApiFailure && error.code == "PRIVATE_ACCESS_REQUIRED"

    companion object {
        fun factory(
            repository: DuplicateRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { DuplicateCenterViewModel(repository, sessionRepository) }
        }
    }
}

data class DuplicateCenterUiState(
    val privateScope: Boolean = false,
    val includeReviewed: Boolean = false,
    val isCheckingPrivateAccess: Boolean = false,
    val isPrivateUnlocked: Boolean = false,
    val isUnlocking: Boolean = false,
    val isLocking: Boolean = false,
    val password: String = "",
    val privateAccessExpiresAt: String? = null,
    val selectedGroup: DuplicateGroup? = null,
    val busyHash: String? = null,
    val refreshRevision: Int = 0,
    val error: TimelineError? = null,
)

internal data class DuplicateScopeToken(
    val routeGeneration: Long,
    val scopeGeneration: Long,
    val privateScope: Boolean,
) {
    fun matches(
        currentRouteGeneration: Long,
        currentScopeGeneration: Long,
        currentPrivateScope: Boolean,
    ): Boolean = routeGeneration == currentRouteGeneration &&
        scopeGeneration == currentScopeGeneration &&
        privateScope == currentPrivateScope
}

internal data class DuplicatePrivateCheckToken(
    val scope: DuplicateScopeToken,
    val requestGeneration: Long,
    val authGeneration: Long,
) {
    fun matches(
        currentRouteGeneration: Long,
        currentScopeGeneration: Long,
        currentPrivateScope: Boolean,
        currentRequestGeneration: Long,
        currentAuthGeneration: Long,
    ): Boolean = scope.matches(
        currentRouteGeneration,
        currentScopeGeneration,
        currentPrivateScope,
    ) && requestGeneration == currentRequestGeneration && authGeneration == currentAuthGeneration
}

internal data class DuplicatePrivateMutationToken(
    val scope: DuplicateScopeToken,
    val authGeneration: Long,
) {
    fun isLatest(currentAuthGeneration: Long): Boolean = authGeneration == currentAuthGeneration
}

internal data class DuplicateReviewRequestToken(
    val scope: DuplicateScopeToken,
    val requestGeneration: Long,
    val contentHash: String,
    val selectionGeneration: Long,
) {
    fun matchesScope(
        currentRouteGeneration: Long,
        currentScopeGeneration: Long,
        currentPrivateScope: Boolean,
        currentRequestGeneration: Long,
    ): Boolean = scope.matches(
        currentRouteGeneration,
        currentScopeGeneration,
        currentPrivateScope,
    ) && requestGeneration == currentRequestGeneration

    fun ownsSelection(
        currentSelectionGeneration: Long,
        currentContentHash: String?,
    ): Boolean = selectionGeneration == currentSelectionGeneration && contentHash == currentContentHash
}
