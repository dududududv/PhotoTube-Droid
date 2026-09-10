package com.yunai.phototube.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThemeSettingsControls() {
    val controller = LocalThemeController.current
    val colors = AppTheme.colors
    Text("显示模式", color = colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.group).selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().selectable(controller.mode == mode, role = Role.RadioButton,
                onClick = { controller.updateMode(mode) }).testTag("theme-mode-${mode.name}")
                .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mode.label, color = colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if(controller.mode == mode) Icon(Icons.Outlined.Check, null, tint = colors.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
    Text("跟随系统会随 Android 的深色主题自动切换。", color = colors.secondary, fontSize = 12.sp,
        modifier = Modifier.padding(16.dp))
    Text("主题色", color = colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.group).selectableGroup().padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        ThemeAccent.entries.forEach { accent ->
            val selected = controller.accent == accent
            val swatch = if(LocalDarkTheme.current) accent.dark else accent.light
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                .selectable(selected, role = Role.RadioButton, onClick = { controller.updateAccent(accent) })
                .semantics { contentDescription = "主题色：${accent.label}" }.testTag("theme-accent-${accent.name}")
                .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(44.dp).border(if(selected) 2.dp else 0.dp, if(selected) colors.accent else colors.group, CircleShape)
                    .padding(5.dp).background(swatch, CircleShape), contentAlignment = Alignment.Center) {
                    if(selected) Icon(Icons.Outlined.Check, null, tint = if(LocalDarkTheme.current) MediaColors.background else MediaColors.text, modifier = Modifier.size(18.dp))
                }
                Text(accent.label, color = if(selected) colors.accent else colors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    Text("用于选中的标签、开关、滑块及操作按钮。", color = colors.secondary, fontSize = 12.sp,
        modifier = Modifier.padding(16.dp))
}
