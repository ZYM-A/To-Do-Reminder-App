package com.richang.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.richang.todo.data.LunarDate
import com.richang.todo.data.LunarDates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
internal fun LunarDatePicker(initial: LocalDate, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val original = remember(initial) { LunarDates.fromSolar(initial) }
    var year by rememberSaveable { mutableIntStateOf(original.year) }
    var month by rememberSaveable { mutableIntStateOf(original.month) }
    var day by rememberSaveable { mutableIntStateOf(original.day) }
    val months = remember(year) { LunarDates.months(year) }
    val maxDay = months.first { it.month == month }.days
    val selected = remember(year, month, day) { LunarDates.toSolar(LunarDate(year, month, day)) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("选择农历日期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LunarChoice("农历年", year, (LunarDates.MIN_YEAR..LunarDates.MAX_YEAR).map { it to (it.toString() + "年") }, Modifier.weight(1.2f)) { newYear ->
                        val newMonths = LunarDates.months(newYear)
                        val newMonth = newMonths.find { it.month == month } ?: newMonths.first { it.month == abs(month) }
                        year = newYear
                        month = newMonth.month
                        day = minOf(day, newMonth.days)
                    }
                    LunarChoice("农历月", month, months.map { it.month to LunarDates.monthLabel(it.month) }, Modifier.weight(1f)) { newMonth ->
                        month = newMonth
                        day = minOf(day, months.first { it.month == newMonth }.days)
                    }
                    LunarChoice("农历日", day, (1..maxDay).map { it to LunarDates.dayLabel(it) }, Modifier.weight(1f)) { day = it }
                }
                Text("对应公历：" + selected.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = MaterialTheme.typography.bodyMedium)
                Text("只显示实际存在的月份（含闰月）和日期。切换年月时，缺失的闰月改为普通月，三十改为该月最后一天。", style = MaterialTheme.typography.bodySmall)
                Text("农历年份支持 1900—2100 年", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSelected(selected) }) { Text("确定日期") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun LunarChoice(label: String, selected: Int, options: List<Pair<Int, String>>, modifier: Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val rowHeight = with(LocalDensity.current) { 48.dp.roundToPx() }
    LaunchedEffect(expanded) {
        if (expanded) scroll.scrollTo(options.indexOfFirst { it.first == selected }.coerceAtLeast(0) * rowHeight)
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(label), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(options.first { it.first == selected }.second, maxLines = 1)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 260.dp), scrollState = scroll) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false }, modifier = Modifier.height(48.dp).testTag(label + "-" + value))
                }
            }
        }
    }
}
