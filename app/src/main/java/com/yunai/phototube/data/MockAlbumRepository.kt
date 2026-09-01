package com.yunai.phototube.data

import com.yunai.phototube.R
import com.yunai.phototube.model.MemoryDay
import com.yunai.phototube.model.PhotoMemory
import com.yunai.phototube.model.SharedCollection

object MockAlbumRepository {
    val days = listOf(
        MemoryDay(
            date = "8月29日",
            relativeLabel = "今天",
            location = "上海",
            photos = listOf(
                PhotoMemory("sunset", R.drawable.photo_sunset, "海边日落与灯塔"),
                PhotoMemory("cafe", R.drawable.photo_cafe_portrait, "咖啡馆中的女孩"),
                PhotoMemory("cat", R.drawable.photo_cat, "窗边的白猫"),
                PhotoMemory("museum", R.drawable.photo_museum, "白色现代建筑"),
                PhotoMemory("mountain", R.drawable.photo_mountain, "山谷与湖泊"),
                PhotoMemory("city", R.drawable.photo_city, "上海夜景"),
                PhotoMemory("tart", R.drawable.photo_tart, "无花果奶油挞"),
            ),
        ),
        MemoryDay(
            date = "8月28日",
            relativeLabel = "昨天",
            location = "上海",
            photos = listOf(
                PhotoMemory("paris", R.drawable.photo_paris, "鸟居前的旅行伙伴"),
                PhotoMemory("sea", R.drawable.photo_sea, "富士山下的旅行伙伴"),
                PhotoMemory("bistro", R.drawable.photo_bistro, "寺庙长廊中的旅行伙伴"),
            ),
        ),
    )

    val collections = listOf(
        SharedCollection(
            id = "japan",
            title = "Japan group trip",
            date = "Jan 2, 2026",
            memoryCount = 50,
            coverRes = R.drawable.collection_japan,
            memberLabels = listOf("妍", "梨", "紫", "+4"),
        ),
        SharedCollection(
            id = "december",
            title = "Detty December 1",
            date = "Dec 20, 2025",
            memoryCount = 102,
            coverRes = R.drawable.collection_party,
            memberLabels = listOf("安", "悦", "宁", "+16"),
        ),
    )
}
