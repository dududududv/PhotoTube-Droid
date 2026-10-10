package com.yunai.phototube.ui.remote

import com.yunai.phototube.ui.media.mediaBounds

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.*
import androidx.paging.compose.*
import coil3.compose.AsyncImage
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Also protects child dialogs and recent-task snapshots before any private pixels are shown. */
@Composable
internal fun PrivateAccessScreen(server: PhotoTubeServer, onClose: () -> Unit, content: @Composable (PhotoTubeServer, () -> Unit) -> Unit) {
    val controller = remember(server) { PrivateAccessController(server) }
    val lease by controller.lease.collectAsState()
    val lockMessage by controller.lockMessage.collectAsState()
    val activity = LocalContext.current.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var waitSeconds by remember { mutableLongStateOf(0) }
    val scope = rememberCoroutineScope()
    DisposableEffect(controller, lifecycle, activity) {
        val wasSecure = ((activity?.window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                password = ""; error = null
                controller.lock("离开应用后已自动锁定")
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            password = ""
            lifecycle.removeObserver(observer)
            controller.close()
            if (!wasSecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val current = lease
    if (current == null) ManagementPage("私密空间", onClose, secure = true) {
        ManagementGroup("解锁查看") {
            Text("输入当前账号密码。离开应用后自动锁定，授权最长持续 15 分钟。", color = AppTheme.colors.secondary)
            GlassTextField(password, { password = it; error = null }, "账号密码", singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("private-password"))
            GlassSettingsAction("解锁", {
                val supplied = password
                password = ""; busy = true; error = null
                scope.launch {
                    try { controller.unlock(supplied) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (limited: PasswordRateLimit) {
                        withContext(Dispatchers.Main.immediate) {
                            error = "验证过于频繁，请稍后再试"
                            scope.launch {
                                val started = android.os.SystemClock.elapsedRealtime()
                                do {
                                    val remaining = (limited.waitMillis - (android.os.SystemClock.elapsedRealtime() - started)).coerceAtLeast(0)
                                    waitSeconds = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
                                    if (remaining > 0) delay(minOf(remaining, 1000L))
                                } while (remaining > 0)
                            }
                        }
                    }
                    catch (problem: Exception) { withContext(Dispatchers.Main.immediate) {
                        error = if (problem is ServerError && problem.code == "INVALID_CREDENTIALS") "密码不正确，请重新输入" else "暂时无法解锁，请稍后重试"
                        controller.lock()
                    } }
                    finally { withContext(Dispatchers.Main.immediate) { busy = false } }
                }
            }, enabled = !busy && waitSeconds == 0L && password.codePointCount(0, password.length) in 1..1024, modifier = Modifier.fillMaxWidth())
            if (waitSeconds > 0) Text("请在 $waitSeconds 秒后重新验证", color = AppTheme.colors.secondary)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: lockMessage)?.let { Text(it, color = AppTheme.colors.secondary) }
        }
    } else key(current) { content(current.server) { controller.lock("已锁定") } }
}

@Composable
internal fun PrivateSpace(server: PhotoTubeServer, onChanged: () -> Unit, onClose: () -> Unit) {
    PrivateAccessScreen(server, onClose) { isolated, lock ->
        PrivateLibrary(isolated, lock, onChanged, onClose)
    }
}

@Composable
private fun PrivateLibrary(server: PhotoTubeServer, lock: () -> Unit, onChanged: () -> Unit, onClose: () -> Unit) {
    val filters = remember { WallFilters(privateOnly = true) }
    val flow = remember(server) { Pager(PagingConfig(100, initialLoadSize = 100, maxSize = 500, enablePlaceholders = false)) { AssetPagingSource(server, filters) }.flow }
    val items = flow.collectAsLazyPagingItems()
    var count by remember { mutableStateOf<Long?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var selecting by remember { mutableStateOf(false) }
    var duplicates by remember { mutableStateOf(false) }
    var exports by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<PagedViewerSession?>(null) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    fun changed() { revision++; items.refresh(); onChanged() }
    LaunchedEffect(revision) {
        try { val amount = server.summary(filters).sumOf { it.count }; withContext(Dispatchers.Main.immediate) { count = amount } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { withContext(Dispatchers.Main.immediate) { count = null } }
    }
    ManagementPage("私密空间", onClose, scrollable = false, secure = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { GlassSettingsAction("立即锁定", lock, modifier = Modifier.fillMaxWidth()) }
            Box(Modifier.weight(1f)) { GlassSettingsAction("选择", { selecting = true },
                enabled = items.itemCount > 0 && items.loadState.refresh !is LoadState.Loading,
                modifier = Modifier.fillMaxWidth()) }
        }
        Column(Modifier.fillMaxWidth()) {
            SettingsNavigationRow("重复照片", { duplicates = true })
            SettingsNavigationRow("XMP 导出", { exports = true })
        }
        Text(count?.let { "$it 项私密照片与视频" } ?: "私密照片与视频", color = AppTheme.colors.secondary)
        BoxWithConstraints(Modifier.weight(1f)) {
            val width=maxWidth.value.coerceAtLeast(1f)
            val snapshot=items.itemSnapshotList
            val rows=remember(snapshot,width) {photoWallRows(snapshot.items.map {WallEntry.Asset(it)},width).filterIsInstance<PhotoWallRow.Photos>()}
            val rowMap=remember(rows) {rows.associateBy {it.key}}
            val listState=rememberLazyListState()
            LaunchedEffect(rowMap,listState,viewer) {
                if(viewer!=null) return@LaunchedEffect
                snapshotFlow {listState.layoutInfo.visibleItemsInfo.map {it.key}}.collect {keys ->
                    keys.forEach {key -> rowMap[key]?.photos?.forEach {photo ->
                        if(items.peek(photo.index)?.id==photo.asset.id) items[photo.index]
                    }}
                }
            }
            LazyColumn(Modifier.fillMaxSize().testTag("private-grid"),state=listState,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(rows,key={it.key}) {row ->
                    Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.photos.forEach {photo -> key(photo.asset.id) {
                            val asset=photo.asset
                            Box(Modifier.width((row.height*photo.ratio).dp).fillMaxHeight()
                                .mediaBounds(asset.id,bounds).testTag("private-asset-${asset.id}").clickable {
                                    viewer=PagedViewerSession.create(items,{it},server,asset,bounds)
                                }) {AssetThumbnail(asset,server,Modifier.fillMaxSize())}
                        }}
                    }
                }
                if(items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
                val failure=(items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
                if(failure!=null) item {Column {Text("私密内容加载失败",color=AppTheme.colors.text);GlassSettingsAction("重试",items::retry)}}
                if(items.loadState.refresh is LoadState.NotLoading && items.itemCount==0) item {Text("还没有私密内容",color=AppTheme.colors.secondary)}
            }
        }
    }
    if (duplicates) DuplicatesScreen(server,onClose={duplicates=false},onLibraryChanged=::changed)
    if (exports) XmpExportsScreen(server,onClose={exports=false})
    if (selecting) BulkAssetsScreen(server, filters, count ?: 0L, ::changed, onClose = { selecting = false })
    viewer?.let { request -> PagedMediaViewer(request, onClose = { viewer = null }, onRemoteChanged = ::changed) }
}

/** The user already confirmed the frozen operation before reaching this password gate. */
@Composable
internal fun PrivateMutationGate(server: PhotoTubeServer, onClose: () -> Unit, onAuthorized: (PhotoTubeServer) -> Unit) {
    PrivateAccessScreen(server, onClose) { isolated, _ ->
        LaunchedEffect(isolated) { onAuthorized(isolated) }
        ManagementPage("正在处理私密操作", onClose, dismissEnabled = false, secure = true) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在等待服务器确认…", color = AppTheme.colors.secondary)
        }
    }
}
