package com.yunai.phototube.data.album

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

class AlbumPagingSourceTest {
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
    fun albumCursorIsOpaqueAndPassedUnchanged() = runTest {
        server.enqueue(jsonResponse("""{"items":[${normalAlbumJson()}],"nextCursor":null}"""))
        val source = AlbumPagingSource(api, moshi)

        val result = source.load(
            PagingSource.LoadParams.Append(
                key = "opaque+/=album-cursor",
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page

        assertEquals("album-1", result.data.single().id)
        assertEquals(
            "opaque+/=album-cursor",
            server.takeRequest().url.queryParameter("cursor"),
        )
    }

    @Test
    fun invalidAlbumCursorInvalidatesGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"code":"INVALID_CURSOR","message":"相册游标已失效","retryable":true,"logId":"log-album"}""",
                )
                .build(),
        )

        val result = AlbumPagingSource(api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "expired",
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        )

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }

    @Test
    fun pathSyncHistoryKeepsOpaqueCursorAndClampsPageSize() = runTest {
        server.enqueue(jsonResponse("""{"items":[${syncRunJson()}],"nextCursor":"next-run"}"""))

        val result = AlbumPathSyncRunPagingSource("album-path", api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "opaque+/=sync-cursor",
                loadSize = 900,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page

        assertEquals(AlbumPathSyncTrigger.MANUAL, result.data.single().trigger)
        assertEquals("next-run", result.nextKey)
        server.takeRequest().url.also { url ->
            assertEquals("/api/v1/albums/album-path/syncs", url.encodedPath)
            assertEquals("opaque+/=sync-cursor", url.queryParameter("cursor"))
            assertEquals("500", url.queryParameter("limit"))
        }
    }

    @Test
    fun invalidPathSyncHistoryCursorInvalidatesGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"code":"INVALID_CURSOR","message":"同步游标已失效","retryable":true,"logId":"log-sync"}""",
                )
                .build(),
        )

        val result = AlbumPathSyncRunPagingSource("album-path", api, moshi).load(
            PagingSource.LoadParams.Append(
                key = "expired",
                loadSize = 100,
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

internal fun syncRunJson(
    id: String = "run-1",
    state: String = "PENDING",
): String =
    """{"id":"$id","albumId":"album-path","configGeneration":5,"trigger":"MANUAL","state":"$state","totalPaths":1,"succeededPaths":0,"failedPaths":0,"offlinePaths":0,"createdAt":"2026-08-31T10:00:00Z","startedAt":null,"finishedAt":null,"error":null}"""

internal fun normalAlbumJson(): String =
    """{
      "id":"album-1",
      "userId":"user-1",
      "name":"旅行",
      "kind":"NORMAL",
      "sortMode":"TAKEN_AT_DESC",
      "aiPrompt":null,
      "filter":null,
      "pathSync":null,
      "coverAssetId":null,
      "coverFocalPoint":null,
      "cover":null,
      "assetCount":0,
      "createdAt":"2026-08-31T10:00:00Z",
      "updatedAt":"2026-08-31T10:00:00Z"
    }""".trimIndent()
