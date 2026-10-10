package com.yunai.phototube

import com.yunai.phototube.data.photoTags
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic JSON only; no server, session or media access. */
class PhotoTagParsingTest {
    @Test fun missingOrNullSourceKeepsNamesWithoutInventingProvenance() {
        val tags = photoTags(JSONObject("""{"tags":[
            {"id":1,"name":"风景"},
            {"id":2,"name":"湖畔","source":null},
            {"id":3,"name":"森林","source":"AI"},
            {"id":9007199254740993,"name":"花园","source":"RULE"}
        ]}"""))
        assertEquals(listOf("风景", "湖畔", "森林", "花园"), tags.map { it.name })
        assertEquals(listOf("", "", "AI", "RULE"), tags.map { it.source })
        assertEquals(9007199254740993L, tags.last().id)
    }

    @Test fun malformedEntriesDoNotHideValidTagsOrCrashTheViewer() {
        val tags = photoTags(JSONObject("""{"tags":[null,42,{},
            {"id":-1,"name":"无效"},{"id":1,"name":null},
            {"id":2,"name":" "},{"id":"invalid","name":"无效"},
            {"id":3,"name":"风景","source":42}
        ]}"""))
        assertEquals(1, tags.size)
        assertEquals("风景", tags.single().name)
        assertEquals("", tags.single().source)
    }

    @Test fun absentTagListsAreEmpty() {
        listOf(null, JSONObject(), JSONObject("""{"tags":null}"""),
            JSONObject("""{"tags":{}}""")).forEach { assertTrue(photoTags(it).isEmpty()) }
    }
}
