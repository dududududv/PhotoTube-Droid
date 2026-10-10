package com.yunai.phototube.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.glass.LiquidToggle
import com.yunai.phototube.ui.theme.AppTheme

@Composable
internal fun GlassSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f)) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(AppTheme.colors.group))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), color = AppTheme.colors.text)
            LiquidToggle({ checked }, onCheckedChange, backdrop,
                Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = label }, enabled = enabled)
        }
    }
}

@Composable
internal fun GlassChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, enabled: Boolean = true) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp).alpha(if (enabled) 1f else .45f),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = colors.text)
        if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(22.dp), tint = colors.accent)
        else Spacer(Modifier.size(22.dp))
    }
}

@Composable
internal fun GlassSettingsAction(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, destructive: Boolean = false, media: Boolean = false, sourceBackdrop: com.kyant.backdrop.Backdrop? = null) {
    val localBackdrop = rememberLayerBackdrop()
    val backdrop = sourceBackdrop ?: localBackdrop
    val colors = AppTheme.colors
    Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .45f)) {
        if (sourceBackdrop == null) Box(Modifier.matchParentSize().clip(RoundedCornerShape(50)).layerBackdrop(localBackdrop).background(if(media) com.yunai.phototube.ui.theme.MediaColors.background else colors.groupedBackground))
        LiquidButton(onClick, backdrop, modifier.fillMaxWidth(), surfaceColor = (if(media) androidx.compose.ui.graphics.Color(0xFF242426) else colors.glass).copy(alpha = .4f), enabled = enabled, verticalPadding = 12.dp) {
            Text(label, color = if (destructive) {if(media) com.yunai.phototube.ui.theme.MediaColors.destructive else colors.destructive} else if(media) com.yunai.phototube.ui.theme.MediaColors.text else if(sourceBackdrop != null) colors.text else colors.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun GlassChoiceChip(label: String, selected: Boolean, onSelect: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(modifier.clip(RoundedCornerShape(18.dp))
        .background(if (selected) colors.subtle else colors.group)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp)
        .alpha(if (enabled) 1f else .45f), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) colors.accent else colors.secondary,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
    }
}

@Composable
internal fun SettingsNavigationRow(label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, media: Boolean = false, destructive: Boolean = false, value: String? = null, inlineValue: Boolean = false) {
    val colors = AppTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = {focus.clearFocus();keyboard?.hide();onClick()})
        .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 12.dp)
        .alpha(if (enabled) 1f else .45f), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = if(destructive) {
                if(media) com.yunai.phototube.ui.theme.MediaColors.destructive else colors.destructive
            } else if(media) com.yunai.phototube.ui.theme.MediaColors.text else colors.text)
            value?.takeUnless { inlineValue }?.let { Text(it, color = if(media) com.yunai.phototube.ui.theme.MediaColors.secondary else colors.secondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
        }
        if (inlineValue && value != null) Text(value, Modifier.weight(1f),
            color = if(media) com.yunai.phototube.ui.theme.MediaColors.secondary else colors.secondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp), tint = if(media) com.yunai.phototube.ui.theme.MediaColors.secondary else colors.secondary)
    }
}

/** Independent on/off tool, unlike the mutually exclusive choice chips. */
@Composable
internal fun GlassToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = AppTheme.colors
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (checked) colors.subtle else colors.group)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp)
        .alpha(if (enabled) 1f else .45f), contentAlignment = Alignment.Center) {
        Text(label, color = if (checked) colors.accent else colors.secondary,
            fontWeight = if (checked) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal)
    }
}
