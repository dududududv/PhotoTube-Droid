package com.yunai.phototube.ui.duplicates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateRequestTokenTest {
    private val privateScope = DuplicateScopeToken(
        routeGeneration = 3,
        scopeGeneration = 7,
        privateScope = true,
    )

    @Test
    fun `路由 分域 generation 与私密标志必须同时一致`() {
        assertTrue(privateScope.matches(3, 7, true))
        assertFalse(privateScope.matches(4, 7, true))
        assertFalse(privateScope.matches(3, 8, true))
        assertFalse(privateScope.matches(3, 7, false))
    }

    @Test
    fun `会话检查会被新检查或私密授权操作独立失效`() {
        val request = DuplicatePrivateCheckToken(
            scope = privateScope,
            requestGeneration = 5,
            authGeneration = 11,
        )

        assertTrue(request.matches(3, 7, true, 5, 11))
        assertFalse(request.matches(3, 7, true, 6, 11))
        assertFalse(request.matches(3, 7, true, 5, 12))
    }

    @Test
    fun `私密 mutation 只有最后一次操作可以提交结果`() {
        val request = DuplicatePrivateMutationToken(
            scope = privateScope,
            authGeneration = 12,
        )

        assertTrue(request.isLatest(12))
        assertFalse(request.isLatest(13))
    }

    @Test
    fun `组操作不能跨分域写回也不能关闭后来打开的组`() {
        val request = DuplicateReviewRequestToken(
            scope = privateScope,
            requestGeneration = 9,
            contentHash = "a".repeat(64),
            selectionGeneration = 4,
        )

        assertTrue(request.matchesScope(3, 7, true, 9))
        assertFalse(request.matchesScope(3, 8, true, 9))
        assertFalse(request.matchesScope(3, 7, false, 9))
        assertTrue(request.ownsSelection(4, "a".repeat(64)))
        assertFalse(request.ownsSelection(5, "a".repeat(64)))
        assertFalse(request.ownsSelection(4, "b".repeat(64)))
    }
}
