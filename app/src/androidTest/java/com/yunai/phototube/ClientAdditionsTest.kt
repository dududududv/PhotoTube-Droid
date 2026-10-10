package com.yunai.phototube

import androidx.compose.ui.test.*
import com.yunai.phototube.data.PlaybackPreferences
import com.yunai.phototube.ui.remote.JobAssetDetails
import com.yunai.phototube.ui.remote.jobAssetIdentity
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class ClientAdditionsTest : RemoteAppTest() {
    private val assetId="318ddf49-1b6d-4b61-8752-63a118fc6c3b"
    private var privateAsset=false
    @Volatile private var cleanupQuery:String?=null
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        if(request.method=="DELETE" && request.requestUrl!!.encodedPath=="/api/v1/albums/restore-album") {
            cleanupQuery=request.requestUrl!!.queryParameter("cleanupExclusiveAssets")
            return MockResponse().setResponseCode(204)
        }
        return MockResponse().setResponseCode(405)
    }
    @Test fun albumCleanupRequiresExplicitSelection() {
        rule.onNodeWithContentDescription("相册标签").performClick();waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick();waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("删除相册").performScrollTo().performClick()
        rule.onNode(isToggleable()).assertIsOff().performClick()
        rule.onNodeWithText("删除相册",substring=false).performClick()
        rule.waitUntil(10000) {cleanupQuery!=null}
        assertEquals("true",cleanupQuery)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? = if(request.requestUrl!!.encodedPath=="/api/v1/assets/$assetId")
        MockResponse().setHeader("Content-Type","application/json").setBody("""{"fileName":"旅行.webp","relativePath":"旅行/旅行.webp","private":$privateAsset,"albumIds":["restore-album"],"state":"BROWSABLE"}""") else null
    @Test fun failureFileShowsAuthorizedLocationAndAlbum() {
        rule.runOnUiThread { UiTestActivity.content={ PhotoTubeTheme {JobAssetDetails(wall.server!!,assetId,{})} } }
        rule.waitUntil(10000) {rule.onAllNodesWithText("相册：恢复测试相册").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("旅行/旅行.webp").assertExists()
        rule.onNodeWithText("复制路径").performClick()
        rule.onNodeWithText("已复制路径").assertExists()
        rule.waitForIdle()
        Thread.sleep(450)
        val target=java.io.File(app.getExternalFilesDir(null),"job-file-details.png")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {bitmap ->
            target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    @Test fun ordinaryDetailsNeverExposePrivatePath() {
        privateAsset=true
        rule.runOnUiThread { UiTestActivity.content={ PhotoTubeTheme {JobAssetDetails(wall.server!!,assetId,{})} } }
        rule.waitUntil(10000) {rule.onAllNodesWithText("解锁私密空间").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("旅行/旅行.webp").assertDoesNotExist()
        rule.onNodeWithText("旅行.webp").assertDoesNotExist()
    }
    @Test fun playbackPreferencesSurviveNewInstancesAndIdentityParsingIsStrict() {
        val p=PlaybackPreferences(app);val auto=p.autoPlay;val muted=p.muted;val volume=p.volume
        try {
            p.autoPlay=true;p.muted=false;p.volume=.35f
            val second=PlaybackPreferences(app)
            assertTrue(second.autoPlay);assertFalse(second.muted);assertEquals(.35f,second.volume,0f)
            p.volume=2f;assertEquals(1f,second.volume,0f)
        } finally {p.autoPlay=auto;p.muted=muted;p.volume=volume}
        assertEquals(assetId,jobAssetIdentity("解析失败（assetId=$assetId）").assetId)
        assertEquals("解析失败",jobAssetIdentity("解析失败（assetId=$assetId）").reason)
        assertNull(jobAssetIdentity("assetId=../../private").assetId)
    }
}
