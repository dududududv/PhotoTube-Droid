package com.yunai.phototube.ui

import com.yunai.phototube.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.R
import com.yunai.phototube.glass.*
import kotlin.math.roundToInt

@Composable
internal fun AppearanceSettingsContent(
    opacity: Float, onOpacity: (Float) -> Unit,
    onAppearance: (GlassAppearance) -> Unit, onBack: () -> Unit
) {
    val appearance = LocalGlassAppearance.current
    val preview = rememberLayerBackdrop()
    val page = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground).testTag("appearance-screen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).layerBackdrop(page)
            .padding(horizontal = 20.dp).testTag("appearance-scroll")) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(64.dp))
            Text("外观", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.text)
            Text("液态玻璃", color = AppTheme.colors.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            ThemeSettingsControls()
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(26.dp)).testTag("appearance-preview")) {
                Image(painterResource(R.drawable.lake), null, Modifier.matchParentSize().layerBackdrop(preview), contentScale = ContentScale.Crop)
                LiquidButton({}, preview, Modifier.align(Alignment.Center).size(176.dp, 64.dp),
                    surfaceColor = AppTheme.colors.glass.copy(opacity)) { Text("实时预览", color = AppTheme.colors.text) }
            }
            Spacer(Modifier.height(20.dp))
            Parameter("玻璃深浅", "表面覆盖的深浅", "${(opacity * 100).roundToInt()}%", "opacity", opacity, .1f.. .7f, onOpacity)
            Parameter("模糊半径", "柔化玻璃后方的细节", "${appearance.blur.roundToInt()} dp", "blur", appearance.blur, 0f..24f) { onAppearance(appearance.copy(blur = it)) }
            Parameter("折射高度", "边缘发生折射的范围", "${appearance.refractionHeight.roundToInt()} dp", "height", appearance.refractionHeight, 0f..24f) { onAppearance(appearance.copy(refractionHeight = it)) }
            Parameter("折射强度", "背景在玻璃边缘的偏移", "${appearance.refractionAmount.roundToInt()} dp", "amount", appearance.refractionAmount, 0f..48f) { onAppearance(appearance.copy(refractionAmount = it)) }
            MaterialCard { backdrop ->
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("色散", color = AppTheme.colors.text, fontSize = 17.sp)
                        Text("在折射边缘分离色彩", color = AppTheme.colors.secondary, fontSize = 12.sp)
                    }
                    LiquidToggle({ appearance.dispersion }, { onAppearance(appearance.copy(dispersion = it)) }, backdrop,
                        Modifier.semantics { contentDescription = "色散" })
                }
            }
            Spacer(Modifier.height(20.dp))
            // Keep the adjustment controls on their upstream material so extreme
            // preview values never make the controls themselves difficult to use.
            MaterialCard { backdrop ->
                LiquidButton({ onOpacity(.4f); onAppearance(GlassAppearance()) }, backdrop, Modifier.fillMaxWidth(), surfaceColor = Color.Transparent) {
                    Text("恢复默认玻璃效果", color = AppTheme.colors.accent)
                }
            }
            Text("应用于图库、相册与视频预览的玻璃按钮和导航。", color = AppTheme.colors.secondary, fontSize = 12.sp,
                modifier = Modifier.padding(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        Box(Modifier.statusBarsPadding().padding(16.dp)) {
            LiquidButton(onBack, page, Modifier.size(44.dp).semantics { contentDescription = "返回设置" },
                shape = CircleShape, horizontalPadding = 0.dp, surfaceColor = AppTheme.colors.glass.copy(.4f)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppTheme.colors.text)
            }
        }
    }
}

@Composable
private fun Parameter(title: String, subtitle: String, valueLabel: String, tag: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    MaterialCard { backdrop ->
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = AppTheme.colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(valueLabel, color = AppTheme.colors.secondary, fontSize = 14.sp)
            }
            Text(subtitle, color = AppTheme.colors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            LiquidSlider({ value }, onChange, range, .001f, backdrop,
                Modifier.testTag("appearance-$tag").semantics { contentDescription = title })
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun MaterialCard(content: @Composable (Backdrop) -> Unit) {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(AppTheme.colors.group))
        CompositionLocalProvider(LocalGlassAppearance provides GlassAppearance()) { content(backdrop) }
    }
}
