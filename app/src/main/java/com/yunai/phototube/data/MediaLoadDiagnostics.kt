package com.yunai.phototube.data

import android.util.Log
import coil3.EventListener
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.yunai.phototube.BuildConfig
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import java.io.IOException

/** Debug media responses and failures. Never dump headers, cookies, bodies or arbitrary query values. */
internal object MediaLoadDiagnostics {
    private const val TAG = "PhotoTubeImage"

    val responses = okhttp3.Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (BuildConfig.DEBUG) safeUrl(chain.request().url.toString())?.let { url ->
            // A cache-generated 504 must be distinguishable from a server response.
            Log.d(TAG, "stage=response url=$url http=${response.code} network=${response.networkResponse != null}")
        }
        response
    }

    internal fun safeUrl(value: String): String? {
        val url = value.toHttpUrlOrNull() ?: return null
        if (!url.encodedPath.matches(Regex("/api/v1/assets/[^/]+/(thumbnail|original|motion-video|edit-versions/[^/]+/render)"))) return null
        val builder = url.newBuilder().username("").password("").query(null).fragment(null)
        url.queryParameter("size")?.takeIf { it in setOf("SM", "MD", "PREVIEW", "FULL") }
            ?.let { builder.addQueryParameter("size", it) }
        url.queryParameter("v")?.takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
            ?.let { builder.addQueryParameter("v", it) }
        return builder.build().toString()
    }

    internal fun reason(error: Throwable): String = generateSequence(error) { it.cause }
        .take(5).joinToString(" <- ") {
            val message = it.message.orEmpty()
                .replace(Regex("(?i)(?:set-cookie|cookie|authorization|password)\\s*[:=][^\\r\\n]*"), "[redacted]")
                .replace(Regex("https?://[^\\s]+")) { match -> safeUrl(match.value) ?: "[url]" }
                .replace('\n', ' ').replace('\r', ' ').take(350)
            "${it.javaClass.simpleName}: $message"
        }

    val networkEvents = okhttp3.EventListener.Factory { call ->
        val url = safeUrl(call.request().url.toString())
        if (!BuildConfig.DEBUG || url == null) okhttp3.EventListener.NONE else object : okhttp3.EventListener() {
            private var status: Int? = null
            override fun responseHeadersEnd(call: Call, response: Response) {
                status = response.code
                if (!response.isSuccessful) Log.w(TAG, "stage=http url=$url http=${response.code} reason=HTTP_${response.code}")
            }
            override fun callFailed(call: Call, ioe: IOException) {
                Log.e(TAG, "stage=transport url=$url http=${status ?: "unavailable"} reason=${reason(ioe)}")
            }
        }
    }

    val imageEvents = EventListener.Factory { request ->
        if (!BuildConfig.DEBUG || safeUrl(request.data.toString()) == null) EventListener.NONE else object : EventListener() {
            override fun onError(request: ImageRequest, result: ErrorResult) {
                val status = generateSequence(result.throwable) { it.cause }.take(5)
                    .filterIsInstance<HttpException>().firstOrNull()?.response?.code
                Log.e(TAG, "stage=image url=${safeUrl(request.data.toString())} http=${status ?: "see-transport"} reason=${reason(result.throwable)}")
            }
        }
    }
}
