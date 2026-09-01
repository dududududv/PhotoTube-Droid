package com.yunai.phototube.ui.creation

import com.yunai.phototube.data.timeline.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationViewModelTest {
    @Test
    fun `创作入口固定读取普通未归档照片`() {
        val filter = creationAssetFilter(favoriteOnly = false)

        assertEquals(AssetKind.PHOTO, filter.kind)
        assertFalse(filter.archived)
        assertFalse(filter.private)
        assertNull(filter.favorite)
        assertEquals("PHOTO", filter.toQueryMap()["kind"])
        assertEquals("false", filter.toQueryMap()["archived"])
        assertEquals("false", filter.toQueryMap()["private"])
    }

    @Test
    fun `收藏开关只增加目标状态筛选`() {
        val filter = creationAssetFilter(favoriteOnly = true)

        assertTrue(filter.favorite == true)
        assertEquals("true", filter.toQueryMap()["favorite"])
    }

    @Test
    fun `创作网格只在舒适和紧凑两档切换`() {
        assertEquals(CREATION_COMPACT_COLUMNS, nextCreationGridColumns(CREATION_COMFORTABLE_COLUMNS))
        assertEquals(CREATION_COMFORTABLE_COLUMNS, nextCreationGridColumns(CREATION_COMPACT_COLUMNS))
        assertEquals(CREATION_COMFORTABLE_COLUMNS, nextCreationGridColumns(99))
    }
}
