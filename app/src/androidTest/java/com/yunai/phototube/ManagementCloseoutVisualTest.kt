package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.*
import org.junit.Test
import java.io.File

/** Real navigation and rendered pages; fixture data only, no mutations. */
class ManagementCloseoutVisualTest : RemoteAppTest() {
    @Test fun inspectLocalThumbnailTransport() = kotlinx.coroutines.runBlocking {
        val server = requireNotNull(wall.server)
        val result = server.imageLoader.execute(coil3.request.ImageRequest.Builder(app)
            .data(server.thumbnail("restore-0", "a".repeat(64))).size(48, 32).build())
        if (result is coil3.request.ErrorResult) throw result.throwable
    }
    override val albumKindFixture = "PATH_SYNC"
    override fun readFixture(request: RecordedRequest): MockResponse? {
        val path = request.requestUrl!!.encodedPath
        val body = when {
            path == "/api/v1/auth/session" -> """{"authenticated":true,"passwordSet":true,"user":{"username":"fixture"},"privateAccessUnlocked":false,"originalDeleteEnabled":false}"""
            path == "/api/v1/health" -> """{"status":"ok","database":{"reachable":true,"migrationVersion":1},"xmpExport":{"available":false}}"""
            path == "/api/v1/system/status" -> """{"generatedAt":"2026-10-08T00:00:00Z","alerts":[]}"""
            path.endsWith("/syncs") -> """{"items":[{"id":"visual-scan","state":"PARTIAL","createdAt":"2026-10-08T00:00:00Z","succeededPaths":3,"failedPaths":1,"offlinePaths":1,"error":"旅行目录暂时离线，已保留原有相册成员。请确认存储连接后再次扫描。"}],"nextCursor":null}"""
            else -> return null
        }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse = error("Visual inspection must not submit mutations")
    private fun capture(page: String, mode: ThemeMode) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(app.getExternalFilesDir(null), "closeout-$page-${mode.name.lowercase()}.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun back() { rule.onAllNodesWithContentDescription("返回", substring = false).onLast().performClick() }
    @Test fun recentManagementPagesRenderInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithText("服务状态与 AI 配置").performScrollTo().performClick()
            rule.onNodeWithText("修改密码", substring = false).performScrollTo().performClick(); waitTag("password-current")
            rule.onNodeWithText("修改并退出全部设备").performScrollTo().assertIsDisplayed()
            capture("password", mode)
            rule.onNodeWithTag("password-current").performScrollTo().performTextReplacement("mock-current-only")
            rule.onNodeWithTag("password-new").performScrollTo().performTextReplacement("mock-new-password-only")
            rule.onNodeWithTag("password-confirm").performScrollTo().performTextReplacement("mock-new-password-only")
            rule.onNodeWithText("修改并退出全部设备").performScrollTo().performClick()
            rule.onNodeWithText("修改密码并退出登录？").assertIsDisplayed()
            capture("password-confirm", mode)
            rule.onNodeWithText("取消", substring = false).performClick()
            back(); rule.waitUntil(10000) { rule.onAllNodesWithTag("password-current").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("退出登录", substring = false).performScrollTo().performClick()
            rule.onNodeWithText("退出当前账号？").assertIsDisplayed()
            capture("logout-confirm", mode)
            rule.onNodeWithText("取消", substring = false).performClick()
            back()
            rule.onNodeWithContentDescription("返回相册").performClick(); waitTag("album-restore-album")
            rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
            rule.onNodeWithContentDescription("相册选项").performClick()
            rule.onNodeWithText("整理与 AI").performClick()
            rule.onNodeWithText("最近一次扫描").performScrollTo().performClick(); waitTag("scan-history-visual-scan")
            rule.onNodeWithTag("scan-history-attention", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithText("需要关注 · 失败 1 · 离线 1", useUnmergedTree = true).assertIsDisplayed()
            capture("scan-history", mode)
            back(); rule.waitUntil(10000) { rule.onAllNodesWithTag("scan-history-list").fetchSemanticsNodes().isEmpty() }
            back()
            rule.onNodeWithTag("album-media-restore-0").performTouchInput { click(androidx.compose.ui.geometry.Offset(12f,12f)) }
            capture("after-media-click", mode)
            waitTag("media-viewer")
            rule.onNodeWithContentDescription("编辑照片").assertDoesNotExist()
            capture("viewer", mode)
            rule.onNodeWithContentDescription("关闭照片").performClick()
            rule.onNodeWithContentDescription("返回相册列表").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty() }
        }
    }
}
