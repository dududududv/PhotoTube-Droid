package com.yunai.phototube.data.duplicate

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.PhotoTubeApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DuplicateApiContractTest {
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
    fun reviewedGroupParsesRepresentativeAndTimestamp() = runTest {
        server.enqueue(jsonResponse("""{"items":[${duplicateGroupJson(reviewedAt = "2026-09-01T01:00:00Z")}],"nextCursor":null}"""))

        val group = api.getDuplicateGroups(includeReviewed = true).body()!!.items.single()

        assertNotNull(group.reviewedAt)
        assertEquals("asset-1", group.representative.assetId)
        assertEquals(3, group.copyCount)
    }

    @Test
    fun reviewAndReopenUseReversibleBodylessEndpoints() = runTest {
        val hash = "c".repeat(64)
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(204, api.reviewDuplicateGroup(hash, true).code())
        assertEquals(204, api.reopenDuplicateGroup(hash, true).code())

        server.takeRequest().also { request ->
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/duplicates/$hash/review", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("private"))
            assertEquals(0, request.bodySize)
        }
        server.takeRequest().also { request ->
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/duplicates/$hash/review", request.url.encodedPath)
            assertEquals("true", request.url.queryParameter("private"))
            assertEquals(0, request.bodySize)
        }
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
