package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ProviderDraftRestoreTest : RemoteAppTest() {
    private val writes = CopyOnWriteArrayList<JSONObject>()
    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        "/api/v1/auth/session" -> json("""{"authenticated":true,"passwordSet":true,"user":{"username":"fixture"},"privateAccessUnlocked":false,"originalDeleteEnabled":false}""")
        "/api/v1/health" -> json("""{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":false}}""")
        "/api/v1/system/status" -> json("""{"generatedAt":"2026-09-17T00:00:00Z","alerts":[]}""")
        "/api/v1/system/ai-settings" -> json("""{"baseUrl":"https://fixture.example/v1/chat/completions","model":"original-model","thinkingEnabled":false,"apiKeyConfigured":true,"configured":true,"updatedAt":null}""")
        else -> null
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("PUT", request.method); assertEquals("/api/v1/system/ai-settings", request.requestUrl!!.encodedPath)
        writes += JSONObject(request.body.readUtf8())
        return json("""{"code":"UNAVAILABLE","message":"配置暂时无法保存"}""").setResponseCode(503)
    }
    private fun value(tag: String, text: String) = rule.onNodeWithTag(tag).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(if (tag == "ai-provider-key") "•".repeat(text.length) else text)))
    @Test fun landscapeProviderFormKeepsKeyAndSaveReachable() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("服务状态与 AI 配置").performScrollTo().performClick()
        rule.onNodeWithText("AI 供应商配置").performScrollTo().performClick(); waitTag("ai-provider-url")
        try {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            waitTag("ai-provider-url")
            rule.onNodeWithTag("ai-provider-model").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("替换密钥").performScrollTo().performClick()
            rule.onNodeWithTag("ai-provider-key").performScrollTo().performTextReplacement("fixture-only")
            rule.onNodeWithText("保存配置").performScrollTo().assertIsDisplayed().assertIsEnabled()
            rule.onNodeWithTag("ai-provider-key").performImeAction()
            rule.onNodeWithTag("ai-provider-model").performScrollTo()
            rule.waitForIdle(); android.os.SystemClock.sleep(450)
            val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(app.getExternalFilesDir(null), "ai-provider-landscape.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            assertTrue(writes.isEmpty())
        } finally {
            rule.runOnUiThread { rule.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        }
    }
    private var captureDark = false
    private fun screenshot(name:String) {
        rule.waitForIdle()
        val bitmap=requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {java.io.File(app.getExternalFilesDir(null),name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }} finally {bitmap.recycle()}
    }
    @Test fun darkProviderKeyboardAndSaveFailurePreserveDraft() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=automation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val previous=shell("settings get secure show_ime_with_hard_keyboard")
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-DARK").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("theme-mode-DARK") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()
            captureDark=true
            providerDraftRetainsInMemoryAndClosingDiscardsSecret()
        } finally {
            captureDark=false
            shell(if(previous=="null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $previous")
        }
    }
    @Test fun providerDraftRetainsInMemoryAndClosingDiscardsSecret() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("服务状态与 AI 配置").performScrollTo().performClick()
        rule.onNodeWithText("AI 供应商配置").performScrollTo().performClick(); waitTag("ai-provider-url")
        rule.onNodeWithTag("ai-provider-url").performTextReplacement("https://fixture.example/other")
        rule.onNodeWithTag("ai-provider-model").performTextReplacement("draft-model")
        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.onNodeWithText("替换密钥").performScrollTo().performClick()
        rule.onNodeWithTag("ai-provider-key").performScrollTo().performTextReplacement("fixture-only-secret")
        rule.onNodeWithText("替换密钥").performScrollTo().performClick()
        value("ai-provider-key", "fixture-only-secret")
        rule.activityRule.scenario.recreate(); waitTag("ai-provider-url")
        value("ai-provider-url", "https://fixture.example/other")
        value("ai-provider-model", "draft-model")
        value("ai-provider-key", "fixture-only-secret")
        rule.onNode(isToggleable()).assertIsOn()
        if(captureDark) {
            rule.onNodeWithTag("ai-provider-key").performScrollTo().performClick()
            rule.waitUntil(10000) {rule.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true}
            rule.onNodeWithTag("ai-provider-key").performScrollTo().assertIsDisplayed()
            screenshot("provider-dark-keyboard.png")
        }
        assertTrue(writes.isEmpty())
        rule.onNodeWithText("保存配置").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("配置暂时无法保存").fetchSemanticsNodes().isNotEmpty() }
        if(captureDark) {
            rule.onNodeWithText("配置暂时无法保存").performScrollTo().assertIsDisplayed()
            screenshot("provider-dark-save-failure.png")
        }
        assertEquals(1, writes.size)
        assertEquals("draft-model", writes.single().getString("model"))
        assertEquals("fixture-only-secret", writes.single().getString("apiKey"))
        assertTrue(writes.single().getBoolean("thinkingEnabled"))
        rule.activityRule.scenario.recreate(); waitTag("ai-provider-url")
        rule.onNodeWithText("配置暂时无法保存").assertExists()
        assertEquals(1, writes.size)
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("ai-provider-url").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("AI 供应商配置").performScrollTo().performClick(); waitTag("ai-provider-url")
        value("ai-provider-model", "original-model")
        rule.onNodeWithText("替换密钥").performScrollTo().performClick()
        value("ai-provider-key", "")
        assertEquals(1, writes.size)
    }
}
