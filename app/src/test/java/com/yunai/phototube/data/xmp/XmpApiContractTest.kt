package com.yunai.phototube.data.xmp

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class XmpApiContractTest {
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
    fun healthExposesUnavailableXmpWithoutDegradingCoreStatus() = runTest {
        server.enqueue(
            jsonResponse(
                """{"status":"ok","version":"1.0","database":{"reachable":true,"migrationVersion":38,"dirty":false},"aiWorker":null,"xmpExport":{"available":false}}""",
            ),
        )

        val health = api.getHealth().body()!!

        assertEquals("ok", health.status)
        assertFalse(health.xmpExport.available)
        assertEquals("/api/v1/health", server.takeRequest().url.encodedPath)
    }

    @Test
    fun previewAllOmitsLibraryIdsAndNeverOmitsIncludePrivate() = runTest {
        server.enqueue(
            jsonResponse(
                """{"resolvedLibraryIds":["library-1"],"includePrivate":false,"estimatedAssetCount":12,"estimatedAt":"2026-09-01T00:00:00Z"}""",
            ),
        )

        val preview = api.previewXmpExport(XmpExportRequest(includePrivate = false)).body()!!

        assertEquals(12L, preview.estimatedAssetCount)
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/xmp-exports/preview", request.url.encodedPath)
            assertEquals("""{"includePrivate":false}""", request.body!!.utf8())
        }
    }

    @Test
    fun createUsesResolvedScopeAndListAndDetailExposeOnlySummaries() = runTest {
        server.enqueue(jsonResponse(runJson(state = "PENDING"), code = 202))
        server.enqueue(jsonResponse("""{"items":[${runJson()}],"nextCursor":"opaque-xmp-cursor"}"""))
        server.enqueue(jsonResponse(runJson()))
        val request = XmpExportRequest(libraryIds = listOf("library-1"), includePrivate = true)

        val created = api.createXmpExport(request).body()!!
        val page = api.getXmpExportRuns(limit = 20).body()!!
        val detail = api.getXmpExportRun("run-1").body()!!

        assertEquals(XmpExportRunState.PENDING, created.state)
        assertEquals("opaque-xmp-cursor", page.nextCursor)
        assertEquals("xmp/snapshot-1", detail.logicalPath)
        assertNull(detail.errorCode)
        server.takeRequest().also { recorded ->
            assertEquals("/api/v1/xmp-exports", recorded.url.encodedPath)
            assertEquals("""{"libraryIds":["library-1"],"includePrivate":true}""", recorded.body!!.utf8())
        }
        server.takeRequest().url.also { url ->
            assertEquals("/api/v1/xmp-exports", url.encodedPath)
            assertEquals("20", url.queryParameter("limit"))
            assertNull(url.queryParameter("cursor"))
        }
        assertEquals("/api/v1/xmp-exports/run-1", server.takeRequest().url.encodedPath)
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}

internal fun runJson(state: String = "SUCCEEDED"): String =
    """{
      "id":"run-1",
      "state":"$state",
      "libraryIds":["library-1"],
      "includePrivate":true,
      "authorizedAt":"2026-09-01T00:00:00Z",
      "matchedCount":12,
      "succeededCount":12,
      "failedCount":0,
      "attemptCount":1,
      "snapshotId":"snapshot-1",
      "snapshotAt":"2026-09-01T00:00:01Z",
      "logicalPath":"xmp/snapshot-1",
      "factsSha256":"${"a".repeat(64)}",
      "manifestSha256":"${"b".repeat(64)}",
      "errorCode":null,
      "createdAt":"2026-09-01T00:00:00Z",
      "startedAt":"2026-09-01T00:00:00Z",
      "finishedAt":"2026-09-01T00:00:01Z"
    }""".trimIndent()
