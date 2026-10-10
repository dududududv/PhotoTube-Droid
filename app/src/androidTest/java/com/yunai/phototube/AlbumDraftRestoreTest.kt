package com.yunai.phototube

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Rotation keeps unsaved inputs, but must never create or update an album. */
class AlbumDraftRestoreTest : RemoteAppTest() {
    private val writes = AtomicInteger()
    private var createResponse: (() -> MockResponse)? = null
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        writes.incrementAndGet()
        createResponse?.let { return it() }
        return MockResponse().setResponseCode(405)
    }
    override fun readFixture(request: RecordedRequest): MockResponse? {
        if (request.requestUrl!!.encodedPath != "/api/v1/albums/created-fixture/assets") return null
        return MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"album":{"id":"created-fixture","name":"创建成功示例","kind":"NORMAL","sortMode":"TAKEN_AT_ASC","assetCount":0},"assets":{"items":[],"nextCursor":null}}""")
    }
    @Test fun successfulCreateClosesFormAndOpensCreatedAlbumOnce() {
        createResponse = { MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json")
            .setBody("""{"id":"created-fixture","name":"创建成功示例","kind":"NORMAL","sortMode":"TAKEN_AT_ASC","assetCount":0}""") }
        albums()
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("普通相册").performClick()
        rule.onNodeWithText("名称").performTextInput("创建成功示例")
        rule.onNodeWithText("创建").performScrollTo().performClick()
        waitTag("album-detail")
        rule.waitUntil(10000) { rule.onAllNodesWithText("名称").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("创建成功示例").assertExists()
        assertEquals(1, writes.get())
        rule.onNodeWithContentDescription("返回相册列表").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("album-detail").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, writes.get())
    }
    private fun albums() {
        rule.onNodeWithContentDescription("相册标签").performClick()
        waitTag("album-restore-album")
    }
    private fun detail() {
        albums()
        rule.onNodeWithTag("album-restore-album").performClick()
        waitTag("album-detail")
    }
    @Test fun lostCreateResponseCannotBeResubmittedAfterRecreation() {
        createResponse = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        albums()
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("普通相册").performClick()
        rule.onNodeWithText("名称").performTextInput("响应丢失测试")
        rule.onNodeWithText("创建").performClick()
        val warning = "创建结果未确认，请返回相册列表核对，避免重复创建。"
        rule.waitUntil(15_000) { rule.onAllNodesWithText(warning).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("创建").assertIsNotEnabled()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(warning).assertExists()
        rule.onNodeWithText("创建").assertIsNotEnabled()
        assertEquals(1, writes.get())
        rule.onNodeWithText("返回相册列表").performClick()
        waitTag("album-restore-album")
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("普通相册").performClick()
        rule.onNodeWithText("名称").performTextInput("新草稿")
        rule.onNodeWithText("创建").assertIsEnabled()
        assertEquals(1, writes.get())
    }

    @Test fun rejectedCreateAllowsCorrectingNameWithoutLeavingDialog() {
        createResponse = { MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
            .setBody("""{"code":"INVALID_ARGUMENT","message":"名称不符合要求"}""") }
        albums()
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("普通相册").performClick()
        rule.onNodeWithText("名称").performTextInput("被拒绝的名称")
        rule.onNodeWithText("创建").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("名称不符合要求").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("名称").performTextReplacement("修正的名称")
        rule.onNodeWithText("创建").assertIsEnabled()
        assertEquals(1, writes.get())
    }
    @Test fun createDraftSurvivesRecreationAndCancelDiscardsIt() {
        albums()
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("名称").performTextInput("旅行草稿")
        rule.onNodeWithText("智能相册").performClick()
        rule.onNodeWithText("视频").performClick()
        rule.onNode(isToggleable()).performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("旅行草稿").assertExists()
        rule.onNodeWithText("智能相册").assertIsSelected()
        rule.onNodeWithText("视频").assertIsSelected()
        rule.onNode(isToggleable()).assertIsOn()
        assertEquals(0, writes.get())
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("旅行草稿").assertDoesNotExist()
        rule.onNode(hasText("普通相册") and SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton)).assertIsSelected()
    }
    @Test fun promptDraftSurvivesRecreationAndDoesNotSaveOnReturn() {
        detail()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("整理与 AI").performClick()
        rule.onNodeWithText("AI 提示词").performClick()
        waitTag("photo-ai-prompt")
        rule.onNodeWithTag("photo-ai-prompt").performTextReplacement("照片：识别场景和植物")
        rule.onNodeWithTag("video-ai-prompt").performScrollTo().performTextReplacement("视频：描述场景变化")
        rule.activityRule.scenario.recreate()
        waitTag("photo-ai-prompt")
        rule.onNodeWithTag("photo-ai-prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("照片：识别场景和植物")))
        rule.onNodeWithTag("video-ai-prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("视频：描述场景变化")))
        rule.onAllNodesWithContentDescription("返回").onLast().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("photo-ai-prompt").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("AI 提示词").performClick()
        waitTag("photo-ai-prompt")
        rule.onNodeWithTag("photo-ai-prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithTag("video-ai-prompt").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(0, writes.get())
    }
    @Test fun folderSelectionSurvivesRecreationWithoutStartingScan() {
        albums()
        rule.onNodeWithContentDescription("新建相册").performClick()
        rule.onNodeWithText("名称").performTextInput("文件夹草稿")
        rule.onNodeWithText("同步目录").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("旅行").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("旅行").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("选择此目录").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("选择此目录").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) { rule.onAllNodesWithText("取消选择此目录").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("文件夹草稿").assertExists()
        rule.onNodeWithText("旅行 · 移除").assertExists()
        rule.onNodeWithText("创建", substring = false).assertIsEnabled()
        assertEquals(0, writes.get())
    }
    @Test fun glassMenuKeepsDisabledActionsAndBackDoesNotWrite() {
        detail()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithTag("glass-action-menu").assertIsDisplayed()
        rule.onNodeWithText("清除自定义封面").performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        rule.onNodeWithTag("glass-action-menu").assertExists()
        auditShot("album-options-glass")
        androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitUntil(10000) { rule.onAllNodesWithTag("glass-action-menu").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("album-detail").assertIsDisplayed()
        assertEquals(0, writes.get())
    }
    @Test fun albumDeleteCancellationDoesNotWrite() {
        detail()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("删除相册").performClick()
        rule.onNodeWithTag("glass-confirmation").assertIsDisplayed()
        rule.onNode(isToggleable()).assertIsOff().performClick()
        rule.onNodeWithText("照片及视频的标签、编辑等附属记录也会清理，不进入回收站。其他相册共享的照片与全局标签保留；重新扫描不会恢复已清理的信息。").assertIsDisplayed()
        auditShot("repair-album-delete-options")
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithTag("album-detail").assertIsDisplayed()
        assertEquals(0,writes.get())
    }
    @Test fun renameDraftSurvivesRecreationAndCancelKeepsOriginalName() {
        detail()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("重命名").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("新的相册名称")
        rule.activityRule.scenario.recreate()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("新的相册名称")))
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("重命名").performClick()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("恢复测试相册")))
        assertEquals(0, writes.get())
    }

}
