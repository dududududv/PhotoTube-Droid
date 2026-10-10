package com.yunai.phototube.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class LocalConnectionTest {
    @Test fun localOptInAllowsOnlyTheAuthorizedOrigin() {
        assertTrue(LocalConnection.allowsTransport(LocalConnection.BASE.toHttpUrl(), false))
        listOf("http://127.0.0.1:18473", "https://127.0.0.1:18474", "http://localhost:18474",
            "http://10.0.2.2:18474", "http://192.168.6.56:18473", "https://example.com").forEach {
            assertFalse(it, LocalConnection.allowsTransport(it.toHttpUrl(), false))
        }
    }
    @Test fun onlyInjectedLoopbackFixturesCanUseOtherPorts() {
        assertFalse(LocalConnection.allowsTransport("http://127.0.0.1:18475".toHttpUrl(), false))
        assertFalse(LocalConnection.allowsTransport("https://mock.phototube.invalid".toHttpUrl(), false))
        assertTrue(LocalConnection.allowsTransport("https://localhost:12345".toHttpUrl(), true))
        assertFalse(LocalConnection.allowsTransport("http://192.168.6.56:18473".toHttpUrl(), true))
    }
}
