package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunai.phototube.ui.state.rememberConfigurationState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.request.*
import coil3.toBitmap
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassToggleChip
import com.yunai.phototube.ui.media.RemoteMedia
import com.yunai.phototube.ui.theme.AppTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.math.*

/** Bounded decoded preview and recipe only; never retain a Context or UI callback. */
private class PhotoEditDraft {
    val image = mutableStateOf<ImageBitmap?>(null)
    val original = mutableStateOf(EditTransform())
    val draft = mutableStateOf(EditTransform())
    val expected = mutableStateOf<String?>(null)
    val hash = mutableStateOf<String?>(null)
    val error = mutableStateOf<String?>(null)
    val conflict = mutableStateOf(false)
    val croppedPreview = mutableStateOf(false)
}

@Composable
internal fun PhotoEditorScreen(source:RemoteMedia,onSaved:()->Unit,onClose:()->Unit) {
    val repository=remember(source) {EditVersionsRepository(source.server)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val retained = rememberConfigurationState(source.server to source.asset.id, enabled = !source.server.privateMedia) { PhotoEditDraft() }
    var image by retained.image
    var original by retained.original
    var draft by retained.draft
    var expected by retained.expected
    var hash by retained.hash
    var loading by remember {mutableStateOf(true)}
    var saving by remember {mutableStateOf(false)}
    var error by retained.error
    var conflict by retained.conflict
    var attempt by remember {mutableIntStateOf(0)}
    var croppedPreview by retained.croppedPreview
    var confirmDiscard by rememberSaveable(source.asset.id) { mutableStateOf(false) }
    var closeRequested by rememberSaveable(source.asset.id) { mutableStateOf(false) }
    val dirty = draft != original
    LaunchedEffect(attempt) {
        if (attempt == 0 && image != null) { loading = false; return@LaunchedEffect }
        loading=true;error=null;image=null;hash=null
        try {
            val detail=source.server.detail(source.asset.id)
            check(detail.getString("state") in setOf("BROWSABLE","PROCESSING")) {"此照片当前不可编辑"}
            val contentHash=detail.getString("contentHash")
            check(contentHash.matches(Regex("[a-f0-9]{64}"))) {"原文件信息尚未准备好"}
            val active=detail.optJSONObject("activeEdit")
            val current=active?.nullString("editVersionId")
            var transform=EditTransform()
            if(current!=null && active.optString("sourceState")=="CURRENT") {
                var cursor:String?=null
                val seen=mutableSetOf<String>()
                do {
                    val page=repository.list(source.asset.id,cursor)
                    val selected=page.items.firstOrNull {it.id==current}
                    if(selected!=null) {transform=EditTransform.from(selected.transform);break}
                    cursor=page.cursor
                    check(cursor!=null && seen.add(cursor)) {"未找到当前编辑版本，请刷新重试"}
                } while(true)
            }
            val result=source.server.imageLoader.execute(ImageRequest.Builder(context).data(source.server.original(source.asset.id)).memoryCacheKey("${source.server.base}/${source.asset.id}/original/$contentHash").size(2048).allowHardware(false).build())
            check(result is SuccessResult) {"原图加载失败，请重试"}
            val bitmap=result.image.toBitmap().asImageBitmap()
            withContext(Dispatchers.Main.immediate) {source.detail=detail;hash=contentHash;expected=current;original=transform;draft=transform;image=bitmap;conflict=false}
        } catch(cancel:CancellationException) {throw cancel}
        catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {error=problem.message}}
        finally {withContext(Dispatchers.Main.immediate) {loading=false}}
    }
    ManagementPage("编辑照片",onClose,dismissEnabled=!saving,scrollable=false,
        compactHeader=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp<480,
        onCloseAttempt={if(dirty && !closeRequested) {confirmDiscard=true;false} else true},
        closeRequested=closeRequested, overlay={ scene ->
            if(confirmDiscard) com.yunai.phototube.ui.GlassConfirmation(scene,
                title="放弃未保存的修改？", message="当前裁切、旋转和镜像尚未保存。返回编辑可以继续调整。",
                confirmLabel="放弃修改", cancelLabel="继续编辑", destructive=true,
                onConfirm={confirmDiscard=false;closeRequested=true}, onDismiss={confirmDiscard=false})
        }) {
        val bitmap=image
        EditorWorkspace(canvas={ canvasModifier ->
            if(bitmap!=null) CropEditorCanvas(bitmap,draft,!loading && !saving && !croppedPreview,{draft=it},croppedPreview,canvasModifier.testTag("crop-canvas"))
            else Spacer(canvasModifier)
        }, controls={
        Text("拖动边角裁切，拖动选区调整位置。保存为新版本，原图保留。",color=AppTheme.colors.secondary)
        if(loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!saving) error?.let {Text(it,color=AppTheme.colors.text)}
        if((image==null && !loading) || conflict) GlassSettingsAction(if(conflict) "重新载入当前版本（重置草稿）" else "重试加载原图", {attempt++}, enabled=!saving)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(116.dp)) { GlassSettingsAction("旋转 90°", {draft=draft.copy(rotation=(draft.rotation+90)%360)}, enabled=bitmap!=null && !loading && !saving) }
            GlassToggleChip("水平镜像", draft.flipHorizontal, {draft=draft.copy(flipHorizontal=it)}, enabled=bitmap!=null && !loading && !saving)
            GlassToggleChip("垂直镜像", draft.flipVertical, {draft=draft.copy(flipVertical=it)}, enabled=bitmap!=null && !loading && !saving)
        }
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(112.dp)) { GlassSettingsAction("完整画幅", {draft=draft.copy(x=0,y=0,width=CROP_UNIT,height=CROP_UNIT)}, enabled=bitmap!=null && !loading && !saving) }
            Box(Modifier.width(112.dp)) { GlassSettingsAction(if(croppedPreview) "调整裁切" else "预览效果", {croppedPreview=!croppedPreview}, enabled=bitmap!=null && !loading && !saving) }
            Box(Modifier.width(112.dp)) { GlassSettingsAction("还原修改", {draft=original}, enabled=bitmap!=null && !loading && !saving) }
        }
        GlassSettingsAction("保存新版本", {val transform=draft;val sourceHash=hash ?: return@GlassSettingsAction;saving=true;conflict=true;error="保存结果尚未确认，请核对版本历史，避免重复创建。";scope.launch {
            try {
                val version=repository.create(source.asset.id,expected,sourceHash,transform)
                withContext(Dispatchers.Main.immediate) {
                    source.detail=JSONObject(source.detail!!.toString()).put("activeEdit",JSONObject().put("editVersionId",version.id).put("sourceState",version.sourceState)
                        .put("renderState",version.renderState).put("displayWidth",version.width).put("displayHeight",version.height))
                    source.server.imageLoader.memoryCache?.clear();onSaved()
                }
            } catch(cancel:CancellationException) {throw cancel}
            catch(problem:Exception) {withContext(Dispatchers.Main.immediate) {error="保存未确认：${problem.message}。请核对版本历史，避免重复创建。";conflict=true}}
            finally {withContext(Dispatchers.Main.immediate) {saving=false}}
        }},enabled=bitmap!=null && !loading && !saving && !conflict && draft!=original,modifier=Modifier.fillMaxWidth())
        })
    }

}

