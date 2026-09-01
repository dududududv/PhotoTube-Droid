package com.yunai.phototube.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yunai.phototube.data.takeUnicodeCodePoints
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagLibraryGateway
import com.yunai.phototube.data.tag.TagRepository
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.photos.toTimelineError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TagManagementViewModel(
    private val repository: TagLibraryGateway,
) : ViewModel() {
    private val submittedRequest = MutableStateFlow(TagQueryRequest(generation = 0, keyword = ""))
    private val mutableState = MutableStateFlow(TagManagementUiState())
    private val mutableChanges = Channel<TagLibraryChange>(capacity = Channel.BUFFERED)
    private var queryGeneration = 0L

    val state: StateFlow<TagManagementUiState> = mutableState.asStateFlow()
    val changes: Flow<TagLibraryChange> = mutableChanges.receiveAsFlow()
    val tags: Flow<PagingData<Tag>> = submittedRequest
        .flatMapLatest { repository.pagedTags(it.keyword) }
        .cachedIn(viewModelScope)

    fun updateQuery(value: String) {
        mutableState.update { it.copy(query = value.takeUnicodeCodePoints(MAX_TAG_NAME_CODE_POINTS)) }
    }

    fun submitQuery() {
        val query = mutableState.value.query.trim()
        queryGeneration += 1
        submittedRequest.value = TagQueryRequest(queryGeneration, query)
        mutableState.update { it.copy(query = query, submittedQuery = query, actionError = null) }
    }

    fun clearQuery() {
        queryGeneration += 1
        submittedRequest.value = TagQueryRequest(queryGeneration, "")
        mutableState.update { it.copy(query = "", submittedQuery = "", actionError = null) }
    }

    fun create(name: String) = mutate(CREATE_TARGET) {
        val tag = repository.create(validatedName(name))
        mutableState.update { it.copy(feedback = "已创建标签“${tag.name}”") }
        mutableChanges.trySend(TagLibraryChange(TagLibraryChangeKind.CREATED, tag.id))
    }

    fun rename(tag: Tag, name: String) = mutate(tagTarget(tag.id)) {
        val normalized = validatedName(name)
        if (normalized == tag.name) {
            mutableState.update { it.copy(feedback = "标签名称没有变化") }
            return@mutate
        }
        val updated = repository.rename(tag.id, normalized)
        check(updated.id == tag.id) { "PhotoTube 返回了其它标签的重命名结果" }
        mutableState.update { it.copy(feedback = "已重命名为“${updated.name}”") }
        mutableChanges.trySend(TagLibraryChange(TagLibraryChangeKind.RENAMED, tag.id))
    }

    fun delete(tag: Tag) = mutate(tagTarget(tag.id)) {
        val result = repository.delete(tag.id)
        mutableState.update {
            it.copy(feedback = "已删除“${tag.name}”，移除 ${result.affectedAssetCount} 项资产关系")
        }
        mutableChanges.trySend(
            TagLibraryChange(
                kind = TagLibraryChangeKind.DELETED,
                tagId = tag.id,
                affectedAssetCount = result.affectedAssetCount,
            ),
        )
    }

    fun consumeFeedback() {
        mutableState.update { it.copy(feedback = null) }
    }

    private fun mutate(target: String, operation: suspend () -> Unit) {
        if (mutableState.value.busyTarget != null) return
        mutableState.update { it.copy(busyTarget = target, actionError = null) }
        viewModelScope.launch {
            try {
                operation()
                mutableState.update { it.copy(busyTarget = null, actionError = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(busyTarget = null, actionError = failure.toTimelineError())
                }
            }
        }
    }

    companion object {
        const val CREATE_TARGET = "create"

        fun tagTarget(tagId: Long): String = "tag:$tagId"

        fun factory(repository: TagRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TagManagementViewModel(repository) }
        }
    }
}

internal data class TagQueryRequest(
    val generation: Long,
    val keyword: String,
)

data class TagManagementUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val busyTarget: String? = null,
    val actionError: TimelineError? = null,
    val feedback: String? = null,
)

data class TagLibraryChange(
    val kind: TagLibraryChangeKind,
    val tagId: Long,
    val affectedAssetCount: Long = 0,
)

enum class TagLibraryChangeKind { CREATED, RENAMED, DELETED }

private fun validatedName(raw: String): String = raw.trim().also {
    require(it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_TAG_NAME_CODE_POINTS) {
        "标签名称必须为 1–120 个字符"
    }
}

internal const val MAX_TAG_NAME_CODE_POINTS = 120
