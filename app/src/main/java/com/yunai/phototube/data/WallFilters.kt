package com.yunai.phototube.data

internal enum class WallKind(val label:String) { PHOTO("照片"), VIDEO("视频") }
internal data class WallFilters(val kind:WallKind?=null,val favoritesOnly:Boolean=false,val archived:Boolean=false) {
    val active get()=kind!=null || favoritesOnly || archived
    val label get()=listOfNotNull(kind?.label,if(favoritesOnly) "收藏" else null,if(archived) "归档" else null).joinToString(" · ")
    fun query():Map<String,String> = buildMap {
        put("archived",archived.toString())
        kind?.let {put("kind",it.name)}
        if(favoritesOnly) put("favorite","true")
    }
}
