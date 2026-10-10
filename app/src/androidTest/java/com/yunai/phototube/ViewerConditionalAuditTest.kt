package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Synthetic rejection fixtures only; no private authentication or successful mutation. */
class ViewerConditionalAuditTest : RemoteAppTest() {
    override val photographicFixtures = true
    @Volatile private var rejectOriginal = false
    private val writes = CopyOnWriteArrayList<String>()
    override fun readFixture(request: RecordedRequest): MockResponse? =
        if (rejectOriginal && request.requestUrl!!.encodedPath.endsWith("/original"))
            MockResponse().setResponseCode(503).setBody("mock share unavailable") else null
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes += request.requestUrl!!.encodedPath
        return MockResponse().setResponseCode(409).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"MOCK_REJECTED","message":"本地模拟：操作未保存"}""")
    }
    private fun openPhoto() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithTag("album-media-restore-0").performTouchInput {click(androidx.compose.ui.geometry.Offset(12f,12f))}
        waitTag("media-viewer")
    }
    @Test fun inspectPrivateConfirmAndFailureDialogs() {
        openPhoto()
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasText("移入私密空间") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("移入私密空间",substring=false).performClick()
        rule.onNodeWithText("移入私密空间？").assertIsDisplayed()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        auditShot("repair-viewer-private-confirm")
        rule.onNodeWithText("取消",substring=false).performClick()
        assertTrue(writes.isEmpty())
        rule.onNodeWithContentDescription("删除",substring=false).performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        auditShot("repair-viewer-delete-confirm")
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).click(20, 200)
        rule.waitUntil(5000) { rule.onAllNodesWithTag("glass-confirmation").fetchSemanticsNodes().isEmpty() }
        assertTrue(writes.isEmpty())
        rule.onNodeWithContentDescription("更多操作").performClick()
        rule.onNodeWithText("归档",substring=false).performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("操作未完成").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("本地模拟：操作未保存").assertIsDisplayed()
        auditShot("repair-viewer-action-error")
        rule.onNodeWithText("好",substring=false).performClick()
        assertEquals(1,writes.size)
        rejectOriginal = true
        rule.onNodeWithContentDescription("分享",substring=false).performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("暂时无法打开分享，请稍后再试。").fetchSemanticsNodes().isNotEmpty()}
        auditShot("repair-viewer-share-error")
        rule.onNodeWithText("好",substring=false).performClick()
        assertEquals(1,writes.size)
    }
}
