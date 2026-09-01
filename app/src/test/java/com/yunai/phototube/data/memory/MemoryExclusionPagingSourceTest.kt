package com.yunai.phototube.data.memory

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

class MemoryExclusionPagingSourceTest {
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
    fun opaqueCursorIsPassedWithoutInterpretation() = runTest {
        server.enqueue(jsonResponse("""{"items":[${dateRuleJson()}],"nextCursor":null}"""))

        val result = MemoryExclusionPagingSource(api, moshi)
            .load(append("opaque+/=memory-cursor")) as PagingSource.LoadResult.Page

        assertEquals(DATE_RULE_ID, result.data.single().id)
        server.takeRequest().url.also { url ->
            assertEquals("opaque+/=memory-cursor", url.queryParameter("cursor"))
            assertEquals("500", url.queryParameter("limit"))
        }
    }

    @Test
    fun invalidAppendCursorInvalidatesPagingGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body("""{"code":"INVALID_CURSOR","message":"回忆游标已失效","retryable":true,"logId":"log-memory"}""")
                .build(),
        )

        val result = MemoryExclusionPagingSource(api, moshi).load(append("expired"))

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }

    private fun append(key: String) = PagingSource.LoadParams.Append<String>(
        key = key,
        loadSize = 600,
        placeholdersEnabled = false,
    )

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
