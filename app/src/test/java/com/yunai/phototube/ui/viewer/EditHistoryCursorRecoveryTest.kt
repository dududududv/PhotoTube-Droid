package com.yunai.phototube.ui.viewer

import com.yunai.phototube.data.edit.EditRenderState
import com.yunai.phototube.data.edit.EditSourceState
import com.yunai.phototube.data.edit.EditTransform
import com.yunai.phototube.data.edit.EditVersion
import com.yunai.phototube.data.edit.EditVersionPage
import com.yunai.phototube.data.remote.ApiErrorBody
import com.yunai.phototube.data.remote.ApiFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EditHistoryCursorRecoveryTest {
    @Test
    fun `寻找当前版本时追加游标失效会从首页重新开始`() = runTest {
        var rootLoads = 0
        val history = loadEditHistoryWithCursorRecovery("active") { cursor ->
            when (cursor) {
                null -> {
                    rootLoads += 1
                    if (rootLoads == 1) EditVersionPage(listOf(version("old")), "expired")
                    else EditVersionPage(listOf(version("active")), null)
                }
                "expired" -> throw ApiFailure(
                    400,
                    ApiErrorBody(
                        code = "INVALID_CURSOR",
                        message = "游标失效",
                        retryable = true,
                        logId = "log-edit",
                    ),
                )
                else -> error("unexpected cursor")
            }
        }

        assertEquals(listOf("active"), history.versions.map(EditVersion::id))
        assertEquals(2, rootLoads)
    }

    private fun version(id: String) = EditVersion(
        id = id,
        assetId = "asset-1",
        parentEditVersionId = null,
        sourceContentHash = "a".repeat(64),
        transform = EditTransform.Source,
        outputWidth = 1200,
        outputHeight = 800,
        active = id == "active",
        sourceState = EditSourceState.CURRENT,
        renderState = EditRenderState.READY,
        createdAt = "2026-09-01T00:00:00Z",
    )
}
