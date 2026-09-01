package com.yunai.phototube.data.memory

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

class MemoryApiContractTest {
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
    fun tearDown() = server.close()

    @Test
    fun listKeepsOpaqueCursorAndCanReadExistingPersonRules() = runTest {
        server.enqueue(jsonResponse("""{"items":[${personRuleJson()}],"nextCursor":"next-memory"}"""))

        val page = api.getMemoryExclusions(limit = 37, cursor = "opaque+/=memory").body()!!

        assertEquals(MemoryExclusionKind.PERSON, page.items.single().kind)
        assertEquals("next-memory", page.nextCursor)
        server.takeRequest().also { request ->
            assertEquals("GET", request.method)
            assertEquals("/api/v1/memory-exclusions", request.url.encodedPath)
            assertEquals("37", request.url.queryParameter("limit"))
            assertEquals("opaque+/=memory", request.url.queryParameter("cursor"))
        }
    }

    @Test
    fun createSendsDateRangeOnlyAndAcceptsIdempotentCreatedResponse() = runTest {
        server.enqueue(jsonResponse(dateRuleJson(), code = 201))

        val created = api.createMemoryExclusion(
            CreateDateMemoryExclusionRequest(
                dateFrom = "2026-03-01",
                dateTo = "2026-03-31",
            ),
        ).body()!!

        assertEquals(MemoryExclusionKind.DATE_RANGE, created.kind)
        server.takeRequest().also { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/memory-exclusions", request.url.encodedPath)
            assertEquals(
                """{"kind":"DATE_RANGE","dateFrom":"2026-03-01","dateTo":"2026-03-31"}""",
                request.body!!.utf8(),
            )
        }
    }

    @Test
    fun deleteUsesUuidPathAndHasNoBody() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        api.deleteMemoryExclusion(DATE_RULE_ID)

        server.takeRequest().also { request ->
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/memory-exclusions/$DATE_RULE_ID", request.url.encodedPath)
            assertEquals(0L, request.bodySize)
            assertNull(request.url.query)
        }
    }

    private fun jsonResponse(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()
}

internal const val DATE_RULE_ID = "11111111-1111-4111-8111-111111111111"

internal fun dateRuleJson() = """{
  "id":"$DATE_RULE_ID",
  "kind":"DATE_RANGE",
  "dateFrom":"2026-03-01",
  "dateTo":"2026-03-31",
  "personId":null,
  "createdAt":"2026-09-01T08:00:00Z"
}""".trimIndent()

private fun personRuleJson() = """{
  "id":"22222222-2222-4222-8222-222222222222",
  "kind":"PERSON",
  "dateFrom":null,
  "dateTo":null,
  "personId":"person-42",
  "createdAt":"2026-09-01T08:01:00Z"
}""".trimIndent()
