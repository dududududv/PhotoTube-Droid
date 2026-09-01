package com.yunai.phototube.data.album

import com.yunai.phototube.data.remote.ApiErrorBody
import com.yunai.phototube.data.remote.ApiFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPathCursorRecoveryTest {
    @Test
    fun `追加游标失效后丢弃旧集合并从第一页重建一次`() = runTest {
        var rootLoads = 0
        val paths = loadAlbumPathsWithCursorRecovery { cursor ->
            when (cursor) {
                null -> {
                    rootLoads += 1
                    if (rootLoads == 1) AlbumPathPage(listOf(path("old")), "expired")
                    else AlbumPathPage(listOf(path("fresh")), null)
                }
                "expired" -> throw invalidCursor()
                else -> error("unexpected cursor")
            }
        }

        assertEquals(listOf("fresh"), paths.map(AlbumPath::id))
        assertEquals(2, rootLoads)
    }

    @Test(expected = ApiFailure::class)
    fun `连续两代游标都失效时停止自动重建`() = runTest {
        loadAlbumPathsWithCursorRecovery { cursor ->
            if (cursor == null) AlbumPathPage(listOf(path("page")), "expired")
            else throw invalidCursor()
        }
    }

    private fun invalidCursor() = ApiFailure(
        400,
        ApiErrorBody(
            code = "INVALID_CURSOR",
            message = "游标失效",
            retryable = true,
            logId = "log-path",
        ),
    )

    private fun path(id: String) = AlbumPath(
        id = id,
        albumId = "album-1",
        libraryId = "library-1",
        relativePath = "Photos/$id",
        enabled = true,
        createdAt = "2026-09-01T00:00:00Z",
        updatedAt = "2026-09-01T00:00:00Z",
        lastSuccessfulSyncAt = null,
        lastRunState = null,
    )
}
