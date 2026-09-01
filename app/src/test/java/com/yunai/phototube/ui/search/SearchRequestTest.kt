package com.yunai.phototube.ui.search

import com.yunai.phototube.data.takeUnicodeCodePoints
import com.yunai.phototube.data.timeline.AssetFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRequestTest {
    @Test
    fun `输入截断不会切断 Unicode 代理对`() {
        val input = "🌊".repeat(256)
        val result = input.takeUnicodeCodePoints(255)

        assertEquals(255, result.codePointCount(0, result.length))
        assertEquals("🌊".repeat(255), result)
    }

    @Test
    fun `相同筛选重复提交也会建立新的 Paging generation`() {
        val filter = AssetFilter(keyword = "海边照片")
        val first = SearchRequest(generation = 3, filter = filter)
        val second = SearchRequest(generation = 4, filter = filter)

        assertNotEquals(first, second)
        assertTrue(first.matches(first))
        assertFalse(first.matches(second))
        assertFalse(first.matches(null))
    }
}
