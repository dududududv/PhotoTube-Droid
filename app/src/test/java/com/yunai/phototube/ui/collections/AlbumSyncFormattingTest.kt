package com.yunai.phototube.ui.collections

import com.yunai.phototube.data.album.AlbumPathSyncRunState
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumSyncFormattingTest {
    @Test
    fun everyRunStateHasAStableChineseLabel() {
        assertEquals("等待扫描", syncStateLabel(AlbumPathSyncRunState.PENDING))
        assertEquals("正在扫描", syncStateLabel(AlbumPathSyncRunState.RUNNING))
        assertEquals("扫描成功", syncStateLabel(AlbumPathSyncRunState.SUCCEEDED))
        assertEquals("部分完成", syncStateLabel(AlbumPathSyncRunState.PARTIAL))
        assertEquals("扫描失败", syncStateLabel(AlbumPathSyncRunState.FAILED))
        assertEquals("已取消", syncStateLabel(AlbumPathSyncRunState.CANCELLED))
    }
}
