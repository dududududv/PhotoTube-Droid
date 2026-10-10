package com.yunai.phototube

import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.yunai.phototube.data.FilterCatalog
import com.yunai.phototube.ui.remote.TagConditionPicker
import com.yunai.phototube.ui.theme.PhotoTubeTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** One hundred generated labels; only the isolated loopback fixture is queried. */
class TagPickerLargeFontTest : RemoteAppTest() {
    override val fontScaleFixture=2f
    private val automation get()=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
    private var keyboardSetting="null"
    private var accessibilityFlags=0
    private fun shell(command:String)=automation.executeShellCommand(command).use {descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText().trim()
    }
    @org.junit.Before fun enableFixtureKeyboard() {
        keyboardSetting=shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        accessibilityFlags=automation.serviceInfo.flags
        automation.serviceInfo=automation.serviceInfo.apply {flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
    }
    @org.junit.After fun restoreFixtureKeyboard() {
        shell(if(keyboardSetting=="null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $keyboardSetting")
        automation.serviceInfo=automation.serviceInfo.apply {flags=accessibilityFlags}
    }
    override fun readFixture(request:RecordedRequest):MockResponse? {
        if(request.requestUrl!!.encodedPath!="/api/v1/tags") return null
        val tags=JSONArray()
        (1..100).forEach {id -> tags.put(JSONObject().put("id",id).put("name","合成标签%03d".format(id)).put("assetCount",id))}
        return MockResponse().setHeader("Content-Type","application/json")
            .setBody(JSONObject().put("items",tags).put("nextCursor",JSONObject.NULL).toString())
    }
    @Test fun hundredTagsRemainReadableAndCompletableWithLargeTypeAndKeyboard() {
        var result:Set<Long>?=null
        rule.runOnUiThread {UiTestActivity.content={PhotoTubeTheme {
            TagConditionPicker(remember {FilterCatalog(requireNotNull(wall.server))},(1L..99L).toSet(),{}, {result=it})
        }}}
        rule.waitUntil(10000) {rule.onAllNodesWithText("合成标签001 · 移除",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val layouts=mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("合成标签001 · 移除",useUnmergedTree=true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(2f,layouts.single().layoutInput.density.fontScale,.01f)
        assertFalse("Selected chip line height must not be clipped",layouts.single().didOverflowHeight)
        assertEquals(1,layouts.single().lineCount)
        // TextLayoutResult.width is integer pixels; the paragraph retains fractional pixels.
        assertTrue(layouts.single().getLineRight(0)<=layouts.single().size.width+1f)
        rule.onNodeWithTag("tag-condition-list").performScrollTo()
            .performScrollToNode(hasText("合成标签100",substring=false))
        rule.onNodeWithText("合成标签100",substring=false).performClick()
        rule.onNodeWithText("已选 100/100").performScrollTo().assertIsDisplayed()
        auditShot("tag-picker-large-selected")
        rule.onNodeWithText("搜索标签").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextInput("合成")
        rule.waitUntil(10000) {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
                it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }
        }
        rule.onNodeWithText("完成选择").performScrollTo().assertIsDisplayed()
        auditShot("tag-picker-large-keyboard")
        rule.onNodeWithText("完成选择").performClick()
        rule.runOnIdle {assertEquals((1L..100L).toSet(),result)}
    }
}
