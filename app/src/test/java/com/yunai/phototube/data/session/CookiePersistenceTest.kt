package com.yunai.phototube.data.session

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiePersistenceTest {
    private val now = 1_800_000_000_000L
    private val httpsOrigin = "https://photos.example.test/api/v1/auth/login?source=android".toHttpUrl()

    @Test
    fun `持久化往返保留 Cookie 安全和匹配属性`() {
        val cookie = cookie(
            value = "opaque-session",
            path = "/api/v1",
            secure = true,
            httpOnly = true,
        )
        val merged = mergePersistedCookies(emptyList(), httpsOrigin, listOf(cookie), now)

        val restored = decodePersistedCookies(encodePersistedCookies(merged), now)

        assertEquals(1, restored.size)
        assertEquals("https://photos.example.test/", restored.single().origin.toString())
        assertEquals("opaque-session", restored.single().cookie.value)
        assertTrue(restored.single().cookie.secure)
        assertTrue(restored.single().cookie.httpOnly)
        assertTrue(restored.single().cookie.hostOnly)
        assertEquals("/api/v1", restored.single().cookie.path)
    }

    @Test
    fun `host path 和 secure 共同限制请求携带范围`() {
        val stored = mergePersistedCookies(
            emptyList(),
            httpsOrigin,
            listOf(cookie(path = "/api/v1", secure = true)),
            now,
        )

        assertEquals(1, cookiesForRequest(stored, "https://photos.example.test/api/v1/assets".toHttpUrl(), now).size)
        assertTrue(cookiesForRequest(stored, "https://other.example.test/api/v1/assets".toHttpUrl(), now).isEmpty())
        assertTrue(cookiesForRequest(stored, "http://photos.example.test/api/v1/assets".toHttpUrl(), now).isEmpty())
        assertTrue(cookiesForRequest(stored, "https://photos.example.test/health".toHttpUrl(), now).isEmpty())
    }

    @Test
    fun `同名同域同路径响应覆盖旧值而不是累积重复项`() {
        val old = mergePersistedCookies(emptyList(), httpsOrigin, listOf(cookie(value = "old")), now)
        val updated = mergePersistedCookies(old, httpsOrigin, listOf(cookie(value = "new")), now)

        assertEquals(1, updated.size)
        assertEquals("new", updated.single().cookie.value)
    }

    @Test
    fun `删除 Cookie 与恰好到期 Cookie 都不会继续发送`() {
        val current = mergePersistedCookies(emptyList(), httpsOrigin, listOf(cookie(value = "old")), now)
        val deletion = cookie(value = "", expiresAt = now)

        val updated = mergePersistedCookies(current, httpsOrigin, listOf(deletion), now)

        assertTrue(updated.isEmpty())
        assertTrue(activePersistedCookies(listOf(PersistedCookie(httpsOrigin, deletion)), now).isEmpty())
    }

    @Test
    fun `恢复重复记录时以后写入值为准`() {
        val first = PersistedCookie(httpsOrigin, cookie(value = "first"))
        val second = PersistedCookie(httpsOrigin, cookie(value = "second"))

        val restored = decodePersistedCookies(encodePersistedCookies(listOf(first, second)), now)

        assertEquals(1, restored.size)
        assertEquals("second", restored.single().cookie.value)
        assertFalse(restored.single().cookie.value == "first")
    }

    private fun cookie(
        value: String = "session",
        path: String = "/",
        expiresAt: Long = now + 86_400_000L,
        secure: Boolean = false,
        httpOnly: Boolean = false,
    ): Cookie {
        val builder = Cookie.Builder()
            .name("phototube_session")
            .value(value)
            .hostOnlyDomain("photos.example.test")
            .path(path)
            .expiresAt(expiresAt)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }
}
