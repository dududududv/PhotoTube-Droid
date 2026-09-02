package com.yunai.phototube.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.home.HomeFeed
import com.yunai.phototube.data.job.JobSummary
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import com.yunai.phototube.ui.components.AlbumTopBar
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.FloatingAlbumDock
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.photos.TimelineError
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import dev.chrisbanes.haze.hazeSource

@Composable
fun HomeFeedScreen(
    state: HomeFeedState,
    serverRoot: ServerRoot,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilter: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenCreation: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenJobs: () -> Unit,
    onSwitchLayout: () -> Unit,
    onOpenLibraryMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberAndroid16HazeState()
    Android16HazeProvider(state = hazeState) {
        Box(modifier.fillMaxSize().background(PhotoTubeColors.Background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = PhotoTubeDimens.ScreenPadding,
                    end = PhotoTubeDimens.ScreenPadding,
                    bottom = 116.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                item {
                    Column(Modifier.statusBarsPadding()) {
                        Spacer(Modifier.height(20.dp))
                        AlbumTopBar(
                            onProfileClick = onOpenCollections,
                            onSearchClick = onOpenSearch,
                            onFilterClick = onOpenFilter,
                        )
                        Spacer(Modifier.height(30.dp))
                        Text("照片", style = MaterialTheme.typography.displayLarge, color = PhotoTubeColors.Ink)
                        Text("首页概览", color = PhotoTubeColors.Muted, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
                when (state) {
                    HomeFeedState.Loading -> item { HomeLoading() }
                    is HomeFeedState.Error -> item { HomeFailure(state.error, onRetry) }
                    is HomeFeedState.Ready -> homeSections(
                        feed = state.feed,
                        serverRoot = serverRoot,
                        onOpenAsset = onOpenAsset,
                        onOpenAlbum = onOpenAlbum,
                        onOpenJobs = onOpenJobs,
                    )
                }
            }
            FloatingAlbumDock(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedItem = 0,
                onItemClick = { index ->
                    if (index == 1) onOpenCollections()
                },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeSections(
    feed: HomeFeed,
    serverRoot: ServerRoot,
    onOpenAsset: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenJobs: () -> Unit,
) {
    if (feed.isEmpty) {
        item {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Text("暂无首页内容", color = PhotoTubeColors.Muted)
            }
        }
        return
    }
    if (feed.onThisDay.isNotEmpty()) {
        item {
            HomeAssetSection("往年今日", "同月同日的旧时光", feed.onThisDay, serverRoot, onOpenAsset)
        }
    }
    if (feed.recentPhotos.isNotEmpty()) {
        item {
            HomeAssetSection("最近拍摄", null, feed.recentPhotos, serverRoot, onOpenAsset)
        }
    }
    if (feed.recentImports.isNotEmpty()) {
        item {
            HomeAssetSection("最近入库", null, feed.recentImports, serverRoot, onOpenAsset)
        }
    }
    if (feed.frequentAlbums.isNotEmpty()) {
        item {
            HomeAlbumSection(feed.frequentAlbums, serverRoot, onOpenAlbum)
        }
    }
    if (feed.jobSummary.items.isNotEmpty()) {
        item {
            HomeJobSection(feed.jobSummary.items, onOpenJobs)
        }
    }
}

@Composable
private fun HomeAssetSection(
    title: String,
    subtitle: String?,
    assets: List<MediaAsset>,
    serverRoot: ServerRoot,
    onOpenAsset: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeading(title, subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(assets, key = MediaAsset::id) { asset ->
                AsyncImage(
                    model = asset.thumbnailUrl(serverRoot, ThumbnailSize.MD),
                    contentDescription = asset.fileName,
                    modifier = Modifier
                        .size(width = 148.dp, height = 172.dp)
                        .clip(RoundedCornerShape(PhotoTubeDimens.PhotoRadius))
                        .background(Color(0xFFE9EBEF))
                        .clickable { onOpenAsset(asset.id) },
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFFE9EBEF)),
                    error = ColorPainter(Color(0xFFDADDE3)),
                )
            }
        }
    }
}

@Composable
private fun HomeAlbumSection(
    albums: List<Album>,
    serverRoot: ServerRoot,
    onOpenAlbum: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeading("常用图集", "按当前有效成员数量排序")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(albums, key = Album::id) { album ->
                Box(
                    modifier = Modifier
                        .size(width = 204.dp, height = 142.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFE9EBEF))
                        .clickable { onOpenAlbum(album.id) },
                ) {
                    AsyncImage(
                        model = album.cover?.thumbnailUrl(serverRoot),
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(Color(0xFFE9EBEF)),
                        error = ColorPainter(Color(0xFFDADDE3)),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.46f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.assetCount} 项", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeJobSection(items: List<JobSummary>, onOpenJobs: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeSectionHeading("后台任务", "与任务中心使用同一统计口径")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.kind.name }) { summary ->
                Column(
                    modifier = Modifier
                        .width(190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .clickable(onClick = onOpenJobs)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(summary.kind.label(), fontWeight = FontWeight.SemiBold)
                    val progress = if (summary.total == null) {
                        "已发现 ${summary.discovered} 个"
                    } else {
                        "${summary.finished} / ${summary.total}"
                    }
                    Text(progress, color = PhotoTubeColors.Muted, fontSize = 13.sp)
                    if (summary.running > 0) Text("正在运行 ${summary.running}", color = PhotoTubeColors.Accent, fontSize = 12.sp)
                    if (summary.failed > 0) Text("失败 ${summary.failed}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String, subtitle: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = PhotoTubeColors.Ink, modifier = Modifier.weight(1f))
        subtitle?.let { Text(it, color = PhotoTubeColors.Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun HomeLoading() {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun HomeFailure(error: TimelineError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiagnosticErrorText(
            message = error.message,
            logId = error.logId,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("重试") }
    }
}

private fun com.yunai.phototube.data.job.JobKind.label(): String = when (this) {
    com.yunai.phototube.data.job.JobKind.SCAN_LIBRARY -> "媒体扫描"
    com.yunai.phototube.data.job.JobKind.EXTRACT_METADATA -> "元数据提取"
    com.yunai.phototube.data.job.JobKind.GENERATE_THUMBNAIL -> "缩略图生成"
    com.yunai.phototube.data.job.JobKind.TRANSCODE_VIDEO -> "视频转码"
    com.yunai.phototube.data.job.JobKind.AI_INDEX -> "AI 索引"
    com.yunai.phototube.data.job.JobKind.TAG_SCAN -> "AI 标签扫描"
    com.yunai.phototube.data.job.JobKind.DERIVATIVE_PRUNE -> "派生缓存清理"
    com.yunai.phototube.data.job.JobKind.ALBUM_PATH_SYNC -> "路径相册同步"
    com.yunai.phototube.data.job.JobKind.XMP_EXPORT -> "XMP 导出"
}
