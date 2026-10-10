package com.yunai.phototube

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import okhttp3.mockwebserver.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PasswordRestoreTest : RemoteAppTest() {
    private val writes = AtomicInteger()
    override fun readFixture(request: RecordedRequest): MockResponse? {
        val body = when (request.requestUrl!!.encodedPath) {
            "/api/v1/auth/session" -> """{"authenticated":true,"passwordSet":true,"user":{"username":"fixture"},"privateAccessUnlocked":false,"originalDeleteEnabled":false}"""
            "/api/v1/health" -> """{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":false}}"""
            "/api/v1/system/status" -> """{"generatedAt":"2026-09-17T00:00:00Z","alerts":[]}"""
            else -> return null
        }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/password", request.requestUrl!!.encodedPath)
        writes.incrementAndGet()
        return MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"UNAVAILABLE","message":"结果未确认"}""").setBodyDelay(3, TimeUnit.SECONDS)
    }
    @Test fun recreationKeepsUncertaintyButDiscardsCredentials() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("服务状态与 AI 配置").performScrollTo().performClick()
        rule.onNodeWithText("修改密码", substring = false).performScrollTo().performClick()
        waitTag("password-current")
        rule.onNodeWithTag("password-current").performScrollTo().performTextInput("fixture-current")
        rule.onNodeWithTag("password-new").performScrollTo().performTextInput("fixture-new-password")
        rule.onNodeWithTag("password-confirm").performScrollTo().performTextInput("fixture-new-password")
        rule.onNodeWithText("修改并退出全部设备").performScrollTo().performClick()
        rule.onNodeWithText("确认修改").performClick()
        rule.waitUntil(10000) { writes.get() == 1 }
        rule.activityRule.scenario.recreate(); waitTag("password-current")
        for (tag in listOf("password-current", "password-new", "password-confirm")) {
            rule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        }
        rule.onNodeWithText("修改并退出全部设备").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("返回登录核对").performScrollTo().assertIsDisplayed()
        assertEquals(1, writes.get())
    }
}
