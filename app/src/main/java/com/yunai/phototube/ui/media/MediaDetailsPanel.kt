package com.yunai.phototube.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.Velocity
import com.yunai.phototube.ui.theme.LocalThemeController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.MediaColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal const val MediaDetailsRestingExtent = .44f

private enum class DetailPage { INFO, TAGS, PICK }

/** Shares the viewer window and a single sheet; never opens another management dialog. */
@Composable
internal fun BoxScope.MediaDetailsPanel(photo: Photo, metadata: MediaMetadata?, height: Float,
    extent: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, onClose: () -> Unit,
    onChanged: () -> Unit, onBusyChanged: (Boolean) -> Unit = {}, modifier: Modifier = Modifier) {
    var page by rememberSaveable(photo.id) { mutableStateOf(DetailPage.INFO) }
    var busy by remember { mutableStateOf(false) }
    var needsRefresh by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { onBusyChanged(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    // Keep a successful creation separate from a failed asset attachment.
    val createdTags = remember(photo.id) { mutableMapOf<String, Long>() }
    LaunchedEffect(page) {
        focus.clearFocus()
    }
    val source = photo.remote
    val tags = photoTags(source?.detail)
    val infoScroll = rememberScrollState()
    val accent = LocalThemeController.current.accent.dark
    val currentBusy by rememberUpdatedState(busy)
    val currentClose by rememberUpdatedState(onClose)
    val scrollConnection = remember(height) { object : NestedScrollConnection {
        fun shift(delta: Float): Offset {
            if (currentBusy) return Offset.Zero
            val target = (extent.value - delta / height).coerceIn(.08f, .88f)
            val consumed = (extent.value - target) * height
            scope.launch { extent.snapTo(target) }
            return Offset(0f, consumed)
        }
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y < 0 && extent.value < .88f) shift(available.y) else Offset.Zero
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y > 0) shift(available.y) else Offset.Zero
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (currentBusy) return Velocity.Zero
            val projected = extent.value - available.y / height * .15f
            val target = listOf(0f, MediaDetailsRestingExtent, .88f).minBy { abs(it - projected) }
            if (extent.value >= .879f && available.y < 0) return Velocity.Zero
            if (target == 0f) currentClose() else extent.animateTo(target, spring(1f, 450f), initialVelocity = -available.y / height)
            return available
        }
    } }
    fun back() {
        if (busy) return
        when (page) { DetailPage.PICK -> page = DetailPage.TAGS; DetailPage.TAGS -> page = DetailPage.INFO; DetailPage.INFO -> onClose() }
    }
    fun mutate(action: suspend (TagRepository) -> Unit) {
        if (source == null || busy || needsRefresh) return
        busy = true; error = null
        scope.launch {
            try {
                action(TagRepository(source.server))
                needsRefresh = true
                onChanged()
                source.detail = source.server.detail(source.asset.id)
                needsRefresh = false
                page = DetailPage.TAGS
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { error = if (needsRefresh) "操作已保存，标签读取失败，请重新读取。" else problem.message ?: "操作未完成" }
            finally { busy = false }
        }
    }
    BackHandler { back() }
    // Block both pointers and semantics from the photo while a panel is presented.
    Box(Modifier.fillMaxSize().pointerInput(busy) { detectTapGestures { if (!busy) onClose() } })
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        .height(with(density) { (height * extent.value).toDp() })
        .then(modifier).background(MediaColors.background).testTag("media-details-panel")
        .semantics {
            isTraversalGroup = true; paneTitle = "照片与视频信息"
            expand { scope.launch { extent.animateTo(.88f, spring(1f, 450f)) }; true }
            collapse { scope.launch { extent.animateTo(MediaDetailsRestingExtent, spring(1f, 450f)) }; true }
            dismiss { if (!busy) { onClose(); true } else false }
        }
        .nestedScroll(scrollConnection)
        .navigationBarsPadding().imePadding()) {
        if (page != DetailPage.INFO) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::back, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = if (page == DetailPage.PICK) "返回照片标签" else "返回照片信息" }) {
                MediaIcon(MediaSymbol.BACK, accent)
                Text(if (page == DetailPage.PICK) "标签" else "信息", color = accent)
            }
            Text(if (page == DetailPage.PICK) "添加标签" else "照片标签", color = MediaColors.text,
                fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MediaColors.text)
        error?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MediaColors.text) }
        if (needsRefresh) TextButton(onClick = {
            if (!busy && source != null) { busy = true; scope.launch {
                try { source.detail = source.server.detail(source.asset.id); needsRefresh = false; error = null; page = DetailPage.TAGS }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { error = "标签读取失败，请重试。" }
                finally { busy = false }
            } }
        }, enabled = !busy) { Text("重新读取标签", color = MediaColors.text) }
        AnimatedContent(page, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "detail-section") { section ->
            when (section) {
                DetailPage.INFO -> Column(Modifier.fillMaxSize().testTag("media-info").verticalScroll(infoScroll)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 82.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MediaColors.detailPanel)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(source?.detail?.nullString("takenAt")?.let { com.yunai.phototube.ui.remote.taskTimestamp(it) } ?: photo.date,
                                color = MediaColors.text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                            Text(photo.name, color = MediaColors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                        val specs = buildList {
                            add(photo.name.substringAfterLast('.', if(photo.video) "视频" else "照片").uppercase())
                            metadata?.takeIf { it.width > 0 && it.height > 0 }?.let { add("${it.width} × ${it.height}") }
                            metadata?.takeIf { it.bytes > 0 }?.let { add(mediaSizeLabel(it.bytes)) }
                            metadata?.takeIf { photo.video && it.duration > 0 }?.let { add(mediaTime(it.duration)) }
                        }
                        if (specs.isNotEmpty()) {
                            HorizontalDivider(color = Color.White.copy(.10f))
                            Text(specs.joinToString(" · "), Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = MediaColors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    source?.detail?.nullString("relativePath")?.let { path ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MediaColors.detailPanel).padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("文件位置", color = MediaColors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
                            Text(path, color = MediaColors.text, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MediaColors.detailPanel)
                        .clickable(enabled = source?.detail != null) { page = DetailPage.TAGS }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (tags.isEmpty()) "添加标签" else "编辑标签", color = accent, fontSize = 14.sp, lineHeight = 20.sp)
                        if (tags.isNotEmpty()) Text(tags.joinToString(" · ") { it.name }, color = MediaColors.text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    source?.detail?.nullString("state")?.takeIf { it != "BROWSABLE" }?.let { Text(mediaStateLabel(it), color = MediaColors.secondary) }
                }
                DetailPage.TAGS -> LazyColumn(Modifier.fillMaxSize().testTag("photo-tags-panel"), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 82.dp)) {
                    item { Text(photo.name, color = MediaColors.secondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                    if (tags.isEmpty()) item { Text("这张照片还没有标签", color = MediaColors.secondary) }
                    items(tags, key = { it.id }) { tag ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tag.name, color = MediaColors.text, fontSize = 13.sp, lineHeight = 18.sp)
                                Text(when(tag.source) {"AI" -> "AI 标签"; "RULE" -> "规则标签"; "MANUAL" -> "人工标签"; else -> "标签"}, color = MediaColors.secondary, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                            IconButton(onClick = { mutate { it.detach(source!!.asset.id, tag.id) } }, enabled = !busy && !needsRefresh,
                                modifier = Modifier.testTag("remove-photo-tag-${tag.id}")) { Icon(Icons.Rounded.Close, "移除标签：${tag.name}", tint = MediaColors.secondary) }
                        }
                    }
                    item { TextButton(onClick = { page = DetailPage.PICK }, enabled = !busy && !needsRefresh) {
                        Icon(Icons.Rounded.Add, null, tint = MediaColors.text); Spacer(Modifier.width(8.dp)); Text("添加标签", color = MediaColors.text)
                    } }
                    if (tags.any { it.source in setOf("AI", "RULE") }) item { Text("移除自动标签后，后续扫描不会立即添加回来。", color = MediaColors.secondary, fontSize = 13.sp, lineHeight = 18.sp) }
                }
                DetailPage.PICK -> if (source != null) DetailTagPicker(source.server, tags.map { it.id }.toSet(), !busy && !needsRefresh,
                    onSearchFocus = { scope.launch { extent.animateTo(.88f, spring(1f, 450f)) } },
                    onPick = { id -> mutate { it.attach(source.asset.id, id) } },
                    onCreate = { name -> mutate { repository -> val id = createdTags[name] ?: repository.create(name).id.also { createdTags[name] = it }; repository.attach(source.asset.id, id) } })
            }
        }
    }
}

@Composable
private fun DetailTagPicker(server: PhotoTubeServer, selected: Set<Long>, enabled: Boolean,
    onSearchFocus: () -> Unit, onPick: (Long) -> Unit, onCreate: (String) -> Unit) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(keyword) { delay(250); query = keyword.trim() }
    val catalog = remember(server) { FilterCatalog(server) }
    val flow = remember(server, query) { Pager(PagingConfig(100, enablePlaceholders = false)) { AlbumPagingSource { cursor -> catalog.tags(query, cursor) } }.flow }
    val rows = flow.collectAsLazyPagingItems()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(bottom = 82.dp)) {
        OutlinedTextField(keyword, { if (it.length <= 120) keyword = it }, placeholder = { Text("搜索标签", fontSize = 14.sp) }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            modifier = Modifier.fillMaxWidth().testTag("detail-tag-search").onFocusChanged { if (it.isFocused) onSearchFocus() }, enabled = enabled, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MediaColors.text, unfocusedTextColor = MediaColors.text,
                cursorColor = MediaColors.text, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(.08f), unfocusedContainerColor = Color.White.copy(.08f),
                focusedPlaceholderColor = MediaColors.secondary, unfocusedPlaceholderColor = MediaColors.secondary))
        LazyColumn(Modifier.weight(1f).testTag("detail-tag-picker")) {
            items(rows.itemCount, key = rows.itemKey { it.id }) { index -> rows[index]?.let { tag ->
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled && tag.id !in selected) { onPick(tag.id) }
                    .heightIn(min = 56.dp).testTag("pick-tag-${tag.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.name, Modifier.weight(1f), color = MediaColors.text, fontSize = 14.sp, lineHeight = 20.sp)
                    Icon(if(tag.id in selected) Icons.Rounded.Check else Icons.Rounded.Add, if(tag.id in selected) "已添加" else "添加", tint = MediaColors.secondary)
                }
            } }
            if (rows.loadState.refresh is LoadState.Loading || rows.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (rows.loadState.refresh as? LoadState.Error) ?: (rows.loadState.append as? LoadState.Error)
            if (failure != null) item { TextButton(onClick = rows::retry) { Text("加载失败，重试", color = MediaColors.text) } }
            if (query.isNotBlank() && query == keyword.trim() && rows.loadState.refresh is LoadState.NotLoading && rows.itemSnapshotList.items.none { it.name == query }) item {
                TextButton(onClick = { onCreate(query) }, enabled = enabled) { Text("新建“$query”并添加", color = MediaColors.text) }
            }
        }
    }
}
