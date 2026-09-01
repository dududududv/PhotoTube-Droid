package com.yunai.phototube.data.folder

data class FolderListing(
    val current: FolderNode,
    val children: FolderPage,
)

data class FolderPage(
    val items: List<FolderNode>,
    val nextCursor: String?,
)

data class FolderNode(
    val kind: FolderKind,
    val libraryId: String? = null,
    val name: String,
    val path: String,
    val online: Boolean,
    val directAssetCount: Long,
    val hasChildren: Boolean,
) {
    init {
        require(directAssetCount >= 0) { "目录资产数不能为负数" }
        when (kind) {
            FolderKind.ROOT -> require(libraryId == null && path.isEmpty()) {
                "虚拟根不能包含媒体库 ID 或相对路径"
            }
            FolderKind.LIBRARY -> require(!libraryId.isNullOrBlank() && path.isEmpty()) {
                "媒体库节点必须包含 ID 且路径为空"
            }
            FolderKind.DIRECTORY -> require(!libraryId.isNullOrBlank() && path.isSafeRelativeFolderPath()) {
                "目录节点必须包含媒体库 ID 和安全相对路径"
            }
        }
    }
}

enum class FolderKind { ROOT, LIBRARY, DIRECTORY }

fun String.isSafeRelativeFolderPath(): Boolean =
    isNotBlank() &&
        length <= 4096 &&
        !startsWith('/') &&
        !endsWith('/') &&
        !contains('\\') &&
        split('/').all { it.isNotEmpty() && it != "." && it != ".." }
