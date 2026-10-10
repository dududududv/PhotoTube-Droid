package com.yunai.phototube.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.yunai.phototube.data.PhotoTubeServer
import com.yunai.phototube.data.RemoteAsset
import com.yunai.phototube.ui.theme.MediaColors
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Shares the originating pager's window and cursor chain, never re-queries a different scope. */
internal class PagedViewerSession private constructor(
    initialConnection: PagedViewerConnection,
    private val server: PhotoTubeServer,
    private var bounds: Map<String, Rect>,
    val initialAssetId: String,
) {
    private var connection by mutableStateOf<PagedViewerConnection?>(initialConnection)
    var dirty = false
    fun attach(value: PagedViewerConnection) { connection = value }
    fun detach(value: PagedViewerConnection) {
        if (connection === value) {
            connection = null
            // The old window's coordinates are invalid after a configuration change.
            bounds = emptyMap()
        }
    }
    fun loadStates(): CombinedLoadStates? = connection?.states?.invoke()
    fun retry() { connection?.retry?.invoke() }
    private var nextId = -1
    private val photos = mutableMapOf<String, Photo>()
    var changed = false
    private var retained = emptyList<Photo>()
    var anchor = initialAssetId
        private set

    fun media(): List<Photo> {
        val active = connection ?: return retained
        val assets = active.snapshot().map { it.second }
        if (assets.isEmpty() && active.states().refresh is LoadState.Loading) return retained
        // A server invalidation can restart at page one. Keep the current image instead of
        // silently moving to a different asset; ask the user to reopen the refreshed list.
        if (assets.none { it.id == anchor }) {
            changed = retained.isNotEmpty()
            return retained
        }
        changed = false
        val ids = assets.mapTo(mutableSetOf()) { it.id }
        photos.keys.retainAll(ids)
        retained = assets.map { asset ->
            photos.getOrPut(asset.id) {
                val date = OffsetDateTime.parse(asset.takenAt).let { value ->
                    asset.takenAtOffsetMinutes?.let { value.withOffsetSameInstant(ZoneOffset.ofTotalSeconds(it * 60)) } ?: value
                }
                Photo(nextId--, asset.fileName, date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                    asset.favorite, asset.kind == "VIDEO", RemoteMedia(asset, server))
            }
        }
        return retained
    }

    fun request(media: List<Photo>): ViewerRequest = ViewerRequest(
        media, media.firstOrNull { it.remote!!.asset.id == anchor }?.id ?: media.first().id,
        media.mapNotNull { photo -> bounds[photo.remote!!.asset.id]?.let { photo.id to it } }.toMap(), sourceCornerRadius=5.dp,
        resolveSource = { id -> media.firstOrNull { it.id == id }?.remote?.asset?.id?.let { bounds[it] } },
    )

    fun visible(photo: Photo) {
        if (changed) return
        anchor = photo.remote!!.asset.id
        connection?.let { active -> active.snapshot().firstOrNull { it.second.id == anchor }?.let { active.access(it.first) } }
    }

    companion object {
        fun <T : Any> create(items: LazyPagingItems<T>, asset: (T) -> RemoteAsset?, server: PhotoTubeServer,
                             selected: RemoteAsset, bounds: Map<String, Rect>): PagedViewerSession =
            PagedViewerSession(PagedViewerConnection.create(items, asset), server, bounds, selected.id).also { it.media() }
    }
}

/** UI-owned bindings are explicitly detached before their composition is destroyed. */
internal class PagedViewerConnection(
    val snapshot: () -> List<Pair<Int, RemoteAsset>>,
    val access: (Int) -> Unit,
    val states: () -> CombinedLoadStates,
    val retry: () -> Unit,
) {
    companion object {
        fun <T:Any> create(items: LazyPagingItems<T>, asset: (T) -> RemoteAsset?) = PagedViewerConnection(
            snapshot = { items.itemSnapshotList.items.mapIndexedNotNull { index, item -> asset(item)?.let { index to it } } },
            access = { index -> if (index in 0 until items.itemCount) items[index] },
            states = { items.loadState }, retry = items::retry,
        )
    }
}

@Composable
internal fun <T:Any> BindPagedViewer(session: PagedViewerSession?, items: LazyPagingItems<T>, asset: (T) -> RemoteAsset?) {
    val connection = remember(items) { PagedViewerConnection.create(items, asset) }
    DisposableEffect(session, connection) {
        session?.attach(connection)
        onDispose { session?.detach(connection) }
    }

}

@Composable
internal fun PagedMediaViewer(session: PagedViewerSession, onClose: () -> Unit, onRemoteChanged: () -> Unit) {
    val media = session.media()
    if (media.isEmpty()) return
    val states = session.loadStates()
    // Refresh the underlying list after dismissal. A favorite/edit mutation must not reset
    // its cursor window underneath an open viewer or restart the opening animation.
    MediaViewer(session.request(media), emptySet(), {}, {}, {
        onClose()
        if (session.dirty) onRemoteChanged()
    }, onRemoteChanged = { session.dirty = true }, onPageVisible = session::visible, paged = true, pagingStatus = {
        when {
            session.changed -> Text("列表已变化，请返回后重新打开", color = MediaColors.text)
            states?.append is LoadState.Error || states?.prepend is LoadState.Error ->
                Box(Modifier.padding(horizontal=24.dp,vertical=8.dp)) {com.yunai.phototube.ui.GlassSettingsAction("加载更多失败 · 重试",session::retry,media=true)}
            states?.append is LoadState.Loading || states?.prepend is LoadState.Loading ->
                Text("正在加载更多…", color = MediaColors.text)
        }
    })
}
