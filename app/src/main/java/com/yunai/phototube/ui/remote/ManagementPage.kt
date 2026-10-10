package com.yunai.phototube.ui.remote

import androidx.compose.animation.core.*
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import com.kyant.backdrop.backdrops.*
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.ui.media.*
import com.yunai.phototube.ui.theme.*

/** Shared theme, glass navigation and interruptible scale transition for remote forms. */
@Composable
internal fun ManagementPage(title: String, onClose: () -> Unit, dismissEnabled: Boolean = true, scrollable: Boolean = true, secure: Boolean = false, onNavigateUp: (() -> Unit)? = null, closeRequested: Boolean = false, showTitle: Boolean = true, compactHeader: Boolean = false, horizontalInset: androidx.compose.ui.unit.Dp = 20.dp, contentTopInset: androidx.compose.ui.unit.Dp? = null, showBackButton: Boolean = true, scaleTransition: Boolean = true, onCloseAttempt: (() -> Boolean)? = null, overlay: @Composable (com.kyant.backdrop.Backdrop) -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    var presented by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (presented) 1f else 0f) }
    val latestOnClose by rememberUpdatedState(onClose)
    var closing by rememberSaveable { mutableStateOf(false) }
    fun close() {
        if (!dismissEnabled || closing) return
        if (onCloseAttempt?.invoke() == false) return
        if (onNavigateUp != null) {
            onNavigateUp()
            return
        }
        closing = true
    }
    LaunchedEffect(closeRequested, dismissEnabled) { if (closeRequested && dismissEnabled) close() }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = if(secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit)) {
        ThemeSystemBars()
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply { setDimAmount(0f); setWindowAnimations(0) } }
        // One owner cancels entrance at its current value and delivers dismissal once.
        LaunchedEffect(closing) {
            if (closing) {
                progress.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                latestOnClose()
            }
            else if (!presented) {
                presented = true
                withFrameNanos { }
                progress.animateTo(1f, spring(1f, 380f))
            }
        }
        val colors = AppTheme.colors
        val scene = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value; scaleX = if(scaleTransition) .94f + .06f * progress.value else 1f; scaleY = scaleX }.background(colors.groupedBackground)) {
            Column(Modifier.fillMaxSize().layerBackdrop(scene).background(colors.groupedBackground)
                .then(if(contentTopInset == null) Modifier.statusBarsPadding().padding(top = if(compactHeader) 64.dp else 80.dp) else Modifier.padding(top = contentTopInset))
                .navigationBarsPadding().imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = horizontalInset).padding(bottom = if(compactHeader) 16.dp else 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showTitle && !compactHeader) ManagementTitle(title)
                CompositionLocalProvider(LocalContentColor provides colors.text) { content() }
            }
            if(showBackButton) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp,vertical=if(compactHeader) 8.dp else 16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                LiquidButton(::close, scene, Modifier.size(48.dp).semantics { contentDescription = if(onNavigateUp != null) "返回上一级" else "返回" }, shape = CircleShape,
                    horizontalPadding = 0.dp, surfaceColor = colors.glass.copy(.4f)) { MediaIcon(MediaSymbol.BACK, colors.text) }
                if(compactHeader && showTitle) Text(title,Modifier.weight(1f).padding(horizontal=16.dp),
                    fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=colors.text,maxLines=1,
                    overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        overlay(scene)
    }
}
@Composable
internal fun ManagementGroup(title: String, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AppTheme.colors.group).padding(if(compact) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(if(compact) 4.dp else 10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.text)
        content()
    }
}

@Composable
internal fun ManagementTitle(title: String) {
    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
}

/** Secondary refresh stays in navigation, leaving the empty state to explain the page. */
@Composable
internal fun ManagementRefreshAction(backdrop: com.kyant.backdrop.Backdrop, enabled: Boolean, onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
        LiquidButton(onRefresh, backdrop, Modifier.size(48.dp).semantics { contentDescription = "刷新" },
            shape = CircleShape, horizontalPadding = 0.dp, enabled = enabled, surfaceColor = AppTheme.colors.glass.copy(.4f)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Refresh, null, tint = AppTheme.colors.text)
        }
    }
}
