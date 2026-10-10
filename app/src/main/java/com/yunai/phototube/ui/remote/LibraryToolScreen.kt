package com.yunai.phototube.ui.remote

import androidx.compose.runtime.Composable
import com.yunai.phototube.data.PhotoTubeServer
import com.yunai.phototube.data.WallFilters

/** Shared routes used by the album shortcuts and Settings. */
internal enum class LibraryTool(val title:String) {
    FAVORITES("收藏"), DIRECTORIES("文件夹"), TAGS("标签库"), MEMORIES("回忆与精选"),
    DUPLICATES("完全重复"), TASKS("任务中心"), TRASH("回收站"), PRIVATE("私密空间")
}

@Composable
internal fun LibraryToolScreen(tool:LibraryTool,server:PhotoTubeServer,onChanged:()->Unit,onClose:()->Unit) {
    when(tool) {
        LibraryTool.FAVORITES -> ScopedMediaGallery(server,WallFilters(favorite=true),"收藏",
            "不含归档与私密内容","还没有收藏的照片或视频。","favorite-photos",onChanged,onClose)
        LibraryTool.DIRECTORIES -> DirectoryBrowser(server,onChanged=onChanged,onClose=onClose)
        LibraryTool.TAGS -> TagsScreen(server,onChanged=onChanged,onClose=onClose)
        LibraryTool.MEMORIES -> MemoriesScreen(server,onLibraryChanged=onChanged,onClose=onClose)
        LibraryTool.DUPLICATES -> DuplicatesScreen(server,onLibraryChanged=onChanged,onClose=onClose)
        LibraryTool.TASKS -> TasksScreen(server,onClose=onClose)
        LibraryTool.TRASH -> TrashScreen(server,onChanged=onChanged,onClose=onClose)
        LibraryTool.PRIVATE -> PrivateSpace(server,onChanged=onChanged,onClose=onClose)
    }
}
