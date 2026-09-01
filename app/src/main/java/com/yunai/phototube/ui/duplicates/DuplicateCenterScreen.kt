package com.yunai.phototube.ui.duplicates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.duplicate.DuplicateGroup
import com.yunai.phototube.data.duplicate.DuplicateRepository
import com.yunai.phototube.data.remote.ApiFailure
import com.yunai.phototube.data.session.SessionRepository
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.ui.photos.toTimelineError
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.theme.PhotoTubeColors
import java.util.Locale

@Composable
fun DuplicateCenterRoute(
    repository: DuplicateRepository,
    sessionRepository: SessionRepository,
    refreshRevision: Int,
    onPrivateScopeChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val model: DuplicateCenterViewModel = viewModel(
        key = "duplicate-center",
        factory = DuplicateCenterViewModel.factory(repository, sessionRepository),
    )
    val state by model.uiState.collectAsStateWithLifecycle()
    val groups = model.groups.collectAsLazyPagingItems()
    val members = model.members.collectAsLazyPagingItems()
    DisposableEffect(model) {
        onDispose(model::onRouteLeft)
    }
    BackHandler {
        if (state.selectedGroup != null) model.closeGroup() else onBack()
    }
    LaunchedEffect(state.privateScope) { onPrivateScopeChanged(state.privateScope) }
    LaunchedEffect(refreshRevision, state.refreshRevision) {
        if (refreshRevision > 0 || state.refreshRevision > 0) {
            groups.refresh()
            members.refresh()
        }
    }
    val pagingFailure = listOf(
        groups.loadState.refresh,
        groups.loadState.append,
        members.loadState.refresh,
        members.loadState.append,
    )
        .filterIsInstance<LoadState.Error>()
        .mapNotNull { it.error as? ApiFailure }
        .firstOrNull { it.error.code == "PRIVATE_ACCESS_REQUIRED" }
    LaunchedEffect(pagingFailure?.error?.code) {
        if (pagingFailure != null) model.expirePrivateAccess()
    }

    when {
        state.privateScope && !state.isPrivateUnlocked -> DuplicatePrivateGate(
            state = state,
            onPasswordChanged = model::updatePassword,
            onUnlock = model::unlockPrivateAccess,
            onUseNormalScope = { model.setPrivateScope(false) },
            onBack = onBack,
            modifier = modifier,
        )
        state.selectedGroup != null -> {
            val group = requireNotNull(state.selectedGroup)
            DuplicateGroupDetail(
                group = group,
                members = members,
                serverRoot = model.serverRoot,
                busy = state.busyHash == group.contentHash,
                onBack = model::closeGroup,
                onReview = { model.review(group) },
                onOpenAsset = onOpenAsset,
                modifier = modifier,
            )
        }
        else -> DuplicateGroupList(
            state = state,
            groups = groups,
            serverRoot = model.serverRoot,
            onBack = onBack,
            onPrivateScopeChanged = model::setPrivateScope,
            onIncludeReviewedChanged = model::setIncludeReviewed,
            onLock = model::lockPrivateAccess,
            onOpenGroup = model::openGroup,
            onReview = model::review,
            modifier = modifier,
        )
    }
}

@Composable
private fun DuplicatePrivateGate(
    state: DuplicateCenterUiState,
    onPasswordChanged: (String) -> Unit,
    onUnlock: () -> Unit,
    onUseNormalScope: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Spacer(Modifier.height(52.dp))
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("解锁私密重复项", style = MaterialTheme.typography.headlineLarge)
        Text(
            "普通与私密内容必须分开统计。请输入当前账号口令，授权事实仍由 PhotoTube 服务端会话决定。",
            color = PhotoTubeColors.Muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("账号口令") },
        )
        state.error?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = onUnlock,
            enabled = state.password.isNotEmpty() && !state.isUnlocking && !state.isCheckingPrivateAccess,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            if (state.isUnlocking || state.isCheckingPrivateAccess) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else Text("解锁")
        }
        TextButton(onClick = onUseNormalScope, modifier = Modifier.fillMaxWidth()) {
            Text("查看普通重复项")
        }
    }
}

