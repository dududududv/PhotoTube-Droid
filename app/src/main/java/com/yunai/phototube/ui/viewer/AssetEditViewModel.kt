package com.yunai.phototube.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yunai.phototube.data.asset.AssetRepository
import com.yunai.phototube.data.edit.ActiveEdit
import com.yunai.phototube.data.edit.CreateEditVersionRequest
import com.yunai.phototube.data.edit.EDIT_PPM
import com.yunai.phototube.data.edit.EditRepository
import com.yunai.phototube.data.edit.EditSourceState
import com.yunai.phototube.data.edit.EditTransform
import com.yunai.phototube.data.edit.EditVersion
import com.yunai.phototube.data.edit.SelectActiveEditRequest
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.remote.isInvalidCursorFailure
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.rethrowCancellation
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssetEditViewModel(
    private val editRepository: EditRepository,
    private val assetRepository: AssetRepository,
) : ViewModel() {
    private val eventSequence = AtomicLong(0L)
    private var loadGeneration = 0L
    private var openedAsset: MediaAsset? = null
    private val mutableUiState = MutableStateFlow(AssetEditUiState())
    val uiState: StateFlow<AssetEditUiState> = mutableUiState.asStateFlow()

    fun open(asset: MediaAsset) {
        if (
            openedAsset?.id == asset.id &&
            openedAsset?.contentHash == asset.contentHash &&
            openedAsset?.activeEdit == asset.activeEdit &&
            mutableUiState.value.isReady
        ) return
        openedAsset = asset
        mutableUiState.value = AssetEditUiState(isLoading = true)
        loadHistory(asset)
    }

    fun retry() {
        openedAsset?.let(::loadHistory)
    }

    fun rotateClockwise() = updateDraft { it.rotateClockwise() }

    fun flipHorizontally() = updateDraft { it.flipHorizontally() }

    fun flipVertically() = updateDraft { it.flipVertically() }

    fun resetDraft() {
        mutableUiState.update { state ->
            state.copy(draft = state.baseline, error = null, message = "已恢复当前版本的配方")
        }
    }

    fun resetToSourceTransform() = updateDraft { EditTransform.Source }

    fun updateHorizontalCrop(start: Float, end: Float) = updateDraft { transform ->
        transform.withHorizontalCrop(
            startPPM = (start * EDIT_PPM).roundToInt(),
            endPPM = (end * EDIT_PPM).roundToInt(),
        )
    }

    fun updateVerticalCrop(start: Float, end: Float) = updateDraft { transform ->
        transform.withVerticalCrop(
            startPPM = (start * EDIT_PPM).roundToInt(),
            endPPM = (end * EDIT_PPM).roundToInt(),
        )
    }

    fun saveAsNewVersion() {
        val asset = openedAsset ?: return
        val generation = loadGeneration
        val state = mutableUiState.value
        val contentHash = asset.contentHash ?: return
        if (!state.isReady || !state.hasUnsavedChanges || state.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, message = "正在保存新版本…") }
        viewModelScope.launch {
            runCatching {
                editRepository.createVersion(
                    assetId = asset.id,
                    request = CreateEditVersionRequest(
                        expectedActiveEditVersionId = state.activeEdit?.editVersionId,
                        sourceContentHash = contentHash,
                        transform = state.draft,
                    ),
                )
            }.onSuccess { version ->
                if (!isCurrentAssetRequest(asset.id, generation)) return@onSuccess
                val active = version.activeSummary()
                openedAsset = asset.copy(activeEdit = active)
                mutableUiState.update {
                    it.copy(
                        versions = listOf(version) + it.versions
                            .filterNot { existing -> existing.id == version.id }
                            .map { existing -> existing.copy(active = false) },
                        activeEdit = active,
                        baseline = version.transform,
                        draft = version.transform,
                        isSaving = false,
                        message = "已保存为不可变新版本",
                        applyEvent = EditApplyEvent(
                            revision = eventSequence.incrementAndGet(),
                            assetId = asset.id,
                            activeEdit = active,
                        ),
                    )
                }
            }.onFailure { failure -> handleMutationFailure(asset.id, generation, failure) }
        }
    }

    fun requestSelectVersion(targetEditVersionId: String?) {
        val state = mutableUiState.value
        if (!state.isReady || state.isSaving) return
        val target = targetEditVersionId?.let { id -> state.versions.firstOrNull { it.id == id } }
        if (targetEditVersionId != null && (target == null || target.sourceState == EditSourceState.STALE)) {
            mutableUiState.update { it.copy(message = "该历史版本基于旧原图，不能重新激活") }
            return
        }
        if (state.hasUnsavedChanges) {
            mutableUiState.update { it.copy(pendingSelectionId = targetEditVersionId, showDiscardConfirmation = true) }
            return
        }
        selectVersion(targetEditVersionId)
    }

    fun confirmDiscardAndSelect() {
        val target = mutableUiState.value.pendingSelectionId
        mutableUiState.update { it.copy(showDiscardConfirmation = false, pendingSelectionId = null) }
        selectVersion(target)
    }

    fun dismissDiscardConfirmation() {
        mutableUiState.update { it.copy(showDiscardConfirmation = false, pendingSelectionId = null) }
    }

    fun loadMore() {
        val asset = openedAsset ?: return
        val state = mutableUiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || state.isSaving) return
        val generation = loadGeneration
        mutableUiState.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            runCatching { editRepository.getVersions(asset.id, cursor, HISTORY_PAGE_SIZE) }
                .onSuccess { page ->
                    if (openedAsset?.id != asset.id || loadGeneration != generation) return@onSuccess
                    if (page.nextCursor == cursor) {
                        mutableUiState.update {
                            it.copy(
                                isLoadingMore = false,
                                error = TimelineError("编辑历史返回了重复 cursor"),
                            )
                        }
                        return@onSuccess
                    }
                    mutableUiState.update {
                        it.copy(
                            versions = (it.versions + page.items).distinctBy(EditVersion::id),
                            nextCursor = page.nextCursor,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (failure.isInvalidCursorFailure(cursor)) {
                        loadHistory(asset, message = "编辑历史已变化，已从第一页刷新")
                    } else {
                        mutableUiState.update {
                            it.copy(isLoadingMore = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    fun consumeApplyEvent() {
        mutableUiState.update { it.copy(applyEvent = null) }
    }

    private fun loadHistory(
        asset: MediaAsset,
        message: String? = null,
        requestViewerRefresh: Boolean = false,
    ) {
        val generation = ++loadGeneration
        mutableUiState.update {
            it.copy(isLoading = true, isLoadingMore = false, error = null, message = message)
        }
        viewModelScope.launch {
            runCatching { loadUntilActiveVersion(asset) }
                .onSuccess { loaded ->
                    if (openedAsset?.id != asset.id || loadGeneration != generation) return@onSuccess
                    val activeVersion = asset.activeEdit
                        ?.takeIf { it.sourceState == EditSourceState.CURRENT }
                        ?.let { active -> loaded.versions.firstOrNull { it.id == active.editVersionId } }
                    if (asset.activeEdit?.sourceState == EditSourceState.CURRENT && activeVersion == null) {
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                error = TimelineError("当前编辑版本不在历史中，请刷新后重试"),
                            )
                        }
                        return@onSuccess
                    }
                    val baseline = activeVersion?.transform ?: EditTransform.Source
                    mutableUiState.value = AssetEditUiState(
                        versions = loaded.versions,
                        nextCursor = loaded.nextCursor,
                        activeEdit = asset.activeEdit,
                        baseline = baseline,
                        draft = baseline,
                        isLoading = false,
                        isReady = true,
                        message = message ?: if (asset.activeEdit?.sourceState == EditSourceState.STALE) {
                            "原图内容已变化，当前旧配方只保留在历史中"
                        } else {
                            "所有编辑只保存配方，不修改 NAS 原文件"
                        },
                        applyEvent = if (requestViewerRefresh) {
                            EditApplyEvent(
                                revision = eventSequence.incrementAndGet(),
                                assetId = asset.id,
                                activeEdit = asset.activeEdit,
                                refreshAsset = true,
                            )
                        } else {
                            null
                        },
                    )
                }
                .onFailure { failure ->
                    if (failure is CancellationException) throw failure
                    if (openedAsset?.id == asset.id && loadGeneration == generation) {
                        mutableUiState.update {
                            it.copy(isLoading = false, error = failure.toTimelineError())
                        }
                    }
                }
        }
    }

    private suspend fun loadUntilActiveVersion(asset: MediaAsset): LoadedHistory {
        return loadEditHistoryWithCursorRecovery(asset.activeEdit?.editVersionId) { cursor ->
            editRepository.getVersions(asset.id, cursor, HISTORY_PAGE_SIZE)
        }
    }

    private fun selectVersion(targetEditVersionId: String?) {
        val asset = openedAsset ?: return
        val generation = loadGeneration
        val state = mutableUiState.value
        if (state.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, message = "正在切换显示版本…") }
        viewModelScope.launch {
            runCatching {
                editRepository.selectVersion(
                    assetId = asset.id,
                    request = SelectActiveEditRequest(
                        expectedActiveEditVersionId = state.activeEdit?.editVersionId,
                        targetEditVersionId = targetEditVersionId,
                    ),
                )
            }.onSuccess { active ->
                if (!isCurrentAssetRequest(asset.id, generation)) return@onSuccess
                val baseline = targetEditVersionId
                    ?.let { id -> state.versions.firstOrNull { it.id == id }?.transform }
                    ?: EditTransform.Source
                openedAsset = asset.copy(activeEdit = active)
                mutableUiState.update {
                    it.copy(
                        versions = it.versions.map { version ->
                            version.copy(active = version.id == targetEditVersionId)
                        },
                        activeEdit = active,
                        baseline = baseline,
                        draft = baseline,
                        isSaving = false,
                        message = if (active == null) "已切回原图，历史版本仍保留" else "已切换显示版本",
                        applyEvent = EditApplyEvent(
                            revision = eventSequence.incrementAndGet(),
                            assetId = asset.id,
                            activeEdit = active,
                        ),
                    )
                }
            }.onFailure { failure -> handleMutationFailure(asset.id, generation, failure) }
        }
    }

    private fun handleMutationFailure(assetId: String, generation: Long, failure: Throwable) {
        if (failure is CancellationException) throw failure
        if (!isCurrentAssetRequest(assetId, generation)) return
        if (failure is ApiFailure && failure.httpStatus == 409) {
            mutableUiState.update {
                it.copy(isSaving = false, message = "编辑基线已变化，正在读取服务端最新状态")
            }
            viewModelScope.launch {
                runCatching { assetRepository.getAsset(assetId) }
                    .onSuccess { latest ->
                        if (!isCurrentAssetRequest(assetId, generation)) return@onSuccess
                        openedAsset = latest
                        loadHistory(
                            latest,
                            message = "检测到并发修改，已刷新到最新版本",
                            requestViewerRefresh = true,
                        )
                    }
                    .onFailure { refreshFailure ->
                        refreshFailure.rethrowCancellation()
                        if (!isCurrentAssetRequest(assetId, generation)) return@onFailure
                        mutableUiState.update {
                            it.copy(isSaving = false, error = refreshFailure.toTimelineError())
                        }
                    }
            }
            return
        }
        mutableUiState.update {
            it.copy(isSaving = false, error = failure.toTimelineError(), message = null)
        }
    }

    private fun isCurrentAssetRequest(assetId: String, generation: Long): Boolean =
        openedAsset?.id == assetId && loadGeneration == generation

    private fun updateDraft(transform: (EditTransform) -> EditTransform) {
        val state = mutableUiState.value
        if (!state.isReady || state.isSaving) return
        mutableUiState.update {
            it.copy(draft = transform(it.draft), error = null, message = "编辑草稿尚未保存")
        }
    }

    companion object {
        private const val HISTORY_PAGE_SIZE = 100

        fun factory(
            editRepository: EditRepository,
            assetRepository: AssetRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AssetEditViewModel(editRepository, assetRepository) }
        }
    }
}

data class AssetEditUiState(
    val versions: List<EditVersion> = emptyList(),
    val nextCursor: String? = null,
    val activeEdit: ActiveEdit? = null,
    val baseline: EditTransform = EditTransform.Source,
    val draft: EditTransform = EditTransform.Source,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSaving: Boolean = false,
    val isReady: Boolean = false,
    val message: String? = null,
    val error: TimelineError? = null,
    val showDiscardConfirmation: Boolean = false,
    val pendingSelectionId: String? = null,
    val applyEvent: EditApplyEvent? = null,
) {
    val hasUnsavedChanges: Boolean get() = isReady && draft != baseline
}

data class EditApplyEvent(
    val revision: Long,
    val assetId: String,
    val activeEdit: ActiveEdit?,
    val refreshAsset: Boolean = false,
)

internal data class LoadedHistory(
    val versions: List<EditVersion>,
    val nextCursor: String?,
)

internal suspend fun loadEditHistoryWithCursorRecovery(
    activeEditVersionId: String?,
    loadPage: suspend (String?) -> com.yunai.phototube.data.edit.EditVersionPage,
): LoadedHistory {
    var restartCount = 0
    while (true) {
        val versions = mutableListOf<EditVersion>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        try {
            do {
                val page = loadPage(cursor)
                versions += page.items
                cursor = page.nextCursor
                check(cursor == null || seenCursors.add(cursor)) {
                    "编辑历史返回了重复 cursor"
                }
            } while (
                activeEditVersionId != null &&
                versions.none { it.id == activeEditVersionId } &&
                cursor != null
            )
            return LoadedHistory(versions.distinctBy(EditVersion::id), cursor)
        } catch (failure: Throwable) {
            if (!failure.isInvalidCursorFailure(cursor) || restartCount >= 1) throw failure
            restartCount += 1
        }
    }
}
