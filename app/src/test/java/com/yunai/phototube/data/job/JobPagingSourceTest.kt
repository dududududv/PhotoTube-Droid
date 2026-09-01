package com.yunai.phototube.data.job

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

class JobPagingSourceTest {
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
    fun cursorAndFiltersArePassedUnchanged() = runTest {
        server.enqueue(jsonResponse("""{"items":[${jobJson()}],"nextCursor":null}"""))

        val result = JobPagingSource(
            api = api,
            moshi = moshi,
            filter = JobFilter(state = JobState.RUNNING, kind = JobKind.ALBUM_PATH_SYNC),
        ).load(
            PagingSource.LoadParams.Append(
                key = "opaque+/=job-cursor",
                loadSize = 100,
                placeholdersEnabled = false,
            ),
        ) as PagingSource.LoadResult.Page

        assertEquals("job-1", result.data.single().id)
        server.takeRequest().url.also { url ->
            assertEquals("opaque+/=job-cursor", url.queryParameter("cursor"))
            assertEquals("RUNNING", url.queryParameter("state"))
            assertEquals("ALBUM_PATH_SYNC", url.queryParameter("kind"))
        }
    }

    @Test
    fun invalidCursorInvalidatesGeneration() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .addHeader("Content-Type", "application/json")
                .body(
                    """{"code":"INVALID_CURSOR","message":"任务游标已失效","retryable":true,"logId":"log-job"}""",
                )
                .build(),
        )

        val result = JobPagingSource(api, moshi, JobFilter()).load(
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

internal fun jobJson(
    kind: String = "ALBUM_PATH_SYNC",
    state: String = "RUNNING",
): String =
    """{
      "id":"job-1",
      "kind":"$kind",
      "state":"$state",
      "priority":3,
      "attempts":1,
      "maxAttempts":3,
      "runAfter":"2026-09-01T00:00:00Z",
      "parentJobId":null,
      "error":null,
      "startedAt":"2026-09-01T00:01:00Z",
      "finishedAt":null,
      "createdAt":"2026-09-01T00:00:00Z"
    }""".trimIndent()
