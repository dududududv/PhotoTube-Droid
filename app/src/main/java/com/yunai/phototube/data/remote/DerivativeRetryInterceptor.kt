package com.yunai.phototube.data.remote

import java.io.InterruptedIOException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * 只为可安全重放的派生图片 GET 处理 `503 + Retry-After`。
 *
 * 原图、视频和业务 API 不进入此处，避免自动重放写操作或把服务不可用变成请求风暴。
 */
internal class DerivativeRetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Instant = Instant::now,
) : Interceptor {
    init {
        require(maxRetries >= 0) { "重试次数不能为负数" }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        if (!request.isDerivativeImageGet()) return response

        var retryCount = 0
        while (response.code == DERIVATIVE_PENDING_STATUS && retryCount < maxRetries) {
            val delayMillis = parseRetryAfterMillis(response.header("Retry-After"), now()) ?: break
            // OkHttp 的 RetryAndFollowUpInterceptor 已对 Retry-After: 0 做一次立即重放。
            // 应用层不再叠加零等待重试，否则一次挂起会放大成多次网络请求。
            if (delayMillis == 0L) break
            response.close()
            try {
                sleeper(delayMillis)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("派生图片重试等待被中断").apply {
                    initCause(interrupted)
                }
            }
            retryCount += 1
            response = chain.proceed(request)
        }
        return response
    }

    private companion object {
        const val DERIVATIVE_PENDING_STATUS = 503
        const val MAX_RETRIES = 2
    }
}

internal fun Request.isDerivativeImageGet(): Boolean {
    if (method != "GET") return false
    val segments = url.pathSegments
    val assetsIndex = segments.indexOfLast { it == "assets" }
    if (assetsIndex < 0) return false
    val tail = segments.drop(assetsIndex + 1)
    return (tail.size == 2 && tail[1] == "thumbnail") ||
        (tail.size == 4 && tail[1] == "edit-versions" && tail[3] == "render")
}

internal fun parseRetryAfterMillis(value: String?, now: Instant): Long? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (normalized.matches(INTEGER_HEADER)) {
        val seconds = normalized.toLongOrNull() ?: if (normalized.startsWith('-')) 0 else MAX_RETRY_AFTER_SECONDS
        return seconds.coerceIn(0, MAX_RETRY_AFTER_SECONDS) * 1_000L
    }

    val target = try {
        ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
        return null
    }
    return ChronoUnit.MILLIS.between(now, target)
        .coerceIn(0L, MAX_RETRY_AFTER_SECONDS * 1_000L)
}

private const val MAX_RETRY_AFTER_SECONDS = 5L
private val INTEGER_HEADER = Regex("[+-]?\\d+")
