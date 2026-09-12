package com.richang.todo.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.richang.todo.data.Anniversary
import com.richang.todo.data.CalendarType
import com.richang.todo.data.LunarDates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val anniversaryDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")

internal fun LazyListScope.anniversaryItems(
    entries: List<Anniversary>, today: LocalDate, loading: Boolean, onEdit: (Anniversary) -> Unit,
) {
    val sorted = entries.map { it to it.countdown(today) }.sortedWith(compareBy(
        { it.second.days < 0 }, { abs(it.second.days) }, { it.first.title }, { it.first.id },
    )).map { it.first }
    item {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("值得记住的日子", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(entries.size.toString() + " 个纪念日", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (loading) item {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
    else if (entries.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("让重要的日子，有个位置", Modifier.padding(top = 20.dp), fontWeight = FontWeight.Medium)
            Text("生日、相识、旅行，或下一个期待", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("点「添加纪念日」，开始记录", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    itemsIndexed(sorted, key = { _, entry -> entry.id }) { index, entry ->
        AnniversaryCard(entry, today, featured = index == 0, onClick = { onEdit(entry) })
    }
    if (entries.isNotEmpty()) item {
        Text("按自然日计算 · 支持公历与农历纪念日", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AnniversaryCard(entry: Anniversary, today: LocalDate, featured: Boolean, onClick: () -> Unit) {
    val countdown = remember(entry, today) { entry.countdown(today) }
    val foreground = if (featured) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = if (featured) Color(0xFFD7E6DA) else MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("anniversary-" + entry.id),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (featured) Pine else MaterialTheme.colorScheme.surface, contentColor = foreground)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (entry.yearly) Icons.Rounded.Cake else Icons.Rounded.FavoriteBorder, null, Modifier.size(18.dp), tint = secondary)
                Text(entry.calendarType.label + " · " + (if (entry.yearly) "每年纪念" else "专属纪念日"), Modifier.weight(1f).padding(start = 8.dp), fontSize = 12.sp, color = secondary)
                Icon(Icons.Rounded.Edit, "编辑纪念日", Modifier.size(17.dp), tint = secondary)
            }
            Text(entry.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (countdown.days == 0L) {
                Text("就是今天", fontSize = 34.sp, fontWeight = FontWeight.Light)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (countdown.days > 0) "还有" else "已经", Modifier.padding(bottom = 10.dp), fontSize = 15.sp)
                    Text(abs(countdown.days).toString(), fontSize = if (abs(countdown.days) > 99999) 34.sp else 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Light)
                    Text("天", Modifier.padding(bottom = 10.dp), fontSize = 15.sp)
                }
            }
            Text(if (entry.calendarType == CalendarType.LUNAR) LunarDates.fromSolar(countdown.target).display() else countdown.target.format(anniversaryDateFormat), color = secondary, fontSize = 13.sp)
            if (entry.calendarType == CalendarType.LUNAR) Text("对应公历 · " + countdown.target.format(anniversaryDateFormat), color = secondary, fontSize = 12.sp)
            if (entry.yearly && entry.date != countdown.target) {
                Text("最初的日子 · " + (if (entry.calendarType == CalendarType.LUNAR) LunarDates.fromSolar(entry.date).display() else entry.date.format(anniversaryDateFormat)), color = secondary, fontSize = 12.sp)
            }
            if (entry.note.isNotBlank()) Text(entry.note, color = secondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnniversaryEditor(
    entry: Anniversary?, today: LocalDate, busy: Boolean, serverError: String?,
    onDismiss: () -> Unit, onSave: (Anniversary) -> Unit, onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val draftId = rememberSaveable { entry?.id ?: java.util.UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(entry?.note ?: "") }
    var dateText by rememberSaveable { mutableStateOf((entry?.date ?: today).toString()) }
    var yearly by rememberSaveable { mutableStateOf(entry?.yearly ?: false) }
    var calendarName by rememberSaveable { mutableStateOf((entry?.calendarType ?: CalendarType.SOLAR).name) }
    var showLunarPicker by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val calendarType = CalendarType.valueOf(calendarName)
    val draft = Anniversary(id = draftId, title = title, date = date, note = note, yearly = yearly, calendarType = calendarType)
    val days = remember(date, yearly, calendarType, today) { draft.countdown(today).days }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(title = { Text(if (entry == null) "添加纪念日" else "编辑纪念日", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = { IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                        actions = { if (entry != null) IconButton(onClick = { confirmDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "删除纪念日") } })
                },
                bottomBar = {
                    Surface {
                        Button(onClick = {
                            val checked = runCatching { draft.validated() }
                            validation = checked.exceptionOrNull()?.message
                            checked.getOrNull()?.let(onSave)
                        }, enabled = !busy,
                            modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp).height(52.dp),
                            shape = RoundedCornerShape(16.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Text("保存纪念日", fontSize = 16.sp)
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("给期待一个日期，也给回忆一个位置。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    OutlinedTextField(value = title, onValueChange = { if (it.length <= 100) title = it; validation = null },
                        label = { Text("纪念日名称") }, placeholder = { Text("例如：生日、相识纪念日、下一次旅行") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("anniversary-title"),
                        shape = RoundedCornerShape(16.dp), isError = validation != null && title.isBlank())
                    Text("历法", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CalendarType.entries.forEach { type ->
                            FilterChip(selected = calendarType == type, onClick = {
                                if (type == CalendarType.LUNAR && !LunarDates.selectable(date)) {
                                    validation = "农历日期支持 1900—2100 年，请先选择范围内的日期"
                                } else { calendarName = type.name; validation = null }
                            }, enabled = !busy, label = { Text(type.label) }, modifier = Modifier.testTag("calendar-" + type.name))
                        }
                    }
                    Text("切换历法保留同一天；点击日期可重新选择。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("选择" + calendarType.label + "日期", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = {
                        if (calendarType == CalendarType.LUNAR) showLunarPicker = true
                        else DatePickerDialog(context, { _, year, month, day -> dateText = LocalDate.of(year, month + 1, day).toString() },
                            date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("anniversary-date"), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(if (calendarType == CalendarType.LUNAR) LunarDates.fromSolar(date).display() else date.format(anniversaryDateFormat))
                    }
                    if (calendarType == CalendarType.LUNAR) Text("对应公历：" + date.format(anniversaryDateFormat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(when {
                            days > 0 -> "还有 " + days + " 天"
                            days < 0 -> "已经 " + abs(days) + " 天"
                            else -> "就是今天"
                        }, Modifier.fillMaxWidth().padding(20.dp), fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("每年重复", fontWeight = FontWeight.SemiBold)
                            Text("开启后，按所选历法倒数下一次纪念日", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = yearly, onCheckedChange = { yearly = it }, enabled = !busy, modifier = Modifier.testTag("anniversary-yearly").semantics { contentDescription = "每年重复" })
                    }
                    if (yearly && calendarType == CalendarType.SOLAR && date.monthValue == 2 && date.dayOfMonth == 29) Text("2 月 29 日在平年按 2 月 28 日纪念。", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    if (yearly && calendarType == CalendarType.LUNAR) Text("每年按农历月日纪念；无对应闰月时按同名普通月，小月没有三十时按廿九。", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = note, onValueChange = { if (it.length <= 2000) note = it },
                        label = { Text("备注（选填）") }, minLines = 3, maxLines = 6, enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    Text("未来显示倒数，过去显示已过天数。本页记录日期，不发送到点通知。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    (validation ?: serverError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (showLunarPicker) LunarDatePicker(date, onDismiss = { showLunarPicker = false }, onSelected = {
            dateText = it.toString()
            showLunarPicker = false
            validation = null
        })
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个纪念日？") }, text = { Text("这条纪念日记录将被删除。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    }
}
