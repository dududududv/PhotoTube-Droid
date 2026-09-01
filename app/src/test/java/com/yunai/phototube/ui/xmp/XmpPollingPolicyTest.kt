package com.yunai.phototube.ui.xmp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpPollingPolicyTest {
    @Test
    fun `存在运行中任务且没有失败时允许自动轮询`() {
        assertTrue(shouldAutomaticallyPollXmp(hasActiveWork = true, hasFailure = false))
    }

    @Test
    fun `任何一次轮询失败都会停止自动请求`() {
        assertFalse(shouldAutomaticallyPollXmp(hasActiveWork = true, hasFailure = true))
    }

    @Test
    fun `没有运行中任务时不会启动自动轮询`() {
        assertFalse(shouldAutomaticallyPollXmp(hasActiveWork = false, hasFailure = false))
    }
}
