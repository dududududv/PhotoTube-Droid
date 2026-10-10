package com.yunai.phototube.data

internal enum class WallKind(val label:String) { PHOTO("照片"), VIDEO("视频") }
internal data class WallFilters(val kind:WallKind?=null,val favorite:Boolean?=null,val archived:Boolean=false,val keyword:String="",
    val libraryId:String?=null,val folderPath:String?=null,val takenFrom:String?=null,val takenTo:String?=null,
    val tagsAll:Set<Long> = emptySet(),val tagsAny:Set<Long> = emptySet(),val tagsExclude:Set<Long> = emptySet(),val privateOnly:Boolean=false) {
    init {require(keyword.isEmpty() || (keyword==keyword.trim() && keyword.length in 3..255)) {"文件名关键词须为 3～255 个字符"}}
    val hasScope get()=libraryId!=null || takenFrom!=null || takenTo!=null || tagsAll.isNotEmpty() || tagsAny.isNotEmpty() || tagsExclude.isNotEmpty()
    val active get()=kind!=null || favorite!=null || archived || hasScope
    val label get()=listOfNotNull(kind?.label,when(favorite) {true->"收藏";false->"未收藏";null->null},if(archived) "归档" else null,if(libraryId!=null) "指定目录" else null,if(takenFrom!=null || takenTo!=null) "日期范围" else null,if(tagsAll.isNotEmpty() || tagsAny.isNotEmpty() || tagsExclude.isNotEmpty()) "标签组合" else null).joinToString(" · ")
    /** JSON body keeps booleans and 64-bit tag IDs typed, unlike URL query values. */
    fun json():org.json.JSONObject {
        val result=org.json.JSONObject()
        query().forEach {(key,value) -> when(key) {
            "archived","favorite","private" -> result.put(key,value.toBooleanStrict())
            "tagsAll","tagsAny","tagsExclude" -> result.put(key,org.json.JSONArray(value.split(',').map(String::toLong)))
            else -> result.put(key,value)
        } }
        return result
    }
    fun query():Map<String,String> = buildMap {
        require(folderPath==null || libraryId!=null) {"目录条件需要媒体库"}
        require(listOf(tagsAll,tagsAny,tagsExclude).all {it.size<=100 && it.all {id -> id>0}})
        require(takenFrom==null || takenTo==null || java.time.OffsetDateTime.parse(takenFrom).toInstant()<=java.time.OffsetDateTime.parse(takenTo).toInstant()) {"结束日期不能早于开始日期"}
        libraryId?.let {put("libraryId",it)}
        folderPath?.takeIf {it.isNotEmpty()}?.let {put("folderPath",it)}
        takenFrom?.let {put("takenFrom",it)}
        takenTo?.let {put("takenTo",it)}
        listOf("tagsAll" to tagsAll,"tagsAny" to tagsAny,"tagsExclude" to tagsExclude).forEach {(key,ids) -> if(ids.isNotEmpty()) put(key,ids.sorted().joinToString(","))}
        if(keyword.isNotEmpty()) put("keyword",keyword)
        if(privateOnly) put("private","true") else put("archived",archived.toString())
        kind?.let {put("kind",it.name)}
        favorite?.let {put("favorite",it.toString())}
    }
}
