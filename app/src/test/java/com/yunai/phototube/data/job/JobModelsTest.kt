package com.yunai.phototube.data.job

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobModelsTest {
    @Test
    fun aiKindsRemainReadOnlyUntilStableContractShips() {
        assertFalse(JobKind.AI_INDEX.canControl)
        assertFalse(JobKind.TAG_SCAN.canControl)
        assertTrue(JobKind.ALBUM_PATH_SYNC.canControl)
    }

    @Test
    fun onlyActiveControllableJobsCanBeCancelled() {
        assertTrue(job(JobKind.XMP_EXPORT, JobState.RUNNING).canCancel)
        assertFalse(job(JobKind.XMP_EXPORT, JobState.SUCCEEDED).canCancel)
        assertFalse(job(JobKind.TAG_SCAN, JobState.RUNNING).canCancel)
    }

    private fun job(kind: JobKind, state: JobState) = Job(
        id = "job-1",
        kind = kind,
        state = state,
        priority = 3,
        attempts = 0,
        maxAttempts = 3,
        runAfter = "2026-09-01T00:00:00Z",
        createdAt = "2026-09-01T00:00:00Z",
    )
}
