package com.yunai.phototube.data.edit

import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.AssetState
import com.yunai.phototube.data.timeline.MediaAsset
import com.yunai.phototube.data.timeline.ThumbnailSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EditModelsTest {
    @Test
    fun transformRejectsInvalidRotationAndOutOfBoundsCrop() {
        assertThrows(IllegalArgumentException::class.java) {
            EditTransform(rotationDegrees = 45)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditTransform(cropXPPM = 900_000, cropWidthPPM = 200_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditTransform(cropWidthPPM = MIN_EDIT_CROP_PPM - 1)
        }
    }

    @Test
    fun clockwiseRotationMovesCropInTheRotatedCanvas() {
        val original = EditTransform(
            cropXPPM = 100_000,
            cropYPPM = 200_000,
            cropWidthPPM = 300_000,
            cropHeightPPM = 400_000,
        )

        assertEquals(
            EditTransform(
                cropXPPM = 400_000,
                cropYPPM = 100_000,
                cropWidthPPM = 400_000,
                cropHeightPPM = 300_000,
                rotationDegrees = 90,
            ),
            original.rotateClockwise(),
        )
    }

    @Test
    fun singleAxisFlipChangesRotationConjugationDirection() {
        val original = EditTransform(
            cropXPPM = 100_000,
            cropYPPM = 200_000,
            cropWidthPPM = 300_000,
            cropHeightPPM = 400_000,
            flipHorizontal = true,
        )

        val rotated = original.rotateClockwise()

        assertEquals(200_000, rotated.cropXPPM)
        assertEquals(600_000, rotated.cropYPPM)
        assertEquals(400_000, rotated.cropWidthPPM)
        assertEquals(300_000, rotated.cropHeightPPM)
    }

    @Test
    fun flipsMirrorCropAndCropRangesKeepMinimumSize() {
        val transform = EditTransform(
            cropXPPM = 100_000,
            cropYPPM = 200_000,
            cropWidthPPM = 300_000,
            cropHeightPPM = 400_000,
        )

        assertEquals(600_000, transform.flipHorizontally().cropXPPM)
        assertEquals(400_000, transform.flipVertically().cropYPPM)
        assertEquals(
            MIN_EDIT_CROP_PPM,
            EditTransform.Source.withHorizontalCrop(999_999, 1_000_000).cropWidthPPM,
        )
    }

    @Test
    fun currentEditOwnsThumbnailAndViewerRenderUrls() {
        val root = ServerRoot.parse("http://phototube.local").getOrThrow()
        val asset = asset().copy(
            activeEdit = ActiveEdit(
                editVersionId = "edit-1",
                sourceState = EditSourceState.CURRENT,
                renderState = EditRenderState.READY,
                displayWidth = 900,
                displayHeight = 600,
            ),
        )

        assertEquals(
            "http://phototube.local/api/v1/assets/asset-1/edit-versions/edit-1/render?size=MD&v=${"a".repeat(64)}",
            asset.thumbnailUrl(root, ThumbnailSize.MD),
        )
        assertEquals(
            "http://phototube.local/api/v1/assets/asset-1/edit-versions/edit-1/render?size=PREVIEW&v=${"a".repeat(64)}",
            asset.displayPhotoUrl(root),
        )
        assertTrue(asset.isEditablePhoto())
    }

    @Test
    fun staleEditFallsBackToOriginalAndUnsupportedFilesHaveNoEditor() {
        val root = ServerRoot.parse("http://phototube.local").getOrThrow()
        val asset = asset().copy(
            fileName = "photo.webp",
            activeEdit = ActiveEdit(
                editVersionId = "edit-old",
                sourceState = EditSourceState.STALE,
                renderState = EditRenderState.READY,
                displayWidth = 900,
                displayHeight = 600,
            ),
        )

        assertEquals(
            "http://phototube.local/api/v1/assets/asset-1/original",
            asset.displayPhotoUrl(root),
        )
        assertFalse(asset.isEditablePhoto())
    }

    @Test
    fun malformedContentVersionCannotEnterThumbnailOrEditRenderUrl() {
        val root = ServerRoot.parse("http://phototube.local").getOrThrow()
        val asset = asset().copy(
            contentHash = "A".repeat(64),
            activeEdit = ActiveEdit(
                editVersionId = "edit-1",
                sourceState = EditSourceState.CURRENT,
                renderState = EditRenderState.READY,
                displayWidth = 900,
                displayHeight = 600,
            ),
        )

        assertNull(asset.thumbnailUrl(root, ThumbnailSize.MD))
        assertEquals("http://phototube.local/api/v1/assets/asset-1/original", asset.displayPhotoUrl(root))
        assertFalse(asset.isEditablePhoto())
        assertThrows(IllegalArgumentException::class.java) {
            asset.editRenderUrl(root, "edit-1", "PREVIEW")
        }
    }

    private fun asset() = MediaAsset(
        id = "asset-1",
        userId = "user-1",
        kind = AssetKind.PHOTO,
        state = AssetState.BROWSABLE,
        takenAt = "2026-09-01T00:00:00Z",
        takenAtOffsetMinutes = null,
        takenAtSource = "EXIF",
        importedAt = "2026-09-01T00:00:00Z",
        libraryId = "library-1",
        relativePath = "DCIM/photo.jpg",
        fileName = "photo.jpg",
        fileSize = 10,
        contentHash = "a".repeat(64),
        width = 1200,
        height = 800,
        favorite = false,
        archived = false,
        private = false,
        rating = null,
    )
}
