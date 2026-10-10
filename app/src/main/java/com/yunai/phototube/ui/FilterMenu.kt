package com.yunai.phototube.ui

import com.yunai.phototube.data.WallFilters
import com.yunai.phototube.data.WallKind
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import com.yunai.phototube.ui.icons.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.yunai.phototube.glass.LiquidButton
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** One anchored glass surface grows from the source button and hosts the menu stack. */
@Composable
internal fun FilterMenu(backdrop:Backdrop,insets:CornerInsets,opacity:Float,filters:WallFilters,
    onChange:(WallFilters)->Unit,onDismiss:()->Unit,onAdvanced:(()->Unit)?=null) {
    val expansion=remember {Animatable(0f)}
    val scope=rememberCoroutineScope()
    var closing by remember {mutableStateOf(false)}
    var submenu by remember {mutableStateOf<String?>(null)}
    val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    val rowHeight=48.dp*scale
    val headerHeight=maxOf(48.dp,32.dp*scale)
    val expandedHeight by animateDpAsState(headerHeight+rowHeight*(3+(if(submenu==null && filters.active) 1 else 0)+(if(submenu==null && onAdvanced!=null) 1 else 0))+8.dp,spring(.9f,550f),label="filter-menu-height")
    val colors=AppTheme.colors
    val glass=LocalGlassAppearance.current
    fun close() {
        if(closing) return
        closing=true
        scope.launch { expansion.animateTo(0f,spring(.9f,650f));onDismiss() }
    }
    LaunchedEffect(Unit) {expansion.animateTo(1f,spring(.8f,480f))}
    BackHandler {if(submenu!=null) submenu=null else close()}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("filter-menu-overlay")) {
        Box(Modifier.matchParentSize().clickable(interactionSource=null,indication=null,onClick=::close)
            .semantics {contentDescription="关闭筛选菜单"})
        val menuWidth=minOf(244.dp*scale,352.dp,maxWidth-insets.left-insets.right)
        val availableHeight=(maxHeight-insets.top-insets.bottom).coerceAtLeast(48.dp)
        val targetHeight=minOf(expandedHeight,availableHeight)
        val p=expansion.value.coerceIn(0f,1f)
        val contentAlpha=((p-.35f)/.65f).coerceIn(0f,1f)
        Box(Modifier.align(Alignment.TopEnd).absolutePadding(right=insets.right,top=insets.top)
            .width(48.dp+(menuWidth-48.dp)*p).height(48.dp+(targetHeight-48.dp)*p)
            .drawBackdrop(backdrop=backdrop,shape={RoundedCornerShape(24.dp)},
                // The collapsed control uses button glass; its expanded reading surface
                // needs more background diffusion, without changing tint or lens geometry.
                effects={vibrancy();blur((glass.blur + 6f*p).dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                onDrawSurface={drawRect(colors.glass.copy(alpha=opacity))})
            .clip(RoundedCornerShape(24.dp)).clickable(interactionSource=null,indication=null) {}
            .testTag("filter-menu").semantics {paneTitle=submenu ?: "筛选"}) {
            Icon(Icons.Rounded.FilterList,null,Modifier.align(Alignment.TopEnd).padding(13.dp).size(22.dp)
                .graphicsLayer {alpha=1f-contentAlpha},tint=colors.text)
            Column(Modifier.requiredWidth(menuWidth).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer {alpha=contentAlpha}) {
                Row(Modifier.fillMaxWidth().height(headerHeight).padding(start=12.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(submenu!=null) LiquidButton({submenu=null},backdrop,Modifier.size(48.dp).semantics {contentDescription="返回筛选菜单"},
                        enabled=!closing,horizontalPadding=0.dp,surfaceColor=colors.glass.copy(alpha=.7f)) {MenuChevron(colors.secondary,back=true)}
                    Text(submenu ?: "筛选",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=colors.secondary,modifier=Modifier.weight(1f))
                    LiquidButton(::close,backdrop,Modifier.height(48.dp),enabled=!closing,horizontalPadding=12.dp,
                        surfaceColor=colors.glass.copy(alpha=.7f)) {Text("完成",color=colors.accent,fontSize=14.sp)}
                }
                AnimatedContent(submenu,modifier=Modifier.weight(1f),transitionSpec={
                    (fadeIn(tween(160))+slideInHorizontally(tween(200)){if(targetState!=null) it/5 else -it/5}) togetherWith
                        (fadeOut(tween(100))+slideOutHorizontally(tween(160)){if(targetState!=null) -it/5 else it/5})
                },label="filter-submenu") {sub ->
                    Column(Modifier.verticalScroll(rememberScrollState()).then(if(sub!=null) Modifier.selectableGroup() else Modifier).padding(bottom=8.dp)) {
                        if(sub=="媒体类型") {
                            MenuChoice("照片与视频",filters.kind==null,!closing,radio=true) {onChange(filters.copy(kind=null));submenu=null}
                            WallKind.entries.forEach {kind ->
                                MenuChoice("仅${kind.label}",filters.kind==kind,!closing,radio=true) {onChange(filters.copy(kind=kind));submenu=null}
                            }
                        } else if(sub=="收藏状态") {
                            listOf(null to "全部",true to "只看收藏",false to "只看未收藏").forEach {(value,label) ->
                                MenuChoice(label,filters.favorite==value,!closing,radio=true) {onChange(filters.copy(favorite=value));submenu=null}
                            }
                        } else {
                            Row(Modifier.fillMaxWidth().height(rowHeight).clickable(enabled=!closing){submenu="媒体类型"}.padding(horizontal=18.dp)
                                .semantics {contentDescription="媒体类型，${filters.kind?.label ?: "照片与视频"}，子菜单"},verticalAlignment=Alignment.CenterVertically) {
                                Text("媒体类型",color=colors.text,fontSize=16.sp,modifier=Modifier.weight(1f))
                                Text(filters.kind?.label ?: "全部",color=colors.secondary,fontSize=13.sp)
                                Spacer(Modifier.width(10.dp))
                                MenuChevron(colors.secondary)
                            }
                            Row(Modifier.fillMaxWidth().height(rowHeight).clickable(enabled=!closing){submenu="收藏状态"}.padding(horizontal=18.dp)
                                .semantics {contentDescription="收藏状态，${when(filters.favorite) {true->"只看收藏";false->"只看未收藏";null->"全部"}}，子菜单"},verticalAlignment=Alignment.CenterVertically) {
                                Text("收藏状态",color=colors.text,fontSize=16.sp,modifier=Modifier.weight(1f))
                                Text(when(filters.favorite) {true->"已收藏";false->"未收藏";null->"全部"},color=colors.secondary,fontSize=13.sp)
                                Spacer(Modifier.width(10.dp))
                                MenuChevron(colors.secondary)
                            }
                            MenuChoice("只看归档",filters.archived,!closing) {onChange(filters.copy(archived=!filters.archived))}
                            if(onAdvanced!=null) Row(Modifier.fillMaxWidth().height(rowHeight).clickable(enabled=!closing) {
                                closing=true
                                scope.launch {expansion.animateTo(0f,spring(.9f,650f));onAdvanced()}
                            }.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("日期、目录与标签",color=colors.text,fontSize=16.sp,modifier=Modifier.weight(1f))
                                MenuChevron(colors.secondary)
                            }
                            // Actions belong to the same reading surface. Resampling the raw
                            // wall here would reintroduce sharp seams inside the blurred menu.
                            if(filters.active) Box(Modifier.fillMaxWidth().height(rowHeight)
                                .clickable(enabled=!closing,role=Role.Button) {onChange(WallFilters(keyword=filters.keyword))}
                                .padding(horizontal=18.dp),contentAlignment=Alignment.Center) {
                                Text("清除筛选",color=colors.accent,fontSize=16.sp,fontWeight=FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChoice(title:String,checked:Boolean,enabled:Boolean,radio:Boolean=false,onClick:()->Unit) {
    val colors=AppTheme.colors
    Row(Modifier.fillMaxWidth().height(48.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).then(if(radio) Modifier.selectable(checked,enabled=enabled,role=Role.RadioButton,onClick=onClick) else Modifier.toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange={onClick()}))
        .padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=colors.text,fontSize=16.sp,modifier=Modifier.weight(1f))
        if(checked) Icon(Icons.Outlined.Check,null,Modifier.size(20.dp),tint=colors.accent)
    }
}

@Composable
private fun MenuChevron(tint:androidx.compose.ui.graphics.Color,back:Boolean=false) {
    Canvas(Modifier.size(7.dp,11.dp).graphicsLayer {rotationZ=if(back) 180f else 0f}) {
        val inset=1.dp.toPx()
        val path=androidx.compose.ui.graphics.Path().apply {
            moveTo(inset,inset)
            lineTo(size.width-inset,size.height/2f)
            lineTo(inset,size.height-inset)
        }
        drawPath(path,tint,style=androidx.compose.ui.graphics.drawscope.Stroke(
            width=1.6.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
