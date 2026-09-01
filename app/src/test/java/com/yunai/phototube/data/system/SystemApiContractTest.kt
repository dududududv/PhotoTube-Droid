package com.yunai.phototube.data.system

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.PhotoTubeApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class SystemApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PhotoTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PhotoTubeApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun systemStatusUsesReadOnlyEndpointAndParsesAllStableAlertKinds() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(statusJson())
                .build(),
        )

        val status = api.getSystemStatus().body()!!

        assertEquals(SystemAlertCode.entries, status.alerts.map(SystemAlert::code))
        assertEquals(listOf(SystemAlertUnit.BYTES, SystemAlertUnit.BYTES, SystemAlertUnit.BYTES, SystemAlertUnit.COUNT), status.alerts.map(SystemAlert::unit))
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/v1/system/status", request.url.encodedPath)
            assertEquals(0L, request.bodySize)
        }
    }

    private fun statusJson() = """
        {
          "generatedAt":"2026-09-01T03:30:00Z",
          "alerts":[
            {"code":"DERIVATIVE_DISK_FREE_LOW","subject":"派生目录","message":"空间不足","currentValue":100,"thresholdValue":200,"unit":"BYTES"},
            {"code":"LIBRARY_DISK_FREE_LOW","subject":"家庭照片","message":"空间不足","currentValue":300,"thresholdValue":400,"unit":"BYTES"},
            {"code":"DERIVATIVE_CACHE_HIGH","subject":"派生缓存","message":"缓存过高","currentValue":900,"thresholdValue":1000,"unit":"BYTES"},
            {"code":"CONSECUTIVE_JOB_FAILURES","subject":"SCAN_LIBRARY","message":"任务连续失败","currentValue":3,"thresholdValue":3,"unit":"COUNT"}
          ]
        }
    """.trimIndent()
}
