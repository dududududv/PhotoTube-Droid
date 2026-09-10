package com.yunai.phototube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*
import com.yunai.phototube.glass.LocalGlassAppearance
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.delay

@Composable
internal fun SearchDock(expanded:Boolean,library:Boolean,value:String,onValue:(String)->Unit,onOpen:()->Unit,onClose:()->Unit,
    backdrop:Backdrop,insets:CornerInsets,collapsedSize:Dp,iconSize:Dp,opacity:Float,resultCount:Int?) {
    val p by animateFloatAsState(if(expanded) 1f else 0f,spring(.9f,550f),label="search-expansion")
    val density=LocalDensity.current
    val ime=with(density) {WindowInsets.ime.getBottom(this).toDp()}
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val requester=remember {FocusRequester()}
    val colors=AppTheme.colors
    val glass=LocalGlassAppearance.current
    val label=if(library) "搜索图库" else "搜索相册"
    fun close() {focus.clearFocus();keyboard?.hide();onClose()}
    LaunchedEffect(expanded) {
        if(expanded) {delay(120);requester.requestFocus();keyboard?.show()}
        else {focus.clearFocus();keyboard?.hide()}
    }
    BackHandler(expanded && ime==0.dp) {close()}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val targetWidth=maxWidth-insets.left-insets.right
        val height=collapsedSize+(52.dp-collapsedSize)*p
        Column(Modifier.align(Alignment.BottomEnd).absolutePadding(right=insets.right,bottom=maxOf(insets.bottom,ime+8.dp)),horizontalAlignment=Alignment.End) {
            if(expanded) Text(if(resultCount==null) "搜索界面预览 · 暂未连接搜索服务" else "找到 $resultCount ${if(library) "项内容" else "本相册"}",color=colors.secondary,fontSize=12.sp,
                modifier=Modifier.padding(end=16.dp,bottom=8.dp).graphicsLayer {alpha=p})
            Row(Modifier.width(collapsedSize+(targetWidth-collapsedSize)*p).height(height)
                .drawBackdrop(backdrop=backdrop,shape={RoundedCornerShape(50)},
                    effects={vibrancy();blur(glass.blur.dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                    onDrawSurface={drawRect(colors.glass.copy(alpha=opacity))})
                .clip(RoundedCornerShape(50))
                .then(if(!expanded) Modifier.clickable(onClick=onOpen).testTag("search-button").semantics {contentDescription=label} else Modifier.testTag("search-dock")),
                verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(collapsedSize+(48.dp-collapsedSize)*p).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.Search,null,Modifier.size(iconSize),tint=colors.text)
                }
                if(expanded || p>.05f) {
                    val alpha=((p-.35f)/.65f).coerceIn(0f,1f)
                    BasicTextField(value,onValue,Modifier.weight(1f).focusRequester(requester).testTag("search-input")
                        .graphicsLayer {this.alpha=alpha}.semantics {contentDescription=label},singleLine=true,
                        textStyle=TextStyle(color=colors.text,fontSize=16.sp),cursorBrush=SolidColor(colors.accent),
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus();keyboard?.hide()}),
                        decorationBox={inner -> Box {if(value.isEmpty()) Text(label,color=colors.secondary,fontSize=16.sp);inner()} })
                    if(value.isNotEmpty()) IconButton({onValue("")},Modifier.size(36.dp).graphicsLayer {this.alpha=alpha}.semantics {contentDescription="清除搜索"}) {
                        Icon(Icons.Outlined.Close,null,Modifier.size(18.dp),tint=colors.secondary)
                    }
                    TextButton({close()},Modifier.graphicsLayer {this.alpha=alpha},contentPadding=PaddingValues(horizontal=12.dp)) {Text("取消",color=colors.text)}
                }
            }
        }
    }
}
