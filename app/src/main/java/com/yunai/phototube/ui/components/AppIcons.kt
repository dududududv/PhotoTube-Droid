package com.yunai.phototube.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class AppIconKind { Search, Filter, Grid, Stack, People, Inbox, Plus }

@Composable
fun AppIcon(
    kind: AppIconKind,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1A1B1E),
) {
    val imageVector = when (kind) {
        AppIconKind.Search -> Icons.Rounded.Search
        AppIconKind.Filter -> Icons.Rounded.FilterList
        AppIconKind.Grid -> Icons.Rounded.GridView
        AppIconKind.Stack -> Icons.Rounded.Dns
        AppIconKind.People -> Icons.Outlined.Group
        AppIconKind.Inbox -> Icons.Outlined.Inbox
        AppIconKind.Plus -> Icons.Rounded.Add
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = modifier,
        tint = color,
    )
}
