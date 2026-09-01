package com.yunai.phototube.ui.photos

import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.TimelineGranularity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineGroupingTest {
    @Test
    fun dayGroupingPreservesServerOrderAndAddsRelativeLabels() {
        val assets = listOf(
            asset("a", "2026-09-01T08:00:00+08:00"),
            asset("b", "2026-09-01T07:00:00+08:00"),
            asset("c", "2026-08-31T23:00:00+08:00"),
        )

        val groups = groupLoadedTimelineAssets(
            assets = assets,
            granularity = TimelineGranularity.DAY,
            today = LocalDate.of(2026, 9, 1),
        )

        assertEquals(listOf("2026-09-01", "2026-08-31"), groups.map { it.key })
        assertEquals(listOf("今天", "昨天"), groups.map { it.relativeLabel })
        assertEquals(listOf(0, 1), groups.first().rows.single().indices)
        assertEquals(listOf(2), groups.last().rows.single().indices)
    }

    @Test
    fun monthAndYearTitlesUseTakenAtOffsetWithoutPhoneTimezoneRewrite() {
        val assets = listOf(
            asset("a", "2026-01-01T00:15:00+14:00"),
            asset("b", "2025-12-31T23:45:00-10:00"),
        )

        assertEquals(
            listOf("2026年1月", "2025年12月"),
            groupLoadedTimelineAssets(assets, TimelineGranularity.MONTH).map { it.title },
        )
        assertEquals(
            listOf("2026年", "2025年"),
            groupLoadedTimelineAssets(assets, TimelineGranularity.YEAR).map { it.title },
        )
    }

    @Test
    fun sevenAssetsFollowReferenceTwoThreeTwoRhythm() {
        val rows = editorialRows((0..6).toList())

        assertEquals(listOf(2, 3, 2), rows.map { it.indices.size })
        assertEquals(
            listOf(EditorialRowStyle.FEATURED, EditorialRowStyle.TRIPLE, EditorialRowStyle.DOUBLE),
            rows.map { it.style },
        )
    }

    @Test
    fun threeAssetPreviewUsesOneEqualWidthRow() {
        val rows = editorialRows(listOf(0, 1, 2))

        assertEquals(1, rows.size)
        assertEquals(EditorialRowStyle.TRIPLE, rows.single().style)
    }

    private fun asset(id: String, takenAt: String) = MediaAsset(
        id = id,
        userId = "user-1",
        kind = AssetKind.PHOTO,
        state = AssetState.BROWSABLE,
        takenAt = takenAt,
        takenAtOffsetMinutes = null,
        takenAtSource = "EXIF",
        importedAt = "2026-09-01T00:00:00Z",
        libraryId = "library-1",
        relativePath = "DCIM",
        fileName = "$id.jpg",
        fileSize = 1024,
        contentHash = "a".repeat(64),
        width = 1920,
        height = 1080,
        favorite = false,
        archived = false,
        private = false,
        rating = null,
    )
}
