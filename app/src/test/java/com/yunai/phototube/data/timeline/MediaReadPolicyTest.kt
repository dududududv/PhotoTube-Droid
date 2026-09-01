package com.yunai.phototube.data.timeline

import com.yunai.phototube.data.connection.ServerRoot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaReadPolicyTest {
    private val root = ServerRoot.parse("https://photos.example.test").getOrThrow()

    @Test
    fun onlyBrowsableAssetsCanBuildOriginalMediaRequests() {
        assertTrue(asset(AssetState.BROWSABLE).canReadOriginal())

        AssetState.entries.filterNot { it == AssetState.BROWSABLE }.forEach { state ->
            val asset = asset(state)
            assertFalse("$state 不应读取原图", asset.canReadOriginal())
            assertThrows(IllegalArgumentException::class.java) { asset.originalUrl(root) }
            assertThrows(IllegalArgumentException::class.java) { asset.displayPhotoUrl(root) }
        }
    }

    @Test
    fun motionComponentIsUnavailableOutsideBrowsableState() {
        assertTrue(asset(AssetState.BROWSABLE).motionVideoUrl(root)?.endsWith("/motion-video") == true)

        AssetState.entries.filterNot { it == AssetState.BROWSABLE }.forEach { state ->
            assertNull(asset(state).motionVideoUrl(root))
        }
    }

    @Test
    fun processingAssetMayStillBuildVersionedThumbnailForItsCard() {
        val processing = asset(AssetState.PROCESSING)

        assertTrue(processing.thumbnailUrl(root, ThumbnailSize.MD)?.contains("v=${"a".repeat(64)}") == true)
        assertNull(processing.copy(contentHash = null).thumbnailUrl(root, ThumbnailSize.MD))
    }

    private fun asset(state: AssetState) = MediaAsset(
        id = "asset-1",
        userId = "user-1",
        kind = AssetKind.PHOTO,
        state = state,
        takenAt = "2026-09-01T00:00:00Z",
        takenAtOffsetMinutes = null,
        takenAtSource = "FILESYSTEM",
        importedAt = "2026-09-01T00:00:00Z",
        libraryId = "library-1",
        relativePath = "DCIM/motion.jpg",
        fileName = "motion.jpg",
        fileSize = 1024,
        contentHash = "a".repeat(64),
        width = 1920,
        height = 1080,
        favorite = false,
        archived = false,
        private = false,
        rating = null,
        motionPhoto = MotionPhoto(
            format = "OPPO_MOTION_PHOTO_V2",
            videoMime = "video/mp4",
            width = 1920,
            height = 1080,
            durationSec = 2.4,
        ),
    )
}
