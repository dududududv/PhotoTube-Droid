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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.yunai.phototube.ui.icons.FilterList
import com.yunai.phototube.ui.theme.AppTheme
import com.yunai.phototube.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

internal enum class FilterPage(val title: String, val advanced: Boolean = false) {
    ROOT("筛选"), MEDIA("媒体类型"), FAVORITE("收藏状态"),
    SCOPE("组合筛选", true), FROM("开始日期", true), TO("结束日期", true),
    FOLDERS("目录", true), ALL_TAGS("全部包含", true), ANY_TAGS("任一包含", true), EXCLUDE_TAGS("排除标签", true)
}

/** One source-anchored surface; advanced selections are a draft until Apply. */
@Composable
internal fun FilterMenu(backdrop: Backdrop, insets: CornerInsets, opacity: Float, filters: WallFilters,
    onChange: (WallFilters) -> Unit, onDismiss: () -> Unit, server: PhotoTubeServer? = null) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val expansion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(FilterPage.ROOT) }
    var draft by remember { mutableStateOf(filters) }
    val catalogRows = remember { mutableStateMapOf<FilterPage, Int>() }
    val density = LocalDensity.current
    val view = LocalView.current
    var windowBottom by remember { mutableFloatStateOf(0f) }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val keyboardOverlap = with(density) { (windowBottom-(view.height-imeBottom)).coerceAtLeast(0f).toDp() }
    val scale = density.fontScale.coerceAtLeast(1f)
    val rowHeight = 48.dp * scale
    val headerHeight = 48.dp * scale
    val colors = AppTheme.colors
    val dark = LocalDarkTheme.current
    val glass = LocalGlassAppearance.current
    val invalid = filterScopeError(draft)
    val catalog = remember(server) { server?.let(::FilterCatalog) }
    fun close() {
        if (closing) return
        closing = true
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
        val requestedHeight = when (page) {
            FilterPage.ROOT -> headerHeight + rowHeight * (3 + (if(filters.active) 1 else 0) + (if(catalog!=null) 1 else 0)) + 12.dp
            FilterPage.MEDIA, FilterPage.FAVORITE -> headerHeight + rowHeight * 3 + 12.dp
            FilterPage.FOLDERS, FilterPage.ALL_TAGS, FilterPage.ANY_TAGS, FilterPage.EXCLUDE_TAGS ->
                headerHeight + rowHeight * (catalogRows[page] ?: 5) + 20.dp
            else -> 560.dp * scale
        }
        val menuWidth by animateDpAsState(minOf((if(page.advanced) 336.dp else 264.dp)*scale,
            maxWidth-insets.left-insets.right), spring(1f,550f), label="filter-menu-width")
        val menuHeight by animateDpAsState(minOf(requestedHeight,
            (maxHeight-insets.top-maxOf(insets.bottom,keyboardOverlap)).coerceAtLeast(96.dp)), spring(1f,550f), label="filter-menu-height")
        val p = expansion.value.coerceIn(0f,1f)
        val contentAlpha = ((p-.35f)/.65f).coerceIn(0f,1f)
        Box(Modifier.align(Alignment.TopEnd).absolutePadding(right=insets.right,top=insets.top)
            .width(48.dp+(menuWidth-48.dp)*p).height(48.dp+(menuHeight-48.dp)*p)
            .drawBackdrop(backdrop, shape={RoundedCornerShape(24.dp)},
                effects={vibrancy();blur((glass.blur+10f*p).dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                // A reading surface needs more luminance stability than an icon's lens.
                // Keep the same glass hue and edge optics, materializing its body on expansion.
                onDrawSurface={drawRect(colors.glass.copy(alpha=opacity+((if(dark) .90f else .88f)-opacity)*p))})
            .clip(RoundedCornerShape(24.dp)).pointerInput(Unit) { detectTapGestures { } }
            .testTag("filter-menu").semantics { paneTitle=page.title }) {
            Icon(Icons.Rounded.FilterList,null,Modifier.align(Alignment.TopEnd).padding(13.dp).size(22.dp)
                .graphicsLayer { alpha=1f-contentAlpha },tint=colors.text)
            Column(Modifier.requiredWidth(menuWidth).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer {alpha=contentAlpha}) {
                Row(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(page!=FilterPage.ROOT) Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp))
                        .clickable(enabled=!closing,role=Role.Button,onClick=::back)
                        .semantics {contentDescription="返回筛选菜单"},contentAlignment=Alignment.Center) { MenuChevron(colors.text,true) }
                    else Spacer(Modifier.width(12.dp))
                    Text(page.title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=colors.text,maxLines=1,overflow=TextOverflow.Ellipsis)
                    TextButton(onClick={if(page.advanced) onChange(draft);close()},enabled=!closing && (!page.advanced || invalid==null)) {
                        Text(if(page.advanced) "应用" else "完成",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
                AnimatedContent(page,modifier=Modifier.weight(1f),transitionSpec={
                    (fadeIn(tween(140))+slideInHorizontally(spring(1f,600f)){if(targetState.ordinal>initialState.ordinal) it/12 else -it/12}) togetherWith
                        (fadeOut(tween(90))+slideOutHorizontally(tween(140)){if(targetState.ordinal>initialState.ordinal) -it/12 else it/12})
                },label="filter-submenu") { current ->
                    if(current.advanced && catalog!=null) FilterScopeContent(current,draft,{draft=it},{page=it},catalog) {catalogRows[current]=it}
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=8.dp)
                        .then(if(current!=FilterPage.ROOT) Modifier.selectableGroup() else Modifier)) {
                        when(current) {
                            FilterPage.MEDIA -> {
                                MenuChoice("照片与视频",filters.kind==null,!closing,true) {onChange(filters.copy(kind=null));page=FilterPage.ROOT}
                                WallKind.entries.forEach {kind -> MenuChoice("仅${kind.label}",filters.kind==kind,!closing,true) {onChange(filters.copy(kind=kind));page=FilterPage.ROOT} }
                            }
                            FilterPage.FAVORITE -> listOf(null to "全部",true to "只看收藏",false to "只看未收藏").forEach {(value,label) ->
                                MenuChoice(label,filters.favorite==value,!closing,true) {onChange(filters.copy(favorite=value));page=FilterPage.ROOT}
                            }
                            else -> {
                                FilterNavigationRow("媒体类型",filters.kind?.label ?: "全部",enabled=!closing) {page=FilterPage.MEDIA}
                                FilterNavigationRow("收藏状态",when(filters.favorite){true->"已收藏";false->"未收藏";null->"全部"},enabled=!closing) {page=FilterPage.FAVORITE}
                                MenuChoice("只看归档",filters.archived,!closing,uncheckedLabel="关闭") {onChange(filters.copy(archived=!filters.archived))}
                                if(catalog!=null) {
                                    FilterSeparator()
                                    FilterNavigationRow("日期、目录与标签",if(filters.hasScope) "已设置" else "",enabled=!closing) {draft=filters;page=FilterPage.SCOPE}
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
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),color=AppTheme.colors.text,fontSize=16.sp)
        if(value.isNotEmpty()) Text(value,Modifier.widthIn(max=130.dp).padding(start=8.dp),color=AppTheme.colors.secondary,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp));MenuChevron(AppTheme.colors.secondary)
    }
}

@Composable
internal fun MenuChoice(title:String,checked:Boolean,enabled:Boolean=true,radio:Boolean=false,uncheckedLabel:String?=null,onClick:()->Unit) {
    val colors=AppTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp*LocalDensity.current.fontScale.coerceAtLeast(1f))
        .then(if(radio) Modifier.selectable(checked,enabled=enabled,role=Role.RadioButton,onClick=onClick) else Modifier.toggleable(checked,enabled,Role.Checkbox){onClick()})
        .padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),color=colors.text.copy(alpha=if(enabled) 1f else .45f),fontSize=16.sp)
        if(checked) Icon(Icons.Outlined.Check,null,Modifier.padding(start=12.dp).size(20.dp),tint=colors.accent)
        else if(uncheckedLabel!=null) Text(uncheckedLabel,color=colors.secondary,fontSize=13.sp)
    }
}

@Composable
internal fun FilterTextAction(label:String,enabled:Boolean=true,onClick:()->Unit) {
    TextButton(onClick,Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=enabled) { Text(label,fontSize=15.sp) }
}

@Composable
internal fun FilterSeparator() { Box(Modifier.padding(horizontal=18.dp).fillMaxWidth().height(.5.dp).background(AppTheme.colors.separator)) }

@Composable
internal fun MenuChevron(tint:androidx.compose.ui.graphics.Color,back:Boolean=false) {
    Canvas(Modifier.size(7.dp,11.dp).graphicsLayer {rotationZ=if(back) 180f else 0f}) {
        val inset=1.dp.toPx()
        val path=androidx.compose.ui.graphics.Path().apply {moveTo(inset,inset);lineTo(size.width-inset,size.height/2);lineTo(inset,size.height-inset)}
        drawPath(path,tint,style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.6.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
