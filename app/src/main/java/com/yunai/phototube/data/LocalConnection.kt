package com.yunai.phototube.data

import okhttp3.HttpUrl

/** User-authorized local development endpoint; never a general LAN/production opt-out. */
internal object LocalConnection {
    const val BASE = "http://127.0.0.1:18474"

    fun isLocalDevelopment(url: HttpUrl): Boolean =
        url.scheme == "http" && url.host == "127.0.0.1" && url.port == 18474

    fun allowsTransport(url: HttpUrl, injected: Boolean): Boolean =
        isLocalDevelopment(url) ||
            (injected && url.host in setOf("localhost", "127.0.0.1", "::1"))
}
