package com.yunai.phototube.data.timeline

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AssetFilterTest {
    @Test
    fun deletingTagRemovesItFromEveryTagConditionWithoutChangingOtherFilters() {
        val filter = AssetFilter(
            favorite = true,
            tagsAll = setOf(1, 2),
            tagsAny = setOf(3, 4),
            tagsExclude = setOf(5, 6),
        )

        val updated = filter.withoutTag(4).withoutTag(5).withoutTag(2)

        assertEquals(setOf(1L), updated.tagsAll)
        assertEquals(setOf(3L), updated.tagsAny)
        assertEquals(setOf(6L), updated.tagsExclude)
        assertEquals(true, updated.favorite)
    }

    @Test
    fun queryMapIsStableAndCsvTagsAreSorted() {
        val filter = AssetFilter(
            kind = AssetKind.PHOTO,
            libraryId = "library-a",
            folderPath = "旅行/上海",
            keyword = "  外滩夜景  ",
            favorite = true,
            rating = 5,
            archived = false,
            private = true,
            takenFrom = OffsetDateTime.parse("2026-08-01T00:00:00+08:00"),
            takenTo = OffsetDateTime.parse("2026-09-01T00:00:00+08:00"),
            tagsAll = setOf(9, 2, 5),
            tagsAny = setOf(7, 3),
            tagsExclude = setOf(8, 1),
        )

        assertEquals(
            mapOf(
                "kind" to "PHOTO",
                "libraryId" to "library-a",
                "keyword" to "外滩夜景",
                "folderPath" to "旅行/上海",
                "favorite" to "true",
                "rating" to "5",
                "archived" to "false",
                "private" to "true",
                "takenFrom" to "2026-08-01T00:00+08:00",
                "takenTo" to "2026-09-01T00:00+08:00",
                "tagsAll" to "2,5,9",
                "tagsAny" to "3,7",
                "tagsExclude" to "1,8",
            ),
            filter.toQueryMap(),
        )
    }

    @Test
    fun folderScopeRequiresLibraryId() {
        assertThrows(IllegalArgumentException::class.java) {
            AssetFilter(folderPath = "旅行")
        }
    }

    @Test
    fun libraryScopeMayBeUsedWithoutFolderButFolderPathMustBeSafe() {
        assertEquals("library-a", AssetFilter(libraryId = "library-a").toQueryMap()["libraryId"])
        listOf("/旅行", "旅行/", "旅行//上海", "旅行/../上海", "旅行\\上海").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                AssetFilter(libraryId = "library-a", folderPath = path)
            }
        }
    }

    @Test
    fun takenRangeCannotRunBackwards() {
        assertThrows(IllegalArgumentException::class.java) {
            AssetFilter(
                takenFrom = OffsetDateTime.parse("2026-09-02T00:00:00+08:00"),
                takenTo = OffsetDateTime.parse("2026-09-01T23:59:59+08:00"),
            )
        }
    }

    @Test
    fun keywordMustMeetServerContract() {
        assertThrows(IllegalArgumentException::class.java) {
            AssetFilter(keyword = "ab")
        }
    }

    @Test
    fun keywordLengthUsesUnicodeCodePointsInsteadOfUtf16Units() {
        val emoji = "🌊"
        val maximum = emoji.repeat(255)

        assertEquals(maximum, AssetFilter(keyword = maximum).toQueryMap()["keyword"])
        assertThrows(IllegalArgumentException::class.java) {
            AssetFilter(keyword = emoji.repeat(2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssetFilter(keyword = emoji.repeat(256))
        }
    }
}
