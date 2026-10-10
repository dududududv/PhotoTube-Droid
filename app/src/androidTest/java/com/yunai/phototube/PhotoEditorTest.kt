package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.PhotoEditorScreen
import com.yunai.phototube.ui.remote.CropEditorCanvas
import com.yunai.phototube.ui.media.RemoteMedia
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.*
import java.util.concurrent.CopyOnWriteArrayList

class PhotoEditorTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var host:MockWebServer
    private lateinit var server:PhotoTubeServer
    private fun json(value:Any)=MockResponse().setHeader("Content-Type","application/json").setBody(value.toString())
    @Before fun setup() {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        host=MockWebServer().apply {useHttps(tls.sslSocketFactory(),false);start()}
        server=PhotoTubeServer(InstrumentationRegistry.getInstrumentation().targetContext,host.url("/").toString(),{},OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build())
    }
    @After fun cleanup() {server.cookies.clear();server.dispose();host.shutdown()}
    private val hash="a".repeat(64)
    private fun version(id:String="v2",transform:EditTransform=EditTransform())=JSONObject().put("id",id).put("sourceContentHash",hash).put("active",true).put("sourceState","CURRENT").put("renderState","PENDING")
        .put("outputWidth",50).put("outputHeight",50).put("createdAt","2026-09-17T00:00:00Z").put("transform",transform.json())
    @Test fun transformRoundTripAndCreateUseExactRecipeAndFence()=runBlocking {
        val transform=EditTransform(123,456,800000,700000,270,true,false)
        Assert.assertEquals(transform,EditTransform.from(transform.json()))
        Assert.assertTrue(kotlin.runCatching {EditTransform(800000,0,300000)}.isFailure)
        Assert.assertTrue(kotlin.runCatching {EditTransform(rotation=45)}.isFailure)
        host.enqueue(json(version(transform=transform)).setResponseCode(201))
        val created=EditVersionsRepository(server).create("photo","v1",hash,transform)
        Assert.assertEquals("v2",created.id)
        val request=host.takeRequest();val body=JSONObject(request.body.readUtf8())
        Assert.assertEquals("POST",request.method);Assert.assertEquals("/api/v1/assets/photo/edit-versions",request.requestUrl!!.encodedPath)
        Assert.assertEquals(setOf("expectedActiveEditVersionId","sourceContentHash","transform"),body.keys().asSequence().toSet())
        Assert.assertEquals("v1",body.getString("expectedActiveEditVersionId"));Assert.assertEquals(hash,body.getString("sourceContentHash"))
        Assert.assertEquals(transform,EditTransform.from(body.getJSONObject("transform")))
    }
    @Test fun previewRotatesThenMirrorsThenCrops() {
        val bitmap=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888)
        for(y in 0 until 100) for(x in 0 until 100) bitmap.setPixel(x,y,when {x<50 && y<50 -> android.graphics.Color.RED;x>=50 && y<50 -> android.graphics.Color.GREEN;x<50 -> android.graphics.Color.BLUE;else -> android.graphics.Color.YELLOW})
        rule.setContent {PhotoTubeTheme {CropEditorCanvas(bitmap.asImageBitmap(),EditTransform(x=500000,width=500000,rotation=90,flipHorizontal=true),false,{},true,Modifier.size(200.dp).testTag("preview"))}}
        val pixels=rule.onNodeWithTag("preview").captureToImage().toPixelMap()
        val top=pixels[pixels.width/2,pixels.height/4];val bottom=pixels[pixels.width/2,pixels.height*3/4]
        Assert.assertTrue(top.blue>.95f && top.red<.05f && top.green<.05f)
        Assert.assertTrue(bottom.red>.95f && bottom.green>.95f && bottom.blue<.05f)
    }
    @Test fun cropCornersResizeAndSelectionMovesWithinImage() {
        val bitmap=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888)
        var transform by mutableStateOf(EditTransform())
        rule.setContent {PhotoTubeTheme {CropEditorCanvas(bitmap.asImageBitmap(),transform,true,{transform=it},false,Modifier.size(200.dp).testTag("crop"))}}
        rule.onNodeWithTag("crop").performTouchInput {swipe(Offset(4f,4f),Offset(width*.25f,height*.25f))}
        rule.runOnIdle {Assert.assertTrue(transform.x>150000 && transform.y>150000);Assert.assertTrue(transform.width<850000 && transform.height<850000)}
        val before=transform
        rule.onNodeWithTag("crop").performTouchInput {swipe(Offset(width*.6f,height*.6f),Offset(width*.45f,height*.6f))}
        rule.runOnIdle {Assert.assertEquals(before.width,transform.width);Assert.assertTrue(transform.x+transform.width<=CROP_UNIT);Assert.assertTrue(transform.x<before.x && transform.x>=0)}
    }
    @Test fun editorKeepsDraftOnConflictAndReloadsBeforeAnotherSave() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=android.graphics.Bitmap.createBitmap(48,32,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val png=java.io.ByteArrayOutputStream().apply {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        var writes=0;var saved=0
        val requests=CopyOnWriteArrayList<RecordedRequest>()
        host.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return when {
                    request.method=="POST" -> {writes++;if(writes==1) json("""{"code":"CONFLICT","message":"版本冲突"}""").setResponseCode(409) else json(version("v4")).setResponseCode(201)}
                    request.requestUrl!!.encodedPath.endsWith("/original") -> MockResponse().setHeader("Content-Type","image/png").setBody(okio.Buffer().write(png))
                    request.requestUrl!!.encodedPath.endsWith("/edit-versions") -> json(JSONObject().put("items",JSONArray().put(version("v3"))).put("nextCursor",JSONObject.NULL))
                    else -> json(JSONObject().put("state","BROWSABLE").put("contentHash",hash).put("activeEdit",if(writes==0) JSONObject.NULL else JSONObject().put("editVersionId","v3").put("sourceState","CURRENT")))
                }
            }
        }
        val source=RemoteMedia(RemoteAsset("photo","PHOTO","BROWSABLE","2026-09-17T00:00:00Z",0,"photo.jpg",hash,48,32,false,null),server)
        rule.setContent {PhotoTubeTheme {PhotoEditorScreen(source,{saved++},{})}}
        rule.waitUntil(10000) {rule.onAllNodesWithTag("crop-canvas").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("保存新版本").assertIsNotEnabled()
        rule.onNodeWithText("旋转 90°").performClick();rule.onNodeWithText("水平镜像").performClick()
        rule.waitForIdle()
        androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
            java.io.File(context.getExternalFilesDir(null),"editor-preview.png"))
        rule.onNodeWithText("保存新版本").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("重新载入当前版本（重置草稿）").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("水平镜像").assertIsSelected();rule.onNodeWithText("保存新版本").assertIsNotEnabled();Assert.assertEquals(0,saved)
        rule.onNodeWithText("重新载入当前版本（重置草稿）").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithTag("crop-canvas").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("水平镜像").assertIsNotSelected()
        rule.onNodeWithText("旋转 90°").performClick();rule.onNodeWithText("保存新版本").performClick()
        rule.waitUntil(10000) {saved==1}
        val bodies=requests.filter {it.method=="POST"}.map {JSONObject(it.body.readUtf8())}
        Assert.assertEquals(2,bodies.size);Assert.assertTrue(bodies[0].isNull("expectedActiveEditVersionId"));Assert.assertEquals("v3",bodies[1].getString("expectedActiveEditVersionId"))
        Assert.assertTrue(bodies[0].getJSONObject("transform").getBoolean("flipHorizontal"));Assert.assertEquals(90,bodies[0].getJSONObject("transform").getInt("rotationDegrees"))
        Assert.assertEquals("v4",source.detail!!.getJSONObject("activeEdit").getString("editVersionId"))
    }
}
