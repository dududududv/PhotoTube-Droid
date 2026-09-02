package com.yunai.phototube.ui.account

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.SessionUser
import com.yunai.phototube.ui.UiError
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.theme.PhotoTubeColors

@Composable
fun AccountRoute(
    user: SessionUser?,
    serverRoot: ServerRoot,
    isBusy: Boolean,
    error: UiError?,
    onBack: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenSystemStatus: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenXmpExport: () -> Unit,
    onOpenMemoryExclusions: () -> Unit,
    onOpenTagManagement: () -> Unit,
    onLogout: () -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmation by remember { mutableStateOf<AccountConfirmation?>(null) }
    BackHandler(enabled = !isBusy, onBack = onBack)

    AccountScreen(
        user = user,
        serverRoot = serverRoot,
        isBusy = isBusy,
        error = error,
        onBack = onBack,
        onOpenJobs = onOpenJobs,
        onOpenSystemStatus = onOpenSystemStatus,
        onOpenArchived = onOpenArchived,
        onOpenPrivate = onOpenPrivate,
        onOpenTrash = onOpenTrash,
        onOpenDuplicates = onOpenDuplicates,
        onOpenXmpExport = onOpenXmpExport,
        onOpenMemoryExclusions = onOpenMemoryExclusions,
        onOpenTagManagement = onOpenTagManagement,
        onRequestLogout = { confirmation = AccountConfirmation.Logout },
        onRequestSwitchServer = { confirmation = AccountConfirmation.SwitchServer },
        modifier = modifier,
    )

    confirmation?.let { action ->
        AccountConfirmationDialog(
            action = action,
            isBusy = isBusy,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                when (action) {
                    AccountConfirmation.Logout -> onLogout()
                    AccountConfirmation.SwitchServer -> onSwitchServer()
                }
            },
        )
    }
}

@Composable
private fun AccountScreen(
    user: SessionUser?,
    serverRoot: ServerRoot,
    isBusy: Boolean,
    error: UiError?,
    onBack: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenSystemStatus: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenPrivate: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenXmpExport: () -> Unit,
    onOpenMemoryExclusions: () -> Unit,
    onOpenTagManagement: () -> Unit,
    onRequestLogout: () -> Unit,
    onRequestSwitchServer: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = !isBusy) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回照片")
            }
            Text("账号与管理", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            AccountIdentityCard(user)
            ServerCard(serverRoot)
            error?.let { SessionErrorCard(it) }
            Text(
                "后台与系统",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            ManagementCard(
                entries = listOf(
                    ManagementEntry(
                        title = "任务中心",
                        subtitle = "查看扫描、导入与后台处理进度",
                        onClick = onOpenJobs,
                    ),
                    ManagementEntry(
                        title = "系统状态与缓存",
                        subtitle = "服务状态、异常与本机媒体缓存",
                        onClick = onOpenSystemStatus,
                    ),
                ),
            )
            Text(
                "照片库管理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            ManagementCard(
                entries = listOf(
                    ManagementEntry("已归档", "查看已归档的照片", onOpenArchived),
                    ManagementEntry("私密空间", "管理需要解锁的私密照片", onOpenPrivate),
                    ManagementEntry("回收站", "恢复或彻底删除照片", onOpenTrash),
                    ManagementEntry("完全重复项", "检查内容完全相同的照片", onOpenDuplicates),
                    ManagementEntry("照片信息备份", "管理 XMP 信息导出", onOpenXmpExport),
                    ManagementEntry("回忆屏蔽", "设置不参与回忆的日期与照片", onOpenMemoryExclusions),
                    ManagementEntry("标签管理", "维护照片标签", onOpenTagManagement),
                ),
            )
            Text(
                "会话操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = onRequestSwitchServer,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                Text("切换服务器", modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onRequestLogout,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB3261E),
                    contentColor = Color.White,
                ),
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                }
                Text(
                    if (isBusy) "正在处理…" else "退出登录",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                "退出和切换只影响会话、服务地址与本机认证媒体缓存，不会修改照片、相册、PhotoTube 数据或 NAS 原文件。",
                color = PhotoTubeColors.Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

private data class ManagementEntry(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun ManagementCard(entries: List<ManagementEntry>) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = entry.onClick)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    Text(entry.subtitle, color = PhotoTubeColors.Muted, fontSize = 12.sp)
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "进入${entry.title}",
                    tint = PhotoTubeColors.Muted,
                )
            }
            if (index < entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 18.dp),
                    color = PhotoTubeColors.Hairline,
                )
            }
        }
    }
}

@Composable
private fun AccountIdentityCard(user: SessionUser?) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(50.dp).background(PhotoTubeColors.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user?.displayName?.firstOrNull()?.uppercase() ?: "P",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    user?.displayName?.ifBlank { user.username } ?: "PhotoTube 用户",
                    style = MaterialTheme.typography.titleMedium,
                )
                user?.let {
                    Text("@${it.username}", color = PhotoTubeColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ServerCard(serverRoot: ServerRoot) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Dns, contentDescription = null, tint = PhotoTubeColors.Muted)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("当前服务器", fontWeight = FontWeight.SemiBold)
                Text(
                    serverRoot.value,
                    color = PhotoTubeColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionErrorCard(error: UiError) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEDEA)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("操作未完成", color = Color(0xFF8C1D18), fontWeight = FontWeight.SemiBold)
            DiagnosticErrorText(
                message = error.message,
                logId = error.logId,
                messageColor = Color(0xFF8C1D18),
            )
        }
    }
}

@Composable
private fun AccountConfirmationDialog(
    action: AccountConfirmation,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isLogout = action == AccountConfirmation.Logout
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(if (isLogout) "退出 PhotoTube？" else "切换服务器？") },
        text = {
            Text(
                if (isLogout) {
                    "将请求当前服务器删除会话。只有服务器确认后，应用才会退出并清除 Cookie、图片内存、图片磁盘和认证 HTTP 缓存；当前服务器地址会保留。"
                } else {
                    "将尝试通知旧服务器删除会话，然后清除本机 Cookie、三层媒体缓存和已保存的服务地址。即使旧服务器离线，也可以继续配置新服务器，但应用会明确提示旧会话未得到确认。"
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLogout) Color(0xFFB3261E) else PhotoTubeColors.Ink,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text(if (isLogout) "退出登录" else "切换服务器")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("取消") }
        },
    )
}

private enum class AccountConfirmation { Logout, SwitchServer }
