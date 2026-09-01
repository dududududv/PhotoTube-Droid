package com.yunai.phototube.data.xmp

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

class XmpExportPagingSourceTest {
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
    fun opaqueCursorIsPassedWithoutInterpretation() = runTest {
        server.enqueue(jsonResponse("""{"items":[${runJson()}],"nextCursor":null}"""))

        val result = XmpExportPagingSource(api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "opaque+/=xmp-cursor",
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page

        assertEquals("run-1", result.data.single().id)
        server.takeRequest().url.also { url ->
            assertEquals("opaque+/=xmp-cursor", url.queryParameter("cursor"))
            assertEquals("20", url.queryParameter("limit"))
        }
    }

    @Test
    fun invalidAppendCursorInvalidatesPagingGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"code":"INVALID_CURSOR","message":"XMP 游标已失效","retryable":true,"logId":"log-xmp"}""",
                )
                .build(),
        )

        val result = XmpExportPagingSource(api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "expired",
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
