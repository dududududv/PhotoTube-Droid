package com.yunai.phototube.data.album

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

class AlbumApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PhotoTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder()
            .add(UpdateAlbumRequestJsonAdapter)
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
    fun creatingPathAlbumOnlyPersistsConfiguration() = runTest {
        server.enqueue(jsonResponse(pathAlbumJson(), code = 201))

        val album = api.createAlbum(
            CreateAlbumRequest(
                name = "家庭照片",
                kind = AlbumKind.PATH_SYNC,
                paths = listOf(AlbumPathInput("library-1", "Photos/Family")),
            ),
        ).body()!!

        assertEquals(AlbumKind.PATH_SYNC, album.kind)
        val request = server.takeRequest()
        assertEquals("/api/v1/albums", request.url.encodedPath)
        assertTrue(request.body!!.utf8().contains("\"paths\""))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun previewAndApplySendTheSameRequestBeforeExplicitSync() = runTest {
        server.enqueue(
            jsonResponse(
                """{"configGeneration":4,"action":"ADD","affectedPathId":null,"currentCoveredAssets":0,"membersToRemove":0,"membersRetainedByOtherPaths":0,"requiresSync":true}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"configGeneration":5,"action":"ADD","path":${albumPathJson()},"syncRunId":null}""",
                code = 202,
            ),
        )
        server.enqueue(jsonResponse(syncRunJson(), code = 202))
        val change = AlbumPathChangeRequest.add(
            generation = 4,
            path = AlbumPathInput("library-1", "Photos/Family"),
        )

        assertTrue(api.previewAlbumPathChange("album-path", change).body()!!.requiresSync)
        assertEquals(5L, api.applyAlbumPathChange("album-path", change).body()!!.configGeneration)
        assertEquals("run-1", api.triggerAlbumPathSync("album-path").body()!!.id)

        val previewRequest = server.takeRequest()
        val applyRequest = server.takeRequest()
        val syncRequest = server.takeRequest()
        assertEquals("/api/v1/albums/album-path/path-change-previews", previewRequest.url.encodedPath)
        assertEquals("/api/v1/albums/album-path/path-changes", applyRequest.url.encodedPath)
        assertEquals(previewRequest.body!!.utf8(), applyRequest.body!!.utf8())
        assertEquals("/api/v1/albums/album-path/syncs", syncRequest.url.encodedPath)
        assertEquals("POST", syncRequest.method)
    }

    @Test
    fun memberRemovalUsesDeleteWithJsonBody() = runTest {
        server.enqueue(jsonResponse("""{"succeeded":["asset-1"],"failed":[]}"""))

        api.removeAlbumAssets("album-1", AlbumAssetsRequest(listOf("asset-1")))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/albums/album-1/assets", request.url.encodedPath)
        assertEquals("""{"assetIds":["asset-1"]}""", request.body!!.utf8())
    }

    @Test
    fun settingsUpdateSendsOnlyNameAndSortMode() = runTest {
        server.enqueue(jsonResponse(normalAlbumJson()))

        api.updateAlbum(
            "album-1",
            UpdateAlbumRequest.settings("新的旅行", AlbumSortMode.TAKEN_AT_ASC),
        )

        server.takeRequest().also { request ->
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/albums/album-1", request.url.encodedPath)
            assertEquals(
                """{"name":"新的旅行","sortMode":"TAKEN_AT_ASC"}""",
                request.body!!.utf8(),
            )
        }
    }

    @Test
    fun memberCoverUpdateSendsCenteredFocalPoint() = runTest {
        server.enqueue(jsonResponse(normalAlbumJson()))

        api.updateAlbum("album-1", UpdateAlbumRequest.setCover("asset-1"))

        assertEquals(
            """{"coverAssetId":"asset-1","coverFocalPoint":{"x":0.5,"y":0.5}}""",
            server.takeRequest().body!!.utf8(),
        )
    }

    @Test
    fun clearCoverSendsExplicitNullsInsteadOfEmptyPatch() = runTest {
        server.enqueue(jsonResponse(normalAlbumJson()))

        api.updateAlbum("album-1", UpdateAlbumRequest.clearCover())

        assertEquals(
            """{"coverAssetId":null,"coverFocalPoint":null}""",
            server.takeRequest().body!!.utf8(),
        )
    }

    @Test
    fun sourceFolderBrowsingUsesOnlyRelativePath() = runTest {
        server.enqueue(
            jsonResponse(
                """{"libraryId":"library-1","path":"Photos","items":[{"name":"Family","path":"Photos/Family"}]}""",
            ),
        )

        val listing = api.getSourceFolders("Photos").body()!!

        assertEquals("Photos/Family", listing.items.single().path)
        val request = server.takeRequest()
        assertEquals("/api/v1/source-folders", request.url.encodedPath)
        assertEquals("Photos", request.url.queryParameter("path"))
    }

    @Test
    fun pathSyncHistoryAndDetailUseSeparateDocumentedGetRoutes() = runTest {
        server.enqueue(
            jsonResponse(
                """{"items":[${syncRunJson()}],"nextCursor":"opaque-next"}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"run":${syncRunJson()},"pathResults":[${syncPathResultJson()}]}""",
            ),
        )

        val page = api.getAlbumPathSyncRuns(
            albumId = "album-path",
            limit = 100,
            cursor = "opaque-current",
        ).body()!!
        val detail = api.getAlbumPathSyncRun("album-path", "run-1").body()!!

        assertEquals("opaque-next", page.nextCursor)
        assertEquals(7L, detail.pathResults.single().discoveredCount)
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/v1/albums/album-path/syncs", request.url.encodedPath)
            assertEquals("opaque-current", request.url.queryParameter("cursor"))
            assertEquals("100", request.url.queryParameter("limit"))
        }
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/v1/albums/album-path/syncs/run-1", request.url.encodedPath)
        }
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun pathAlbumJson(): String = normalAlbumJson()
        .replace("\"id\":\"album-1\"", "\"id\":\"album-path\"")
        .replace("\"name\":\"旅行\"", "\"name\":\"家庭照片\"")
        .replace("\"kind\":\"NORMAL\"", "\"kind\":\"PATH_SYNC\"")
        .replace(
            "\"pathSync\":null",
            "\"pathSync\":{\"configGeneration\":4,\"totalPathCount\":1,\"enabledPathCount\":1,\"lastRunId\":null,\"lastRunState\":null,\"lastRunAt\":null}",
        )

    private fun albumPathJson(): String =
        """{"id":"path-1","albumId":"album-path","libraryId":"library-1","relativePath":"Photos/Family","enabled":true,"createdAt":"2026-08-31T10:00:00Z","updatedAt":"2026-08-31T10:00:00Z","lastSuccessfulSyncAt":null,"lastRunState":null}"""

    private fun syncPathResultJson(): String =
        """{"pathId":"path-1","libraryId":"library-1","relativePath":"Photos/Family","state":"SUCCEEDED","discoveredCount":7,"reusedCount":5,"addedCount":2,"removedCount":0,"skippedCount":0,"startedAt":"2026-08-31T10:00:01Z","finishedAt":"2026-08-31T10:00:02Z","error":null}"""
}
