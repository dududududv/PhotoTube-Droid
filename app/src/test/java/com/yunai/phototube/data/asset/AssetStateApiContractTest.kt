package com.yunai.phototube.data.asset

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.ArchiveByIdsRequest
import com.yunai.phototube.data.remote.AssetIdsRequest
import com.yunai.phototube.data.remote.PhotoTubeApi
import com.yunai.phototube.data.remote.PrivateAccessRequest
import com.yunai.phototube.data.remote.PrivateByIdsRequest
import com.yunai.phototube.data.remote.RatingByIdsRequest
import com.yunai.phototube.data.remote.RatingByIdsRequestJsonAdapter
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AssetStateApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PhotoTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder()
            .add(RatingByIdsRequestJsonAdapter)
            .addLast(KotlinJsonAdapterFactory())
            .build()
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
    fun targetStateMutationsUseExactBodies() = runTest {
        repeat(4) { server.enqueue(batchResponse()) }

        api.setAssetsArchived(ArchiveByIdsRequest(listOf("asset-1"), true))
        api.setAssetsRating(RatingByIdsRequest(listOf("asset-1"), null))
        api.setAssetsPrivate(PrivateByIdsRequest(listOf("asset-1"), true))
        api.trashAssets(AssetIdsRequest(listOf("asset-1")))

        server.takeRequest().also {
            assertEquals("/api/v1/assets/archive", it.url.encodedPath)
            assertEquals("""{"assetIds":["asset-1"],"archived":true}""", it.body!!.utf8())
        }
        server.takeRequest().also {
            assertEquals("/api/v1/assets/rating", it.url.encodedPath)
            assertEquals("""{"assetIds":["asset-1"],"rating":null}""", it.body!!.utf8())
        }
        server.takeRequest().also {
            assertEquals("/api/v1/assets/private", it.url.encodedPath)
            assertEquals("""{"assetIds":["asset-1"],"private":true}""", it.body!!.utf8())
        }
        server.takeRequest().also {
            assertEquals("/api/v1/assets/trash", it.url.encodedPath)
            assertEquals("""{"assetIds":["asset-1"]}""", it.body!!.utf8())
        }
    }

    @Test
    fun privateUnlockAndLockUseSessionEndpoints() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        assertEquals(204, api.unlockPrivateAccess(PrivateAccessRequest("secret")).code())
        assertEquals(204, api.lockPrivateAccess().code())

        server.takeRequest().also {
            assertEquals("POST", it.method)
            assertEquals("/api/v1/auth/private-access", it.url.encodedPath)
            assertEquals("""{"password":"secret"}""", it.body!!.utf8())
        }
        server.takeRequest().also {
            assertEquals("DELETE", it.method)
            assertEquals("/api/v1/auth/private-access", it.url.encodedPath)
        }
    }

    @Test
    fun trashListRestoreAndPurgeKeepSeparateSemantics() = runTest {
        server.enqueue(jsonResponse("""{"items":[${trashedAssetJson()}],"nextCursor":null}"""))
        server.enqueue(batchResponse())
        server.enqueue(batchResponse())

        val asset = api.getTrash().body()!!.items.single()
        assertEquals(30, asset.retentionDays)
        assertEquals("2026-08-31T10:00:00Z", asset.trashedAt)
        assertNull(asset.rating)
        api.restoreAssets(AssetIdsRequest(listOf("asset-1")))
        api.purgeTrash(AssetIdsRequest(listOf("asset-1")))

        assertEquals("/api/v1/trash", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/assets/restore", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/trash/purge", server.takeRequest().url.encodedPath)
    }

    private fun batchResponse() = jsonResponse("""{"succeeded":["asset-1"],"failed":[]}""")

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}

internal fun trashedAssetJson(): String =
    """{
      "id":"asset-1",
      "userId":"user-1",
      "kind":"PHOTO",
      "state":"TRASHED",
      "takenAt":"2026-08-29T09:41:00+08:00",
      "takenAtOffsetMinutes":480,
      "takenAtSource":"EXIF",
      "importedAt":"2026-08-29T10:00:00+08:00",
      "libraryId":"library-a",
      "relativePath":"2026/08/coast.jpg",
      "fileName":"coast.jpg",
      "fileSize":2048000,
      "contentHash":"sha256-version",
      "width":4032,
      "height":3024,
      "favorite":false,
      "archived":false,
      "private":false,
      "rating":null,
      "title":null,
      "description":null,
      "gpsSource":null,
      "motionPhoto":null,
      "activeEdit":null,
      "trashedAt":"2026-08-31T10:00:00Z",
      "retentionDays":30,
      "tags":[]
    }""".trimIndent()
