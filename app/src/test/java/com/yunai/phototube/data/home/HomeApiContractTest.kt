package com.yunai.phototube.data.home

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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HomeApiContractTest {
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
    fun homeFeedParsesAllBoundedSectionsWithoutPagination() = runTest {
        server.enqueue(jsonResponse(homeFeedJson()))

        val feed = api.getHomeFeed().body()!!

        assertEquals("on-this-day", feed.onThisDay.single().id)
        assertEquals("recent-photo", feed.recentPhotos.single().id)
        assertTrue(feed.recentImports.isEmpty())
        assertEquals("album-1", feed.frequentAlbums.single().id)
        assertEquals(7, feed.jobSummary.items.single().discovered)
        assertFalse(feed.isEmpty)
        server.takeRequest().also { request ->
            assertEquals("/api/v1/home", request.url.encodedPath)
            assertEquals(null, request.url.query)
        }
    }

    @Test
    fun emptyHomeFeedRemainsAnExplicitEmptySummary() = runTest {
        server.enqueue(
            jsonResponse(
                """{"onThisDay":[],"recentPhotos":[],"recentImports":[],"frequentAlbums":[],"jobSummary":{"items":[]}}""",
            ),
        )

        assertTrue(api.getHomeFeed().body()!!.isEmpty)
    }

    @Test
    fun clientRejectsAFeedBeyondTheDocumentedAssetBound() = runTest {
        server.enqueue(jsonResponse(homeFeedJson()))
        val feed = api.getHomeFeed().body()!!

        assertThrows(IllegalArgumentException::class.java) {
            feed.copy(onThisDay = List(19) { feed.onThisDay.single() })
        }
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun homeFeedJson(): String = """{
      "onThisDay":[${assetJson("on-this-day")}],
      "recentPhotos":[${assetJson("recent-photo")}],
      "recentImports":[],
      "frequentAlbums":[${albumJson()}],
      "jobSummary":{"items":[{
        "kind":"SCAN_LIBRARY",
        "pending":1,
        "paused":0,
        "running":1,
        "succeeded":5,
        "failed":0,
        "cancelled":0,
        "queuePaused":false,
        "discovered":7,
        "total":null,
        "lastFinishedAt":null
      }]}
    }""".trimIndent()

    private fun assetJson(id: String): String = """{
      "id":"$id",
      "userId":"user-1",
      "kind":"PHOTO",
      "state":"BROWSABLE",
      "takenAt":"2025-09-01T09:00:00+08:00",
      "takenAtOffsetMinutes":480,
      "takenAtSource":"EXIF",
      "importedAt":"2026-09-01T10:00:00+08:00",
      "libraryId":"library-1",
      "relativePath":"2025/09/$id.jpg",
      "fileName":"$id.jpg",
      "fileSize":1024,
      "contentHash":"${"a".repeat(64)}",
      "width":1920,
      "height":1080,
      "favorite":false,
      "archived":false,
      "private":false,
      "rating":null
    }""".trimIndent()

    private fun albumJson(): String = """{
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
      "assetCount":8,
      "createdAt":"2026-08-31T10:00:00Z",
      "updatedAt":"2026-08-31T10:00:00Z"
    }""".trimIndent()
}
