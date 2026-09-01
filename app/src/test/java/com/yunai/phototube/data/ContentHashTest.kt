package com.yunai.phototube.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentHashTest {
    @Test
    fun onlyCanonicalLowercaseSha256CanBeUsedAsContentVersion() {
        assertTrue("0".repeat(64).isSha256ContentHash())
        assertTrue("0123456789abcdef".repeat(4).isSha256ContentHash())
        assertFalse("A".repeat(64).isSha256ContentHash())
        assertFalse("g".repeat(64).isSha256ContentHash())
        assertFalse("a".repeat(63).isSha256ContentHash())
        assertFalse("a".repeat(65).isSha256ContentHash())
        assertFalse("".isSha256ContentHash())
    }
}
