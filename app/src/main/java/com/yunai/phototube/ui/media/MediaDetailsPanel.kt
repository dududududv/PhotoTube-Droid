package com.yunai.phototube.ui.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private enum class DetailPage { INFO, TAGS }

/** Information follows the photo in one scroll flow; tag management stays in the same window. */
@Composable
internal fun BoxScope.MediaDetailsPanel(photo: Photo, metadata: MediaMetadata?, height: Float,
    reveal: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, onClose: () -> Unit,
    onChanged: () -> Unit, onBusyChanged: (Boolean) -> Unit = {}, modifier: Modifier = Modifier,
    photoHeaderHeight: Float = 0f, infoScroll: ScrollState = rememberScrollState(),
    onBodyHeight: (Int) -> Unit = {}, onEditingChanged: (Boolean) -> Unit = {},
    pullOffset: Float = 0f, onPull: (Float) -> Unit = {}) {
    var page by rememberSaveable(photo.id) { mutableStateOf(DetailPage.INFO) }
    var busy by remember { mutableStateOf(false) }
    var needsRefresh by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { onBusyChanged(busy); onEditingChanged(page == DetailPage.TAGS) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false); onEditingChanged(false) } }
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
    var locationExpanded by rememberSaveable(photo.id) { mutableStateOf(false) }
    val accent = LocalThemeController.current.accent.dark
    val currentBusy by rememberUpdatedState(busy)
    val currentClose by rememberUpdatedState(onClose)
    val currentPull by rememberUpdatedState(onPull)
    val closeDistance = with(density) { 72.dp.toPx() }
    // Only a downward pull beyond the top of the content dismisses information.
    val scrollConnection = remember(height) { object : NestedScrollConnection {
        var distance = 0f
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y < 0 && distance > 0) {
                val consumed = maxOf(available.y, -distance)
                distance += consumed; currentPull(distance * .5f)
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!currentBusy && source == NestedScrollSource.UserInput && available.y > 0) {
                distance += available.y; currentPull(distance * .5f)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (distance == 0f) return Velocity.Zero
            if (distance > closeDistance) currentClose()
            val release = distance * .5f
            distance = 0f
            androidx.compose.animation.core.animate(release, 0f, animationSpec = spring(1f, 450f)) { value, _ -> currentPull(value) }
            return available
        }
    } }
    fun back() {
        if (busy) return
        when (page) { DetailPage.TAGS -> page = DetailPage.INFO; DetailPage.INFO -> onClose() }
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
    Column(Modifier.fillMaxSize()
        .graphicsLayer { translationY = (1f - reveal.value) * height + pullOffset }
        .then(modifier).testTag("media-details-panel")
        .semantics { isTraversalGroup = true; paneTitle = if (page == DetailPage.INFO) "照片与视频信息" else "照片标签" }) {
        if (page == DetailPage.TAGS) Box(Modifier.fillMaxWidth().background(MediaColors.background).statusBarsPadding()) {
            Text("标签", Modifier.align(Alignment.Center), color = MediaColors.text, fontSize = 15.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = ::back, enabled = !busy, modifier = Modifier.align(Alignment.CenterStart)
                .padding(start = 8.dp).semantics { contentDescription = "返回照片信息" }) { MediaIcon(MediaSymbol.BACK, accent) }
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
                DetailPage.INFO -> Column(Modifier.fillMaxSize().testTag("media-info").nestedScroll(scrollConnection).verticalScroll(infoScroll)) {
                    Spacer(Modifier.fillMaxWidth().height(with(density) { photoHeaderHeight.toDp() })
                        .pointerInput(busy) { detectTapGestures { if (!busy) onClose() } })
                    Column(Modifier.fillMaxWidth().background(MediaColors.background).onSizeChanged { onBodyHeight(it.height) }
                        .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 82.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MediaColors.detailPanel)
                        .clickable(enabled = source?.detail != null) { page = DetailPage.TAGS }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (tags.isEmpty()) "添加标签" else "编辑标签", color = accent, fontSize = 14.sp, lineHeight = 20.sp)
                        if (tags.isNotEmpty()) Text(tags.joinToString(" · ") { it.name }, color = MediaColors.text, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    source?.detail?.nullString("relativePath")?.let { path ->
                        Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(18.dp))
                            .clickable { locationExpanded = !locationExpanded }.heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(if (locationExpanded) "收起文件位置" else "文件位置", color = MediaColors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
                            if (locationExpanded) Text(path, Modifier.padding(top = 6.dp), color = MediaColors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    source?.detail?.nullString("state")?.takeIf { it != "BROWSABLE" }?.let { Text(mediaStateLabel(it), color = MediaColors.secondary) }
                }
                }
                DetailPage.TAGS -> if (source != null) DetailTagPicker(source.server, tags, !busy && !needsRefresh,
                    onRemove = { id -> mutate { it.detach(source.asset.id, id) } },
                    onPick = { id -> mutate { it.attach(source.asset.id, id) } },
                    onCreate = { name -> mutate { repository -> val id = createdTags[name] ?: repository.create(name).id.also { createdTags[name] = it }; repository.attach(source.asset.id, id) } })
            }
        }
    }
}

