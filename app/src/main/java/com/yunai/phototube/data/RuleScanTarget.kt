package com.yunai.phototube.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.security.MessageDigest

internal sealed interface RuleScanTarget {
    val label: String
    fun json(): JSONObject
    val bookmark: String get() = "scope/" + MessageDigest.getInstance("SHA-256").digest(json().toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    data class Album(val id: String, override val label: String) : RuleScanTarget {
        override val bookmark get() = id // Preserve existing album scan history bookmarks.
        override fun json() = JSONObject().put("type", "ALBUM").put("albumId", id)
    }
    data class Directory(val library: String, val path: String, val name: String) : RuleScanTarget {
        init { require(library.isNotBlank() && path.isNotEmpty() && path.length <= 4096 && !path.startsWith('/') && !path.contains("..")) }
        override val label get() = "目录：$name\n$path（包含子目录）"
        override fun json() = JSONObject().put("type", "DIRECTORY").put("libraryId", library).put("directoryPath", path)
    }
    data class TimeRange(val from: String, val to: String) : RuleScanTarget {
        init { require(!OffsetDateTime.parse(from).toInstant().isAfter(OffsetDateTime.parse(to).toInstant())) }
        override val label get() = "拍摄时间：$from 至 $to"
        override fun json() = JSONObject().put("type", "TIME_RANGE").put("takenFrom", from).put("takenTo", to)
    }
    class Selection(ids: Set<String>) : RuleScanTarget {
        val ids = ids.toSortedSet().toSet()
        init { require(this.ids.size in 1..500 && this.ids.all { it.isNotBlank() }) }
        override val label get() = "选中的 ${ids.size} 项内容"
        override fun json() = JSONObject().put("type", "SELECTION").put("assetIds", JSONArray(ids.toList()))
    }
}

internal fun ruleScanBookmark(target: RuleScanTarget, rules: Set<String>): String {
    require(rules.size<=100)
    if(rules.isEmpty()) return target.bookmark
    val hash=MessageDigest.getInstance("SHA-256").digest(JSONArray(rules.sorted()).toString().toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
    return target.bookmark+"/rules/"+hash
}
