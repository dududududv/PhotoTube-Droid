package com.yunai.phototube.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/** Test APK only; generated responses on an isolated loopback socket. */
internal class LocalMockFixture(private val context: Context) : AutoCloseable {
    private val data = LocalMockData(context)
    private val host = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val input = Request.Builder().url(requireNotNull(request.requestUrl)).build()
                if (request.method != "GET") return MockResponse().setResponseCode(405)
                return data.respond(input).use { response ->
                    MockResponse().setResponseCode(response.code).setHeaders(response.headers)
                        .setHeader("Content-Type", response.body.contentType().toString())
                        .setBody(Buffer().write(response.body.bytes()))
                }
            }
        }
        start(java.net.InetAddress.getByName("127.0.0.1"), 0)
    }
    private val transport = OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().removeHeader("Cache-Control").build())
    }.build()
    val address = host.url("/").newBuilder().host("127.0.0.1").build().toString()
    fun server(onUnauthorized: () -> Unit = {}) = PhotoTubeServer(context, address, onUnauthorized, transport)
    override fun close() { host.shutdown() }
}
