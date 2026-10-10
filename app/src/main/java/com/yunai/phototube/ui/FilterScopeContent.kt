package com.yunai.phototube.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.*
import androidx.paging.compose.*
import com.yunai.phototube.data.*
import com.yunai.phototube.ui.remote.filterDayInstant
import com.yunai.phototube.ui.theme.AppTheme
import java.time.*

internal fun filterScopeError(filters:WallFilters):String? =
    if(filters.takenFrom!=null && filters.takenTo!=null && Instant.parse(filters.takenFrom)>Instant.parse(filters.takenTo)) "结束日期不能早于开始日期" else null

private fun dateLabel(value:String?):String = value?.let {Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()} ?: "不限"

/** Every picker stays inside FilterMenu's surface; none opens a new window. */
@Composable
internal fun FilterScopeContent(page:FilterPage,draft:WallFilters,onDraft:(WallFilters)->Unit,onPage:(FilterPage)->Unit,catalog:FilterCatalog,onCatalogRows:(Int)->Unit) {
    when(page) {
        FilterPage.SCOPE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("filter-scope")) {
            ScopeLabel("拍摄日期")
            FilterNavigationRow("开始",dateLabel(draft.takenFrom)) {onPage(FilterPage.FROM)}
            FilterNavigationRow("结束",dateLabel(draft.takenTo)) {onPage(FilterPage.TO)}
            FilterSeparator()
            FilterNavigationRow("目录",draft.folderPath ?: if(draft.libraryId!=null) "整个媒体库" else "全部") {onPage(FilterPage.FOLDERS)}
            FilterSeparator();ScopeLabel("标签组合")
            FilterNavigationRow("全部包含",if(draft.tagsAll.isEmpty()) "不限" else "${draft.tagsAll.size} 个") {onPage(FilterPage.ALL_TAGS)}
            FilterNavigationRow("任一包含",if(draft.tagsAny.isEmpty()) "不限" else "${draft.tagsAny.size} 个") {onPage(FilterPage.ANY_TAGS)}
            FilterNavigationRow("排除",if(draft.tagsExclude.isEmpty()) "不限" else "${draft.tagsExclude.size} 个") {onPage(FilterPage.EXCLUDE_TAGS)}
            filterScopeError(draft)?.let {Text(it,Modifier.padding(horizontal=18.dp),color=AppTheme.colors.destructive,fontSize=13.sp)}
            FilterTextAction("重置高级条件",draft.hasScope) {onDraft(draft.copy(libraryId=null,folderPath=null,takenFrom=null,takenTo=null,tagsAll=emptySet(),tagsAny=emptySet(),tagsExclude=emptySet()))}
            ScopeLabel("点应用生效；回到筛选首页或点外部取消修改。")
        }
        FilterPage.FROM,FilterPage.TO -> key(page) {
            FilterCalendar(if(page==FilterPage.FROM) draft.takenFrom else draft.takenTo,page==FilterPage.TO) {value ->
                onDraft(if(page==FilterPage.FROM) draft.copy(takenFrom=value) else draft.copy(takenTo=value));onPage(FilterPage.SCOPE)
            }
        }
        FilterPage.FOLDERS -> FilterFolders(catalog,draft,onDraft,onCatalogRows)
        else -> {
            val selected=when(page){FilterPage.ALL_TAGS->draft.tagsAll;FilterPage.ANY_TAGS->draft.tagsAny;else->draft.tagsExclude}
            FilterTags(catalog,selected,onCatalogRows) {ids -> onDraft(when(page){FilterPage.ALL_TAGS->draft.copy(tagsAll=ids);FilterPage.ANY_TAGS->draft.copy(tagsAny=ids);else->draft.copy(tagsExclude=ids)})}
        }
    }
}

@Composable
private fun ScopeLabel(text:String) {Text(text,Modifier.padding(horizontal=18.dp,vertical=10.dp),fontSize=13.sp,color=AppTheme.colors.secondary)}

