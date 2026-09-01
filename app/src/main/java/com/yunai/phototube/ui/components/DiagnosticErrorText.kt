package com.yunai.phototube.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 统一展示可选择的错误正文，并为服务端诊断 ID 提供明确复制入口。 */
@Composable
fun DiagnosticErrorText(
    message: String,
    logId: String?,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.error,
    diagnosticColor: Color = messageColor.copy(alpha = 0.72f),
) {
    val context = LocalContext.current
    val diagnosticId = logId?.takeIf(String::isNotBlank)
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SelectionContainer {
            Text(message, color = messageColor, fontSize = 14.sp, lineHeight = 20.sp)
        }
        diagnosticId?.let { id ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        text = "日志 ID：$id",
                        color = diagnosticColor,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("PhotoTube 日志 ID", id))
                    },
                ) {
                    Text("复制日志 ID", color = diagnosticColor, fontSize = 12.sp)
                }
            }
        }
    }
}
