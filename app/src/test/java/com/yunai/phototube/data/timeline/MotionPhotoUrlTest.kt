package com.yunai.phototube.data.timeline

import com.yunai.phototube.data.connection.ServerRoot
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPhotoUrlTest {
    @Test
    fun motionVideoUsesDedicatedAuthenticatedComponentPath() {
        val asset = MediaAsset(
            id = "asset-1",
            userId = "user-1",
            kind = AssetKind.PHOTO,
            state = AssetState.BROWSABLE,
            takenAt = "2026-09-01T00:00:00Z",
            takenAtOffsetMinutes = null,
            takenAtSource = "EXIF",
            importedAt = "2026-09-01T00:00:00Z",
            libraryId = "library-1",
            relativePath = "DCIM/motion.jpg",
            fileName = "motion.jpg",
            fileSize = 10,
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

        assertEquals(
            "http://phototube.local/api/v1/assets/asset-1/motion-video",
            asset.motionVideoUrl(ServerRoot.parse("http://phototube.local").getOrThrow()),
        )
    }
}
