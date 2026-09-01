package com.yunai.phototube.data.duplicate

import androidx.paging.PagingSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.PhotoTubeApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class DuplicatePagingSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var moshi: Moshi
    private lateinit var api: PhotoTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
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
    fun groupCursorAndScopeArePassedUnchanged() = runTest {
        server.enqueue(jsonResponse("""{"items":[${duplicateGroupJson()}],"nextCursor":null}"""))

        val result = DuplicateGroupPagingSource(
            api = api,
            moshi = moshi,
            query = DuplicateQuery(includeReviewed = true, privateScope = true),
        ).load(append("opaque+/=duplicate-cursor")) as PagingSource.LoadResult.Page

        assertEquals("a".repeat(64), result.data.single().contentHash)
        server.takeRequest().url.also { url ->
            assertEquals("opaque+/=duplicate-cursor", url.queryParameter("cursor"))
            assertEquals("true", url.queryParameter("includeReviewed"))
            assertEquals("true", url.queryParameter("private"))
        }
    }

    @Test
    fun memberEndpointKeepsHashCursorAndPrivateScope() = runTest {
        val hash = "b".repeat(64)
        server.enqueue(jsonResponse("""{"items":[],"nextCursor":"next-member"}"""))

        val result = DuplicateAssetPagingSource(api, moshi, hash, true)
            .load(append("asset-cursor")) as PagingSource.LoadResult.Page

        assertEquals("next-member", result.nextKey)
        server.takeRequest().url.also { url ->
            assertEquals("/api/v1/duplicates/$hash", url.encodedPath)
            assertEquals("asset-cursor", url.queryParameter("cursor"))
            assertEquals("true", url.queryParameter("private"))
        }
    }

    @Test
    fun invalidCursorInvalidatesGeneration() = runTest {
        server.enqueue(errorResponse("INVALID_CURSOR"))

        val result = DuplicateGroupPagingSource(api, moshi, DuplicateQuery())
            .load(append("expired"))

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }

    private fun append(key: String) = PagingSource.LoadParams.Append<String>(
        key = key,
        loadSize = 100,
        placeholdersEnabled = false,
    )

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun errorResponse(code: String) = MockResponse.Builder()
        .code(400)
        .addHeader("Content-Type", "application/json")
        .body("""{"code":"$code","message":"游标已失效","retryable":true,"logId":"log-duplicate"}""")
        .build()
}

internal fun duplicateGroupJson(
    hash: String = "a".repeat(64),
    reviewedAt: String? = null,
): String = """{
  "contentHash":"$hash",
  "copyCount":3,
  "reclaimableBytes":2048,
  "latestChangeAt":"2026-09-01T00:00:00Z",
  "reviewedAt":${reviewedAt?.let { "\"$it\"" } ?: "null"},
  "representative":{
    "assetId":"asset-1",
    "kind":"PHOTO",
    "fileName":"IMG_0001.jpg",
    "fileSize":1024,
    "contentHash":"$hash"
  }
}""".trimIndent()
