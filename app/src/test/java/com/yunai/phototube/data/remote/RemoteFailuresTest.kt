package com.yunai.phototube.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFailuresTest {
    @Test
    fun `只有非首页 INVALID_CURSOR 才触发分页重建`() {
        val invalid = failure("INVALID_CURSOR")

        assertTrue(invalid.isInvalidCursorFailure("opaque-cursor"))
        assertFalse(invalid.isInvalidCursorFailure(null))
        assertFalse(failure("RATE_LIMITED").isInvalidCursorFailure("opaque-cursor"))
        assertFalse(IllegalStateException("local").isInvalidCursorFailure("opaque-cursor"))
    }

    private fun failure(code: String) = ApiFailure(
        httpStatus = 400,
        error = ApiErrorBody(code = code, message = "请求失败", retryable = true, logId = "log-1"),
    )
}