@Composable
private fun DuplicateGroupList(
    state: DuplicateCenterUiState,
    groups: LazyPagingItems<DuplicateGroup>,
    serverRoot: ServerRoot,
    onBack: () -> Unit,
    onPrivateScopeChanged: (Boolean) -> Unit,
    onIncludeReviewedChanged: (Boolean) -> Unit,
    onLock: () -> Unit,
    onOpenGroup: (DuplicateGroup) -> Unit,
    onReview: (DuplicateGroup) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text("完全重复项", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.privateScope) {
                IconButton(onClick = onLock, enabled = !state.isLocking) {
                    if (state.isLocking) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Lock, contentDescription = "锁定私密重复项")
                }
            }
        }
        Text(
            "仅显示 SHA-256 字节完全相同的在线文件。PhotoTube 不会自动删除或移动 NAS 原文件。",
            color = PhotoTubeColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !state.privateScope,
                onClick = { onPrivateScopeChanged(false) },
                label = { Text("普通") },
            )
            FilterChip(
                selected = state.privateScope,
                onClick = { onPrivateScopeChanged(true) },
                label = { Text("私密") },
            )
            FilterChip(
                selected = state.includeReviewed,
                onClick = { onIncludeReviewedChanged(!state.includeReviewed) },
                label = { Text("包含已处理") },
            )
        }
        state.error?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
        }
        when (val refresh = groups.loadState.refresh) {
            is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Error -> PagedFailure(refresh.error, groups::retry)
            is LoadState.NotLoading -> {
                if (groups.itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有待处理的完全重复组", color = PhotoTubeColors.Muted)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(count = groups.itemCount, key = { groups[it]?.contentHash ?: "placeholder-$it" }) { index ->
                            groups[index]?.let { group ->
                                DuplicateGroupCard(
                                    group = group,
                                    serverRoot = serverRoot,
                                    busy = state.busyHash == group.contentHash,
                                    actionsEnabled = state.busyHash == null,
                                    onOpen = { onOpenGroup(group) },
                                    onReview = { onReview(group) },
                                )
                            }
                        }
                        PagingAppend(groups)
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    serverRoot: ServerRoot,
    busy: Boolean,
    actionsEnabled: Boolean,
    onOpen: () -> Unit,
    onReview: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = group.representative.thumbnailUrl(serverRoot),
                contentDescription = group.representative.fileName,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE9EBEF)),
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(Color(0xFFE9EBEF)),
                error = ColorPainter(Color(0xFFDADDE3)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(group.representative.fileName, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                Text("${group.copyCount} 个完全相同副本", color = PhotoTubeColors.Muted, fontSize = 13.sp)
                Text("理论可释放 ${formatBytes(group.reclaimableBytes)}", color = PhotoTubeColors.Muted, fontSize = 13.sp)
                TextButton(onClick = onReview, enabled = actionsEnabled) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(if (group.reviewedAt == null) "保留全部副本" else "恢复提醒")
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupDetail(
    group: DuplicateGroup,
    members: LazyPagingItems<MediaAsset>,
    serverRoot: ServerRoot,
    busy: Boolean,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onOpenAsset: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(PhotoTubeColors.Background).statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回重复组")
            }
            Text("${group.copyCount} 个完全相同副本", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(
            "这只是路径核对与提醒处置，不会删除或移动任何 NAS 文件。",
            color = PhotoTubeColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Button(
            onClick = onReview,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (group.reviewedAt == null) "确认保留全部副本" else "恢复重复提醒")
        }
        when (val refresh = members.loadState.refresh) {
            is LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Error -> PagedFailure(refresh.error, members::retry)
            is LoadState.NotLoading -> {
                if (members.itemCount == 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个组已不足两个在线副本，请返回刷新", color = PhotoTubeColors.Muted)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(count = members.itemCount, key = { members[it]?.id ?: "placeholder-$it" }) { index ->
                            members[index]?.let { asset ->
                                DuplicateAssetRow(asset, serverRoot) { onOpenAsset(asset.id) }
                            }
                        }
                        PagingAppend(members)
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateAssetRow(asset: MediaAsset, serverRoot: ServerRoot, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = asset.thumbnailUrl(serverRoot, ThumbnailSize.SM),
            contentDescription = asset.fileName,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE9EBEF)),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFFE9EBEF)),
            error = ColorPainter(Color(0xFFDADDE3)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(asset.fileName, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Text(asset.relativePath, maxLines = 2, color = PhotoTubeColors.Muted, fontSize = 12.sp)
            Text(formatBytes(asset.fileSize), color = PhotoTubeColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PagedFailure(failure: Throwable, onRetry: () -> Unit) {
    val error = failure.toTimelineError()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(error.message, error.logId, Modifier.fillMaxWidth())
        Button(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) { Text("重试") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.PagingAppend(items: LazyPagingItems<*>) {
    when (items.loadState.append) {
        is LoadState.Loading -> item {
            Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        is LoadState.Error -> item {
            TextButton(onClick = items::retry, modifier = Modifier.fillMaxWidth()) {
                Text("加载更多失败，点击重试")
            }
        }
        is LoadState.NotLoading -> Unit
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}
