package com.yunai.phototube.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            Text("账号与服务器", style = MaterialTheme.typography.titleLarge)
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
