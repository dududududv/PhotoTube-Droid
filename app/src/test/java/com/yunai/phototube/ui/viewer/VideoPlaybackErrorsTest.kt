package com.yunai.phototube.ui.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackErrorsTest {
    @Test
    fun rangeBoundaryOffersExplicitRestartFromBeginning() {
        val failure = videoPlaybackFailureForHttpCode(416)

        assertEquals("播放位置已超出媒体范围，可以从头重新请求", failure.message)
        assertTrue(failure.retryFromStart)
    }

    @Test
    fun transientAndUnknownFailuresRetryWithoutDiscardingPosition() {
        assertEquals("视频仍在准备中，请稍后重试", videoPlaybackFailureForHttpCode(503).message)
        assertFalse(videoPlaybackFailureForHttpCode(503).retryFromStart)
        assertEquals("视频加载失败，请检查网络后重试", videoPlaybackFailureForHttpCode(null).message)
        assertFalse(videoPlaybackFailureForHttpCode(null).retryFromStart)
    }

    @Test
    fun authenticationAndMissingMediaHaveHonestMessages() {
        assertEquals("视频会话已失效，请重新登录", videoPlaybackFailureForHttpCode(401).message)
        assertEquals("当前账号无权读取这个视频", videoPlaybackFailureForHttpCode(403).message)
        assertEquals("视频原文件当前不可用", videoPlaybackFailureForHttpCode(404).message)
    }

    @Test
    fun unexpectedHttpStatusKeepsTheDiagnosticCode() {
        assertEquals("视频加载失败（HTTP 502）", videoPlaybackFailureForHttpCode(502).message)
    }
}
