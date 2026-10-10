package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*
import java.time.Instant

@Composable
internal fun OriginalDeleteScreen(server: PhotoTubeServer, initialIds: Set<String>, onResult: (AssetActionResult) -> Unit, onClose: () -> Unit) {
    val repository = remember(server) { OriginalDeleteRepository(server) }
    var ids by remember { mutableStateOf(initialIds.toSet()) }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var capabilityError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<OriginalDeleteConfirmation?>(null) }
    var result by remember { mutableStateOf<AssetActionResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var expired by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var waitSeconds by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_PAUSE) {
            generation++; password=""; phrase=""; confirmation=null
            error="确认信息已清除，请重新验证密码后继续"
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reload) {
        enabled=null; capabilityError=false
        try { val value=repository.enabled(); withContext(Dispatchers.Main.immediate) { enabled=value } }
        catch(cancel:CancellationException) {throw cancel}
        catch(_:Exception) {withContext(Dispatchers.Main.immediate) {capabilityError=true}}
    }
    LaunchedEffect(confirmation) {
        val frozen=confirmation ?: return@LaunchedEffect
        while(true) { expired=!Instant.now().isBefore(frozen.expiresAt); if(expired) break; delay(1000) }
    }
    ManagementPage("删除 NAS 原文件", onClose, dismissEnabled=!busy, secure=true) {
        Text("此操作会永久删除 NAS 文件及对应记录，无法从回收站恢复。", color=AppTheme.colors.text)
        if(enabled==null && !capabilityError) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(enabled==false) Text("服务器未启用原文件删除。",color=AppTheme.colors.secondary)
        if(capabilityError) {Text("无法确认删除能力，请重试。",color=AppTheme.colors.secondary);GlassSettingsAction("重新检查", {reload++})}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it,color=AppTheme.colors.text)}
        result?.let { value -> ManagementGroup("最近确认的整批结果") {
            Text("成功 ${value.succeeded.size} 项 · 失败 ${value.failed.size} 项",color=AppTheme.colors.text)
            value.failed.forEach { failure -> Text("${failure.message}（${failure.code}）",color=AppTheme.colors.secondary) }
            Text("失败或未确认的项目需重新验证后核对，不要用清除记录绕过未完成的原文件删除。",color=AppTheme.colors.secondary)
        } }
        if(enabled==true && result!=null && ids.isEmpty() && confirmation==null) {
            Text("本批次已完成",color=AppTheme.colors.text)
            GlassSettingsAction("完成", onClose)
        } else if(enabled==true) {
            val frozen=confirmation
            if(frozen==null) ManagementGroup("验证当前账号") {
                Text("本次选择 ${ids.size} 项，每次最多选择 20 项。恢复未完成批次时，服务端可能返回更多文件，下一步会展示完整清单。",color=AppTheme.colors.secondary)
                GlassTextField(password,{password=it;error=null},"当前密码",singleLine=true,
                    enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth().testTag("delete-original-password"))
                if(waitSeconds>0) Text("请在 $waitSeconds 秒后重新验证",color=AppTheme.colors.secondary)
                GlassSettingsAction("验证并查看文件清单", {
                    val supplied=password;val requested=generation
                    password="";busy=true;error=null
                    scope.launch {
                        try {val value=repository.prepare(ids,supplied);withContext(Dispatchers.Main.immediate) {
                            if(requested==generation) {confirmation=value;phrase="";result=null;expired=!Instant.now().isBefore(value.expiresAt)}
                        }}
                        catch(cancel:CancellationException) {throw cancel}
                        catch(limited:PasswordRateLimit) {withContext(Dispatchers.Main.immediate) {
                            error="验证过于频繁，请稍后重试"
                            scope.launch {val start=android.os.SystemClock.elapsedRealtime();do {
                                val remaining=(limited.waitMillis-(android.os.SystemClock.elapsedRealtime()-start)).coerceAtLeast(0)
                                waitSeconds=remaining/1000+if(remaining%1000>0) 1 else 0
                                if(remaining>0) delay(minOf(remaining,1000L))
                            } while(remaining>0)}
                        }}
                        catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {error=if(problem is ServerError && problem.code=="INVALID_CREDENTIALS") "当前密码不正确" else problem.message ?: "无法准备删除，请重新验证"}}
                        finally {withContext(Dispatchers.Main.immediate) {busy=false}}
                    }
                },enabled=!busy && waitSeconds==0L && ids.size in 1..20 && password.codePointCount(0,password.length) in 1..1024,modifier=Modifier.fillMaxWidth())
            } else {
                ManagementGroup("服务端确认的文件清单") {
                    Text("${frozen.assetCount} 项资产 · ${frozen.fileCount} 个文件 · ${frozen.totalBytes} 字节",color=AppTheme.colors.text)
                    Text("凭证有效至 ${taskTimestamp(frozen.expiresAt.toString())}",color=AppTheme.colors.secondary)
                    if(frozen.items.map {it.assetId}.toSet()!=ids) Text("这是服务端返回的完整待处理批次，与刚才的选择不同。请核对全部文件。",color=AppTheme.colors.text)
                    frozen.items.forEach { item -> Column {
                        Text(item.name,color=AppTheme.colors.text)
                        Text(item.path,color=AppTheme.colors.secondary)
                        Text("${item.bytes} 字节",color=AppTheme.colors.secondary)
                    } }
                }
                if(expired) Text("确认凭证已过期，请重新验证。",color=AppTheme.colors.text)
                Text("请逐字输入：${frozen.phrase}",color=AppTheme.colors.text)
                GlassTextField(phrase,{phrase=it},"确认短语",enabled=!busy && !expired,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("delete-original-phrase"))
                GlassSettingsAction("永久删除清单中的原文件", {
                    val supplied=phrase;phrase="";busy=true;error=null
                    scope.launch {
                        try {val value=repository.execute(frozen,supplied);withContext(Dispatchers.Main.immediate) {
                            // Replacement, not addition: replay returns the saved whole-batch result.
                            result=value;server.imageLoader.memoryCache?.clear();onResult(value)
                            val remaining=frozen.items.map {it.assetId}.toSet()-value.succeeded+value.failed.map {it.id}
                            ids=remaining.take(20).toSet()
                            if(remaining.isEmpty()) confirmation=null
                        }}
                        catch(cancel:CancellationException) {throw cancel}
                        catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {
                            if(problem is ServerError && problem.code=="CONFIRMATION_REQUIRED") {
                                ids=(frozen.items.map {it.assetId}.toSet()-result?.succeeded.orEmpty()).take(20).toSet()
                                confirmation=null;generation++
                                error="${problem.message}。请重新验证密码并核对完整清单。"
                            } else error="执行结果未完全确认：${problem.message ?: "连接中断"}。凭证有效时可重新输入同一短语核对整批结果；也可重新验证密码恢复未完成批次。"
                        }}
                        finally {withContext(Dispatchers.Main.immediate) {busy=false}}
                    }
                },enabled=!busy && !expired && phrase==frozen.phrase,destructive=true,modifier=Modifier.fillMaxWidth())
                GlassSettingsAction("重新验证未完成项", {
                    ids=(frozen.items.map {it.assetId}.toSet()-result?.succeeded.orEmpty()).take(20).toSet()
                    confirmation=null;phrase="";password="";generation++;error=null
                },enabled=!busy)
            }
        }
    }
}
