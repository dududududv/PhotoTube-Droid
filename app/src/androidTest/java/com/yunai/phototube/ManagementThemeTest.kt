package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.ui.theme.ThemeMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Actual settings navigation plus rendered management pages, without backend mutations. */
class ManagementThemeTest : RemoteAppTest() {
    @Volatile private var emptyTrash = false
    private val writes = java.util.concurrent.atomic.AtomicInteger()
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet()
        return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (emptyTrash && request.requestUrl!!.encodedPath == "/api/v1/trash") {
            return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[],"nextCursor":null}""")
        }
        val body = when (request.requestUrl!!.encodedPath) {
            "/api/v1/auth/session" -> """{"authenticated":true,"passwordSet":true,"user":{"username":"fixture"},"privateAccessUnlocked":false,"originalDeleteEnabled":false}"""
            "/api/v1/trash" -> """{"items":[{"id":"trash-one","kind":"PHOTO","state":"TRASHED","fileName":"海边的傍晚.jpg","favorite":false,"contentHash":null,"takenAt":"2026-09-01T00:00:00Z","trashedAt":"2026-09-16T12:00:00Z","retentionDays":30}],"nextCursor":null}"""
            else -> return null
        }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }

    private fun capture(page: String, mode: ThemeMode, checkBackground: Boolean = true) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(450)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val color = bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
        val brightness = (android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)) / 3
        if (checkBackground) assertTrue("$page must render the selected $mode background", if (mode == ThemeMode.DARK) brightness < 100 else brightness > 180)
        File(app.getExternalFilesDir(null), "management-$page-${mode.name.lowercase()}.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun emptyTrashHasNoSelectionOrClearNavigation() {
        emptyTrash = true
        rule.onNodeWithContentDescription("相册标签").performClick()
        rule.onNodeWithText("回收站").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("回收站是空的").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("回收站是空的").assertIsDisplayed()
        for (label in listOf("选择 0 项", "恢复 0 项", "清除记录", "清理全部回收站记录…")) {
            rule.onNodeWithText(label, substring = false).assertDoesNotExist()
        }
        rule.onNodeWithContentDescription("刷新").assertIsDisplayed().assertIsEnabled().performClick()
        rule.waitForIdle()
        auditShot("trash-empty-current")
        org.junit.Assert.assertEquals(0, writes.get())
    }
    @Test fun tagsTrashAndDuplicatesRenderBothThemesThroughSettings() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            rule.onNodeWithContentDescription("相册标签").performClick()
            rule.onNodeWithContentDescription("设置").performClick()
            rule.onNodeWithText("液态玻璃").performClick()
            rule.onNodeWithTag("theme-mode-${mode.name}").performScrollTo().performClick()
            rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("theme-mode-${mode.name}") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("返回设置").performClick()
            rule.onNodeWithContentDescription("返回相册").performClick()

            rule.onNodeWithText("标签库").performClick(); waitTag("tag-list")
            rule.waitUntil(10000) { rule.onAllNodesWithText("风景", substring = false).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("tag-actions-7").assertIsDisplayed()
            capture("tags", mode)
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("tag-list").fetchSemanticsNodes().isEmpty() }

            rule.onNodeWithText("回收站").performClick(); waitTag("trash-trash-one")
            rule.onNodeWithTag("trash-trash-one").performClick()
            rule.onNodeWithTag("trash-trash-one").assertIsOn()
            rule.onNodeWithText("恢复 1 项").assertIsDisplayed().assertIsEnabled()
            capture("trash", mode)
            rule.onNodeWithText("清除记录", substring = false).performClick()
            rule.onNodeWithText("清除 1 项记录？").assertIsDisplayed()
            capture("trash-confirm", mode, checkBackground = false) // Modal scrim darkens the page.
            rule.onNodeWithText("取消", substring = false).performClick()
            rule.onNodeWithTag("trash-trash-one").assertIsOn()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("trash-list").fetchSemanticsNodes().isEmpty() }

            rule.onNodeWithText("完全重复").performClick()
            val group = "duplicate-group-${"a".repeat(64)}"
            waitTag(group)
            rule.onNodeWithTag(group).assertIsDisplayed()
            rule.onNode(isToggleable()).assertIsDisplayed()
            capture("duplicates", mode)
            rule.onNodeWithTag(group).performClick(); waitTag("duplicate-members")
            rule.onNodeWithText("保留全部副本").assertIsDisplayed().assertIsEnabled()
            capture("duplicate-members", mode)
            rule.onAllNodesWithContentDescription("返回").onLast().performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("duplicate-members").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithTag("duplicate-groups").fetchSemanticsNodes().isEmpty() }
            org.junit.Assert.assertEquals("Browsing and cancelling must not mutate fixture data", 0, writes.get())
        }
    }
}
