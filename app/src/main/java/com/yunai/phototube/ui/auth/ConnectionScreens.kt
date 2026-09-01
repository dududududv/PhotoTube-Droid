package com.yunai.phototube.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunai.phototube.ui.UiError
import com.yunai.phototube.ui.components.DiagnosticErrorText
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens

@Composable
fun LoadingGate() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = PhotoTubeColors.Ink)
        Spacer(Modifier.height(16.dp))
        Text("正在连接 PhotoTube", color = PhotoTubeColors.Muted)
    }
}

@Composable
fun ServerSetupScreen(
    serverAddress: String,
    isBusy: Boolean,
    error: UiError?,
    onServerAddressChanged: (String) -> Unit,
    onConnect: () -> Unit,
) {
    ConnectionScaffold(
        title = "连接你的照片库",
        description = "输入 PhotoTube 所在 NAS 或服务器的地址。保存前会先验证服务状态。",
    ) {
        val keyboard = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = serverAddress,
            onValueChange = onServerAddressChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            label = { Text("服务地址") },
            placeholder = { Text("http://nas.local:18473") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = {
                keyboard?.hide()
                onConnect()
            }),
        )
        ErrorMessage(error)
        PrimaryButton("验证并连接", isBusy, onConnect)
    }
}

@Composable
fun LoginScreen(
    serverAddress: String,
    username: String,
    password: String,
    isBusy: Boolean,
    error: UiError?,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onChooseAnotherServer: () -> Unit,
) {
    ConnectionScaffold(
        title = "登录 PhotoTube",
        description = "连接到 $serverAddress",
    ) {
        val keyboard = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChanged,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username },
            enabled = !isBusy,
            singleLine = true,
            label = { Text("用户名") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            enabled = !isBusy,
            singleLine = true,
            label = { Text("口令") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                onLogin()
            }),
        )
        ErrorMessage(error)
        PrimaryButton("登录", isBusy, onLogin)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onChooseAnotherServer,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isBusy,
        ) {
            Text("更换服务器")
        }
    }
}

@Composable
fun PasswordNotInitializedScreen(
    serverAddress: String,
    onRetry: () -> Unit,
    onChooseAnotherServer: () -> Unit,
) {
    ConnectionScaffold(
        title = "服务尚未初始化口令",
        description = "已连接到 $serverAddress，但管理员还没有设置登录口令。",
    ) {
        Text(
            text = "请在 PhotoTube 服务端执行 ctl set-password，完成后回到这里重新检查。",
            color = PhotoTubeColors.Muted,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("重新检查", false, onRetry)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onChooseAnotherServer,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("更换服务器")
        }
    }
}

@Composable
private fun ConnectionScaffold(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PhotoTubeDimens.ScreenPadding, vertical = 28.dp),
    ) {
        Spacer(Modifier.weight(1f, fill = true))
        Text(
            text = "PhotoTube",
            color = PhotoTubeColors.Muted,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = PhotoTubeColors.Ink,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = description,
            color = PhotoTubeColors.Muted,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
        Spacer(Modifier.height(32.dp))
        content()
        Spacer(Modifier.weight(1.25f, fill = true))
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        enabled = !isBusy,
        colors = ButtonDefaults.buttonColors(
            containerColor = PhotoTubeColors.Ink,
            contentColor = Color.White,
        ),
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.height(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ErrorMessage(error: UiError?) {
    if (error == null) {
        Spacer(Modifier.height(20.dp))
        return
    }
    Spacer(Modifier.height(14.dp))
    DiagnosticErrorText(message = error.message, logId = error.logId)
    Spacer(Modifier.height(14.dp))
}
