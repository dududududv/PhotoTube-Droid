package com.yunai.phototube.data

import org.junit.Assert.*
import org.junit.Test

/** Parsing only: these tests never resolve DNS or connect to the example addresses. */
class ServerAddressTest {
    @Test fun acceptsUserSelectedHttpHttpsDomainsIpsAndPorts() {
        listOf("https://photos.example.com", "http://192.0.2.10:18473", "http://127.0.0.1:32123", "https://[::1]:8443")
            .forEach { assertEquals(it, PhotoTubeServer.normalize(it)) }
    }
    @Test fun acceptsPastedApiRootAndNormalizesItOnce() {
        assertEquals("https://photos.example.com", PhotoTubeServer.normalize(" https://photos.example.com/api/v1/ "))
    }
    @Test fun rejectsCredentialsQueriesFragmentsAndNonHttpSchemes() {
        listOf("", "file:///photos", "ftp://photos.example.com", "https://user:secret@photos.example.com",
            "https://photos.example.com?token=secret", "https://photos.example.com#fragment", "https://photos.example.com/other")
            .forEach { address -> assertThrows(IllegalArgumentException::class.java) { PhotoTubeServer.normalize(address) } }
    }
}
