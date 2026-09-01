package com.yunai.phototube.data.remote

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import okhttp3.OkHttpClient
import okhttp3.Request
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.RequestBody.Companion.toRequestBody

class DerivativeRetryInterceptorTest {
    private lateinit var server: MockWebServer
    private val delays = mutableListOf<Long>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `缩略图按 Retry-After 重试后返回成功响应`() {
        server.enqueue(pendingResponse("1"))
        server.enqueue(MockResponse.Builder().code(200).body("image").build())

        client().newCall(request("api/v1/assets/asset-1/thumbnail?size=MD&v=hash"))
            .execute()
            .use { response -> assertEquals(200, response.code) }

        assertEquals(2, server.requestCount)
        assertEquals(listOf(1_000L), delays)
    }

    @Test
    fun `编辑渲染同样进入有限重试`() {
        server.enqueue(pendingResponse("1"))
        server.enqueue(MockResponse.Builder().code(200).build())

        client().newCall(request("api/v1/assets/asset-1/edit-versions/edit-1/render?size=PREVIEW"))
            .execute()
            .close()

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `连续挂起最多只额外请求两次`() {
        repeat(3) {
            server.enqueue(pendingResponse("1"))
        }

        client().newCall(request("api/v1/assets/asset-1/thumbnail?size=SM&v=hash"))
            .execute()
            .use { response -> assertEquals(503, response.code) }

        assertEquals(3, server.requestCount)
        assertEquals(listOf(1_000L, 1_000L), delays)
    }

    @Test
    fun `没有合法 Retry-After 时不忙重试`() {
        server.enqueue(MockResponse.Builder().code(503).build())

        client().newCall(request("api/v1/assets/asset-1/thumbnail?size=SM&v=hash"))
            .execute()
            .close()

        assertEquals(1, server.requestCount)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `原图和业务请求不会被重放`() {
        server.enqueue(pendingResponse("1"))
        server.enqueue(pendingResponse("1"))

        client().newCall(request("api/v1/assets/asset-1/original")).execute().close()
        client().newCall(request("api/v1/home")).execute().close()

        assertEquals(2, server.requestCount)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `写请求即使路径相同也不重放`() {
        server.enqueue(pendingResponse("1"))
        val request = Request.Builder()
            .url(server.url("api/v1/assets/asset-1/thumbnail"))
            .post(ByteArray(0).toRequestBody())
            .build()

        client().newCall(request).execute().close()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `Retry-After 秒数和日期均限制在五秒内`() {
        val now = Instant.parse("2026-09-01T00:00:00Z")
        val future = ZonedDateTime.ofInstant(now.plusSeconds(20), ZoneOffset.UTC)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME)

        assertEquals(2_000L, parseRetryAfterMillis("2", now))
        assertEquals(5_000L, parseRetryAfterMillis("999999999999", now))
        assertEquals(5_000L, parseRetryAfterMillis(future, now))
        assertEquals(0L, parseRetryAfterMillis("-2", now))
        assertNull(parseRetryAfterMillis("稍后", now))
        assertNull(parseRetryAfterMillis(null, now))
    }

    @Test
    fun `零秒立即重试不与 OkHttp 内建重放叠加`() {
        server.enqueue(pendingResponse("0"))
        server.enqueue(pendingResponse("0"))

        client().newCall(request("api/v1/assets/asset-1/thumbnail?size=SM&v=hash"))
            .execute()
            .use { response -> assertEquals(503, response.code) }

        assertEquals(2, server.requestCount)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `只有两个派生图片路径被识别`() {
        assertTrue(request("api/v1/assets/a/thumbnail").isDerivativeImageGet())
        assertTrue(request("api/v1/assets/a/edit-versions/e/render").isDerivativeImageGet())
        assertFalse(request("api/v1/assets/a/original").isDerivativeImageGet())
        assertFalse(request("api/v1/assets/a/motion-video").isDerivativeImageGet())
        assertFalse(request("api/v1/not-assets/a/thumbnail").isDerivativeImageGet())
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            DerivativeRetryInterceptor(
                sleeper = delays::add,
                now = { Instant.parse("2026-09-01T00:00:00Z") },
            ),
        )
        .build()

    private fun request(path: String): Request = Request.Builder()
        .url(server.url(path))
        .get()
        .build()

    private fun pendingResponse(retryAfter: String): MockResponse = MockResponse.Builder()
        .code(503)
        .addHeader("Retry-After", retryAfter)
        .build()
}
