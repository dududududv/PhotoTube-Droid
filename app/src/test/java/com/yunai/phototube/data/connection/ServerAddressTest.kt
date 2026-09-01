package com.yunai.phototube.data.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    @Test
    fun parse_addsHttpSchemeAndApiBaseExactlyOnce() {
        val root = ServerRoot.parse("nas.local:18473/api/v1/").getOrThrow()

        assertEquals("http://nas.local:18473", root.value)
        assertEquals("http://nas.local:18473/api/v1/", root.apiBaseUrl)
    }

    @Test
    fun parse_preservesHttpsAndRemovesTrailingSlash() {
        val root = ServerRoot.parse(" https://photos.example.com/ ").getOrThrow()

        assertEquals("https://photos.example.com", root.value)
    }

    @Test
    fun parse_rejectsUnexpectedPathAndQuery() {
        assertTrue(ServerRoot.parse("http://nas.local:18473/admin").isFailure)
        assertTrue(ServerRoot.parse("http://nas.local:18473?token=secret").isFailure)
    }
}
