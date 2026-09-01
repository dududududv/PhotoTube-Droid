package com.yunai.phototube.ui.viewer

import com.yunai.phototube.data.timeline.AssetKind
import com.yunai.phototube.data.timeline.AssetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AssetInfoFormattingTest {
    @Test
    fun `拍摄时间保留响应中的原始时区偏移`() {
        assertEquals(
            "2026年8月29日 14:05:06 +08:00",
            formatAssetDateTime("2026-08-29T14:05:06+08:00"),
        )
    }

    @Test
    fun `无法解析的时间原样展示而不崩溃`() {
        assertEquals("待处理", formatAssetDateTime("待处理"))
    }

    @Test
    fun `文件大小使用二进制单位`() {
        assertEquals("0 B", formatAssetFileSize(0))
        assertEquals("1.5 KB", formatAssetFileSize(1536))
        assertEquals("2.0 GB", formatAssetFileSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `负文件大小拒绝进入展示层`() {
        assertThrows(IllegalArgumentException::class.java) { formatAssetFileSize(-1) }
    }

    @Test
    fun `时长显示分钟和小时`() {
        assertEquals("1:05", formatAssetDuration(65.9))
        assertEquals("1:01:01", formatAssetDuration(3661.0))
    }

    @Test
    fun `资产枚举都有中文标签`() {
        assertEquals("照片", assetKindLabel(AssetKind.PHOTO))
        assertEquals("视频", assetKindLabel(AssetKind.VIDEO))
        assertEquals(
            listOf("正在发现", "可浏览", "后台处理中", "处理失败", "原文件离线", "位于回收站"),
            AssetState.entries.map(::assetStateLabel),
        )
    }

    @Test
    fun `时间来源覆盖契约全部稳定枚举`() {
        assertEquals("相机 EXIF", takenAtSourceLabel("EXIF"))
        assertEquals("文件修改时间", takenAtSourceLabel("FILE_MTIME"))
        assertEquals("文件名", takenAtSourceLabel("FILE_NAME"))
        assertEquals("来源未知", takenAtSourceLabel("UNKNOWN"))
        assertEquals("手动设置", takenAtSourceLabel("MANUAL"))
        assertEquals("FUTURE_SOURCE", takenAtSourceLabel("FUTURE_SOURCE"))
    }
}
