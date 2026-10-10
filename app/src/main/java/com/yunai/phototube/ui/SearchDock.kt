package com.yunai.phototube.ui

import androidx.activity.compose.BackHandler
import android.view.WindowInsets as AndroidWindowInsets
import androidx.compose.ui.platform.LocalView
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
    backdrop:Backdrop,insets:CornerInsets,collapsedSize:Dp,iconSize:Dp,opacity:Float,resultCount:Int?,statusText:String?=null) {
    val p by animateFloatAsState(if(expanded) 1f else 0f,spring(.9f,550f),label="search-expansion")
    val density=LocalDensity.current
    val imeBottom=WindowInsets.ime.getBottom(density)
    val view=LocalView.current
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val requester=remember {FocusRequester()}
    val colors=AppTheme.colors
    val glass=LocalGlassAppearance.current
    val label=if(library) "搜索图库" else "搜索相册"
    var closing by remember(expanded) {mutableStateOf(false)}
    fun close() {if(!expanded || closing) return;closing=true;focus.clearFocus();keyboard?.hide();onClose()}
    LaunchedEffect(expanded,closing) {
        if(expanded && !closing) {delay(120);requester.requestFocus();keyboard?.show()}
        else {focus.clearFocus();keyboard?.hide()}
    }
    // Keep ownership during the IME exit animation: its height can remain nonzero
    // after the system keyboard back callback has already unregistered.
    BackHandler(expanded) {
        if(view.rootWindowInsets?.isVisible(AndroidWindowInsets.Type.ime())==true) {
            focus.clearFocus();keyboard?.hide()
        } else close()
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        // Compose consumes the IME inset once and tracks its platform animation.
        val bottom=if(expanded && imeBottom>0) 8.dp else insets.bottom
        val targetWidth=maxWidth-insets.left-insets.right
        val height=collapsedSize+(52.dp-collapsedSize)*p
        Column(Modifier.align(Alignment.BottomEnd).absolutePadding(right=insets.right,bottom=bottom),horizontalAlignment=Alignment.End) {
            if(expanded) Text(statusText ?: if(resultCount==null) "搜索界面预览 · 暂未连接搜索服务" else "找到 $resultCount ${if(library) "项内容" else "本相册"}",color=colors.text,fontSize=12.sp,
                modifier=Modifier.padding(bottom=8.dp).widthIn(max=targetWidth)
                    .graphicsLayer {alpha=p}
                    .drawBackdrop(backdrop=backdrop,shape={RoundedCornerShape(16.dp)},
                        effects={vibrancy();blur((glass.blur+6f*p).dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                        onDrawSurface={drawRect(colors.glass.copy(alpha=opacity))})
                    .padding(horizontal=16.dp,vertical=8.dp))
            Row(Modifier.width(collapsedSize+(targetWidth-collapsedSize)*p).height(height)
                .drawBackdrop(backdrop=backdrop,shape={RoundedCornerShape(50)},
                    effects={vibrancy();blur((glass.blur+6f*p).dp.toPx());lens(glass.refractionHeight.dp.toPx(),glass.refractionAmount.dp.toPx(),chromaticAberration=glass.dispersion)},
                    onDrawSurface={drawRect(colors.glass.copy(alpha=opacity))})
                .clip(RoundedCornerShape(50))
                .then(if(!expanded) Modifier.clickable(onClick=onOpen).testTag("search-button").semantics {contentDescription=label} else Modifier.testTag("search-dock")),
                verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(collapsedSize+(48.dp-collapsedSize)*p).fillMaxHeight(),contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.Search,null,Modifier.size(iconSize),tint=colors.text)
                }
                if(expanded || p>.05f) {
                    val alpha=((p-.35f)/.65f).coerceIn(0f,1f)
                    BasicTextField(value,{onValue(it.take(255))},Modifier.weight(1f).focusRequester(requester).testTag("search-input")
                        .graphicsLayer {this.alpha=alpha}.semantics {contentDescription=label},singleLine=true,enabled=expanded && !closing,
                        textStyle=TextStyle(color=colors.text,fontSize=16.sp),cursorBrush=SolidColor(colors.accent),
                        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus();keyboard?.hide()}),
                        decorationBox={inner -> Box {if(value.isEmpty()) Text(label,color=colors.secondary,fontSize=16.sp);inner()} })
                    // These actions share the search surface. A second backdrop here
                    // would reintroduce sharp photo seams behind text and icons.
                    if(value.isNotEmpty()) Box(Modifier.size(48.dp).graphicsLayer {this.alpha=alpha}
                        .clickable(enabled=expanded && !closing,role=Role.Button) {onValue("")}
                        .semantics {contentDescription="清除搜索"},contentAlignment=Alignment.Center) {
                        Icon(Icons.Outlined.Close,null,Modifier.size(18.dp),tint=colors.text)
                    }
                    Box(Modifier.height(48.dp).widthIn(min=48.dp).graphicsLayer {this.alpha=alpha}
                        .clickable(enabled=expanded && !closing,role=Role.Button,onClick=::close)
                        .padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
                        Text("取消",color=colors.text,maxLines=1)
                    }
                }
            }
        }
    }
}
