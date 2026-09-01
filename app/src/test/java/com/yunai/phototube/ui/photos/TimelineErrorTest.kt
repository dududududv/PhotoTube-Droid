package com.yunai.phototube.ui.photos

import com.yunai.phototube.data.remote.ApiErrorBody
import com.yunai.phototube.data.remote.ApiFailure
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineErrorTest {
    @Test
    fun `结构化 API 错误保留消息日志 ID 和可重试事实`() {
        val error = ApiFailure(
            httpStatus = 503,
            error = ApiErrorBody(
                code = "LIBRARY_OFFLINE",
                message = "媒体库暂时离线",
                retryable = true,
                logId = "log_01HZX",
            ),
        ).toTimelineError()

        assertEquals("媒体库暂时离线", error.message)
        assertEquals("log_01HZX", error.logId)
        assertEquals("LIBRARY_OFFLINE", error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun `空日志 ID 不生成无意义复制入口`() {
        val error = ApiFailure(
            httpStatus = 500,
            error = ApiErrorBody(
                code = "INTERNAL_ERROR",
                message = "请求失败",
                retryable = true,
                logId = "   ",
            ),
        ).toTimelineError()

        assertNull(error.logId)
    }

    @Test
    fun `本地网络错误没有伪造服务端日志 ID`() {
        val error = IOException("socket closed").toTimelineError()

        assertNull(error.logId)
        assertTrue(error.retryable)
        assertNull(error.code)
    }
}
