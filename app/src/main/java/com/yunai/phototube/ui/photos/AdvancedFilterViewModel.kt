package com.yunai.phototube.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yunai.phototube.data.folder.FolderKind
import com.yunai.phototube.data.folder.FolderNode
import com.yunai.phototube.data.folder.FolderRepository
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.AssetKind
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdvancedFilterViewModel(
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AdvancedFilterUiState())
    private var folderRequestRevision = 0
    private var tagRequestRevision = 0
    private val seenFolderCursors = mutableSetOf<String>()
    private val seenTagCursors = mutableSetOf<String>()

    val state: StateFlow<AdvancedFilterUiState> = mutableState.asStateFlow()

    fun begin(filter: AssetFilter) {
        mutableState.update {
            it.copy(
                draft = filter.toAdvancedFilterDraft(),
                validationMessage = null,
            )
        }
        if (mutableState.value.folderItems.isEmpty() && !mutableState.value.isLoadingFolders) browseFolder(null, "")
        if (mutableState.value.tags.isEmpty() && !mutableState.value.isLoadingTags) searchTags()
    }

    fun setKind(kind: AssetKind?) = updateDraft { copy(kind = if (this.kind == kind) null else kind) }

    fun setFavorite(favorite: Boolean?) = updateDraft {
        copy(favorite = if (this.favorite == favorite) null else favorite)
    }

    fun setRating(rating: Int?) = updateDraft {
        copy(rating = if (this.rating == rating) null else rating)
    }

    fun setTakenFrom(date: LocalDate?) = updateDraftSafely { copy(takenFrom = date) }

    fun setTakenTo(date: LocalDate?) = updateDraftSafely { copy(takenTo = date) }

    fun clearDates() = updateDraft { copy(takenFrom = null, takenTo = null) }

    fun clearFolder() = updateDraft { copy(folder = null) }

    fun clearDraft() {
        mutableState.update { it.copy(draft = AdvancedFilterDraft(), validationMessage = null) }
    }

    fun setTagMode(tagId: Long, mode: TagFilterMode?) {
        updateDraft {
            val updated = tagModes.toMutableMap()
            if (mode == null || updated[tagId] == mode) updated.remove(tagId) else updated[tagId] = mode
            copy(tagModes = updated)
        }
    }

    fun updateTagQuery(value: String) {
        mutableState.update { it.copy(tagQuery = value.take(120), tagError = null) }
    }

    fun searchTags() {
        val query = mutableState.value.tagQuery.trim()
        tagRequestRevision += 1
        val revision = tagRequestRevision
        seenTagCursors.clear()
        mutableState.update {
            it.copy(
                tags = emptyList(),
                tagSubmittedQuery = query,
                tagNextCursor = null,
                isLoadingTags = true,
                tagError = null,
            )
        }
        loadTags(query.takeIf(String::isNotEmpty), cursor = null, revision = revision, append = false)
    }

    fun loadMoreTags() {
        val snapshot = mutableState.value
        val cursor = snapshot.tagNextCursor ?: return
        if (snapshot.isLoadingTags || !seenTagCursors.add(cursor)) return
        mutableState.update { it.copy(isLoadingTags = true, tagError = null) }
        loadTags(snapshot.tagSubmittedQuery.takeIf(String::isNotEmpty), cursor, tagRequestRevision, append = true)
    }

    fun onTagLibraryChanged(deletedTagId: Long?) {
        val snapshot = mutableState.value
        val shouldReload = snapshot.tags.isNotEmpty() || snapshot.knownTags.isNotEmpty() ||
            snapshot.tagSubmittedQuery.isNotEmpty()
        if (deletedTagId != null) {
            mutableState.update {
                it.copy(
                    draft = it.draft.copy(tagModes = it.draft.tagModes - deletedTagId),
                    tags = it.tags.filterNot { tag -> tag.id == deletedTagId },
                    knownTags = it.knownTags - deletedTagId,
                )
            }
        }
        if (shouldReload) {
            restartTags(snapshot.tagSubmittedQuery.takeIf(String::isNotEmpty))
        }
    }

    fun openFolder(node: FolderNode) {
        if (!node.online || node.kind == FolderKind.ROOT) return
        browseFolder(requireNotNull(node.libraryId), node.path)
    }

    fun selectFolder(node: FolderNode) {
        if (node.kind != FolderKind.DIRECTORY || !node.online) return
        updateDraft { copy(folder = FolderFilterSelection(requireNotNull(node.libraryId), node.path)) }
    }

    fun selectCurrentFolder() {
        val snapshot = mutableState.value
        val libraryId = snapshot.folderLibraryId ?: return
        val path = snapshot.folderPath.takeIf(String::isNotBlank) ?: return
        updateDraft { copy(folder = FolderFilterSelection(libraryId, path)) }
    }

    fun navigateFolderUp() {
        val snapshot = mutableState.value
        val libraryId = snapshot.folderLibraryId ?: return
        if (snapshot.folderPath.isEmpty()) {
            browseFolder(null, "")
        } else {
            browseFolder(libraryId, snapshot.folderPath.substringBeforeLast('/', ""))
        }
    }

    fun loadMoreFolders() {
        val snapshot = mutableState.value
        val cursor = snapshot.folderNextCursor ?: return
        if (snapshot.isLoadingFolders || !seenFolderCursors.add(cursor)) return
        mutableState.update { it.copy(isLoadingFolders = true, folderError = null) }
        loadFolderPage(snapshot.folderLibraryId, snapshot.folderPath, cursor, folderRequestRevision, append = true)
    }

    fun retryFolders() {
        val snapshot = mutableState.value
        browseFolder(snapshot.folderLibraryId, snapshot.folderPath)
    }

    fun buildFilter(): AssetFilter? = try {
        mutableState.value.draft.toAssetFilter().also {
            mutableState.update { state -> state.copy(validationMessage = null) }
        }
    } catch (failure: IllegalArgumentException) {
        mutableState.update {
            it.copy(validationMessage = failure.message ?: "筛选条件不合法")
        }
        null
    }

    private fun browseFolder(libraryId: String?, path: String) {
        folderRequestRevision += 1
        val revision = folderRequestRevision
        seenFolderCursors.clear()
        mutableState.update {
            it.copy(
                folderLibraryId = libraryId,
                folderPath = path,
                folderItems = emptyList(),
                folderNextCursor = null,
                isLoadingFolders = true,
                folderError = null,
            )
        }
        loadFolderPage(libraryId, path, null, revision, append = false)
    }

    private fun loadFolderPage(
        libraryId: String?,
        path: String,
        cursor: String?,
        revision: Int,
        append: Boolean,
    ) {
        viewModelScope.launch {
            try {
                val listing = folderRepository.browse(libraryId, path, cursor)
                if (folderRequestRevision != revision) return@launch
                mutableState.update {
                    it.copy(
                        folderItems = if (append) {
                            (it.folderItems + listing.children.items)
                                .distinctBy { node -> Triple(node.kind, node.libraryId, node.path) }
                        } else {
                            listing.children.items
                        },
                        folderNextCursor = listing.children.nextCursor,
                        isLoadingFolders = false,
                        folderError = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (folderRequestRevision == revision) {
                    if (failure is ApiFailure && failure.error.code == "INVALID_CURSOR" && cursor != null) {
                        browseFolder(libraryId, path)
                        return@launch
                    }
                    cursor?.let(seenFolderCursors::remove)
                    mutableState.update {
                        it.copy(isLoadingFolders = false, folderError = failure.toTimelineError())
                    }
                }
            }
        }
    }

    private fun loadTags(keyword: String?, cursor: String?, revision: Int, append: Boolean) {
        viewModelScope.launch {
            try {
                val page = tagRepository.getTags(keyword, cursor)
                if (tagRequestRevision != revision) return@launch
                mutableState.update {
                    it.copy(
                        tags = if (append) (it.tags + page.items).distinctBy(Tag::id) else page.items,
                        knownTags = it.knownTags + page.items.associateBy(Tag::id),
                        tagNextCursor = page.nextCursor,
                        isLoadingTags = false,
                        tagError = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                if (tagRequestRevision == revision) {
                    if (failure is ApiFailure && failure.error.code == "INVALID_CURSOR" && cursor != null) {
                        restartTags(keyword)
                        return@launch
                    }
                    cursor?.let(seenTagCursors::remove)
                    mutableState.update { it.copy(isLoadingTags = false, tagError = failure.toTimelineError()) }
                }
            }
        }
    }

    private fun restartTags(keyword: String?) {
        tagRequestRevision += 1
        val revision = tagRequestRevision
        seenTagCursors.clear()
        mutableState.update {
            it.copy(tags = emptyList(), tagNextCursor = null, isLoadingTags = true, tagError = null)
        }
        loadTags(keyword, cursor = null, revision = revision, append = false)
    }

    private fun updateDraft(block: AdvancedFilterDraft.() -> AdvancedFilterDraft) {
        mutableState.update { it.copy(draft = it.draft.block(), validationMessage = null) }
    }

    private fun updateDraftSafely(block: AdvancedFilterDraft.() -> AdvancedFilterDraft) {
        try {
            updateDraft(block)
        } catch (failure: IllegalArgumentException) {
            mutableState.update { it.copy(validationMessage = failure.message) }
        }
    }

    companion object {
        fun factory(
            folderRepository: FolderRepository,
            tagRepository: TagRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AdvancedFilterViewModel(folderRepository, tagRepository) }
        }
    }
}

data class AdvancedFilterUiState(
    val draft: AdvancedFilterDraft = AdvancedFilterDraft(),
    val validationMessage: String? = null,
    val folderLibraryId: String? = null,
    val folderPath: String = "",
    val folderItems: List<FolderNode> = emptyList(),
    val folderNextCursor: String? = null,
    val isLoadingFolders: Boolean = false,
    val folderError: TimelineError? = null,
    val tagQuery: String = "",
    val tagSubmittedQuery: String = "",
    val tags: List<Tag> = emptyList(),
    val knownTags: Map<Long, Tag> = emptyMap(),
    val tagNextCursor: String? = null,
    val isLoadingTags: Boolean = false,
    val tagError: TimelineError? = null,
)
