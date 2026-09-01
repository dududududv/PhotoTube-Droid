package com.yunai.phototube.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import com.yunai.phototube.data.tag.AssetTagRequest
import com.yunai.phototube.data.tag.CreateTagRequest
import com.yunai.phototube.data.timeline.AssetFilter
import com.yunai.phototube.data.timeline.AssetKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PhotoTubeApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PhotoTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val cookieJar = InMemoryCookieJar()
        val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PhotoTubeApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun loginCookieIsSentToSessionProbe() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(204)
                .addHeader(
                    "Set-Cookie",
                    "phototube_session=opaque; Path=/; HttpOnly; Max-Age=2592000",
                )
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"authenticated":true,"passwordSet":true,"privateAccessUnlocked":false,"user":{"id":"00000000-0000-4000-8000-000000000001","username":"admin","displayName":"管理员"}}""",
                )
                .build(),
        )

        assertEquals(204, api.login(LoginRequest("admin", "password")).code())
        assertTrue(api.getSession().body()!!.authenticated)

        assertEquals("/api/v1/auth/login", server.takeRequest().url.encodedPath)
        val sessionRequest = server.takeRequest()
        assertEquals("/api/v1/auth/session", sessionRequest.url.encodedPath)
        assertTrue(sessionRequest.headers["Cookie"].orEmpty().contains("phototube_session=opaque"))
    }

    @Test
    fun assetDetailAndFavoriteUseDocumentedContracts() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(assetDetailJson())
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"succeeded":["asset-1"],"failed":[]}""")
                .build(),
        )

        val asset = api.getAsset("asset-1").body()!!
        assertEquals("OPPO_MOTION_PHOTO_V2", asset.motionPhoto?.format)
        assertEquals(12L, asset.tags?.single()?.id)

        val favoriteResult = api.setAssetsFavorite(
            FavoriteByIdsRequest(assetIds = listOf("asset-1"), favorite = true),
        ).body()!!
        assertEquals(listOf("asset-1"), favoriteResult.succeeded)

        assertEquals("/api/v1/assets/asset-1", server.takeRequest().url.encodedPath)
        val favoriteRequest = server.takeRequest()
        assertEquals("/api/v1/assets/favorite", favoriteRequest.url.encodedPath)
        assertEquals(
            """{"assetIds":["asset-1"],"favorite":true}""",
            favoriteRequest.body!!.utf8(),
        )
    }

    @Test
    fun tagEndpointsKeepInt64IdsAndDocumentedPaths() = runTest {
        val longTagId = 9_000_000_000L
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"items":[{"id":$longTagId,"name":"海边","assetCount":8,"createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z"}],"nextCursor":"opaque-tag-cursor"}""",
                )
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(201)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"id":$longTagId,"name":"海边","assetCount":0,"createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-08-01T00:00:00Z"}""",
                )
                .build(),
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"id":$longTagId,"name":"海边","source":"MANUAL","confidence":null,"confirmed":true}""",
                )
                .build(),
        )
        server.enqueue(MockResponse.Builder().code(204).build())

        val page = api.getTags(limit = 100, keyword = "海边").body()!!
        assertEquals(longTagId, page.items.single().id)
        assertEquals("opaque-tag-cursor", page.nextCursor)
        assertEquals(longTagId, api.createTag(CreateTagRequest("海边")).body()!!.id)
        assertEquals(
            longTagId,
            api.addAssetTag("asset-1", AssetTagRequest(longTagId)).body()!!.id,
        )
        assertEquals(204, api.removeAssetTag("asset-1", longTagId).code())

        val listRequest = server.takeRequest()
        assertEquals("/api/v1/tags", listRequest.url.encodedPath)
        assertEquals("海边", listRequest.url.queryParameter("keyword"))
        assertEquals("100", listRequest.url.queryParameter("limit"))
        assertEquals("""{"name":"海边"}""", server.takeRequest().body!!.utf8())
        val addRequest = server.takeRequest()
        assertEquals("/api/v1/assets/asset-1/tags", addRequest.url.encodedPath)
        assertEquals("""{"tagId":$longTagId}""", addRequest.body!!.utf8())
        assertEquals(
            "/api/v1/assets/asset-1/tags/$longTagId",
            server.takeRequest().url.encodedPath,
        )
    }

    @Test
    fun searchSummaryReceivesTheSameLiteralFilterSet() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("""{"granularity":"DAY","groups":[],"totalCount":0}""")
                .build(),
        )
        val filter = AssetFilter(
            keyword = "Coast_100%",
            kind = AssetKind.PHOTO,
            favorite = true,
            rating = 5,
        )

        api.getTimelineSummary("DAY", filter.toQueryMap())

        server.takeRequest().url.also { url ->
            assertEquals("/api/v1/assets/timeline-summary", url.encodedPath)
            assertEquals("DAY", url.queryParameter("granularity"))
            assertEquals("Coast_100%", url.queryParameter("keyword"))
            assertEquals("PHOTO", url.queryParameter("kind"))
            assertEquals("true", url.queryParameter("favorite"))
            assertEquals("5", url.queryParameter("rating"))
            assertEquals("false", url.queryParameter("private"))
        }
    }

    private fun assetDetailJson(): String =
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
          "gpsSource":"EXIF",
          "motionPhoto":{
            "format":"OPPO_MOTION_PHOTO_V2",
            "videoMime":"video/mp4",
            "width":1920,
            "height":1080,
            "durationSec":2.4
          },
          "activeEdit":null,
          "tags":[{
            "id":12,
            "name":"海边",
            "source":"MANUAL",
            "confidence":null,
            "confirmed":true
          }]
        }""".trimIndent()

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.clear()
            this.cookies += cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
    }
}
