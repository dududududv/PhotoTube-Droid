package com.yunai.phototube.data.job

data class JobPage(
    val items: List<Job>,
    val nextCursor: String?,
)

data class Job(
    val id: String,
    val kind: JobKind,
    val state: JobState,
    val priority: Int,
    val attempts: Int,
    val maxAttempts: Int,
    val runAfter: String,
    val parentJobId: String? = null,
    val error: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val createdAt: String,
) {
    val canCancel: Boolean
        get() = state in setOf(JobState.PENDING, JobState.PAUSED, JobState.RUNNING) && kind.canControl
}

enum class JobKind {
    SCAN_LIBRARY,
    EXTRACT_METADATA,
    GENERATE_THUMBNAIL,
    TRANSCODE_VIDEO,
    AI_INDEX,
    TAG_SCAN,
    DERIVATIVE_PRUNE,
    ALBUM_PATH_SYNC,
    XMP_EXPORT,
    ;

    val canControl: Boolean
        get() = this !in setOf(AI_INDEX, TAG_SCAN)
}

enum class JobState { PENDING, PAUSED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class JobSummaryResponse(
    val items: List<JobSummary>,
)

data class JobSummary(
    val kind: JobKind,
    val pending: Int,
    val paused: Int,
    val running: Int,
    val succeeded: Int,
    val failed: Int,
    val cancelled: Int,
    val queuePaused: Boolean,
    val discovered: Int,
    val total: Int?,
    val lastFinishedAt: String?,
) {
    val finished: Int
        get() = succeeded + failed + cancelled
}

data class JobFilter(
    val state: JobState? = null,
    val kind: JobKind? = null,
)