/** Reserve preview space independently of tool text size and error messages. */
@Composable
internal fun EditorWorkspace(canvas:@Composable (Modifier)->Unit,controls:@Composable ColumnScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val toolsHeight=maxHeight*.5f
        if(maxWidth>=600.dp && maxWidth>maxHeight) {
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                canvas(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("editor-tools"),
                    verticalArrangement=Arrangement.spacedBy(12.dp),content=controls)
            }
        } else {
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                canvas(Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.fillMaxWidth().heightIn(max=toolsHeight).verticalScroll(rememberScrollState()).testTag("editor-tools"),
                    verticalArrangement=Arrangement.spacedBy(12.dp),content=controls)
            }
        }
    }
}

/** Normalized crop is applied after clockwise rotation and canvas-axis mirroring. */
@Composable
internal fun CropEditorCanvas(image:ImageBitmap,transform:EditTransform,enabled:Boolean,onChange:(EditTransform)->Unit,preview:Boolean,modifier:Modifier) {
    val current by rememberUpdatedState(transform)
    val change by rememberUpdatedState(onChange)
    val accent=AppTheme.colors.accent
    fun frame(width:Float,height:Float,t:EditTransform):Rect {
        val iw=if(t.rotation%180==0) image.width else image.height
        val ih=if(t.rotation%180==0) image.height else image.width
        val scale=min(width/iw,height/ih)
        return Rect((width-iw*scale)/2,(height-ih*scale)/2,(width+iw*scale)/2,(height+ih*scale)/2)
    }
    Canvas(modifier.background(AppTheme.colors.subtle).pointerInput(image,enabled) {
        if(!enabled) return@pointerInput
        var corner=-1
        var gestureTransform=current
        detectDragGestures(onDragStart={point ->
            gestureTransform=current
            val t=gestureTransform;val f=frame(size.width.toFloat(),size.height.toFloat(),t)
            val crop=Rect(f.left+f.width*t.x/CROP_UNIT,f.top+f.height*t.y/CROP_UNIT,f.left+f.width*(t.x+t.width)/CROP_UNIT,f.top+f.height*(t.y+t.height)/CROP_UNIT)
            val corners=listOf(crop.topLeft,crop.topRight,crop.bottomLeft,crop.bottomRight)
            corner=corners.indices.minBy {corners[it].minus(point).getDistance()}.takeIf {corners[it].minus(point).getDistance()<32.dp.toPx()} ?: -1
        },onDrag={event,delta ->
            event.consume();val t=gestureTransform;val f=frame(size.width.toFloat(),size.height.toFloat(),t)
            val dx=(delta.x/f.width*CROP_UNIT).roundToInt();val dy=(delta.y/f.height*CROP_UNIT).roundToInt()
            val minimum=10_000
            var l=t.x;var top=t.y;var r=t.x+t.width;var b=t.y+t.height
            when(corner) {
                0 -> {l=(l+dx).coerceIn(0,(r-minimum).coerceAtLeast(0));top=(top+dy).coerceIn(0,(b-minimum).coerceAtLeast(0))}
                1 -> {r=(r+dx).coerceIn((l+minimum).coerceAtMost(CROP_UNIT),CROP_UNIT);top=(top+dy).coerceIn(0,(b-minimum).coerceAtLeast(0))}
                2 -> {l=(l+dx).coerceIn(0,(r-minimum).coerceAtLeast(0));b=(b+dy).coerceIn((top+minimum).coerceAtMost(CROP_UNIT),CROP_UNIT)}
                3 -> {r=(r+dx).coerceIn((l+minimum).coerceAtMost(CROP_UNIT),CROP_UNIT);b=(b+dy).coerceIn((top+minimum).coerceAtMost(CROP_UNIT),CROP_UNIT)}
                else -> {l=(l+dx).coerceIn(0,CROP_UNIT-t.width);top=(top+dy).coerceIn(0,CROP_UNIT-t.height);r=l+t.width;b=top+t.height}
            }
            gestureTransform=t.copy(x=l,y=top,width=r-l,height=b-top)
            change(gestureTransform)
        })
    }) {
        val t=transform
        val iw=if(t.rotation%180==0) image.width.toFloat() else image.height.toFloat()
        val ih=if(t.rotation%180==0) image.height.toFloat() else image.width.toFloat()
        val cropWidth=iw*t.width/CROP_UNIT;val cropHeight=ih*t.height/CROP_UNIT
        val f=frame(size.width,size.height,t)
        val scale=if(preview) min(size.width/cropWidth,size.height/cropHeight) else f.width/iw
        val left=if(preview) (size.width-cropWidth*scale)/2 else f.left
        val top=if(preview) (size.height-cropHeight*scale)/2 else f.top
        clipRect(left,top,left+(if(preview) cropWidth else iw)*scale,top+(if(preview) cropHeight else ih)*scale) {
            withTransform({
                translate(left,top);scale(scale,scale,Offset.Zero)
                if(preview) translate(-iw*t.x/CROP_UNIT,-ih*t.y/CROP_UNIT)
                translate(iw/2,ih/2);scale(if(t.flipHorizontal) -1f else 1f,if(t.flipVertical) -1f else 1f,Offset.Zero)
                rotate(t.rotation.toFloat(),Offset.Zero)
            }) {drawImage(image,Offset(-image.width/2f,-image.height/2f))}
        }
        if(!preview) {
            val crop=Rect(f.left+f.width*t.x/CROP_UNIT,f.top+f.height*t.y/CROP_UNIT,f.left+f.width*(t.x+t.width)/CROP_UNIT,f.top+f.height*(t.y+t.height)/CROP_UNIT)
            val shade=Color.Black.copy(alpha=.48f)
            drawRect(shade,f.topLeft,Size(f.width,crop.top-f.top));drawRect(shade,Offset(f.left,crop.bottom),Size(f.width,f.bottom-crop.bottom))
            drawRect(shade,Offset(f.left,crop.top),Size(crop.left-f.left,crop.height));drawRect(shade,Offset(crop.right,crop.top),Size(f.right-crop.right,crop.height))
            drawRect(Color.White,crop.topLeft,crop.size,style=Stroke(1.5.dp.toPx()))
            for(i in 1..2) {drawLine(Color.White.copy(.45f),Offset(crop.left+crop.width*i/3,crop.top),Offset(crop.left+crop.width*i/3,crop.bottom),1.dp.toPx());drawLine(Color.White.copy(.45f),Offset(crop.left,crop.top+crop.height*i/3),Offset(crop.right,crop.top+crop.height*i/3),1.dp.toPx())}
            listOf(crop.topLeft,crop.topRight,crop.bottomLeft,crop.bottomRight).forEach {drawCircle(accent,6.dp.toPx(),it);drawCircle(Color.White,6.dp.toPx(),it,style=Stroke(1.5.dp.toPx()))}
        }
    }
}