@Composable
private fun FilterCalendar(value:String?,end:Boolean,onSelect:(String?)->Unit) {
    val initial=remember(value) {value?.let {Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate()}}
    var month by remember {mutableStateOf(YearMonth.from(initial ?: LocalDate.now()))}
    var typing by remember {mutableStateOf(false)}
    var input by remember {mutableStateOf(initial?.toString().orEmpty())}
    val parsed=runCatching {LocalDate.parse(input)}.getOrNull()?.takeIf {it.year in 1..9999}
    val colors=AppTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("filter-calendar")) {
        MenuChoice("不限",value==null,radio=true) {onSelect(null)}
        FilterSeparator()
        if(typing) {
            ScopeLabel("年-月-日，例如 2026-09-01")
            FilterInput(input,{input=it},"输入日期")
            if(input.isNotEmpty() && parsed==null) ScopeLabel("请输入有效日期")
            FilterTextAction("选择日期",parsed!=null) {parsed?.let {onSelect(filterDayInstant(it,end,ZoneId.systemDefault()))}}
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${month.year}年${month.monthValue}月",Modifier.weight(1f).padding(start=10.dp),fontWeight=FontWeight.SemiBold,color=colors.text,fontSize=16.sp)
                CalendarArrow("上个月",month>YearMonth.of(1,1),true) {month=month.minusMonths(1)}
                CalendarArrow("下个月",month<YearMonth.of(9999,12),false) {month=month.plusMonths(1)}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=10.dp)) {
                listOf("一","二","三","四","五","六","日").forEach {Text(it,Modifier.weight(1f).padding(vertical=6.dp),color=colors.secondary,fontSize=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
            }
            val offset=month.atDay(1).dayOfWeek.value-1
            Column(Modifier.padding(horizontal=10.dp).selectableGroup()) {
                repeat((offset+month.lengthOfMonth()+6)/7) {week ->
                    Row {
                        repeat(7) {column ->
                            val dayNumber=week*7+column-offset+1
                            if(dayNumber !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp))
                            else {
                                val day=month.atDay(dayNumber)
                                Box(Modifier.weight(1f).height(44.dp).clip(CircleShape)
                                    .background(if(day==initial) colors.selected else androidx.compose.ui.graphics.Color.Transparent)
                                    .selectable(day==initial,role=Role.RadioButton,onClick={onSelect(filterDayInstant(day,end,ZoneId.systemDefault()))})
                                    .semantics {contentDescription="${day.year}年${day.monthValue}月${day.dayOfMonth}日"}
                                    .testTag("filter-day-$day"),contentAlignment=Alignment.Center) {
                                    Text("$dayNumber",color=if(day==initial) colors.accent else colors.text,fontSize=15.sp,fontWeight=if(day==initial) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
        FilterTextAction(if(typing) "显示日历" else "输入日期") {typing=!typing}
        ScopeLabel("按本地时区，包含所选日期。")
    }
}

@Composable
private fun CalendarArrow(label:String,enabled:Boolean,back:Boolean,onClick:()->Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled=enabled,role=Role.Button,onClick=onClick).semantics {contentDescription=label},contentAlignment=Alignment.Center) {MenuChevron(AppTheme.colors.accent,back)}
}

@Composable
private fun FilterInput(value:String,onValue:(String)->Unit,label:String) {
    val colors=AppTheme.colors
    BasicTextField(value,onValue,Modifier.padding(horizontal=18.dp,vertical=8.dp).fillMaxWidth()
        .clip(RoundedCornerShape(12.dp)).background(colors.subtle).padding(12.dp).semantics {contentDescription=label},
        singleLine=true,textStyle=TextStyle(color=colors.text,fontSize=16.sp),cursorBrush=SolidColor(colors.accent),
        decorationBox={inner -> Box {if(value.isEmpty()) Text(label,color=colors.secondary,fontSize=16.sp);inner()}})
}

@Composable
private fun FilterTags(catalog:FilterCatalog,selected:Set<Long>,onRows:(Int)->Unit,onSelected:(Set<Long>)->Unit) {
    var keyword by remember {mutableStateOf("")}
    var query by remember {mutableStateOf("")}
    LaunchedEffect(keyword) {kotlinx.coroutines.delay(250);query=keyword.trim()}
    val flow=remember(catalog,query) {Pager(PagingConfig(100,initialLoadSize=100,enablePlaceholders=false)){AlbumPagingSource {catalog.tags(query,it)}}.flow}
    val tags=flow.collectAsLazyPagingItems()
    LaunchedEffect(tags.itemCount,tags.loadState) {onRows(tags.itemCount.coerceIn(1,6)+2)}
    Column(Modifier.fillMaxSize().testTag("filter-tags")) {
        FilterInput(keyword,{if(it.length<=120) keyword=it},"搜索标签")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("已选 ${selected.size}/100",Modifier.weight(1f).padding(start=18.dp),color=AppTheme.colors.secondary,fontSize=13.sp)
            TextButton({onSelected(emptySet())},enabled=selected.isNotEmpty()) {Text("清除")}
        }
        LazyColumn(Modifier.weight(1f)) {
            items(tags.itemCount,key=tags.itemKey {it.id}) {index -> tags[index]?.let {tag ->
                MenuChoice(tag.name,tag.id in selected,tag.id in selected || selected.size<100) {onSelected(if(tag.id in selected) selected-tag.id else selected+tag.id)}
            }}
            item {CatalogStatus(tags.loadState,tags.itemCount,"没有匹配的标签",tags::retry)}
        }
    }
}

@Composable
private fun FilterFolders(catalog:FilterCatalog,draft:WallFilters,onDraft:(WallFilters)->Unit,onRows:(Int)->Unit) {
    var trail by remember {mutableStateOf<List<LibraryFolder>>(emptyList())}
    val current=trail.lastOrNull()
    val flow=remember(catalog,current) {Pager(PagingConfig(100,initialLoadSize=100,enablePlaceholders=false)){AlbumPagingSource {catalog.folders(current?.libraryId,current?.path.orEmpty(),it)}}.flow}
    val folders=flow.collectAsLazyPagingItems()
    LaunchedEffect(folders.itemCount,current,folders.loadState) {onRows(folders.itemCount.coerceIn(1,6)+1+(if(current!=null) 2 else 0))}
    Column(Modifier.fillMaxSize().testTag("filter-folders")) {
        MenuChoice("全部目录",draft.libraryId==null,radio=true) {onDraft(draft.copy(libraryId=null,folderPath=null))}
        if(current!=null) {
            FilterTextAction("返回上级目录") {trail=trail.dropLast(1)}
            MenuChoice("使用：${current.name}",draft.libraryId==current.libraryId && draft.folderPath==current.path.ifEmpty {null},current.online,true) {onDraft(draft.copy(libraryId=current.libraryId,folderPath=current.path.ifEmpty {null}))}
        }
        FilterSeparator()
        LazyColumn(Modifier.weight(1f)) {
            items(folders.itemCount,key=folders.itemKey {"${it.libraryId}/${it.path}"}) {index -> folders[index]?.let {folder ->
                FilterNavigationRow(folder.name,if(folder.online) "" else "离线",folder.online) {trail=trail+folder}
            }}
            item {CatalogStatus(folders.loadState,folders.itemCount,"没有子目录",folders::retry)}
        }
    }
}

@Composable
private fun CatalogStatus(state:CombinedLoadStates,count:Int,empty:String,retry:()->Unit) {
    if(state.refresh is LoadState.Loading || state.append is LoadState.Loading) ScopeLabel("正在加载…")
    val error=(state.refresh as? LoadState.Error) ?: (state.append as? LoadState.Error)
    if(error!=null) {ScopeLabel("加载失败，请重试");FilterTextAction("重试",onClick=retry)}
    else if(state.refresh is LoadState.NotLoading && count==0) ScopeLabel(empty)
}
