package com.yunai.phototube.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.yunai.phototube.data.ConnectionStage
import com.yunai.phototube.data.PhotoWallViewModel
import kotlinx.coroutines.delay

/** Only valid filename queries reach the server; hide old results during debounce. */
@Composable
internal fun librarySearchMessage(wall:PhotoWallViewModel,input:String,enabled:Boolean):String? {
    val keyword=input.trim()
    val valid=keyword.isEmpty() || keyword.length in 3..255
    LaunchedEffect(enabled,keyword,wall.stage) {
        if(enabled && valid && wall.stage==ConnectionStage.READY && keyword!=wall.filters.keyword) {
            if(keyword.isNotEmpty()) delay(300)
            wall.updateFilters(wall.filters.copy(keyword=keyword))
        }
    }
    if(!enabled || wall.stage!=ConnectionStage.READY) return null
    return when {
        !valid -> "请输入至少 3 个字符，按文件名搜索"
        keyword!=wall.filters.keyword -> "正在搜索…"
        else -> null
    }
}
