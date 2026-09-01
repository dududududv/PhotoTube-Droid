package com.yunai.phototube.data.folder

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderModelsTest {
    @Test
    fun relativeFolderPathRejectsHostAndTraversalShapes() {
        assertTrue("旅行/上海".isSafeRelativeFolderPath())
        listOf("", "/mnt/photos", "旅行/", "旅行//上海", "旅行/../上海", "旅行\\上海").forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                FolderNode(
                    kind = FolderKind.DIRECTORY,
                    libraryId = "library-1",
                    name = "非法目录",
                    path = path,
                    online = true,
                    directAssetCount = 0,
                    hasChildren = false,
                )
            }
        }
    }

    @Test
    fun virtualRootAndLibraryHaveDistinctIdentityRules() {
        FolderNode(FolderKind.ROOT, null, "全部媒体库", "", true, 0, true)
        FolderNode(FolderKind.LIBRARY, "library-1", "照片库", "", true, 12, true)
        assertThrows(IllegalArgumentException::class.java) {
            FolderNode(FolderKind.LIBRARY, null, "照片库", "", true, 0, false)
        }
    }
}
