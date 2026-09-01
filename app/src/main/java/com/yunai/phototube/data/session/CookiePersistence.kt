package com.yunai.phototube.data.session

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class PersistedCookie(
    val origin: HttpUrl,
    val cookie: Cookie,
)

internal fun mergePersistedCookies(
    current: List<PersistedCookie>,
    responseUrl: HttpUrl,
    incoming: List<Cookie>,
    nowMillis: Long,
): List<PersistedCookie> {
    val incomingKeys = incoming.mapTo(mutableSetOf(), Cookie::storageKey)
    val retained = current.filter { stored ->
        stored.cookie.expiresAt > nowMillis && stored.cookie.storageKey() !in incomingKeys
    }
    val normalizedOrigin = responseUrl.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
    val accepted = incoming
        .filter { it.expiresAt > nowMillis }
        .map { PersistedCookie(normalizedOrigin, it) }
    return deduplicatePersistedCookies(retained + accepted)
}

internal fun activePersistedCookies(
    entries: List<PersistedCookie>,
    nowMillis: Long,
): List<PersistedCookie> = deduplicatePersistedCookies(
    entries.filter { it.cookie.expiresAt > nowMillis },
)

internal fun cookiesForRequest(
    entries: List<PersistedCookie>,
    requestUrl: HttpUrl,
    nowMillis: Long,
): List<Cookie> = activePersistedCookies(entries, nowMillis)
    .map(PersistedCookie::cookie)
    .filter { it.matches(requestUrl) }

internal fun encodePersistedCookies(entries: List<PersistedCookie>): String {
    val records = deduplicatePersistedCookies(entries).map { stored ->
        PersistedCookieRecord(
            origin = stored.origin.toString(),
            cookie = stored.cookie.toString(),
        )
    }
    return persistedCookieAdapter.toJson(records)
}

internal fun decodePersistedCookies(payload: String, nowMillis: Long): List<PersistedCookie> {
    val decoded = buildList {
        persistedCookieAdapter.fromJson(payload).orEmpty().forEach { record ->
            val origin = record.origin.toHttpUrlOrNull() ?: return@forEach
            val cookie = Cookie.parse(origin, record.cookie) ?: return@forEach
            if (cookie.expiresAt > nowMillis) add(PersistedCookie(origin, cookie))
        }
    }
    return deduplicatePersistedCookies(decoded)
}

private fun deduplicatePersistedCookies(entries: List<PersistedCookie>): List<PersistedCookie> {
    val byKey = linkedMapOf<CookieStorageKey, PersistedCookie>()
    entries.forEach { byKey[it.cookie.storageKey()] = it }
    return byKey.values.toList()
}

private data class CookieStorageKey(
    val name: String,
    val domain: String,
    val path: String,
)

private fun Cookie.storageKey() = CookieStorageKey(name, domain, path)

private data class PersistedCookieRecord(
    val origin: String,
    val cookie: String,
)

private val persistedCookieAdapter = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()
    .adapter<List<PersistedCookieRecord>>(
        Types.newParameterizedType(List::class.java, PersistedCookieRecord::class.java),
    )
