package com.yunai.phototube.data.trash

import androidx.paging.PagingSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.asset.trashedAssetJson
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

class TrashPagingSourceTest {
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
    fun trashCursorIsOpaqueAndPassedUnchanged() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"items":[${trashedAssetJson()}],"nextCursor":null}""")
                .build(),
        )

        val result = TrashPagingSource(api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "opaque+/=trash-cursor",
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page

        assertEquals("asset-1", result.data.single().id)
        assertEquals("opaque+/=trash-cursor", server.takeRequest().url.queryParameter("cursor"))
    }

    @Test
    fun invalidTrashCursorInvalidatesGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"code":"INVALID_CURSOR","message":"回收站游标已失效","retryable":true,"logId":"log-trash"}""",
                )
                .build(),
        )

        val result = TrashPagingSource(api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "expired",
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        )

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }
}
