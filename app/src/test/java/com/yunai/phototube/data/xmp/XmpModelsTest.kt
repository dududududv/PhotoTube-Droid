package com.yunai.phototube.data.xmp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpModelsTest {
    @Test
    fun requestRejectsEmptyDuplicateAndOversizedExplicitScopes() {
        assertThrows(IllegalArgumentException::class.java) {
            XmpExportRequest(libraryIds = emptyList(), includePrivate = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            XmpExportRequest(libraryIds = listOf("library-1", "library-1"), includePrivate = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            XmpExportRequest(libraryIds = (1..101).map { "library-$it" }, includePrivate = false)
        }
    }

    @Test
    fun previewCreationRequestFreezesResolvedLibraryIds() {
        val preview = XmpExportPreview(
            resolvedLibraryIds = listOf("library-2", "library-1"),
            includePrivate = true,
            estimatedAssetCount = 42,
            estimatedAt = "2026-09-01T00:00:00Z",
        )

        assertEquals(
            XmpExportRequest(
                libraryIds = listOf("library-2", "library-1"),
                includePrivate = true,
            ),
            preview.creationRequest(),
        )
    }

    @Test
    fun emptyResolvedScopeCannotAccidentallyBecomeAllLibraries() {
        val preview = XmpExportPreview(
            resolvedLibraryIds = emptyList(),
            includePrivate = false,
            estimatedAssetCount = 0,
            estimatedAt = "2026-09-01T00:00:00Z",
        )

        assertThrows(IllegalArgumentException::class.java, preview::creationRequest)
    }

    @Test
    fun runAcceptsOnlyLogicalExportPathsAndSha256Digests() {
        assertEquals("xmp/snapshot-1", run().logicalPath)
        assertThrows(IllegalArgumentException::class.java) { run(logicalPath = "/exports/xmp/snapshot-1") }
        assertThrows(IllegalArgumentException::class.java) { run(factsSha256 = "not-a-hash") }
    }

    @Test
    fun onlyPendingAndRunningStatesAreNonTerminal() {
        assertFalse(XmpExportRunState.PENDING.isTerminal)
        assertFalse(XmpExportRunState.RUNNING.isTerminal)
        assertTrue(XmpExportRunState.SUCCEEDED.isTerminal)
        assertTrue(XmpExportRunState.PARTIAL.isTerminal)
        assertTrue(XmpExportRunState.FAILED.isTerminal)
        assertTrue(XmpExportRunState.CANCELLED.isTerminal)
    }

    private fun run(
        logicalPath: String? = "xmp/snapshot-1",
        factsSha256: String? = "a".repeat(64),
    ) = XmpExportRun(
        id = "run-1",
        state = XmpExportRunState.SUCCEEDED,
        libraryIds = listOf("library-1"),
        includePrivate = false,
        authorizedAt = "2026-09-01T00:00:00Z",
        matchedCount = 2,
        succeededCount = 2,
        failedCount = 0,
        attemptCount = 1,
        snapshotId = "snapshot-1",
        snapshotAt = "2026-09-01T00:00:01Z",
        logicalPath = logicalPath,
        factsSha256 = factsSha256,
        manifestSha256 = "b".repeat(64),
        errorCode = null,
        createdAt = "2026-09-01T00:00:00Z",
        startedAt = "2026-09-01T00:00:00Z",
        finishedAt = "2026-09-01T00:00:01Z",
    )
}
