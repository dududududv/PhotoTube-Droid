package com.yunai.phototube.ui.remote

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.theme.AppTheme

@Composable
internal fun RemotePhotoWall(vm:PhotoWallViewModel,topInset:Dp,modifier:Modifier=Modifier,onOpen:((RemoteAsset,List<RemoteAsset>,Map<String,androidx.compose.ui.geometry.Rect>)->Unit)?=null) {
    val colors=AppTheme.colors
    val bounds=remember {mutableMapOf<String,androidx.compose.ui.geometry.Rect>()}
    if(vm.stage!=ConnectionStage.READY) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(top=topInset+56.dp,bottom=150.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("图库",fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=colors.text)
            ConnectionForm(vm)
        }
        return
    }
    val flow=vm.pageFlow
    if(flow==null) {
        Column(modifier.fillMaxSize().padding(top=topInset+64.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(vm.busy) CircularProgressIndicator() else Failure(vm.error,vm::refresh)
        }
        return
    }
    val items=flow.collectAsLazyPagingItems()
    val api=vm.server ?: return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val snapshot=items.itemSnapshotList
        val width=(maxWidth-36.dp).value.coerceAtLeast(1f)
        val rows=remember(snapshot,width) {photoWallRows(snapshot.items,width)}
        LazyColumn(modifier=Modifier.fillMaxSize().testTag("remote-photo-wall"),
            contentPadding=PaddingValues(start=18.dp,end=18.dp,top=topInset,bottom=140.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            item(key="title") {
                Column {
                    Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("图库",fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=colors.text,modifier=Modifier.weight(1f))
                    }
                    Text("${vm.groups.sumOf {it.count}} 项内容"+(if(vm.filters.active) " · ${vm.filters.label}" else ""),color=colors.secondary,fontSize=13.sp,modifier=Modifier.padding(top=8.dp,bottom=16.dp))
                    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    vm.error?.let {Failure(it,vm::refresh)}
                }
            }
            if(items.loadState.refresh is LoadState.Loading) item {Loading("正在加载照片…")}
            if(items.loadState.refresh is LoadState.Error) item {Failure((items.loadState.refresh as LoadState.Error).error,items::retry)}
            if(items.loadState.refresh is LoadState.NotLoading && items.itemCount==0) item {
                Column(Modifier.padding(vertical=64.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(vm.filters.active) "没有符合条件的照片或视频" else "还没有照片",color=colors.text,fontSize=20.sp)
                    if(vm.filters.active) TextButton({vm.updateFilters(WallFilters())}) {Text("清除筛选")}
                    else Text("请先在服务端创建相册、指定 NAS 文件夹并手动扫描。",color=colors.secondary,modifier=Modifier.padding(top=12.dp))
                }
            }
            items(rows.size,key={rows[it].key}) {rowIndex ->
                when(val row=rows[rowIndex]) {
                    is PhotoWallRow.Month -> {
                        items[row.index] // Notify Paging when a visible row is accessed.
                        Text("${row.entry.key.replace('-','年')}月 · ${row.entry.count} 项",color=colors.text,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
                    }
                    is PhotoWallRow.Photos -> Row(Modifier.fillMaxWidth().height(row.height.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.photos.forEach {photo ->
                            val asset=(items[photo.index] as? WallEntry.Asset)?.value ?: photo.asset
                            AssetThumbnail(asset,api,Modifier.width((row.height*photo.ratio).dp).fillMaxHeight()
                            .onGloballyPositioned {bounds[asset.id]=it.boundsInWindow()}
                            .clickable(enabled=onOpen!=null) {onOpen?.invoke(asset,snapshot.items.filterIsInstance<WallEntry.Asset>().map {it.value},bounds.toMap())})
                        }
                    }
                }
            }
            if(items.loadState.append is LoadState.Loading) item {Loading("正在加载更多…")}
            if(items.loadState.append is LoadState.Error) item {Failure((items.loadState.append as LoadState.Error).error,items::retry)}
        }
    }
}
@Composable
private fun ConnectionForm(vm:PhotoWallViewModel) {
    val colors=AppTheme.colors
    var address by rememberSaveable(vm.address) {mutableStateOf(vm.address)}
    var username by rememberSaveable {mutableStateOf("admin")}
    var password by remember {mutableStateOf("")}
    when(vm.stage) {
        ConnectionStage.ADDRESS,ConnectionStage.CONNECTING -> {
            Text("连接你的 PhotoTube",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=colors.text)
            Text("填写服务地址，连接后登录查看照片。",color=colors.secondary)
            OutlinedTextField(address,{address=it},label={Text("服务器地址")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("server-address"),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),shape=RoundedCornerShape(18.dp),enabled=!vm.busy)
            Button({vm.connect(address)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text(if(vm.busy) "正在连接…" else "连接服务器")}
        }
        ConnectionStage.LOGIN -> {
            Text("登录 PhotoTube",fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=colors.text)
            Text(vm.address,color=colors.secondary)
            OutlinedTextField(username,{username=it},label={Text("用户名")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("server-username"),enabled=!vm.busy,shape=RoundedCornerShape(18.dp))
            OutlinedTextField(password,{password=it},label={Text("密码")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth().testTag("server-password"),enabled=!vm.busy,shape=RoundedCornerShape(18.dp))
            Button({vm.login(username.trim(),password);password=""},enabled=!vm.busy && username.isNotBlank() && password.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {Text(if(vm.busy) "正在登录…" else "登录")}
            TextButton(vm::configure,enabled=!vm.busy) {Text("更换服务器")}
        }
        ConnectionStage.INITIALIZE -> {Text("请先由管理员在 NAS 上初始化登录口令。",color=colors.text);TextButton(vm::configure){Text("返回连接设置")}}
        ConnectionStage.READY -> Unit
    }
    vm.error?.let {Failure(it,null)}
}
@Composable
private fun Loading(label:String) {Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text(label,modifier=Modifier.padding(start=12.dp),color=AppTheme.colors.secondary)}}
@Composable
private fun Failure(error:Throwable?,retry:(()->Unit)?) {
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("server-error")) {
        Text(when(error) {is ServerError->error.message;is java.io.IOException->"无法连接服务器，请检查网络后重试";else->error?.message ?: "加载失败"},color=AppTheme.colors.text)
        (error as? ServerError)?.logId?.let {androidx.compose.foundation.text.selection.SelectionContainer {Text("日志编号：$it",color=AppTheme.colors.secondary,fontSize=12.sp)}}
        if(retry!=null) TextButton(retry) {Text("重试")}
    }
}
@Composable
private fun AssetThumbnail(asset:RemoteAsset,api:PhotoTubeServer,modifier:Modifier) {
    val colors=AppTheme.colors
    var failed by remember(asset.id,asset.contentHash) {mutableStateOf(false)}
    var attempt by remember(asset.id,asset.contentHash) {mutableIntStateOf(0)}
    var loaded by remember(asset.id,asset.contentHash) {mutableStateOf(false)}
    val url=api.thumbnail(asset)
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(colors.subtle)
        .testTag("asset-${asset.id}").semantics {contentDescription=asset.fileName}) {
        if(url!=null) key(attempt) {
            AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).build(),null,imageLoader=api.imageLoader,
                modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onSuccess={loaded=true;failed=false},onError={failed=true})
        }
        if(!loaded) Column(Modifier.align(Alignment.Center).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(url==null || asset.state=="PROCESSING") "缩略图处理中" else if(failed) "缩略图暂不可用" else "正在加载",color=colors.secondary,fontSize=12.sp)
            if(failed && url!=null) TextButton({failed=false;attempt++}) {Text("重试",fontSize=12.sp)}
        }
        if(asset.kind=="VIDEO") Text("▶ ${asset.durationSec?.toInt()?.let {"${it/60}:${(it%60).toString().padStart(2,'0')}"} ?: "视频"}",color=Color.White,fontSize=12.sp,
            modifier=Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(.45f),RoundedCornerShape(topStart=10.dp)).padding(8.dp))
    }
}