@Composable
private fun DetailTagPicker(server: PhotoTubeServer, current: List<PhotoTag>, enabled: Boolean,
    onRemove: (Long) -> Unit, onPick: (Long) -> Unit, onCreate: (String) -> Unit) {
    val selected = current.map { it.id }.toSet()
    var keyword by rememberSaveable { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(keyword) { delay(250); query = keyword.trim() }
    val catalog = remember(server) { FilterCatalog(server) }
    val flow = remember(server, query) { Pager(PagingConfig(100, enablePlaceholders = false)) { AlbumPagingSource { cursor -> catalog.tags(query, cursor) } }.flow }
    val rows = flow.collectAsLazyPagingItems()
    Column(Modifier.fillMaxSize().background(MediaColors.background).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).testTag("photo-tags-panel")) {
        OutlinedTextField(keyword, { if (it.length <= 120) keyword = it }, placeholder = { Text("搜索标签", fontSize = 14.sp) }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).testTag("detail-tag-search"), enabled = enabled, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MediaColors.text, unfocusedTextColor = MediaColors.text,
                cursorColor = MediaColors.text, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(.08f), unfocusedContainerColor = Color.White.copy(.08f),
                focusedPlaceholderColor = MediaColors.secondary, unfocusedPlaceholderColor = MediaColors.secondary))
        LazyColumn(Modifier.weight(1f).testTag("detail-tag-picker"), contentPadding = PaddingValues(bottom = 24.dp)) {
            val visibleCurrent = current.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            if (visibleCurrent.isNotEmpty()) item { Text("已添加", color = MediaColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            items(visibleCurrent, key = { "selected-${it.id}" }) { tag ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(tag.name, color = MediaColors.text, fontSize = 14.sp, lineHeight = 20.sp)
                        Text(when (tag.source) { "AI" -> "AI 标签"; "RULE" -> "规则标签"; "MANUAL" -> "人工标签"; else -> "标签" },
                            color = MediaColors.secondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    IconButton(onClick = { onRemove(tag.id) }, enabled = enabled, modifier = Modifier.testTag("remove-photo-tag-${tag.id}")) {
                        Icon(Icons.Rounded.Close, "移除标签：${tag.name}", Modifier.size(18.dp), tint = MediaColors.secondary)
                    }
                }
            }
            if (rows.itemSnapshotList.items.any { it.id !in selected }) item {
                Text("标签库", color = MediaColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            } else if (rows.loadState.refresh is LoadState.NotLoading) item {
                Text(if (query.isBlank()) "没有其他标签，输入名称可搜索或新建" else "没有其他匹配标签", color = MediaColors.secondary,
                    fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(rows.itemCount, key = rows.itemKey { it.id }) { index -> rows[index]?.takeIf { it.id !in selected }?.let { tag ->
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled && tag.id !in selected) { onPick(tag.id) }
                    .heightIn(min = 56.dp).testTag("pick-tag-${tag.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.name, Modifier.weight(1f), color = MediaColors.text, fontSize = 14.sp, lineHeight = 20.sp)
                    Icon(Icons.Rounded.Add, "添加", Modifier.size(18.dp), tint = MediaColors.secondary)
                }
            } }
            if (rows.loadState.refresh is LoadState.Loading || rows.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (rows.loadState.refresh as? LoadState.Error) ?: (rows.loadState.append as? LoadState.Error)
            if (failure != null) item { TextButton(onClick = rows::retry) { Text("加载失败，重试", color = MediaColors.text) } }
            if (query.isNotBlank() && query == keyword.trim() && rows.loadState.refresh is LoadState.NotLoading && rows.itemSnapshotList.items.none { it.name == query } && current.none { it.name == query }) item {
                TextButton(onClick = { onCreate(query) }, enabled = enabled) { Text("新建“$query”并添加", color = MediaColors.text) }
            }
        }
    }
}
