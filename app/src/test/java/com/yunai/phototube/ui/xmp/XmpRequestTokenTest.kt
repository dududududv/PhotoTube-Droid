package com.yunai.phototube.ui.xmp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmpRequestTokenTest {
    @Test
    fun `路由与操作 generation 都一致时才允许写回`() {
        val request = XmpRequestToken(routeGeneration = 4, requestGeneration = 9)

        assertTrue(request.matches(currentRouteGeneration = 4, currentRequestGeneration = 9))
        assertFalse(request.matches(currentRouteGeneration = 5, currentRequestGeneration = 9))
        assertFalse(request.matches(currentRouteGeneration = 4, currentRequestGeneration = 10))
    }

    @Test
    fun `关闭弹窗或离开路由都能独立使旧请求失效`() {
        val request = XmpRequestToken(routeGeneration = 2, requestGeneration = 6)

        val closedDialog = request.matches(currentRouteGeneration = 2, currentRequestGeneration = 7)
        val leftRoute = request.matches(currentRouteGeneration = 3, currentRequestGeneration = 6)

        assertFalse(closedDialog)
        assertFalse(leftRoute)
    }
}
