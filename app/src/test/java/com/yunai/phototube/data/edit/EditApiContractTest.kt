package com.yunai.phototube.data.edit

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.PhotoTubeApi
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

class EditApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PhotoTubeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder()
            .add(EditRequestJsonAdapter)
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
    fun historyAndCreateUseOpaqueCursorAndExplicitConcurrencyBaseline() = runTest {
        server.enqueue(jsonResponse("""{"items":[${editVersionJson()}],"nextCursor":"opaque-edit-cursor"}"""))
        server.enqueue(jsonResponse(editVersionJson(), code = 201))

        val page = api.getAssetEditVersions("asset-1", limit = 100).body()!!
        val created = api.createAssetEditVersion(
            "asset-1",
            CreateEditVersionRequest(
                expectedActiveEditVersionId = null,
                sourceContentHash = "a".repeat(64),
                transform = EditTransform(rotationDegrees = 90),
            ),
        ).body()!!

        assertEquals("opaque-edit-cursor", page.nextCursor)
        assertEquals(90, created.transform.rotationDegrees)
        server.takeRequest().also { request ->
            assertEquals("/api/v1/assets/asset-1/edit-versions", request.url.encodedPath)
            assertEquals("100", request.url.queryParameter("limit"))
            assertNull(request.url.queryParameter("cursor"))
        }
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/assets/asset-1/edit-versions", request.url.encodedPath)
            assertEquals(
                """{"expectedActiveEditVersionId":null,"sourceContentHash":"${"a".repeat(64)}","transform":{"cropXPPM":0,"cropYPPM":0,"cropWidthPPM":1000000,"cropHeightPPM":1000000,"rotationDegrees":90,"flipHorizontal":false,"flipVertical":false}}""",
                request.body!!.utf8(),
            )
        }
    }

    @Test
    fun selectingOriginalSendsBothNullableFieldsAndAcceptsJsonNull() = runTest {
        server.enqueue(jsonResponse("null"))

        val active = api.selectAssetEditVersion(
            "asset-1",
            SelectActiveEditRequest(
                expectedActiveEditVersionId = "edit-1",
                targetEditVersionId = null,
            ),
        ).body()

        assertNull(active)
        server.takeRequest().also { request ->
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/assets/asset-1/active-edit", request.url.encodedPath)
            assertEquals(
                """{"expectedActiveEditVersionId":"edit-1","targetEditVersionId":null}""",
                request.body!!.utf8(),
            )
        }
    }

    @Test
    fun assetDetailParsesAuthoritativeActiveEditSummary() = runTest {
        server.enqueue(
            jsonResponse(
                """{
                  "id":"asset-1","userId":"user-1","kind":"PHOTO","state":"BROWSABLE",
                  "takenAt":"2026-09-01T00:00:00Z","takenAtOffsetMinutes":null,"takenAtSource":"EXIF",
                  "importedAt":"2026-09-01T00:00:00Z","libraryId":"library-1","relativePath":"DCIM/photo.jpg",
                  "fileName":"photo.jpg","fileSize":10,"contentHash":"${"a".repeat(64)}",
                  "width":1200,"height":800,"favorite":false,"archived":false,"private":false,"rating":null,
                  "activeEdit":{"editVersionId":"edit-1","sourceState":"CURRENT","renderState":"READY","displayWidth":800,"displayHeight":1200},
                  "tags":[]
                }""".trimIndent(),
            ),
        )

        val active = api.getAsset("asset-1").body()!!.activeEdit!!

        assertEquals("edit-1", active.editVersionId)
        assertEquals(EditSourceState.CURRENT, active.sourceState)
        assertEquals(EditRenderState.READY, active.renderState)
        assertEquals(1200, active.displayHeight)
    }

    private fun editVersionJson(): String =
        """{
          "id":"edit-1",
          "assetId":"asset-1",
          "parentEditVersionId":null,
          "sourceContentHash":"${"a".repeat(64)}",
          "transform":{"cropXPPM":0,"cropYPPM":0,"cropWidthPPM":1000000,"cropHeightPPM":1000000,"rotationDegrees":90,"flipHorizontal":false,"flipVertical":false},
          "outputWidth":800,
          "outputHeight":1200,
          "active":true,
          "sourceState":"CURRENT",
          "renderState":"PENDING",
          "createdAt":"2026-09-01T00:00:00Z"
        }""".trimIndent()

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}
