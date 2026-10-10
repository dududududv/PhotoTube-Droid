package com.yunai.phototube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.theme.AppTheme

/** One focus target and one toggle action for the indicator and its wrapping label. */
@Composable
internal fun GlassOptionRow(label: String, selected: Boolean, onSelected: (Boolean) -> Unit,
    enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
        .background(colors.subtle)
        .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onSelected)
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassSelectionMark(selected)
        Text(label, Modifier.weight(1f), color = colors.text)
    }
}

/** Decorative indicator; the enclosing row owns the accessible selection action. */
@Composable
internal fun GlassSelectionMark(selected: Boolean) {
    val colors=AppTheme.colors
    if(selected) Icon(Icons.Rounded.CheckCircle,null,Modifier.size(24.dp),tint=colors.accent)
    else Box(Modifier.size(24.dp).border(1.5.dp,colors.secondary,CircleShape))
}
