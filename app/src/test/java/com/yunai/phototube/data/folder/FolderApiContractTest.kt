package com.yunai.phototube.data.folder

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.yunai.phototube.data.remote.PhotoTubeApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class FolderApiContractTest {
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
    fun rootAndDirectoryBrowsingKeepCursorAndRelativeScope() = runTest {
        server.enqueue(jsonResponse(rootJson()))
        server.enqueue(jsonResponse(directoryJson()))

        val root = api.getFolders(path = "", limit = 100).body()!!
        val directory = api.getFolders(
            libraryId = "library-1",
            path = "旅行",
            limit = 100,
            cursor = "opaque-folder-cursor",
        ).body()!!

        assertEquals(FolderKind.LIBRARY, root.children.items.single().kind)
        assertEquals("旅行/上海", directory.children.items.single().path)
        assertEquals("next-folder-cursor", directory.children.nextCursor)

        server.takeRequest().also { request ->
            assertEquals("/api/v1/folders", request.url.encodedPath)
            assertEquals("", request.url.queryParameter("path"))
            assertEquals("100", request.url.queryParameter("limit"))
        }
        server.takeRequest().also { request ->
            assertEquals("library-1", request.url.queryParameter("libraryId"))
            assertEquals("旅行", request.url.queryParameter("path"))
            assertEquals("opaque-folder-cursor", request.url.queryParameter("cursor"))
        }
    }

    private fun jsonResponse(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun rootJson() = """
        {
          "current":{"kind":"ROOT","libraryId":null,"name":"全部媒体库","path":"","online":true,"directAssetCount":0,"hasChildren":true},
          "children":{"items":[{"kind":"LIBRARY","libraryId":"library-1","name":"照片库","path":"","online":true,"directAssetCount":12,"hasChildren":true}],"nextCursor":null}
        }
    """.trimIndent()

    private fun directoryJson() = """
        {
          "current":{"kind":"DIRECTORY","libraryId":"library-1","name":"旅行","path":"旅行","online":true,"directAssetCount":5,"hasChildren":true},
          "children":{"items":[{"kind":"DIRECTORY","libraryId":"library-1","name":"上海","path":"旅行/上海","online":true,"directAssetCount":3,"hasChildren":false}],"nextCursor":"next-folder-cursor"}
        }
    """.trimIndent()
}
