package com.yunai.phototube.data.tag

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

class TagPagingSourceTest {
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
    fun tearDown() = server.close()

    @Test
    fun opaqueCursorKeywordAndMaximumPageSizePassUnchanged() = runTest {
        server.enqueue(jsonResponse("""{"items":[${tagJson()}],"nextCursor":null}"""))

        val result = TagPagingSource(api, moshi, "海 边")
            .load(append("opaque+/=tag-cursor")) as PagingSource.LoadResult.Page

        assertEquals(LONG_TAG_ID, result.data.single().id)
        server.takeRequest().url.also { url ->
            assertEquals("opaque+/=tag-cursor", url.queryParameter("cursor"))
            assertEquals("500", url.queryParameter("limit"))
            assertEquals("海 边", url.queryParameter("keyword"))
        }
    }

    @Test
    fun invalidAppendCursorInvalidatesPagingGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body("""{"code":"INVALID_CURSOR","message":"标签游标已失效","retryable":true,"logId":"log-tag"}""")
                .build(),
        )

        val result = TagPagingSource(api, moshi, null).load(append("expired"))

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }

    private fun append(key: String) = PagingSource.LoadParams.Append<String>(
        key = key,
        loadSize = 900,
        placeholdersEnabled = false,
    )

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
