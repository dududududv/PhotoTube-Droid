package com.yunai.phototube.ui

enum class AssetChangeKind {
    FAVORITE,
    ARCHIVED,
    RATING,
    PRIVATE,
    TRASHED,
    RESTORED,
    PURGED,
    TAGS,
    EDIT,
}

enum class AssetConsumer {
    TIMELINE_AND_HOME,
    COLLECTIONS,
    ALBUM_DETAIL,
    LIBRARY,
    TRASH,
    DUPLICATES,
    SEARCH,
    TAG_LIBRARY,
}

/** 把资产写操作映射成需要换代的页面数据源，作为唯一跨页面失效规则。 */
fun invalidatedAssetConsumers(changes: Set<AssetChangeKind>): Set<AssetConsumer> = buildSet {
    changes.forEach { change ->
        addAll(
            when (change) {
                AssetChangeKind.FAVORITE -> setOf(
                    AssetConsumer.TIMELINE_AND_HOME,
                    AssetConsumer.ALBUM_DETAIL,
                    AssetConsumer.SEARCH,
                )
                AssetChangeKind.RATING -> setOf(
                    AssetConsumer.TIMELINE_AND_HOME,
                    AssetConsumer.COLLECTIONS,
                    AssetConsumer.ALBUM_DETAIL,
                    AssetConsumer.SEARCH,
                )
                AssetChangeKind.ARCHIVED -> setOf(
                    AssetConsumer.TIMELINE_AND_HOME,
                    AssetConsumer.COLLECTIONS,
                    AssetConsumer.ALBUM_DETAIL,
                    AssetConsumer.LIBRARY,
                    AssetConsumer.SEARCH,
                )
                AssetChangeKind.TAGS -> setOf(
                    AssetConsumer.TIMELINE_AND_HOME,
                    AssetConsumer.COLLECTIONS,
                    AssetConsumer.ALBUM_DETAIL,
                    AssetConsumer.LIBRARY,
                    AssetConsumer.TRASH,
                    AssetConsumer.SEARCH,
                    AssetConsumer.TAG_LIBRARY,
                )
                AssetChangeKind.PRIVATE -> setOf(
                    AssetConsumer.TIMELINE_AND_HOME,
                    AssetConsumer.COLLECTIONS,
                    AssetConsumer.ALBUM_DETAIL,
                    AssetConsumer.LIBRARY,
                    AssetConsumer.TRASH,
                    AssetConsumer.DUPLICATES,
                    AssetConsumer.SEARCH,
                )
                AssetChangeKind.EDIT -> setOf(
                    AssetConsumer.TIMELINE_AND_HOME,
                    AssetConsumer.COLLECTIONS,
                    AssetConsumer.ALBUM_DETAIL,
                    AssetConsumer.LIBRARY,
                    AssetConsumer.TRASH,
                    AssetConsumer.DUPLICATES,
                    AssetConsumer.SEARCH,
                )
                AssetChangeKind.TRASHED,
                AssetChangeKind.RESTORED,
                AssetChangeKind.PURGED,
                -> AssetConsumer.entries.toSet()
            },
        )
    }
}
