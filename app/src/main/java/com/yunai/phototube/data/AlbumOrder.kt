package com.yunai.phototube.data

/** Display order only; never changes an album's member sortMode. */
internal enum class AlbumOrder(val label:String) {
    DEFAULT("默认顺序"), NAME("按名称"), COUNT("按项目数量")
}

/** There is no server-side album ordering parameter. Read all metadata before
 * sorting, so albums on later pages participate in the same order. */
internal suspend fun loadOrderedAlbums(order:AlbumOrder,fetch:suspend (String?)->AlbumPage<RemoteAlbum>):List<RemoteAlbum> {
    val albums=linkedMapOf<String,RemoteAlbum>()
    val cursors=mutableSetOf<String>()
    var cursor:String?=null
    do {
        val page=fetch(cursor)
        page.items.forEach {albums[it.id]=it}
        cursor=page.cursor
        check(cursor==null || cursors.add(cursor)) {"相册列表返回了重复游标"}
    } while(cursor!=null)
    val collator=java.text.Collator.getInstance(java.util.Locale.getDefault())
    val byName=Comparator<RemoteAlbum> {a,b -> collator.compare(a.name,b.name)}.thenBy {it.id}
    return when(order) {
        AlbumOrder.DEFAULT -> albums.values.toList()
        AlbumOrder.NAME -> albums.values.sortedWith(byName)
        AlbumOrder.COUNT -> albums.values.sortedWith(compareByDescending<RemoteAlbum> {it.count}.then(byName))
    }
}
