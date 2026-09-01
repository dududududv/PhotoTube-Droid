package com.yunai.phototube.data.job

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.PhotoTubeApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class JobApiContractTest {
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
    fun tearDown() {
        server.close()
    }

    @Test
    fun summaryPreservesUnknownTotal() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[{"kind":"SCAN_LIBRARY","pending":2,"paused":0,"running":1,"succeeded":5,"failed":0,"cancelled":0,"queuePaused":false,"discovered":8,"total":null,"lastFinishedAt":null}]}""",
            ),
        )

        val summary = api.getJobSummary().body()!!.items.single()

        assertNull(summary.total)
        assertEquals(8, summary.discovered)
        assertFalse(summary.queuePaused)
        assertEquals("/api/v1/jobs/summary", server.takeRequest().url.encodedPath)
    }

    @Test
    fun queueAndJobControlsUseDistinctEndpoints() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(jsonResponse(jobJson(state = "CANCELLED")))

        assertEquals(204, api.pauseJobQueue("ALBUM_PATH_SYNC").code())
        assertEquals(204, api.resumeJobQueue("ALBUM_PATH_SYNC").code())
        assertEquals(JobState.CANCELLED, api.cancelJob("job-1").body()!!.state)

        assertEquals("/api/v1/job-queues/ALBUM_PATH_SYNC/pause", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/job-queues/ALBUM_PATH_SYNC/resume", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/jobs/job-1/cancel", server.takeRequest().url.encodedPath)
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
