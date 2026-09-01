package com.yunai.phototube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAlbumRepositoryTest {
    @Test
    fun timeline_hasStableReferenceDataset() {
        assertEquals(listOf("8月29日", "8月28日"), MockAlbumRepository.days.map { it.date })
        assertEquals(7, MockAlbumRepository.days.first().photos.size)
        assertTrue(MockAlbumRepository.days.flatMap { it.photos }.all { it.drawableRes != 0 })
    }

    @Test
    fun collections_haveCoversAndMembers() {
        assertEquals(2, MockAlbumRepository.collections.size)
        assertTrue(MockAlbumRepository.collections.all { it.coverRes != 0 })
        assertTrue(MockAlbumRepository.collections.all { it.memberLabels.isNotEmpty() })
    }
}
