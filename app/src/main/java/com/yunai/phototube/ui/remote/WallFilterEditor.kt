package com.yunai.phototube.ui.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.SettingsNavigationRow
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.theme.AppTheme

/** A local draft: browsing pickers or dismissing never changes the current query. */
@Composable
internal fun WallFilterEditor(server:PhotoTubeServer,initial:WallFilters,onApply:(WallFilters)->Unit,onClose:()->Unit) {
    var draft by remember {mutableStateOf(SmartFilterDraft(library=initial.libraryId,folder=initial.folderPath,
        takenFrom=initial.takenFrom,takenTo=initial.takenTo,all=initial.tagsAll,any=initial.tagsAny,exclude=initial.tagsExclude))}
    val catalog=remember(server) {FilterCatalog(server)}
    var folderOpen by remember {mutableStateOf(false)}
    var tagGroup by remember {mutableStateOf<String?>(null)}
    var dateField by remember {mutableStateOf<String?>(null)}
    val shortScreen = LocalConfiguration.current.screenHeightDp < 480
    val inlineActions = shortScreen || LocalDensity.current.fontScale > 1.3f
    val actions: @Composable () -> Unit = {
        draft.validationError()?.let {Text(it,color=AppTheme.colors.destructive)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {GlassSettingsAction("重置条件",{draft=SmartFilterDraft()})}
            Box(Modifier.weight(1f)) {GlassSettingsAction("应用筛选",{onApply(initial.copy(libraryId=draft.library,folderPath=draft.folder,takenFrom=draft.takenFrom,takenTo=draft.takenTo,
                tagsAll=draft.all,tagsAny=draft.any,tagsExclude=draft.exclude))},enabled=draft.validationError()==null,
                modifier=Modifier.testTag("apply-wall-filters"))}
        }
    }
    ManagementPage("组合筛选",onClose,scrollable=false,compactHeader=shortScreen) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("wall-filter-conditions"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("与当前媒体类型、收藏和文件名搜索同时生效。",color=AppTheme.colors.secondary)
        ManagementGroup("拍摄日期",compact=true) {
            listOf(Triple("from","开始日期",draft.takenFrom),Triple("to","结束日期",draft.takenTo)).forEach {(key,label,value) ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Box(Modifier.weight(1f)) {SettingsNavigationRow(label,{dateField=key},inlineValue=true,value=value?.let {taskTimestamp(it).substringBefore(' ')} ?: "不限")}
                    if(value!=null) Box(Modifier.width(72.dp)) {GlassSettingsAction("清除",{draft=if(key=="from") draft.copy(takenFrom=null) else draft.copy(takenTo=null)})}
                }
            }
            Text("按本地时区选择，包含开始日和结束日。",color=AppTheme.colors.secondary)
        }
        ManagementGroup("目录",compact=true) {
            SettingsNavigationRow("选择目录",{folderOpen=true},modifier=Modifier.testTag("wall-filter-folder"),inlineValue=true,value=draft.folder ?: if(draft.library!=null) "整个媒体库" else "所有已入库目录")
            if(draft.library!=null) GlassSettingsAction("清除目录条件",{draft=draft.copy(library=null,folder=null)})
            Text("包含所选目录的子目录。",color=AppTheme.colors.secondary)
        }
        ManagementGroup("标签组合",compact=true) {
            listOf(Triple("all","全部包含",draft.all),Triple("any","任一包含",draft.any),Triple("exclude","排除",draft.exclude)).forEach {(key,label,ids) ->
                SettingsNavigationRow(label,{tagGroup=key},modifier=Modifier.testTag("wall-filter-tags-$key"),inlineValue=true,value=if(ids.isEmpty()) "不限" else "${ids.size} 个标签")
            }
            Text("三组条件同时生效，每组最多 100 个标签。",color=AppTheme.colors.secondary)
        }
        if(inlineActions) actions()
        }
        if(!inlineActions) actions()
    }
    if(folderOpen) IndexedFolderPicker(catalog,onClose={folderOpen=false}) {
        draft=draft.copy(library=it.libraryId,folder=it.path.ifEmpty {null});folderOpen=false
    }
    tagGroup?.let {key -> TagConditionPicker(catalog,when(key) {"all"->draft.all;"any"->draft.any;else->draft.exclude},onClose={tagGroup=null}) {
        draft=when(key) {"all"->draft.copy(all=it);"any"->draft.copy(any=it);else->draft.copy(exclude=it)};tagGroup=null
    } }
    dateField?.let {key -> FilterDatePicker(if(key=="from") draft.takenFrom else draft.takenTo,key=="to",onClose={dateField=null}) {
        draft=if(key=="from") draft.copy(takenFrom=it) else draft.copy(takenTo=it);dateField=null
    } }
}
