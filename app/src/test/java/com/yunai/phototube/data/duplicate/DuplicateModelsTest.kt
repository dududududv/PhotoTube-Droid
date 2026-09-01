package com.yunai.phototube.data.duplicate

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DuplicateModelsTest {
    @Test
    fun contentHashMustBeLowercaseSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            group(contentHash = "A".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            group(contentHash = "a".repeat(63))
        }
    }

    @Test
    fun representativeThumbnailUsesHashAsVersion() {
        val hash = "a".repeat(64)
        val url = group(hash).representative.thumbnailUrl(
            ServerRoot.parse("https://photo.example.com").getOrThrow(),
        )

        assertEquals(
            "https://photo.example.com/api/v1/assets/asset-1/thumbnail?size=MD&v=$hash",
            url,
        )
    }

    private fun group(contentHash: String) = DuplicateGroup(
        contentHash = contentHash,
        copyCount = 2,
        reclaimableBytes = 1024,
        latestChangeAt = "2026-09-01T00:00:00Z",
        reviewedAt = null,
        representative = DuplicatePreview(
            assetId = "asset-1",
            kind = AssetKind.PHOTO,
            fileName = "IMG_0001.jpg",
            fileSize = 1024,
            contentHash = contentHash,
        ),
    )
}
