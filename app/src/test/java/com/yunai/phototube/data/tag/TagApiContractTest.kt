package com.yunai.phototube.data.tag

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

class TagApiContractTest {
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
    fun renameUsesLongIdPatchAndNameOnlyBody() = runTest {
        server.enqueue(jsonResponse(tagJson(name = "新的海边")))

        val updated = api.updateTag(LONG_TAG_ID, UpdateTagRequest("新的海边")).body()!!

        assertEquals("新的海边", updated.name)
        server.takeRequest().also { request ->
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/tags/$LONG_TAG_ID", request.url.encodedPath)
            assertEquals("""{"name":"新的海边"}""", request.body!!.utf8())
        }
    }

    @Test
    fun deleteReturnsAffectedAssetCountAndSendsNoBody() = runTest {
        server.enqueue(jsonResponse("""{"affectedAssetCount":27}"""))

        val result = api.deleteTag(LONG_TAG_ID).body()!!

        assertEquals(27L, result.affectedAssetCount)
        server.takeRequest().also { request ->
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/tags/$LONG_TAG_ID", request.url.encodedPath)
            assertEquals(0L, request.bodySize)
        }
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}

internal const val LONG_TAG_ID = 4_294_967_296L

internal fun tagJson(name: String = "海边") = """{
  "id":$LONG_TAG_ID,
  "name":"$name",
  "assetCount":8,
  "createdAt":"2026-09-01T00:00:00Z",
  "updatedAt":"2026-09-01T00:00:00Z"
}""".trimIndent()
