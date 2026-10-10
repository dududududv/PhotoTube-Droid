package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.RuleScanScreen
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic rule runs on the isolated mock host; cancellation must never write. */
class RuleDarkConfirmationTest : RemoteAppTest() {
    @Volatile private var dry=true
    private val writes=java.util.concurrent.atomic.AtomicInteger()
    override fun mutationFixture(request:RecordedRequest):MockResponse {
        writes.incrementAndGet();return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request:RecordedRequest):MockResponse? {
        if(!request.requestUrl!!.encodedPath.startsWith("/api/v1/tag-scans/")) return null
        val run=JSONObject().put("id","dark-run").put("state","SUCCEEDED").put("scanMode","PREVIEW")
            .put("sources",JSONArray().put("RULE")).put("dryRun",dry).put("processedAssets",10).put("totalAssets",10)
            .put("cachedAssets",0).put("providerAttemptCount",0).put("skippedCount",0).put("conflictCount",0).put("batchSize",8)
            .put("createdAt","2026-09-16T00:00:00Z").put("error",JSONObject.NULL).put("matchedAssets",10).put("proposedAdditions",10)
            .put("matchPercentage",100).put("highMatchWarning",true)
            .put("report",JSONArray().put(JSONObject().put("tagName","合成假日").put("matchedAssetCount",10).put("sampleFileNames",JSONArray().put("synthetic-holiday.jpg"))))
        return MockResponse().setHeader("Content-Type","application/json").setBody(run.toString())
    }
    @Test fun darkApplyAndUndoRemainReadableAndCancelWithoutWriting() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("液态玻璃").performClick()
        rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("theme-mode-DARK") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        val server=requireNotNull(wall.server)
        val target=RuleScanTarget.Album("dark-fixture","合成审查相册")
        val prefs=app.getSharedPreferences("rule-scan-bookmarks",0)
        val bookmark=server.base+"/"+ruleScanBookmark(target,emptySet())
        try {
            for(preview in listOf(true,false)) {
                dry=preview
                rule.runOnUiThread {UiTestActivity.content=null};rule.waitForIdle()
                prefs.edit().putString(bookmark,"dark-run").commit()
                rule.runOnUiThread {UiTestActivity.content={PhotoTubeTheme {RuleScanScreen(server,target,{},{})}}}
                val action=if(preview) "应用预览结果" else "撤销本批标签"
                rule.waitUntil(10000) {rule.onAllNodesWithText(action).fetchSemanticsNodes().isNotEmpty()}
                rule.onNodeWithText(action).performScrollTo()
                if(!preview) {
                    rule.onNodeWithText("应用预览结果").assertDoesNotExist()
                    rule.onNodeWithText("本次命中范围较广（100.0%），请核对标签与样本后再应用。").assertDoesNotExist()
                }
                capture(if(preview) "rule-preview-dark" else "rule-applied-dark")
                rule.onNodeWithText(action).performClick()
                rule.onNodeWithText(if(preview) "应用规则预览？" else "撤销本批标签？").assertIsDisplayed()
                rule.onNodeWithText(if(preview) "确认应用" else "确认撤销").assertIsDisplayed().assertIsEnabled()
                capture(if(preview) "rule-apply-confirm-dark" else "rule-undo-confirm-dark")
                rule.onNodeWithText("取消",substring=false).performClick()
                rule.waitUntil(10000) {rule.onAllNodesWithTag("glass-confirmation").fetchSemanticsNodes().isEmpty()}
                assertEquals(0,writes.get())
            }
        } finally {prefs.edit().remove(bookmark).commit()}
    }
    private fun capture(name:String) {
        auditShot(name)
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val pixel=bitmap.getPixel(bitmap.width/2,bitmap.height/8)
            assertTrue("Page must be visibly dark",android.graphics.Color.red(pixel)<100 && android.graphics.Color.green(pixel)<100 && android.graphics.Color.blue(pixel)<100)
        } finally {bitmap.recycle()}
    }
}
