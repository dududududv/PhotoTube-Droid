package com.yunai.phototube

import androidx.compose.ui.test.*
import okhttp3.mockwebserver.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class AlbumPathsRestoreTest : RemoteAppTest() {
    override val albumKindFixture = "PATH_SYNC"
    private val generation = AtomicInteger(1)
    private val folderReads = CopyOnWriteArrayList<String>()
    private val writes = CopyOnWriteArrayList<Pair<String, String>>()
    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
    override fun readFixture(request: RecordedRequest): MockResponse? = when (request.requestUrl!!.encodedPath) {
        "/api/v1/albums/restore-album/assets" -> json(JSONObject().put("album", JSONObject()
            .put("id", "restore-album").put("name", "恢复测试相册").put("kind", "PATH_SYNC").put("sortMode", "TAKEN_AT_ASC")
            .put("assetCount", 0).put("pathSync", JSONObject().put("configGeneration", generation.get())))
            .put("assets", JSONObject().put("items", JSONArray()).put("nextCursor", JSONObject.NULL)))
        "/api/v1/albums/restore-album/paths" -> json(JSONObject().put("items", JSONArray().put(JSONObject()
            .put("id", "path-one").put("libraryId", "test-library").put("relativePath", "原目录").put("enabled", true)))
            .put("nextCursor", JSONObject.NULL))
        "/api/v1/source-folders" -> {
            val path = request.requestUrl!!.queryParameter("path").orEmpty()
            folderReads += path
            json(JSONObject().put("libraryId", "test-library").put("items", if (path.isEmpty())
                JSONArray().put(JSONObject().put("name", "旅行").put("path", "旅行")) else JSONArray()))
        }
        else -> null
    }
    override fun mutationFixture(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath
        writes += path to request.body.readUtf8()
        return when {
            path.endsWith("/path-change-previews") -> json(JSONObject().put("membersToRemove", 2)
                .put("membersRetainedByOtherPaths", 1).put("requiresSync", true))
            path.endsWith("/path-changes") -> json("""{"code":"CONFIG_CONFLICT","message":"目录配置已变化，请重新预览"}""").setResponseCode(409)
            else -> MockResponse().setResponseCode(405)
        }
    }
    @Test fun auditPathImpactWithoutApplying() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("管理文件夹").performScrollTo().performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasText("更换") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("更换").performClick()
        rule.waitUntil(10000) {rule.onAllNodesWithText("旅行",substring=false).fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithText("旅行",substring=false).performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasText("选择此目录") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        rule.mainClock.autoAdvance=false
        try {
            rule.onNodeWithText("选择此目录").performClick()
            rule.mainClock.advanceTimeBy(96)
            assertTrue("Preview must wait for picker exit",writes.isEmpty())
            rule.onNodeWithText("选择此目录").assertIsNotEnabled()
            rule.mainClock.advanceTimeBy(400)
        } finally {rule.mainClock.autoAdvance=true}
        rule.waitUntil(10000) {rule.onAllNodesWithText("确认目录变更").fetchSemanticsNodes().isNotEmpty()}
        auditShot("path-impact")
        rule.onNodeWithText("取消",substring=false).performClick()
        assertEquals(1,writes.size)
        assertTrue(writes.single().first.endsWith("/path-change-previews"))
    }
    @Test fun repeatedPickerOpeningKeepsLoadingAndCancellationIndependent() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("管理文件夹").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasText("更换") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        repeat(8) {
            rule.onNodeWithText("更换").performClick()
            try {
                rule.waitUntil(10000) { rule.onAllNodesWithText("旅行", substring = false).fetchSemanticsNodes().isNotEmpty() }
            } catch (failure: Throwable) {
                println("Cycle $it; folder request count: ${folderReads.size}; paths: ${folderReads.map { "[$it]" }}")
                println("Running: ${wall.server?.client?.dispatcher?.runningCalls()?.map { it.request().url.encodedPath }}")
                println("Queued: ${wall.server?.client?.dispatcher?.queuedCalls()?.map { it.request().url.encodedPath }}")
                println(rule.onAllNodes(isRoot(), useUnmergedTree = true).onLast().printToString())
                throw failure
            }
            rule.onNodeWithText("取消", substring = false).performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText("选择文件夹").fetchSemanticsNodes().isEmpty() }
        }
        assertEquals(8, folderReads.size)
        assertTrue(folderReads.all { it.isEmpty() })
        assertTrue(writes.isEmpty())
    }

    @Test fun replacementAndPreviewKeepExactGenerationAcrossRecreation() {
        rule.onNodeWithContentDescription("相册标签").performClick(); waitTag("album-restore-album")
        rule.onNodeWithTag("album-restore-album").performClick(); waitTag("album-detail")
        rule.onNodeWithContentDescription("相册选项").performClick()
        rule.onNodeWithText("管理文件夹").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasText("更换") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("更换").performClick()
        try {
            rule.waitUntil(10000) { rule.onAllNodesWithText("旅行", substring = false).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            println("Folder request count: ${folderReads.size}; paths: ${folderReads.map { "[$it]" }}")
            println("Running: ${wall.server?.client?.dispatcher?.runningCalls()?.map { it.request().url.encodedPath }}")
            println("Queued: ${wall.server?.client?.dispatcher?.queuedCalls()?.map { it.request().url.encodedPath }}")
            println(rule.onAllNodes(isRoot(), useUnmergedTree = true).onLast().printToString())
            throw failure
        }
        rule.onNodeWithText("旅行", substring = false).performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("选择文件夹").assertExists()
        rule.onNodeWithText("旅行", substring = false).assertExists()
        rule.onNodeWithText("选择此目录").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("确认目录变更").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, writes.size)
        val preview = writes.single().second
        val body = JSONObject(preview)
        assertEquals("UPDATE", body.getString("action")); assertEquals("path-one", body.getString("pathId"))
        assertEquals(1, body.getInt("expectedGeneration"))
        assertEquals("旅行", body.getJSONObject("path").getString("relativePath"))
        generation.set(2)
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("确认目录变更").assertExists()
        assertEquals(1, writes.size)
        rule.waitUntil(10000) { rule.onAllNodes(hasText("确认变更") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("确认变更").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("目录配置已变化，请重新预览").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, writes.size)
        assertTrue(writes.last().first.endsWith("/path-changes"))
        assertEquals("Confirmation must apply the exact previewed request", preview, writes.last().second)
        rule.onNodeWithText("确认目录变更").assertDoesNotExist()
    }
}
