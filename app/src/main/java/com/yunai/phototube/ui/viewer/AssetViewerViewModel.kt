package com.yunai.phototube.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yunai.phototube.data.asset.AssetRepository
import com.yunai.phototube.data.edit.ActiveEdit
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.remote.isInvalidCursorFailure
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.data.timeline.AssetTag
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.AssetChangeKind
import com.yunai.phototube.ui.rethrowCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssetViewerViewModel(
    private val repository: AssetRepository,
    private val tagRepository: TagRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private var assetId: String? = null
    private var assetGeneration = 0L
    private var privateUnlockGeneration = 0L
    private var contentRevision: Int = -1
    private var tagSearchJob: Job? = null
    private var pendingPrivateTarget: Boolean? = null
    private var retryAfterPrivateUnlock = false
    private val mutableUiState = MutableStateFlow(AssetViewerUiState())
    val uiState: StateFlow<AssetViewerUiState> = mutableUiState.asStateFlow()

    fun open(assetId: String, revision: Int = 0) {
        if (
            this.assetId == assetId &&
            contentRevision == revision &&
            mutableUiState.value.asset != null
        ) return
        val changedAsset = this.assetId != assetId
        assetGeneration += 1
        privateUnlockGeneration += 1
        tagSearchJob?.cancel()
        this.assetId = assetId
        contentRevision = revision
        pendingPrivateTarget = null
        retryAfterPrivateUnlock = false
        if (changedAsset) {
            mutableUiState.value = AssetViewerUiState()
        } else {
            mutableUiState.update {
                it.copy(
                    isSavingFavorite = false,
                    isSavingMutation = false,
                    isLoadingTags = false,
                    isSavingTag = false,
                    showPrivateUnlock = false,
                    privatePassword = "",
                    isUnlockingPrivate = false,
                    error = null,
                )
            }
        }
        refresh()
    }

    fun refresh() {
        val request = currentAssetRequest() ?: return
        mutableUiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.getAsset(request.assetId) }
                .onSuccess { asset ->
                    if (isCurrent(request)) {
                        mutableUiState.update { it.copy(asset = asset, isLoading = false, error = null) }
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (isCurrent(request)) {
                        val needsUnlock = failure is ApiFailure &&
                            failure.error.code == "PRIVATE_ACCESS_REQUIRED"
                        retryAfterPrivateUnlock = needsUnlock
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                showPrivateUnlock = needsUnlock,
                                error = if (needsUnlock) null else failure.toTimelineError(),
                            )
                        }
                    }
                }
        }
    }

    fun setFavorite(favorite: Boolean) {
        val current = mutableUiState.value.asset ?: return
        val request = currentAssetRequest() ?: return
        if (mutableUiState.value.isSavingFavorite || current.favorite == favorite) return
        mutableUiState.update { it.copy(isSavingFavorite = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.setFavorite(request.assetId, favorite) }
                .onSuccess {
                    if (isCurrent(request)) {
                        mutableUiState.update {
                            it.copy(
                                asset = it.asset?.copy(favorite = favorite),
                                isSavingFavorite = false,
                                changes = it.changes + AssetChangeKind.FAVORITE,
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (isCurrent(request)) {
                        mutableUiState.update {
                            it.copy(isSavingFavorite = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    fun setArchived(archived: Boolean) = mutateAsset(
        change = AssetChangeKind.ARCHIVED,
        closeAfterSuccess = true,
        transform = { it.copy(archived = archived) },
    ) { targetAssetId ->
        repository.setArchived(targetAssetId, archived)
    }

    fun setRating(rating: Int?) = mutateAsset(
        change = AssetChangeKind.RATING,
        closeAfterSuccess = false,
        transform = { it.copy(rating = rating) },
    ) { targetAssetId ->
        repository.setRating(targetAssetId, rating)
    }

    fun setPrivate(private: Boolean) {
        val request = currentAssetRequest() ?: return
        if (mutableUiState.value.isSavingMutation) return
        mutableUiState.update { it.copy(isSavingMutation = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.setPrivate(request.assetId, private) }
                .onSuccess {
                    if (isCurrent(request)) {
                        pendingPrivateTarget = null
                        mutableUiState.update {
                            it.copy(
                                asset = it.asset?.copy(private = private),
                                isSavingMutation = false,
                                changes = it.changes + AssetChangeKind.PRIVATE,
                                closeRequested = true,
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!isCurrent(request)) return@onFailure
                    val needsUnlock = failure is ApiFailure &&
                        failure.error.code == "PRIVATE_ACCESS_REQUIRED"
                    if (needsUnlock) pendingPrivateTarget = private
                    mutableUiState.update {
                        it.copy(
                            isSavingMutation = false,
                            showPrivateUnlock = needsUnlock,
                            error = if (needsUnlock) null else failure.toTimelineError(),
                        )
                    }
                }
        }
    }

    fun moveToTrash() = mutateAsset(
        change = AssetChangeKind.TRASHED,
        closeAfterSuccess = true,
        transform = { it.copy(state = AssetState.TRASHED) },
    ) { targetAssetId ->
        repository.moveToTrash(targetAssetId)
    }

    fun restoreFromTrash() = mutateAsset(
        change = AssetChangeKind.RESTORED,
        closeAfterSuccess = true,
    ) { targetAssetId ->
        repository.restoreAsset(targetAssetId)
    }

    fun purgeFromTrash() = mutateAsset(
        change = AssetChangeKind.PURGED,
        closeAfterSuccess = true,
    ) { targetAssetId ->
        repository.purgeAsset(targetAssetId)
    }

    fun updatePrivatePassword(password: String) {
        mutableUiState.update { it.copy(privatePassword = password.take(1024)) }
    }

    fun dismissPrivateUnlock() {
        privateUnlockGeneration += 1
        pendingPrivateTarget = null
        retryAfterPrivateUnlock = false
        mutableUiState.update {
            it.copy(showPrivateUnlock = false, privatePassword = "", error = null)
        }
    }

    fun unlockPrivateAccess() {
        val request = currentAssetRequest() ?: return
        val target = pendingPrivateTarget
        val shouldRetryAsset = retryAfterPrivateUnlock
        if (target == null && !shouldRetryAsset) return
        val password = mutableUiState.value.privatePassword
        if (password.isEmpty() || mutableUiState.value.isUnlockingPrivate) return
        val unlockGeneration = ++privateUnlockGeneration
        mutableUiState.update { it.copy(isUnlockingPrivate = true, error = null) }
        viewModelScope.launch {
            runCatching { sessionRepository.unlockPrivateAccess(password) }
                .onSuccess {
                    if (!isCurrent(request) || privateUnlockGeneration != unlockGeneration) return@onSuccess
                    mutableUiState.update {
                        it.copy(
                            isUnlockingPrivate = false,
                            showPrivateUnlock = false,
                            privatePassword = "",
                        )
                    }
                    retryAfterPrivateUnlock = false
                    if (target != null) setPrivate(target) else refresh()
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!isCurrent(request) || privateUnlockGeneration != unlockGeneration) return@onFailure
                    mutableUiState.update {
                        it.copy(isUnlockingPrivate = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    fun consumeCloseRequest() {
        mutableUiState.update { it.copy(closeRequested = false) }
    }

    fun applyActiveEdit(targetAssetId: String, activeEdit: ActiveEdit?) {
        if (assetId != targetAssetId) return
        mutableUiState.update { state ->
            val asset = state.asset ?: return@update state
            state.copy(
                asset = asset.copy(activeEdit = activeEdit),
                changes = state.changes + AssetChangeKind.EDIT,
                error = null,
            )
        }
    }

    fun searchTags(query: String) {
        val request = currentAssetRequest() ?: return
        val normalized = query.take(120)
        mutableUiState.update {
            it.copy(tagQuery = normalized, availableTags = emptyList(), nextTagCursor = null, isLoadingTags = true)
        }
        tagSearchJob?.cancel()
        tagSearchJob = viewModelScope.launch {
            if (normalized.isNotEmpty()) delay(TAG_SEARCH_DEBOUNCE_MS)
            runCatching { tagRepository.getTags(normalized) }
                .onSuccess { page ->
                    if (isCurrent(request) && mutableUiState.value.tagQuery == normalized) {
                        mutableUiState.update {
                            it.copy(
                                availableTags = page.items,
                                nextTagCursor = page.nextCursor,
                                isLoadingTags = false,
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (isCurrent(request)) applyTagFailure(normalized, failure)
                }
        }
    }

    fun loadMoreTags() {
        val request = currentAssetRequest() ?: return
        val state = mutableUiState.value
        val cursor = state.nextTagCursor ?: return
        if (state.isLoadingTags) return
        mutableUiState.update { it.copy(isLoadingTags = true) }
        viewModelScope.launch {
            runCatching { tagRepository.getTags(state.tagQuery, cursor) }
                .onSuccess { page ->
                    if (isCurrent(request) && mutableUiState.value.tagQuery == state.tagQuery) {
                        if (page.nextCursor == cursor) {
                            mutableUiState.update {
                                it.copy(
                                    nextTagCursor = null,
                                    isLoadingTags = false,
                                    error = TimelineError("标签列表返回了重复 cursor"),
                                )
                            }
                            return@onSuccess
                        }
                        mutableUiState.update {
                            it.copy(
                                availableTags = (it.availableTags + page.items).distinctBy(Tag::id),
                                nextTagCursor = page.nextCursor,
                                isLoadingTags = false,
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (!isCurrent(request)) return@onFailure
                    if (mutableUiState.value.tagQuery != state.tagQuery) return@onFailure
                    if (failure.isInvalidCursorFailure(cursor)) {
                        searchTags(state.tagQuery)
                    } else {
                        applyTagFailure(state.tagQuery, failure)
                    }
                }
        }
    }

    fun addTag(tag: Tag) = mutateTags { targetAssetId ->
        TagMutation.Upsert(tagRepository.add(targetAssetId, tag.id))
    }

    fun createAndAddTag(name: String) = mutateTags { targetAssetId ->
        TagMutation.Upsert(
            tag = tagRepository.createAndAdd(targetAssetId, name),
            refreshCandidates = true,
        )
    }

    fun removeTag(tagId: Long) = mutateTags { targetAssetId ->
        tagRepository.remove(targetAssetId, tagId)
        TagMutation.Remove(tagId)
    }

    private fun mutateAsset(
        change: AssetChangeKind,
        closeAfterSuccess: Boolean,
        transform: (MediaAsset) -> MediaAsset = { it },
        operation: suspend (String) -> Unit,
    ) {
        val request = currentAssetRequest() ?: return
        if (mutableUiState.value.isSavingMutation) return
        mutableUiState.update { it.copy(isSavingMutation = true, error = null) }
        viewModelScope.launch {
            runCatching { operation(request.assetId) }
                .onSuccess {
                    if (isCurrent(request)) {
                        mutableUiState.update { state ->
                            val transformed = state.asset?.let(transform)
                            state.copy(
                                asset = transformed,
                                isSavingMutation = false,
                                changes = state.changes + change,
                                closeRequested = closeAfterSuccess,
                            )
                        }
                    }
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (isCurrent(request)) {
                        mutableUiState.update {
                            it.copy(isSavingMutation = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    private fun mutateTags(operation: suspend (String) -> TagMutation) {
        val request = currentAssetRequest() ?: return
        if (mutableUiState.value.isSavingTag) return
        mutableUiState.update { it.copy(isSavingTag = true, error = null) }
        viewModelScope.launch {
            runCatching { operation(request.assetId) }
                .onSuccess { mutation ->
                    if (!isCurrent(request)) return@onSuccess
                    updateAssetTags { current ->
                        when (mutation) {
                            is TagMutation.Upsert -> (current + mutation.tag).distinctBy(AssetTag::id)
                            is TagMutation.Remove -> current.filterNot { it.id == mutation.tagId }
                        }
                    }
                    mutableUiState.update {
                        it.copy(isSavingTag = false, changes = it.changes + AssetChangeKind.TAGS)
                    }
                    if (mutation is TagMutation.Upsert && mutation.refreshCandidates) searchTags("")
                }
                .onFailure { failure ->
                    failure.rethrowCancellation()
                    if (!isCurrent(request)) return@onFailure
                    mutableUiState.update {
                        it.copy(isSavingTag = false, error = failure.toTimelineError())
                    }
                }
        }
    }

    private fun updateAssetTags(
        transform: (List<com.yunai.phototube.data.timeline.AssetTag>) -> List<com.yunai.phototube.data.timeline.AssetTag>,
    ) {
        mutableUiState.update { state ->
            val asset = state.asset ?: return@update state
            state.copy(asset = asset.copy(tags = transform(asset.tags.orEmpty())))
        }
    }

    private fun applyTagFailure(query: String, failure: Throwable) {
        if (mutableUiState.value.tagQuery == query) {
            mutableUiState.update {
                it.copy(isLoadingTags = false, error = failure.toTimelineError())
            }
        }
    }

    private fun currentAssetRequest(): AssetRequestToken? = assetId?.let {
        AssetRequestToken(assetId = it, generation = assetGeneration)
    }

    private fun isCurrent(request: AssetRequestToken): Boolean = request.matches(
        currentAssetId = assetId,
        currentGeneration = assetGeneration,
    )

    companion object {
        private const val TAG_SEARCH_DEBOUNCE_MS = 250L

        fun factory(
            repository: AssetRepository,
            tagRepository: TagRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AssetViewerViewModel(repository, tagRepository, sessionRepository) }
        }
    }
}

internal data class AssetRequestToken(
    val assetId: String,
    val generation: Long,
) {
    fun matches(currentAssetId: String?, currentGeneration: Long): Boolean =
        assetId == currentAssetId && generation == currentGeneration
}

private sealed interface TagMutation {
    data class Upsert(
        val tag: AssetTag,
        val refreshCandidates: Boolean = false,
    ) : TagMutation

    data class Remove(val tagId: Long) : TagMutation
}

data class AssetViewerUiState(
    val asset: MediaAsset? = null,
    val isLoading: Boolean = true,
    val isSavingFavorite: Boolean = false,
    val isSavingMutation: Boolean = false,
    val changes: Set<AssetChangeKind> = emptySet(),
    val closeRequested: Boolean = false,
    val error: TimelineError? = null,
    val tagQuery: String = "",
    val availableTags: List<Tag> = emptyList(),
    val nextTagCursor: String? = null,
    val isLoadingTags: Boolean = false,
    val isSavingTag: Boolean = false,
    val showPrivateUnlock: Boolean = false,
    val privatePassword: String = "",
    val isUnlockingPrivate: Boolean = false,
)
