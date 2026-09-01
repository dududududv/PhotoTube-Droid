package com.yunai.phototube.data.timeline

import androidx.paging.PagingSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.connection.ServerRoot
import com.yunai.phototube.data.remote.PhotoTubeApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class TimelinePagingSourceTest {
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
    fun refreshPassesFiltersAndReturnsOpaqueNextCursor() = runTest {
        server.enqueue(jsonResponse(assetPageJson(nextCursor = "opaque+/=cursor")))
        val source = TimelinePagingSource(
            api = api,
            moshi = moshi,
            filter = AssetFilter(favorite = true, tagsAny = setOf(12, 3)),
        )

        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals("asset-1", page.data.single().id)
        assertEquals("opaque+/=cursor", page.nextKey)
        val request = server.takeRequest()
        assertEquals("/api/v1/assets", request.url.encodedPath)
        assertEquals("100", request.url.queryParameter("limit"))
        assertEquals("true", request.url.queryParameter("favorite"))
        assertEquals("3,12", request.url.queryParameter("tagsAny"))
        assertEquals("false", request.url.queryParameter("archived"))
        assertEquals("false", request.url.queryParameter("private"))
        assertNull(request.url.queryParameter("cursor"))
    }

    @Test
    fun appendPassesCursorUnchanged() = runTest {
        server.enqueue(jsonResponse(assetPageJson(nextCursor = null)))
        val source = TimelinePagingSource(api, moshi, AssetFilter())

        source.load(
            PagingSource.LoadParams.Append(
                key = "opaque+/=cursor",
                loadSize = 40,
                placeholdersEnabled = false,
            ),
        )

        assertEquals("opaque+/=cursor", server.takeRequest().url.queryParameter("cursor"))
    }

    @Test
    fun filenameSearchPassesLiteralTrimmedKeywordAndFilters() = runTest {
        server.enqueue(jsonResponse(assetPageJson(nextCursor = null)))
        val filter = AssetFilter(
            keyword = "  Coast_100%  ",
            kind = AssetKind.PHOTO,
            favorite = true,
            rating = 5,
        )

        TimelinePagingSource(api, moshi, filter).load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        )

        server.takeRequest().url.also { url ->
            assertEquals("Coast_100%", url.queryParameter("keyword"))
            assertEquals("PHOTO", url.queryParameter("kind"))
            assertEquals("true", url.queryParameter("favorite"))
            assertEquals("5", url.queryParameter("rating"))
            assertEquals("false", url.queryParameter("private"))
        }
    }

    @Test
    fun invalidAppendCursorInvalidatesPagingGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"code":"INVALID_CURSOR","message":"游标已失效","retryable":true,"logId":"log-1"}""",
                )
                .build(),
        )
        val source = TimelinePagingSource(api, moshi, AssetFilter())

        val result = source.load(
            PagingSource.LoadParams.Append(
                key = "expired-cursor",
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        )

        assertTrue(result is PagingSource.LoadResult.Invalid)
    }

    @Test
    fun thumbnailUrlCarriesRequestedSizeAndContentVersion() {
        val root = ServerRoot.parse(server.url("/").toString()).getOrThrow()
        val asset = mediaAsset()

        val url = asset.thumbnailUrl(root, ThumbnailSize.MD)!!

        assertTrue(url.startsWith(server.url("/api/v1/assets/asset-1/thumbnail").toString()))
        assertTrue(url.contains("size=MD"))
        assertTrue(url.contains("v=${"a".repeat(64)}"))
        assertNull(asset.copy(contentHash = null).thumbnailUrl(root, ThumbnailSize.MD))
        assertNull(asset.copy(contentHash = "A".repeat(64)).thumbnailUrl(root, ThumbnailSize.MD))
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun assetPageJson(nextCursor: String?): String =
        """{
          "items": [${assetJson()}],
          "nextCursor": ${nextCursor?.let { "\"$it\"" } ?: "null"}
        }""".trimIndent()

    private fun assetJson(): String =
        """{
          "id":"asset-1",
          "userId":"user-1",
          "kind":"PHOTO",
          "state":"BROWSABLE",
          "takenAt":"2026-08-29T09:41:00+08:00",
          "takenAtOffsetMinutes":480,
          "takenAtSource":"EXIF",
          "importedAt":"2026-08-29T10:00:00+08:00",
          "libraryId":"library-a",
          "relativePath":"2026/08",
          "fileName":"coast.jpg",
          "fileSize":2048000,
          "contentHash":"sha256-version",
          "width":4032,
          "height":3024,
          "favorite":false,
          "archived":false,
          "private":false,
          "rating":null
        }""".trimIndent()

    private fun mediaAsset() = MediaAsset(
        id = "asset-1",
        userId = "user-1",
        kind = AssetKind.PHOTO,
        state = AssetState.BROWSABLE,
        takenAt = "2026-08-29T09:41:00+08:00",
        takenAtOffsetMinutes = 480,
        takenAtSource = "EXIF",
        importedAt = "2026-08-29T10:00:00+08:00",
        libraryId = "library-a",
        relativePath = "2026/08",
        fileName = "coast.jpg",
        fileSize = 2_048_000,
        contentHash = "a".repeat(64),
        width = 4032,
        height = 3024,
        favorite = false,
        archived = false,
        private = false,
        rating = null,
    )
}
