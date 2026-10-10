package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*

@Composable
internal fun ChangePasswordScreen(repository: SystemRepository, onSignedOut: () -> Unit, onClose: () -> Unit) {
    // Credentials deliberately stay in composition memory, never SavedState or preferences.
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var confirmSubmit by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var uncertain by rememberSaveable { mutableStateOf(false) }
    var retryAfter by rememberSaveable { mutableLongStateOf(0L) }
    var waitSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(retryAfter) {
        do {
            val remaining = (retryAfter - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            waitSeconds = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
            if (remaining > 0) delay(minOf(remaining, 1000L))
        } while (remaining > 0)
    }
    val scope = rememberCoroutineScope()
    val currentLength = current.codePointCount(0, current.length)
    val newLength = replacement.codePointCount(0, replacement.length)
    val valid = currentLength in 1..1024 && newLength in 12..1024 && confirmation == replacement
    fun submit() {
        confirmSubmit=false
            busy = true; error = null; uncertain = true
            scope.launch {
                try {
                    repository.changePassword(current, replacement)
                    withContext(Dispatchers.Main.immediate) {
                        current = ""; replacement = ""; confirmation = ""; confirmSubmit = false
                        onSignedOut()
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (limited: PasswordRateLimit) {
                    withContext(Dispatchers.Main.immediate) {
                        uncertain = false
                        val now = android.os.SystemClock.elapsedRealtime()
                        retryAfter = now + limited.waitMillis.coerceAtMost(Long.MAX_VALUE - now)
                        error = "密码验证过于频繁，请稍后再试"
                        confirmSubmit = false
                    }
                }
                catch (problem: Exception) {
                    withContext(Dispatchers.Main.immediate) {
                        // Only an explicit rejection establishes that another attempt is safe.
                        uncertain = problem !is ServerError || problem.code !in setOf("INVALID_CREDENTIALS", "VALIDATION_ERROR", "RATE_LIMITED", "BAD_REQUEST")
                        error = if (problem is ServerError && problem.code == "INVALID_CREDENTIALS") "当前密码不正确，请重新输入" else "修改未完成或结果未确认，请核对后再操作"
                        confirmSubmit = false
                    }
                } finally { withContext(Dispatchers.Main.immediate) { busy = false } }
            }
    }
    ManagementPage("修改密码", onClose, dismissEnabled = !busy, overlay={scene ->
        if(confirmSubmit) com.yunai.phototube.ui.GlassConfirmation(scene,title="修改密码并退出登录？",
            message="包括本机在内的所有登录会话都将失效，下次需要使用新密码登录。",confirmLabel="确认修改",
            onDismiss={confirmSubmit=false},onConfirm=::submit)
    }) {
        Text("修改成功后，所有设备都会退出登录。新密码至少 12 个字符，空格会原样保留。", color = AppTheme.colors.secondary)
        ManagementGroup("验证与新密码") {
            @Composable fun field(value: String, update: (String) -> Unit, label: String, tag: String) {
                com.yunai.phototube.ui.GlassTextField(value, update, label,
                    modifier=Modifier.testTag(tag), enabled=!busy && !uncertain,
                    visualTransformation=PasswordVisualTransformation(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false))
            }
            field(current, { current = it; error = null }, "当前密码", "password-current")
            field(replacement, { replacement = it; error = null }, "新密码", "password-new")
            if (replacement.isNotEmpty() && newLength !in 12..1024) Text("请输入 12–1024 个字符", color = AppTheme.colors.secondary)
            field(confirmation, { confirmation = it; error = null }, "确认新密码", "password-confirm")
            if (confirmation.isNotEmpty() && confirmation != replacement) Text("两次新密码不一致", color = AppTheme.colors.secondary)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = AppTheme.colors.text) }
        if (waitSeconds > 0) Text("请在 $waitSeconds 秒后重新验证", color = AppTheme.colors.secondary)
        GlassSettingsAction("修改并退出全部设备", { confirmSubmit = true }, enabled = valid && !busy && !uncertain && waitSeconds == 0L)
        if (uncertain && !busy) {
            Text("请求结果尚未确认，请重新登录核对新密码。为避免重复操作，本次不再提交。", color = AppTheme.colors.secondary)
            GlassSettingsAction("返回登录核对", {
                current = ""; replacement = ""; confirmation = ""
                repository.clearLocalSession()
                onSignedOut()
            })
        }
    }

}

@Composable
internal fun DiagnosticsScreen(repository: SystemRepository, onClose: () -> Unit) {
    var result by remember { mutableStateOf<SystemDiagnostics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(retry) {
        loading = true; error = null
        try { val value = repository.diagnostics(); withContext(Dispatchers.Main.immediate) { result = value } }
        catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            withContext(Dispatchers.Main.immediate) {
                error = if (failure is ServerError && failure.code in setOf("NOT_FOUND", "HTTP_404")) {
                    "当前服务器未提供系统诊断，请更新后端后重试。"
                } else if (result == null) {
                    "暂时无法读取诊断，请稍后重试。"
                } else {
                    "诊断读取失败，下方已有数据可能已过期。"
                }
            }
        }
        finally { withContext(Dispatchers.Main.immediate) { loading = false } }
    }
    ManagementPage("系统诊断", onClose) {
        Text("只读摘要，不会调用 AI，也不会清理或修改服务器文件。", color = AppTheme.colors.secondary)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = AppTheme.colors.text) }
        GlassSettingsAction("刷新诊断", { retry++ }, enabled = !loading)
        result?.let { value ->
            Text("更新于 ${taskTimestamp(value.generatedAt)}", color = AppTheme.colors.secondary)
            ManagementGroup("构建与数据库") {
                Text("构建版本：${value.version}", color = AppTheme.colors.text)
                Text("数据库版本：${value.schemaVersion ?: "空库"}", color = AppTheme.colors.secondary)
                Text(if (value.schemaDirty) "数据库迁移尚未完成" else "数据库迁移状态正常", color = AppTheme.colors.secondary)
            }
            ManagementGroup("服务器缓存") {
                fun bytes(amount: Long?) = amount?.let { "%.1f MiB".format(it / 1048576.0) } ?: "统计不可用"
                Text("缩略图与编辑预览：${bytes(value.thumbnailBytes)}", color = AppTheme.colors.text)
                Text("视频 AI 拼图：${bytes(value.videoPreviewBytes)}", color = AppTheme.colors.text)
                Text("缓存上限：${value.limitBytes?.let(::bytes) ?: "未配置"}", color = AppTheme.colors.secondary)
            }
            ManagementGroup("部署能力") {
                Text("AI：${if (value.aiConfigured) "配置齐全，尚未验证连接" else "配置未齐全"}", color = AppTheme.colors.text)
                Text("原文件删除：${if (value.originalDeleteEnabled) "已启用，操作仍需验证权限" else "未启用"}", color = AppTheme.colors.secondary)
                Text("XMP 导出：${if (value.xmpExportAvailable) "已配置" else "未配置"}", color = AppTheme.colors.secondary)
            }
        }
    }
}
