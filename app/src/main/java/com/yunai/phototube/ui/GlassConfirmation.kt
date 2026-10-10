package com.yunai.phototube.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.ui.theme.*

/** Shared confirmation surface. The source excludes the dialog to avoid sampling itself. */
@Composable
internal fun GlassConfirmation(
    backdrop: Backdrop,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String? = "取消",
    destructive: Boolean = false,
    media: Boolean = false,
    busy: Boolean = false,
    confirmEnabled: Boolean = true,
    closeOnConfirm: Boolean = true,
    closeRequested: Boolean = false,
    details: (@Composable (enabled: Boolean) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    draggable: Boolean = false,
) {
    val colors = AppTheme.colors
    val glass = LocalGlassAppearance.current
    val foreground = if (media) MediaColors.text else colors.text
    val surface = if (media) Color(0xFF242426) else colors.group
    val progress = remember { Animatable(0f) }
    var closingAction by rememberSaveable { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val translation by animateFloatAsState(dragOffset, if (dragging) snap() else spring(1f, 500f), label = "glass-sheet-drag")
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    val velocityThreshold = with(density) { 900.dp.toPx() }
    val latestConfirm by rememberUpdatedState(onConfirm)
    val latestDismiss by rememberUpdatedState(onDismiss)
    fun finish(action: Int) { if (!busy && closingAction == 0) closingAction = action }
    LaunchedEffect(closeRequested, busy) { if (closeRequested && !busy) finish(2) }
    LaunchedEffect(closingAction) {
        if (closingAction == 0) progress.animateTo(1f, spring(1f, 500f))
        else {
            // Cancels entrance from its current presentation value; deliver once.
            progress.animateTo(0f, tween(150))
            if (closingAction == 1) latestConfirm() else latestDismiss()
        }
    }
    Dialog(onDismissRequest = { finish(2) }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.apply {
            setDimAmount(.25f * progress.value)
            setWindowAnimations(0)
        } }
        ThemeSystemBars(media = media)
        Box(Modifier.fillMaxSize().pointerInput(busy) { detectTapGestures { finish(2) } }.safeDrawingPadding().imePadding().padding(24.dp), contentAlignment = alignment) {
            Column(Modifier.widthIn(max = 340.dp).fillMaxWidth()
                .graphicsLayer { alpha = progress.value; scaleX = .94f + .06f * progress.value; scaleY = scaleX; translationY = if (draggable) translation else 0f }
                .drawBackdrop(backdrop = backdrop, shape = { RoundedCornerShape(30.dp) },
                    effects = { vibrancy(); blur(maxOf(12f, glass.blur).dp.toPx()); lens(glass.refractionHeight.dp.toPx(), glass.refractionAmount.dp.toPx(), chromaticAberration = glass.dispersion) },
                    onDrawSurface = { drawRect(surface.copy(alpha = .8f)) })
                .testTag("glass-confirmation").pointerInput(Unit) { detectTapGestures { } }.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (draggable) Box(Modifier.fillMaxWidth().height(48.dp).testTag("glass-sheet-handle")
                    .semantics {
                        contentDescription = "下拉关闭信息"
                        dismiss { if (!busy && closingAction == 0) { finish(2); true } else false }
                    }
                    .draggable(rememberDraggableState { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) },
                        Orientation.Vertical, enabled = !busy && closingAction == 0,
                        onDragStarted = { dragOffset = translation; dragging = true },
                        onDragStopped = { velocity ->
                            dragging = false
                            if (dragOffset >= threshold || (dragOffset > 0f && velocity > velocityThreshold)) finish(2)
                            else dragOffset = 0f
                        }), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 4.dp).background(foreground.copy(alpha = .3f), RoundedCornerShape(2.dp)))
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, color = foreground, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    if (message.isNotBlank()) Text(message, color = foreground.copy(alpha = .85f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                    CompositionLocalProvider(LocalContentColor provides foreground) { details?.invoke(closingAction == 0 && !busy) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
                com.yunai.phototube.glass.LiquidButton(
                    onClick={if(closeOnConfirm) finish(1) else if(!busy && !closeRequested && closingAction==0) latestConfirm()},
                    backdrop=backdrop, enabled=!busy && confirmEnabled && !closeRequested && closingAction==0,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),verticalPadding=12.dp,
                    surfaceColor=surface.copy(alpha=.9f)) {
                    Text(confirmLabel,color=if(destructive) {if(media) MediaColors.destructive else colors.destructive} else foreground,
                        fontSize=16.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
                }
                if(cancelLabel!=null) com.yunai.phototube.glass.LiquidButton(
                    onClick={finish(2)},backdrop=backdrop,enabled=!busy && !closeRequested && closingAction==0,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),verticalPadding=12.dp,
                    surfaceColor=surface.copy(alpha=.9f)) {
                    Text(cancelLabel,color=foreground,fontSize=16.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
                }
            }
        }
    }
}
