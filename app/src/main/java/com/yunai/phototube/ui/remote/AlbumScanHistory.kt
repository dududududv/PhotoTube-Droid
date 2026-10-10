package com.yunai.phototube.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.state.rememberConfigurationPaging
import com.yunai.phototube.ui.state.rememberConfigurationState
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.GlassSettingsAction
import org.json.JSONObject

@Composable
internal fun AlbumScanHistory(repository: AlbumRepository, album: RemoteAlbum, onClose: () -> Unit) {
    val flow = rememberConfigurationPaging(repository.server to album.id) {
        Pager(PagingConfig(100, initialLoadSize = 100, maxSize = 500, enablePlaceholders = false)) {
            AlbumPagingSource { _ -> repository.scanHistory(album.id, null, 1).let { AlbumPage(it.items.take(1), null) } }
        }.flow
    }
    val runs = flow.collectAsLazyPagingItems()
    var selected by rememberConfigurationState(repository.server to album.id) { mutableStateOf<JSONObject?>(null) }
    val colors = AppTheme.colors
    ManagementPage("最近一次扫描", onClose, scrollable = false, showTitle = false) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("scan-history-list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ManagementTitle("最近一次扫描")
                Text(album.name, color = colors.secondary, modifier = Modifier.padding(vertical = 12.dp))
                GlassSettingsAction("刷新记录",runs::refresh,enabled=runs.loadState.refresh !is LoadState.Loading)
            }
            items(runs.itemCount, key = runs.itemKey { it.getString("id") }) { index -> runs[index]?.let { run ->
                val failed = run.getInt("failedPaths")
                val offline = run.getInt("offlinePaths")
                val needsAttention = failed > 0 || offline > 0 || run.getString("state") in setOf("PARTIAL", "FAILED", "OFFLINE")
                Column(Modifier.fillMaxWidth().testTag("scan-history-${run.getString("id")}").clip(RoundedCornerShape(24.dp)).background(colors.group).clickable { selected = run }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(albumRunLabel(run.getString("state")), color = colors.text, fontWeight = FontWeight.SemiBold)
                    Text(taskTimestamp(run.getString("createdAt")), color = colors.secondary)
                    Text("成功 ${run.getInt("succeededPaths")}", color = colors.secondary)
                    if (needsAttention) {
                        Row(Modifier.fillMaxWidth().testTag("scan-history-attention")
                            .background(colors.warning.copy(alpha = .10f), RoundedCornerShape(12.dp)).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = colors.warning)
                            Text(buildList {
                                add("需要关注")
                                if (failed > 0) add("失败 $failed")
                                if (offline > 0) add("离线 $offline")
                            }.joinToString(" · "), color = colors.warning, fontWeight = FontWeight.Medium)
                        }
                    }
                    run.nullString("error")?.let { Text(it, color = colors.text) }
                }
            } }
            if (runs.loadState.refresh is LoadState.Loading || runs.loadState.append is LoadState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val failure = (runs.loadState.refresh as? LoadState.Error) ?: (runs.loadState.append as? LoadState.Error)
            if (failure != null) item { Text(failure.error.message ?: "读取失败", color = colors.text); GlassSettingsAction("重试",runs::retry) }
            if (runs.loadState.refresh is LoadState.NotLoading && runs.itemCount == 0) item { Text("尚无扫描记录", color = colors.secondary) }
        }
    }
    selected?.let { run ->
        AlbumScanDialog(repository, album, onClose = { selected = null; runs.refresh() }, historyRun = run)
    }
}
