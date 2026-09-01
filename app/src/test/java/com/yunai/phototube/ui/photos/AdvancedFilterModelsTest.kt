package com.yunai.phototube.ui.photos

import com.yunai.phototube.data.timeline.AssetKind
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdvancedFilterModelsTest {
    @Test
    fun draftBuildsInclusiveLocalDateRangeAndDisjointTagQueries() {
        val filter = AdvancedFilterDraft(
            kind = AssetKind.PHOTO,
            favorite = false,
            rating = 4,
            takenFrom = LocalDate.parse("2026-03-01"),
            takenTo = LocalDate.parse("2026-03-31"),
            folder = FolderFilterSelection("library-1", "旅行/上海"),
            tagModes = mapOf(
                9L to TagFilterMode.ALL,
                3L to TagFilterMode.ANY,
                7L to TagFilterMode.EXCLUDE,
            ),
        ).toAssetFilter(ZoneId.of("Asia/Shanghai"))

        assertEquals("2026-03-01T00:00+08:00", filter.takenFrom.toString())
        assertEquals("2026-03-31T23:59:59.999999999+08:00", filter.takenTo.toString())
        assertEquals(setOf(9L), filter.tagsAll)
        assertEquals(setOf(3L), filter.tagsAny)
        assertEquals(setOf(7L), filter.tagsExclude)
        assertEquals("false", filter.toQueryMap()["favorite"])
        assertEquals("旅行/上海", filter.toQueryMap()["folderPath"])
    }

    @Test
    fun backwardsDateDraftCannotBecomeServerFilter() {
        val draft = AdvancedFilterDraft(
            takenFrom = LocalDate.parse("2026-04-02"),
            takenTo = LocalDate.parse("2026-04-01"),
        )
        assertThrows(IllegalArgumentException::class.java) { draft.toAssetFilter() }
    }

    @Test
    fun activeDimensionCountCountsTagSetAsOneDimension() {
        val draft = AdvancedFilterDraft(
            kind = AssetKind.VIDEO,
            takenFrom = LocalDate.parse("2026-01-01"),
            tagModes = mapOf(1L to TagFilterMode.ALL, 2L to TagFilterMode.ANY),
        )
        assertEquals(3, draft.activeDimensionCount)
    }
}
