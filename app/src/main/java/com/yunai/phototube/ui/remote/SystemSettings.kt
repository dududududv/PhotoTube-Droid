package com.yunai.phototube.ui.remote

import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.GlassSwitchRow
import com.yunai.phototube.ui.GlassChoiceChip
import com.yunai.phototube.ui.GlassChoiceRow
import com.yunai.phototube.ui.GlassSettingsAction
import androidx.compose.foundation.selection.selectableGroup
import com.yunai.phototube.ui.GlassTextField

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun SystemSettings(server: PhotoTubeServer, onSignedOut: () -> Unit, onClose: () -> Unit) {
    val repository = remember(server) { SystemRepository(server) }
    var session by remember { mutableStateOf<JSONObject?>(null) }
    var health by remember { mutableStateOf<JSONObject?>(null) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var exportsOpen by rememberSaveable(server) { mutableStateOf(false) }
    var aiOpen by rememberSaveable(server) { mutableStateOf(false) }
    var passwordOpen by rememberSaveable(server) { mutableStateOf(false) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var logoutConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(retry) {
        loading = true; errors = emptyList()
        supervisorScope {
            suspend fun read(label: String, call: suspend () -> JSONObject, assign: (JSONObject) -> Unit) {
                try { val result=call(); withContext(Dispatchers.Main.immediate) { assign(result) } } catch (cancel: CancellationException) { throw cancel } catch (problem: Exception) { withContext(Dispatchers.Main.immediate) { errors = errors + "$label：${problem.message}" } }
            }
            listOf(launch { read("会话", server::session) { session = it } }, launch { read("健康状态", repository::health) { health = it } }, launch { read("即时告警", repository::status) { status = it } }).joinAll()
        }
        loading = false
    }
    ManagementPage("服务与 AI", onClose, dismissEnabled = !busy, overlay={scene ->
        if(logoutConfirm) com.yunai.phototube.ui.GlassConfirmation(scene,title="退出当前账号？",
            message="本机会清除登录会话和图片内存缓存，服务器后台任务会继续。",confirmLabel="确认退出",
            onDismiss={logoutConfirm=false},onConfirm={
                logoutConfirm=false;busy=true
                scope.launch {
                    try {repository.logout();withContext(Dispatchers.Main.immediate) {onSignedOut()}}
                    catch(cancel:CancellationException) {throw cancel}
                    catch(problem:Exception) {errors=listOf(problem.message ?: "退出失败")}
                    finally {busy=false}
                }
            })
    }) {
        Text(server.base, color = AppTheme.colors.secondary)
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        errors.forEach { Text(it, color = AppTheme.colors.text) }
        GlassSettingsAction("刷新状态", { retry++ }, enabled = !busy && !loading)
        ManagementGroup("当前会话") {
            session?.let { current ->
                Text(if (current.getBoolean("authenticated")) "已登录：${current.getJSONObject("user").getString("username")}" else "未登录", color = AppTheme.colors.text)
                Text("私密内容：${if (current.getBoolean("privateAccessUnlocked")) "已解锁" else "已锁定"}", color = AppTheme.colors.secondary)
                Text("原文件删除：${if (current.getBoolean("originalDeleteEnabled")) "部署已启用" else "部署未启用"}", color = AppTheme.colors.secondary)
            }
        }
        ManagementGroup("服务健康") {
            health?.let { value ->
                Text(if (value.getString("status") == "ok") "服务正常" else "服务降级", color = AppTheme.colors.text)
                Text("数据库：${if (value.getJSONObject("database").getBoolean("reachable")) "可用" else "不可用"}", color = AppTheme.colors.secondary)
                if (value.getJSONObject("database").optBoolean("dirty")) Text("数据库迁移未完成，请在部署端检查。", color = AppTheme.colors.text)
                Text("版本：${value.optString("version", "未知")}", color = AppTheme.colors.secondary)
                Text("XMP 导出：${if (value.getJSONObject("xmpExport").getBoolean("available")) "可用" else "未配置"}", color = AppTheme.colors.secondary)
            }
        }
        ManagementGroup("即时告警") {
            status?.let { value ->
                Text("更新于 ${taskTimestamp(value.getString("generatedAt"))}", color = AppTheme.colors.secondary)
                val alerts = value.getJSONArray("alerts")
                if (alerts.length() == 0) Text("当前没有告警", color = AppTheme.colors.text)
                for (index in 0 until alerts.length()) { val alert = alerts.getJSONObject(index)
                    Text(alert.getString("subject"), color = AppTheme.colors.text)
                    Text(alert.getString("message"), color = AppTheme.colors.secondary)
                    val unit=alert.getString("unit")
                    fun amount(field:String)=if(unit=="BYTES") "%.1f MiB".format(alert.getLong(field)/1048576.0) else "${alert.getLong(field)} 项"
                    Text("当前 ${amount("currentValue")} · 阈值 ${amount("thresholdValue")}", color=AppTheme.colors.secondary)
                }
            }
        }
        val canNavigate = !busy && session?.optBoolean("authenticated") == true
        ManagementGroup("管理") {
            SettingsNavigationRow("修改密码", { passwordOpen = true }, enabled = canNavigate)
            SettingsNavigationRow("系统诊断", { diagnosticsOpen = true }, enabled = canNavigate)
            SettingsNavigationRow("AI 供应商配置", { aiOpen = true }, enabled = canNavigate)
            SettingsNavigationRow("XMP 导出与历史", { exportsOpen = true }, enabled = canNavigate)
        }
        GlassSettingsAction("退出登录", { logoutConfirm = true }, enabled = canNavigate)
    }
    if (exportsOpen) XmpExportsScreen(server,onClose={exportsOpen=false})
    if (passwordOpen) ChangePasswordScreen(repository, onSignedOut, onClose = { passwordOpen = false })
    if (diagnosticsOpen) DiagnosticsScreen(repository, onClose = { diagnosticsOpen = false })
    if (aiOpen) AiProviderSettings(repository, onClose = { aiOpen = false })

}

/** Secrets live only in this route's memory and are released on normal dismissal. */
private class AiProviderDraft : AutoCloseable {
    val original = mutableStateOf<JSONObject?>(null)
    val url = mutableStateOf("")
    val model = mutableStateOf("")
    val thinking = mutableStateOf(false)
    val concurrency = mutableIntStateOf(2)
    val keyUpdate = mutableStateOf(KeyUpdate.KEEP)
    val key = mutableStateOf("")
    val error = mutableStateOf<String?>(null)
    val saved = mutableStateOf(false)
    override fun close() { key.value = "" }
}

@Composable
internal fun AiProviderSettings(repository: SystemRepository, onClose: () -> Unit) {
    val draft = rememberConfigurationState(repository) { AiProviderDraft() }
    var original by draft.original
    var url by draft.url
    var model by draft.model
    var thinking by draft.thinking
    var concurrency by draft.concurrency
    var keyUpdate by draft.keyUpdate
    var key by draft.key // Deliberately not saveable or persisted.
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by draft.error
    var saved by draft.saved
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun adopt(value: JSONObject) { original = value; url = value.getString("baseUrl"); model = value.getString("model"); thinking = value.optBoolean("thinkingEnabled"); concurrency = value.optInt("concurrency", 2).coerceIn(1, 8); key = ""; keyUpdate = KeyUpdate.KEEP }
    LaunchedEffect(retry) {
        if (retry == 0 && original != null) { loading = false; return@LaunchedEffect }
        loading = true; error = null
        try { val value=repository.ai(); withContext(Dispatchers.Main.immediate) { adopt(value) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (problem: Exception) { withContext(Dispatchers.Main.immediate) { error = problem.message } }
        finally { withContext(Dispatchers.Main.immediate) { loading = false } }
    }
    ManagementPage("AI 供应商", onClose, dismissEnabled = !busy) {
        Text("照片与视频共用配置。已配置不代表已验证供应商连接，实际打标请在相册中发起。", color = AppTheme.colors.secondary)
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (original != null) {
            Text(if (original!!.getBoolean("configured")) "已配置" else "配置未齐全", color = AppTheme.colors.text)
            ManagementGroup("接口与模型") {
                GlassTextField(url, { url = it; saved = false }, "完整 API 地址", enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.testTag("ai-provider-url"))
                GlassTextField(model, { model = it; saved = false }, "视觉模型 ID", enabled = !busy, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.testTag("ai-provider-model"))
                GlassSwitchRow("允许模型思考", thinking, { thinking = it; saved = false }, enabled = !busy)
            }
            ManagementGroup("同时处理数量") {
                Text("照片、视频和抽样调试共享此上限。提高数量可能增加供应商限流。", color = AppTheme.colors.secondary)
                FlowRow(Modifier.selectableGroup()) {
                    (1..8).forEach { count ->
                        GlassChoiceChip("$count", concurrency == count, { concurrency = count; saved = false }, enabled = !busy)
                    }
                }
            }
            ManagementGroup("API 密钥") {
                Text(if (original!!.getBoolean("apiKeyConfigured")) "服务器已保存密钥" else "尚未设置密钥", color = AppTheme.colors.secondary)
                Column(Modifier.selectableGroup()) {
                    KeyUpdate.entries.forEach { option ->
                        GlassChoiceRow(when (option) { KeyUpdate.KEEP -> "保留现有密钥"; KeyUpdate.REPLACE -> "替换密钥"; KeyUpdate.CLEAR -> "清除密钥" },
                            keyUpdate == option, { if (keyUpdate != option) { keyUpdate = option; key = ""; saved = false } }, enabled = !busy)
                    }
                }
                if (keyUpdate == KeyUpdate.REPLACE) GlassTextField(key, { key = it; saved = false }, "新密钥", visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false), enabled = !busy, modifier = Modifier.testTag("ai-provider-key"))
            }
            val valid = url.trim().length in 8..2048 && model.trim().length in 1..200 && (keyUpdate != KeyUpdate.REPLACE || key.isNotBlank() && key.length <= 4096)
            GlassSettingsAction("保存配置", { busy = true; error = null; saved = false; scope.launch {
                try { val value=repository.saveAi(aiSettingsBody(url, model, thinking, keyUpdate, key, concurrency)); withContext(Dispatchers.Main.immediate) { adopt(value); saved = true } }
                catch (cancel: CancellationException) { throw cancel }
                catch (problem: Exception) { withContext(Dispatchers.Main.immediate) { error = problem.message ?: "保存失败" } }
                finally { withContext(Dispatchers.Main.immediate) { busy = false } }
            } }, enabled = !busy && valid)
        }
        if (saved) Text("配置已保存", color = AppTheme.colors.text)
        error?.let { Text(it, color = AppTheme.colors.text) }
        if (original == null && !loading) GlassSettingsAction("重新读取配置", { retry++ })
    }
}
