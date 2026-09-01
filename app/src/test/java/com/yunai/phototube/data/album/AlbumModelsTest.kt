package com.yunai.phototube.data.album

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.ThumbnailSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumModelsTest {
    @Test
    fun coverThumbnailRequiresCanonicalContentVersion() {
        val root = ServerRoot.parse("https://photos.example.test").getOrThrow()
        val cover = AlbumCover(
            id = "asset-1",
            kind = AssetKind.PHOTO,
            fileName = "cover.jpg",
            contentHash = "a".repeat(64),
        )

        assertTrue(cover.thumbnailUrl(root, ThumbnailSize.MD)?.contains("v=${"a".repeat(64)}") == true)
        assertNull(cover.copy(contentHash = "A".repeat(64)).thumbnailUrl(root))
        assertNull(cover.copy(contentHash = null).thumbnailUrl(root))
    }

    @Test
    fun pathChangeShapesRejectMismatchedFields() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPathChangeRequest(
                action = AlbumPathChangeAction.ADD,
                expectedGeneration = 4,
                pathId = "unexpected",
                path = AlbumPathInput("library-1", "Photos/Family"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPathChangeRequest(
                action = AlbumPathChangeAction.REMOVE,
                expectedGeneration = 4,
                pathId = null,
            )
        }
    }

    @Test
    fun onlyTerminalSyncStatesStopPolling() {
        assertFalse(AlbumPathSyncRunState.PENDING.isTerminal)
        assertFalse(AlbumPathSyncRunState.RUNNING.isTerminal)
        assertTrue(AlbumPathSyncRunState.SUCCEEDED.isTerminal)
        assertTrue(AlbumPathSyncRunState.PARTIAL.isTerminal)
        assertTrue(AlbumPathSyncRunState.FAILED.isTerminal)
        assertTrue(AlbumPathSyncRunState.CANCELLED.isTerminal)
    }

    @Test
    fun coverFocalPointAndSettingsRejectInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumCoverFocalPoint(-0.1, 0.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlbumCoverFocalPoint(0.5, 1.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateAlbumRequest.settings("   ", AlbumSortMode.TAKEN_AT_DESC)
        }
        assertTrue(UpdateAlbumRequest.clearCover().updatesCover)
    }

    @Test
    fun syncRunValidatesCountsTimestampsAndTrigger() {
        val run = AlbumPathSyncRun(
            id = "run-1",
            albumId = "album-1",
            configGeneration = 2,
            trigger = AlbumPathSyncTrigger.LIBRARY_SCAN,
            state = AlbumPathSyncRunState.PARTIAL,
            totalPaths = 3,
            succeededPaths = 1,
            failedPaths = 1,
            offlinePaths = 1,
            createdAt = "2026-09-01T00:00:00Z",
            startedAt = "2026-09-01T00:00:01Z",
            finishedAt = "2026-09-01T00:00:02Z",
            error = null,
        )
        assertEquals(AlbumPathSyncTrigger.LIBRARY_SCAN, run.trigger)

        assertThrows(IllegalArgumentException::class.java) {
            run.copy(succeededPaths = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            run.copy(createdAt = "not-a-time")
        }
        assertThrows(IllegalArgumentException::class.java) {
            run.copy(configGeneration = 0)
        }
    }

    @Test
    fun syncDetailRejectsMoreThanOneHundredPathResults() {
        val run = AlbumPathSyncRun(
            id = "run-1",
            albumId = "album-1",
            configGeneration = 1,
            trigger = AlbumPathSyncTrigger.MANUAL,
            state = AlbumPathSyncRunState.SUCCEEDED,
            totalPaths = 0,
            succeededPaths = 0,
            failedPaths = 0,
            offlinePaths = 0,
            createdAt = "2026-09-01T00:00:00Z",
            startedAt = null,
            finishedAt = null,
            error = null,
        )
        val result = AlbumPathSyncPathResult(
            pathId = "path-1",
            libraryId = "library-1",
            relativePath = "Photos",
            state = AlbumPathSyncPathState.SUCCEEDED,
            discoveredCount = 0,
            reusedCount = 0,
            addedCount = 0,
            removedCount = 0,
            skippedCount = 0,
            startedAt = null,
            finishedAt = null,
            error = null,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AlbumPathSyncRunDetail(run, List(101) { result.copy(pathId = "path-$it") })
        }
    }
}
