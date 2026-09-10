package com.yunai.phototube.ui

import com.yunai.phototube.ui.theme.*
import com.yunai.phototube.ui.icons.SettingsGlyph
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.glass.GlassAppearance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.glass.LiquidSlider
import com.yunai.phototube.glass.LiquidToggle
import kotlin.math.roundToInt

@Composable
internal fun SettingsScreen(
    haptics: Boolean, onHaptics: (Boolean) -> Unit,
    favoritesOnly: Boolean, onFavorites: (Boolean) -> Unit,
    newestFirst: Boolean, onNewestFirst: (Boolean) -> Unit,
    glassStrength: Float, onGlassStrength: (Float) -> Unit,
    onAppearance: (GlassAppearance) -> Unit, onVideo: () -> Unit, onClose: () -> Unit, onServer: (() -> Unit)? = null
) {
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (appearanceOpen) appearanceOpen = false else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ThemeSystemBars()
        if (appearanceOpen) {
            AppearanceSettingsContent(glassStrength, onGlassStrength, onAppearance) { appearanceOpen = false }
        } else {
        val scene = rememberLayerBackdrop()
        val state = rememberLazyListState()
        val condensed by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 100 } }
        val titleAlpha by animateFloatAsState(if (condensed) 1f else 0f, label = "settings-title")
        Box(Modifier.fillMaxSize().background(AppTheme.colors.groupedBackground).testTag("settings-screen")) {
            LazyColumn(Modifier.fillMaxSize().layerBackdrop(scene).testTag("settings-list"), state = state,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp)) {
                item {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Spacer(Modifier.height(64.dp))
                    Text("设置", color = AppTheme.colors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("PhotoTube", color = AppTheme.colors.secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
                }
                if(onServer!=null) item {
                    SectionLabel("服务器")
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().clickable(onClick=onServer).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("服务器连接",color=AppTheme.colors.text,fontSize=17.sp,modifier=Modifier.weight(1f))
                            Icon(Icons.Outlined.KeyboardArrowRight,null,tint=AppTheme.colors.secondary)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    SectionLabel("图库")
                    SettingsGroup { backdrop ->
                        ToggleRow("只看收藏", SettingsSymbol.FAVORITES, favoritesOnly, onFavorites, backdrop)
                        InsetDivider()
                        ToggleRow("最新照片优先", SettingsSymbol.CALENDAR, newestFirst, onNewestFirst, backdrop)
                    }
                    GroupFooter("选择图库显示的内容和照片排列顺序。")
                }
                item {
                    SectionLabel("浏览体验")
                    SettingsGroup { backdrop ->
                        ToggleRow("触感反馈", SettingsSymbol.HAPTICS, haptics, onHaptics, backdrop)
                    }
                    GroupFooter("切换图库和相册时，提供轻柔的触感反馈。")
                }
                item {
                    SectionLabel("外观")
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().clickable { appearanceOpen = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsGlyph(SettingsSymbol.APPEARANCE)
                            Text("外观", color = AppTheme.colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Text("液态玻璃", color = AppTheme.colors.secondary, fontSize = 15.sp)
                            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = AppTheme.colors.secondary)
                        }
                    }
                    GroupFooter("设置显示模式、主题色与玻璃效果。")
                }
                item {
                    SectionLabel("视频")
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().clickable(onClick = onVideo).padding(16.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsGlyph(SettingsSymbol.VIDEO)
                            Text("视频预览", color = AppTheme.colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = AppTheme.colors.secondary, modifier = Modifier.size(22.dp))
                        }
                    }
                    GroupFooter("播放一段短片，查看玻璃在动态画面上的效果。")
                }
                item {
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsGlyph(SettingsSymbol.ABOUT)
                            Text("PhotoTube", color = AppTheme.colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Text("1.0", color = AppTheme.colors.secondary, fontSize = 15.sp)
                        }
                    }
                    Text("留住每一刻", color = AppTheme.colors.secondary, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp).height(48.dp)) {
                Text("设置", color = AppTheme.colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = titleAlpha })
                LiquidButton(onClose, scene, Modifier.size(44.dp).align(Alignment.CenterStart).semantics { contentDescription = "返回相册" },
                    shape = CircleShape, horizontalPadding = 0.dp, surfaceColor = AppTheme.colors.group.copy(.55f)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, tint = AppTheme.colors.text, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AppTheme.colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
}

@Composable
private fun GroupFooter(text: String) {
    Text(text, color = AppTheme.colors.secondary, fontSize = 13.sp, lineHeight = 18.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp))
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.(Backdrop) -> Unit) {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))) {
        // The switch/slider samples only this backing surface, never itself.
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(AppTheme.colors.group))
        Column(Modifier.fillMaxWidth()) { content(backdrop) }
    }
}

@Composable
private fun ToggleRow(title: String, symbol: SettingsSymbol, value: Boolean, onChange: (Boolean) -> Unit, backdrop: Backdrop) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsGlyph(symbol)
        Text(title, color = AppTheme.colors.text, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp))
        LiquidToggle({ value }, onChange, backdrop, Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(Modifier.padding(start = 60.dp), thickness = .5.dp, color = AppTheme.colors.separator)
}
