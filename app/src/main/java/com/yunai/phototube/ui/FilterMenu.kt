package com.yunai.phototube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.yunai.phototube.data.*
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.glass.glassHighlight
import com.yunai.phototube.ui.icons.FilterList
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

internal enum class FilterPage(val title: String, val advanced: Boolean = false) {
    ROOT("筛选"), MEDIA("媒体类型"), FAVORITE("收藏状态"),
    SCOPE("组合筛选", true), FROM("开始日期", true), TO("结束日期", true),
    FOLDERS("目录", true), ALL_TAGS("全部包含", true), ANY_TAGS("任一包含", true), EXCLUDE_TAGS("排除标签", true)
}

/** Immediate menu selections. Only mutually exclusive choices dismiss the menu. */
@Composable
internal fun FilterMenu(backdrop: Backdrop, insets: CornerInsets, opacity: Float, filters: WallFilters,
    onChange: (WallFilters) -> Unit, onDismiss: () -> Unit, server: PhotoTubeServer? = null) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val expansion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(FilterPage.ROOT) }
    var selection by remember(filters) { mutableStateOf(filters) }
    val catalogRows = remember { mutableStateMapOf<FilterPage, Int>() }
    val contentHeights = remember(LocalDensity.current) { mutableStateMapOf<FilterPage, Int>() }
    val density = LocalDensity.current
    val view = LocalView.current
    var windowBottom by remember { mutableFloatStateOf(0f) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val keyboardOverlap = with(density) { (windowBottom-(view.height-imeBottom)).coerceAtLeast(0f).toDp() }
    val scale = density.fontScale.coerceAtLeast(1f)
    val rowHeight = 48.dp * scale
    val headerHeight = if(page==FilterPage.ROOT) 0.dp else 44.dp * scale
    val colors = AppTheme.colors
    val dark = LocalDarkTheme.current
    val glass = LocalGlassAppearance.current
    val catalog = remember(server) { server?.let(::FilterCatalog) }
    fun close() {
        if (closing) return
        closing = true
        focusManager.clearFocus(); keyboard?.hide()
        scope.launch { expansion.animateTo(0f, spring(1f, 650f)); onDismiss() }
    }
    fun back() {
        page = when (page) {
            FilterPage.ROOT -> { close(); FilterPage.ROOT }
            FilterPage.MEDIA, FilterPage.FAVORITE, FilterPage.SCOPE -> FilterPage.ROOT
            else -> FilterPage.SCOPE
        }
    }
    LaunchedEffect(Unit) { expansion.animateTo(1f, spring(1f, 480f)) }
    LaunchedEffect(page) { focusManager.clearFocus(); keyboard?.hide() }
    BackHandler { back() }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {windowBottom=it.boundsInWindow().bottom}
        .testTag("filter-menu-overlay")) {
        Box(Modifier.matchParentSize().clickable(interactionSource=null, indication=null, onClick=::close)
            .semantics { contentDescription="关闭筛选菜单" })
        val estimatedHeight = when (page) {
            FilterPage.ROOT -> headerHeight + rowHeight * (3 + (if(filters.active) 1 else 0) + (if(catalog!=null) 1 else 0)) + 12.dp
            FilterPage.MEDIA, FilterPage.FAVORITE -> headerHeight + rowHeight * 3 + 12.dp
            FilterPage.FOLDERS, FilterPage.ALL_TAGS, FilterPage.ANY_TAGS, FilterPage.EXCLUDE_TAGS ->
                headerHeight + rowHeight * (catalogRows[page] ?: 5) + 20.dp
            FilterPage.SCOPE -> headerHeight + rowHeight * 7 + 80.dp
            else -> 464.dp * scale
        }
        val requestedHeight = contentHeights[page]?.let { headerHeight + with(density) {it.toDp()} + (if(page==FilterPage.ROOT) 0.dp else .5.dp) } ?: estimatedHeight
        val preferredWidth = when(page) {
            FilterPage.FROM, FilterPage.TO -> 328.dp
            FilterPage.MEDIA, FilterPage.FAVORITE -> 216.dp
            FilterPage.ROOT -> 232.dp
            FilterPage.SCOPE -> 252.dp
            else -> 280.dp
        }
        val menuWidth by animateDpAsState(minOf(preferredWidth*scale,
            maxWidth-insets.left-insets.right), spring(1f,550f), label="filter-menu-width")
        val menuHeight by animateDpAsState(minOf(requestedHeight,
            (maxHeight-insets.top-maxOf(insets.bottom,keyboardOverlap)).coerceAtLeast(96.dp)), spring(1f,550f), label="filter-menu-height")
        val p = expansion.value.coerceIn(0f,1f)
        val contentAlpha = ((p-.35f)/.65f).coerceIn(0f,1f)
        Box(Modifier.align(Alignment.TopEnd).absolutePadding(right=insets.right,top=insets.top)
            .width(48.dp+(menuWidth-48.dp)*p).height(48.dp+(menuHeight-48.dp)*p)
            .drawBackdrop(backdrop, shape={RoundedCornerShape(24.dp)},
                effects={vibrancy();blur(glass.blur.dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                highlight={glassHighlight(dark)},
                // Preserve the shared glass optics; a light tint keeps menu text readable.
                onDrawSurface={drawRect(colors.glass.copy(alpha=opacity+((if(dark) .58f else .68f)-opacity)*p))})
            .clip(RoundedCornerShape(24.dp)).pointerInput(Unit) { detectTapGestures { } }
            .testTag("filter-menu").semantics { paneTitle=page.title }) {
            Icon(Icons.Rounded.FilterList,null,Modifier.align(Alignment.TopEnd).padding(13.dp).size(22.dp)
                .graphicsLayer { alpha=1f-contentAlpha },tint=colors.text)
            Column(Modifier.requiredWidth(menuWidth).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer {alpha=contentAlpha}) {
                if(page!=FilterPage.ROOT) {
                    Row(Modifier.fillMaxWidth().height(headerHeight)
                        .clickable(enabled=!closing,role=Role.Button,onClick=::back)
                        .semantics {contentDescription="返回筛选菜单"}
                        .padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        MenuChevron(colors.secondary,true)
                        Spacer(Modifier.width(8.dp))
                        Text(page.title,fontSize=14.sp,fontWeight=FontWeight.Medium,color=colors.secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    FilterSeparator()
                }
                AnimatedContent(page,modifier=Modifier.weight(1f),transitionSpec={
                    (fadeIn(tween(140))+slideInHorizontally(spring(1f,600f)){if(targetState.ordinal>initialState.ordinal) it/12 else -it/12}) togetherWith
                        (fadeOut(tween(90))+slideOutHorizontally(tween(140)){if(targetState.ordinal>initialState.ordinal) -it/12 else it/12})
                },label="filter-submenu") { current ->
                    if(current.advanced && catalog!=null) FilterScopeContent(current,selection,{value ->
                        if(!closing) {selection=value;onChange(value)}
                    },{page=it},catalog,onClose=::close,onContentHeight={contentHeights[current]=it}) {catalogRows[current]=it}
                    else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).onSizeChanged {contentHeights[current]=it.height}.padding(vertical=6.dp)
                        .then(if(current!=FilterPage.ROOT) Modifier.selectableGroup() else Modifier)) {
                        when(current) {
                            FilterPage.MEDIA -> {
                                MenuChoice("照片与视频",filters.kind==null,!closing,true) {onChange(filters.copy(kind=null));close()}
                                WallKind.entries.forEach {kind -> MenuChoice("仅${kind.label}",filters.kind==kind,!closing,true) {onChange(filters.copy(kind=kind));close()} }
                            }
                            FilterPage.FAVORITE -> listOf(null to "全部",true to "只看收藏",false to "只看未收藏").forEach {(value,label) ->
                                MenuChoice(label,filters.favorite==value,!closing,true) {onChange(filters.copy(favorite=value));close()}
                            }
                            else -> {
                                FilterNavigationRow("媒体类型",filters.kind?.label ?: "全部",enabled=!closing) {page=FilterPage.MEDIA}
                                FilterNavigationRow("收藏状态",when(filters.favorite){true->"已收藏";false->"未收藏";null->"全部"},enabled=!closing) {page=FilterPage.FAVORITE}
                                MenuChoice("只看归档",filters.archived,!closing) {onChange(filters.copy(archived=!filters.archived))}
                                if(catalog!=null) {
                                    FilterSeparator()
                                    FilterNavigationRow("日期、目录与标签",if(filters.hasScope) "已设置" else "",enabled=!closing) {selection=filters;page=FilterPage.SCOPE}
                                }
                                if(filters.active) FilterTextAction("清除筛选",enabled=!closing) {onChange(WallFilters(keyword=filters.keyword))}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilterNavigationRow(title:String,value:String="",enabled:Boolean=true,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp*LocalDensity.current.fontScale.coerceAtLeast(1f))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Spacer(Modifier.width(20.dp))
        Text(title,Modifier.weight(1f),color=AppTheme.colors.text,fontSize=16.sp)
        if(value.isNotEmpty()) Text(value,Modifier.widthIn(max=130.dp).padding(start=8.dp),color=AppTheme.colors.secondary,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp));MenuChevron(AppTheme.colors.secondary)
    }
}

@Composable
internal fun MenuChoice(title:String,checked:Boolean,enabled:Boolean=true,radio:Boolean=false,onClick:()->Unit) {
    val colors=AppTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp*LocalDensity.current.fontScale.coerceAtLeast(1f))
        .then(if(radio) Modifier.selectable(checked,enabled=enabled,role=Role.RadioButton,onClick=onClick) else Modifier.toggleable(checked,enabled,Role.Checkbox){onClick()})
        .padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(20.dp),contentAlignment=Alignment.CenterStart) {
            if(checked) Icon(Icons.Outlined.Check,null,Modifier.size(17.dp),tint=colors.text)
        }
        Text(title,Modifier.weight(1f),color=colors.text.copy(alpha=if(enabled) 1f else .45f),fontSize=16.sp)
    }
}

@Composable
internal fun FilterTextAction(label:String,enabled:Boolean=true,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp*LocalDensity.current.fontScale.coerceAtLeast(1f))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=16.dp,vertical=10.dp)) {
        Spacer(Modifier.width(20.dp))
        Text(label,fontSize=15.sp,color=AppTheme.colors.accent.copy(alpha=if(enabled) 1f else .4f))
    }
}

@Composable
internal fun FilterSeparator() { Box(Modifier.padding(horizontal=18.dp).fillMaxWidth().height(.5.dp).background(AppTheme.colors.separator)) }

@Composable
internal fun MenuChevron(tint:androidx.compose.ui.graphics.Color,back:Boolean=false) {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,
        Modifier.size(18.dp).graphicsLayer {rotationZ=if(back) 180f else 0f},tint=tint)
}
