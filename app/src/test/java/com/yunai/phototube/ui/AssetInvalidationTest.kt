package com.yunai.phototube.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetInvalidationTest {
    @Test
    fun `无变更不触发任何页面换代`() {
        assertTrue(invalidatedAssetConsumers(emptySet()).isEmpty())
    }

    @Test
    fun `收藏刷新首页时间线相册详情与搜索但不误刷标签库`() {
        assertEquals(
            setOf(
                AssetConsumer.TIMELINE_AND_HOME,
                AssetConsumer.ALBUM_DETAIL,
                AssetConsumer.SEARCH,
            ),
            invalidatedAssetConsumers(setOf(AssetChangeKind.FAVORITE)),
        )
    }

    @Test
    fun `标签关系变化刷新全部标签消费者和标签计数`() {
        assertEquals(
            setOf(
                AssetConsumer.TIMELINE_AND_HOME,
                AssetConsumer.COLLECTIONS,
                AssetConsumer.ALBUM_DETAIL,
                AssetConsumer.LIBRARY,
                AssetConsumer.TRASH,
                AssetConsumer.SEARCH,
                AssetConsumer.TAG_LIBRARY,
            ),
            invalidatedAssetConsumers(setOf(AssetChangeKind.TAGS)),
        )
    }

    @Test
    fun `私密状态变化刷新普通和私密资产消费者`() {
        assertEquals(
            setOf(
                AssetConsumer.TIMELINE_AND_HOME,
                AssetConsumer.COLLECTIONS,
                AssetConsumer.ALBUM_DETAIL,
                AssetConsumer.LIBRARY,
                AssetConsumer.TRASH,
                AssetConsumer.DUPLICATES,
                AssetConsumer.SEARCH,
            ),
            invalidatedAssetConsumers(setOf(AssetChangeKind.PRIVATE)),
        )
    }

    @Test
    fun `回收站三类变化都换代全部资产消费者`() {
        listOf(AssetChangeKind.TRASHED, AssetChangeKind.RESTORED, AssetChangeKind.PURGED).forEach { change ->
            assertEquals(AssetConsumer.entries.toSet(), invalidatedAssetConsumers(setOf(change)))
        }
    }

    @Test
    fun `多个变更合并为消费者并集且不重复计数`() {
        val consumers = invalidatedAssetConsumers(
            setOf(AssetChangeKind.FAVORITE, AssetChangeKind.RATING, AssetChangeKind.EDIT),
        )

        assertEquals(
            setOf(
                AssetConsumer.TIMELINE_AND_HOME,
                AssetConsumer.COLLECTIONS,
                AssetConsumer.ALBUM_DETAIL,
                AssetConsumer.LIBRARY,
                AssetConsumer.TRASH,
                AssetConsumer.DUPLICATES,
                AssetConsumer.SEARCH,
            ),
            consumers,
        )
    }
}
