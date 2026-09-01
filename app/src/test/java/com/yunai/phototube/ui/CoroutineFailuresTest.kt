package com.yunai.phototube.ui

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Test

class CoroutineFailuresTest {
    @Test
    fun `协程取消必须原样抛出而不是映射成页面错误`() {
        val cancellation = CancellationException("route changed")

        val thrown = runCatching { cancellation.rethrowCancellation() }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun `普通失败不会被取消边界改写`() {
        IllegalStateException("request failed").rethrowCancellation()
    }
}
