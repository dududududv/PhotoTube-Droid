package com.yunai.phototube.data.xmp

data class XmpExportRequest(
    val libraryIds: List<String>? = null,
    val includePrivate: Boolean,
) {
    init {
        libraryIds?.let { ids ->
            require(ids.isNotEmpty()) { "指定媒体库范围不能为空" }
            require(ids.size <= 100) { "一次最多指定 100 个媒体库" }
            require(ids.size == ids.distinct().size) { "媒体库范围不能包含重复 ID" }
            require(ids.all(String::isNotBlank)) { "媒体库 ID 不能为空" }
        }
    }
}

data class XmpExportPreview(
    val resolvedLibraryIds: List<String>,
    val includePrivate: Boolean,
    val estimatedAssetCount: Long,
    val estimatedAt: String,
) {
    init {
        require(resolvedLibraryIds.size == resolvedLibraryIds.distinct().size) {
            "预估范围不能包含重复媒体库"
        }
        require(resolvedLibraryIds.all(String::isNotBlank)) { "预估媒体库 ID 不能为空" }
        require(estimatedAssetCount >= 0) { "预估资产数不能为负数" }
    }

    fun creationRequest(): XmpExportRequest {
        require(resolvedLibraryIds.isNotEmpty()) { "没有可用于创建快照的媒体库" }
        return XmpExportRequest(
            libraryIds = resolvedLibraryIds,
            includePrivate = includePrivate,
        )
    }
}

enum class XmpExportRunState {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this !in setOf(PENDING, RUNNING)
}

data class XmpExportRun(
    val id: String,
    val state: XmpExportRunState,
    val libraryIds: List<String>,
    val includePrivate: Boolean,
    val authorizedAt: String,
    val matchedCount: Long,
    val succeededCount: Long,
    val failedCount: Long,
    val attemptCount: Int,
    val snapshotId: String?,
    val snapshotAt: String?,
    val logicalPath: String?,
    val factsSha256: String?,
    val manifestSha256: String?,
    val errorCode: String?,
    val createdAt: String,
    val startedAt: String?,
    val finishedAt: String?,
) {
    init {
        require(id.isNotBlank()) { "XMP 导出运行 ID 不能为空" }
        require(libraryIds.isNotEmpty()) { "XMP 导出必须固化至少一个媒体库" }
        require(libraryIds.size == libraryIds.distinct().size) { "XMP 导出媒体库不能重复" }
        require(matchedCount >= 0 && succeededCount >= 0 && failedCount >= 0) {
            "XMP 导出计数不能为负数"
        }
        require(attemptCount >= 0) { "XMP 导出尝试次数不能为负数" }
        logicalPath?.let { path ->
            require(path.startsWith("xmp/") && !path.contains("..") && !path.startsWith('/')) {
                "服务端返回了非法 XMP 逻辑路径"
            }
        }
        factsSha256?.let(::requireSha256)
        manifestSha256?.let(::requireSha256)
    }

    val completedCount: Long get() = succeededCount + failedCount

    private fun requireSha256(value: String) {
        require(value.matches(SHA256)) { "XMP manifest 哈希格式不正确" }
    }

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

data class XmpExportRunPage(
    val items: List<XmpExportRun>,
    val nextCursor: String?,
)
