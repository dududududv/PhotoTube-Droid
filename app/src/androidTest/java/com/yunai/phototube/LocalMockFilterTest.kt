package com.yunai.phototube

import androidx.test.platform.app.InstrumentationRegistry
import com.yunai.phototube.data.LocalMockData
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Calls fixtures directly. No client, DNS, sockets or real service. */
class LocalMockFilterTest {
    private val fixture=LocalMockData(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun body(path:String="assets",vararg query:Pair<String,String>):JSONObject {
        val url="${LocalMockData.BASE}/api/v1/$path".toHttpUrl().newBuilder()
        query.forEach {(k,v)->url.addQueryParameter(k,v)}
        return fixture.respond(Request.Builder().url(url.build()).build()).use {JSONObject(it.body.string())}
    }
    private fun count(vararg query:Pair<String,String>)=body("assets",*query).getJSONArray("items").length()
    @Test fun filteringAndSummaryAgree() {
        assertEquals(24,count())
        assertEquals(1,count("kind" to "VIDEO"))
        assertEquals("VIDEO",body("assets","kind" to "VIDEO").getJSONArray("items").getJSONObject(0).getString("kind"))
        assertEquals(23,count("kind" to "PHOTO"))
        assertEquals(6,count("favorite" to "true"))
        assertEquals(0,count("favorite" to "true","kind" to "VIDEO"))
        assertEquals(2,count("archived" to "true"))
        assertEquals(0,count("private" to "true"))
        assertEquals(0,count("takenFrom" to "2026-09-02T00:00:00Z"))
        assertEquals(24,count("takenFrom" to "2026-09-01T10:00:00Z","takenTo" to "2026-09-01T10:00:00Z"))
        assertEquals(0,count("takenTo" to "2026-09-01T09:59:59Z"))
        assertEquals(24,count("libraryId" to "mock-library","folderPath" to "示例照片"))
        assertEquals(0,count("libraryId" to "other"))
        assertEquals(0,count("libraryId" to "mock-library","folderPath" to "示例"))
        assertEquals(24,count("tagsAll" to "1"))
        assertEquals(0,count("tagsAll" to "1,2"))
        assertEquals(24,count("tagsAny" to "1,2"))
        assertEquals(0,count("tagsExclude" to "1"))
        assertEquals(1,body("assets/timeline-summary","kind" to "VIDEO").getJSONArray("groups").getJSONObject(0).getInt("count"))
        assertEquals(0,body("assets/timeline-summary","tagsExclude" to "1").getJSONArray("groups").length())
        assertEquals(1,body("folders").getJSONObject("children").getJSONArray("items").length())
        assertEquals(0,body("tags","keyword" to "不存在").getJSONArray("items").length())
    }
}
