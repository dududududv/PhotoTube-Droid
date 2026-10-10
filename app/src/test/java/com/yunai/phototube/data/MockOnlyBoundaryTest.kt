package com.yunai.phototube.data

import org.junit.Assert.*
import org.junit.Test

class MockOnlyBoundaryTest {
    @Test fun externalAndPrivateAddressesAreRejectedBeforeTransportCreation() {
        listOf("http://10.23.45.67:18473", "https://192.168.100.2", "https://example.com", "https://localhost.example.com", "https://user:secret@localhost", "https://localhost/?target=private").forEach { address ->
            assertThrows(IllegalArgumentException::class.java) { PhotoTubeServer.normalize(address) }
        }
    }
    @Test fun removedMockOriginIsRejectedAndLoopbackCanNormalize() {
        assertThrows(IllegalArgumentException::class.java) { PhotoTubeServer.normalize("https://mock.phototube.invalid") }
        assertEquals("https://localhost:12345", PhotoTubeServer.normalize("https://localhost:12345/"))
    }
}
