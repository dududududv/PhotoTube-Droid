package com.yunai.phototube.ui.jobs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobCenterRequestTokenTest {
    @Test
    fun `摘要只允许当前路由的最新请求写回`() {
        val request = JobSummaryRequestToken(
            routeGeneration = 4,
            requestGeneration = 9,
        )

        assertTrue(request.matches(currentRouteGeneration = 4, currentRequestGeneration = 9))
        assertFalse(request.matches(currentRouteGeneration = 5, currentRequestGeneration = 9))
        assertFalse(request.matches(currentRouteGeneration = 4, currentRequestGeneration = 10))
    }

    @Test
    fun `控制操作按 generation 与目标锁存且不依赖路由身份`() {
        val request = JobMutationRequestToken(
            requestGeneration = 7,
            target = "job-job-a",
        )

        assertTrue(request.matches(currentRequestGeneration = 7, currentTarget = "job-job-a"))
        assertFalse(request.matches(currentRequestGeneration = 8, currentTarget = "job-job-a"))
        assertFalse(request.matches(currentRequestGeneration = 7, currentTarget = "job-job-b"))
        assertFalse(request.matches(currentRequestGeneration = 7, currentTarget = null))
    }
}
