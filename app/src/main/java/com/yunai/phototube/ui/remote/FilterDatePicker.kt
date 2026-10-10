package com.yunai.phototube.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.GlassSettingsAction
import com.yunai.phototube.ui.GlassTextField
import com.yunai.phototube.ui.theme.AppTheme
import java.time.*

internal fun filterDayInstant(day: LocalDate, end: Boolean, zone: ZoneId): String =
    (if (end) day.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
    else day.atStartOfDay(zone).toInstant()).toString()

@Composable
internal fun FilterDatePicker(value: String?, end: Boolean, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val initial = remember(value) { value?.let { OffsetDateTime.parse(it).atZoneSameInstant(zone).toLocalDate() } }
    CalendarDatePicker(initial,if(end) "结束日期" else "开始日期",
        "按本地时区选择，${if(end) "包含当天的全部时间" else "从当天零点开始"}。",onClose=onClose) {
        onSelect(filterDayInstant(it,end,zone))
    }
}

@Composable
internal fun CalendarDatePicker(initial: LocalDate?, title: String, description: String,
    confirmLabel: String = "选择", onClose: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val zone=remember {ZoneId.systemDefault()}
    var input by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(initial ?: LocalDate.now(zone)).toString()) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var closing by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)
    val selected = if (input.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) runCatching { LocalDate.parse(input) }.getOrNull()?.takeIf { it.year in 1..9999 } else null
    val editable = !closing
    ManagementPage(title, { result?.let {onSelect(LocalDate.parse(it))} ?: onClose() },
        closeRequested=closing, onCloseAttempt={closing=true;true}) {
        Text(description,color=AppTheme.colors.secondary)
        GlassSettingsAction(if(typing) "显示日历" else "输入日期",{
            typing=!typing
            if(!typing && selected!=null) monthText=YearMonth.from(selected).toString()
        },enabled=editable)
        if(typing) {
            GlassTextField(input,{input=it},"日期（年-月-日）",enabled=editable,modifier=Modifier.testTag("filter-date-input"),
                isError=input.isNotEmpty() && selected==null,
                supportingText={Text(if(input.isNotEmpty() && selected==null) "请输入有效日期，例如 2026-09-17" else "格式：YYYY-MM-DD")})
        } else {
            ManagementGroup("${month.year}年${month.monthValue}月") {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {GlassSettingsAction("上个月",{monthText=month.minusMonths(1).toString()},enabled=editable && month>YearMonth.of(1,1))}
                    Box(Modifier.weight(1f)) {GlassSettingsAction("下个月",{monthText=month.plusMonths(1).toString()},enabled=editable && month<YearMonth.of(9999,12))}
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("一","二","三","四","五","六","日").forEach {Text(it,Modifier.weight(1f),color=AppTheme.colors.secondary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
                }
                val offset = month.atDay(1).dayOfWeek.value-1
                val weeks = (offset+month.lengthOfMonth()+6)/7
                Column(Modifier.selectableGroup()) {
                    repeat(weeks) {week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) {column ->
                                val number=week*7+column-offset+1
                                if(number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                                else {
                                    val day=month.atDay(number)
                                    val checked=selected==day
                                    Box(Modifier.weight(1f).heightIn(min=48.dp).clip(CircleShape)
                                        .background(if(checked) AppTheme.colors.subtle else AppTheme.colors.group)
                                        .selectable(checked,enabled=editable,role=Role.RadioButton,onClick={input=day.toString()})
                                        .testTag("filter-day-$day").semantics {contentDescription="${day.year}年${day.monthValue}月${day.dayOfMonth}日"},
                                        contentAlignment=Alignment.Center) {
                                        Text("$number",color=if(checked) AppTheme.colors.accent else AppTheme.colors.text,
                                            fontWeight=if(checked) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(selected?.let {"已选：${it.year}年${it.monthValue}月${it.dayOfMonth}日"} ?: "请选择日期",color=AppTheme.colors.secondary)
        GlassSettingsAction(confirmLabel,{selected?.let {result=it.toString();closing=true}},enabled=editable && selected!=null)
        GlassSettingsAction("取消",{closing=true},enabled=editable)
    }
}
