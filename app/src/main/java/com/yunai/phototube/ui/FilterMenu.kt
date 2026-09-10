package com.yunai.phototube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
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
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.ui.media.MediaIcon
import com.yunai.phototube.ui.media.MediaSymbol
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** One anchored glass surface grows from the source button and hosts the menu stack. */
@Composable
internal fun FilterMenu(backdrop:Backdrop,insets:CornerInsets,opacity:Float,favoritesOnly:Boolean,newestFirst:Boolean,
    onFavorites:(Boolean)->Unit,onOrder:(Boolean)->Unit,onDismiss:()->Unit,preview:Boolean=false) {
    val expansion=remember {Animatable(0f)}
    val scope=rememberCoroutineScope()
    var closing by remember {mutableStateOf(false)}
    var sorting by remember {mutableStateOf(false)}
    val expandedHeight by animateDpAsState(if(sorting) 156.dp else 208.dp,spring(.9f,550f),label="filter-menu-height")
    val colors=AppTheme.colors
    val glass=LocalGlassAppearance.current
    fun close() {
        if(closing) return
        closing=true
        scope.launch { expansion.animateTo(0f,spring(.9f,650f));onDismiss() }
    }
    LaunchedEffect(Unit) {expansion.animateTo(1f,spring(.8f,480f))}
    BackHandler {if(sorting) sorting=false else close()}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("filter-menu-overlay")) {
        Box(Modifier.matchParentSize().clickable(interactionSource=null,indication=null,onClick=::close)
            .semantics {contentDescription="关闭筛选菜单"})
        val menuWidth=minOf(264.dp,maxWidth-insets.left-insets.right)
        val p=expansion.value.coerceIn(0f,1f)
        val contentAlpha=((p-.35f)/.65f).coerceIn(0f,1f)
        Box(Modifier.align(Alignment.TopEnd).absolutePadding(right=insets.right,top=insets.top)
            .width(48.dp+(menuWidth-48.dp)*p).height(48.dp+(expandedHeight-48.dp)*p)
            .drawBackdrop(backdrop=backdrop,shape={RoundedCornerShape(24.dp)},
                effects={vibrancy();blur((glass.blur+12f*p).dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                onDrawSurface={drawRect(colors.glass.copy(alpha=opacity))})
            .clip(RoundedCornerShape(24.dp)).clickable(interactionSource=null,indication=null) {}
            .testTag("filter-menu").semantics {paneTitle=if(sorting) "排列顺序" else "筛选"}) {
            Icon(Icons.Rounded.FilterList,null,Modifier.align(Alignment.TopEnd).padding(13.dp).size(22.dp)
                .graphicsLayer {alpha=1f-contentAlpha},tint=colors.text)
            Column(Modifier.requiredWidth(menuWidth).align(Alignment.TopEnd).graphicsLayer {alpha=contentAlpha}) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(start=18.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(sorting) IconButton({sorting=false},Modifier.size(36.dp).semantics {contentDescription="返回筛选菜单"}) {MediaIcon(MediaSymbol.BACK,colors.text)}
                    Text(if(sorting) "排列顺序" else if(preview) "筛选 · 界面预览" else "筛选",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=colors.secondary,modifier=Modifier.weight(1f))
                    TextButton({close()},enabled=!closing) {Text("完成",color=colors.accent)}
                }
                AnimatedContent(sorting,transitionSpec={
                    (fadeIn(tween(160))+slideInHorizontally(tween(200)){if(targetState) it/5 else -it/5}) togetherWith
                        (fadeOut(tween(100))+slideOutHorizontally(tween(160)){if(targetState) -it/5 else it/5})
                },label="filter-submenu") {sub ->
                    Column {
                        if(sub) {
                            MenuChoice("最新在前",newestFirst,!closing) {onOrder(true);sorting=false}
                            MenuChoice("最早在前",!newestFirst,!closing) {onOrder(false);sorting=false}
                        } else {
                            MenuChoice("全部照片与视频",!favoritesOnly,!closing) {onFavorites(false)}
                            MenuChoice("只看收藏",favoritesOnly,!closing) {onFavorites(!favoritesOnly)}
                            Row(Modifier.fillMaxWidth().height(52.dp).clickable(enabled=!closing){sorting=true}.padding(horizontal=18.dp)
                                .semantics {contentDescription="排列顺序，${if(newestFirst) "最新在前" else "最早在前"}，子菜单"},verticalAlignment=Alignment.CenterVertically) {
                                Text("排列顺序",color=colors.text,fontSize=16.sp,modifier=Modifier.weight(1f))
                                Text(if(newestFirst) "最新" else "最早",color=colors.secondary,fontSize=13.sp)
                                Box(Modifier.size(24.dp).graphicsLayer {rotationZ=180f}) {MediaIcon(MediaSymbol.BACK,colors.secondary)}
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuChoice(title:String,checked:Boolean,enabled:Boolean,onClick:()->Unit) {
    val colors=AppTheme.colors
    Row(Modifier.fillMaxWidth().height(52.dp).toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange={onClick()})
        .padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=colors.text,fontSize=16.sp,modifier=Modifier.weight(1f))
        if(checked) Icon(Icons.Outlined.Check,null,Modifier.size(20.dp),tint=colors.accent)
    }
}
