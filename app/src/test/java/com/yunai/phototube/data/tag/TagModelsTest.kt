package com.yunai.phototube.data.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TagModelsTest {
    @Test
    fun longIdUnicodeNameAndDeleteCountPreserveContractValues() {
        val name = "🌊".repeat(120)
        val tag = Tag(
            id = 4_294_967_296L,
            name = name,
            assetCount = 8,
            createdAt = "2026-09-01T00:00:00Z",
            updatedAt = "2026-09-01T08:00:00+08:00",
        )

        assertEquals(4_294_967_296L, tag.id)
        assertEquals(120, tag.name.codePointCount(0, tag.name.length))
        assertEquals(8L, TagDeleteResult(8).affectedAssetCount)
    }

    @Test
    fun malformedTagsAndNamesAreRejectedAtModelBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            CreateTagRequest("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateTagRequest("🌊".repeat(121))
        }
        assertThrows(IllegalArgumentException::class.java) {
            tag(id = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            tag(assetCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            tag(createdAt = "not-a-date")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TagDeleteResult(-1)
        }
    }

    private fun tag(
        id: Long = 1,
        assetCount: Long = 0,
        createdAt: String = "2026-09-01T00:00:00Z",
    ) = Tag(
        id = id,
        name = "海边",
        assetCount = assetCount,
        createdAt = createdAt,
        updatedAt = "2026-09-01T00:00:00Z",
    )
}
