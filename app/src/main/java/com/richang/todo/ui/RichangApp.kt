package com.richang.todo.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richang.todo.TodoViewModel
import com.richang.todo.data.RepeatRule
import com.richang.todo.data.Task
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private fun Task.dateTime(): ZonedDateTime = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichangApp(
    model: TodoViewModel, notificationsAllowed: Boolean, exactAllowed: Boolean,
    onNotifications: () -> Unit, onExact: () -> Unit,
    requestedTask: String?, onTaskOpened: () -> Unit,
) {
    val tasks by model.tasks.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var completedFilter by rememberSaveable { mutableStateOf(false) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(error, showEditor) {
        if (error != null && !showEditor) { snack.showSnackbar(error!!); model.clearError() }
    }
    LaunchedEffect(requestedTask, loading) {
        if (requestedTask != null && !loading) {
            if (tasks.any { it.id == requestedTask }) { editorId = requestedTask; showEditor = true }
            else snack.showSnackbar("该任务已删除")
            onTaskOpened()
        }
    }
    fun addTask() { model.clearError(); editorId = null; showEditor = true }
    val day = if (tab == 1) LocalDate.parse(selectedDate) else today
    val shown = when (tab) {
        0 -> tasks.filter { !it.completed && it.dateTime().toLocalDate() <= today }
        1 -> tasks.filter { it.dateTime().toLocalDate() == day }
        else -> tasks.filter { it.completed == completedFilter }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = ::addTask, containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("添加待办") })
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val labels = listOf("今日", "日程", "全部")
                val icons = listOf(Icons.Rounded.WbSunny, Icons.Rounded.CalendarMonth, Icons.AutoMirrored.Rounded.ListAlt)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("日 常  /  RICHANG", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(listOf("把今天，安排好。", "留一点时间，给生活。", "每一件事，都有着落。")[tab], fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(today.format(dateFormat), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        IconButton(onClick = { showSettings = true }, modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)) {
                            Icon(Icons.Rounded.NotificationsNone, "提醒设置")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (tab == 0) item {
                    val count = tasks.count { !it.completed && it.dateTime().toLocalDate() <= today }
                    val done = tasks.count { it.completed && it.dateTime().toLocalDate() == today }
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Pine, contentColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            Text("TODAY'S FOCUS", fontSize = 11.sp, letterSpacing = 2.sp, color = Color(0xFFCDDFD2))
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(count.toString().padStart(2, '0'), fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 56.sp)
                                Text("  件事，慢慢来", Modifier.padding(bottom = 8.dp), fontSize = 15.sp)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Rounded.Spa, null, Modifier.size(44.dp), tint = Color(0xFFB5D0BA))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(if (count == 0) "今天的清单已清空，享受一点自己的时间。" else "专注眼前的一件事，就是很好的开始。", fontSize = 12.sp, color = Color(0xFFE2ECE5))
                            if (done > 0) Text("今日安排已完成 $done 项", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color(0xFFCDDFD2))
                        }
                    }
                }
                if (!notificationsAllowed || !exactAllowed) item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(20.dp))
                            Text(if (!notificationsAllowed) "开启通知，才能收到提醒" else "允许准时提醒，减少通知延迟", Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 12.sp)
                            TextButton(onClick = if (!notificationsAllowed) onNotifications else onExact) { Text("开启") }
                        }
                    }
                }
                if (tab == 1) item { CalendarPanel(day, tasks, onDate = { selectedDate = it.toString() }) }
                if (tab == 2) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !completedFilter, onClick = { completedFilter = false }, label = { Text("待完成") })
                        FilterChip(selected = completedFilter, onClick = { completedFilter = true }, label = { Text("已完成") })
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (tab == 1) day.format(DateTimeFormatter.ofPattern("M月d日")) + "的安排" else if (tab == 2 && completedFilter) "已完成的事" else "待办清单", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${shown.size} 项", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (shown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircleOutline, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (tab == 2 && completedFilter) "完成的任务会出现在这里" else "留白，也是生活的一部分", Modifier.padding(top = 16.dp), fontWeight = FontWeight.Medium)
                        Text(if (tab == 2 && completedFilter) "从完成第一件小事开始" else "点下方「添加待办」，记下下一件事", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(shown, key = { it.id }) { task ->
                    TaskCard(task, now, busy, onToggle = { model.toggle(task) }, onEdit = { model.clearError(); editorId = task.id; showEditor = true })
                }
            }
        }
    }
    if (showEditor && !loading) {
        val task = tasks.find { it.id == editorId }
        key(editorId) {
            TaskEditor(task, if (tab == 1) day else today, busy, error,
                onDismiss = { if (!busy) { showEditor = false; model.clearError() } },
                onSave = { model.clearError(); model.save(it) { showEditor = false } },
                onDelete = { if (task != null) model.delete(task) { showEditor = false } })
        }
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("提醒设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("通知：${if (notificationsAllowed) "已开启" else "未开启"}")
                Text("准时提醒：${if (exactAllowed) "已允许" else "未允许，提醒可能延迟"}")
                Text("日常会使用系统通知提醒你。强行停止应用后，需要重新打开应用才能恢复提醒。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onNotifications) { Text("管理通知") }
                if (!exactAllowed) TextButton(onClick = onExact) { Text("允许准时提醒") }
            }
        }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("知道了") } })
}

@Composable
private fun TaskCard(task: Task, now: Long, busy: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val overdue = !task.completed && task.dueAt < now
    Card(onClick = onEdit, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() }, enabled = !busy)
            Column(Modifier.weight(1f)) {
                Text(task.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (task.note.isNotBlank()) Text(task.note, Modifier.padding(top = 5.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (overdue) Icons.Rounded.AccessTime else Icons.Rounded.Schedule, null, Modifier.size(13.dp), tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text((if (overdue) "已逾期 · " else "") + task.dateTime().format(DateTimeFormatter.ofPattern("M/d HH:mm")), fontSize = 11.sp,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    if (task.repeat != RepeatRule.NONE) { Icon(Icons.Rounded.Repeat, null, Modifier.size(13.dp)); Text(task.repeat.label, fontSize = 11.sp) }
                    if (!task.reminderEnabled) Icon(Icons.Rounded.NotificationsOff, "不提醒", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ChevronRight, "编辑任务", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarPanel(selected: LocalDate, tasks: List<Task>, onDate: (LocalDate) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = YearMonth.parse(monthText)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronLeft, "上个月") }
                Text("${month.year}年 ${month.monthValue}月", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { monthText = YearMonth.now().toString(); onDate(LocalDate.now()) }) { Text("今天", fontSize = 12.sp) }
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.Rounded.ChevronRight, "下个月") }
            }
            Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val weeks = (offset + month.lengthOfMonth() + 6) / 7
            repeat(weeks) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { weekday ->
                        val day = week * 7 + weekday - offset + 1
                        if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                        else {
                            val date = month.atDay(day)
                            val chosen = date == selected
                            val hasTasks = tasks.any { !it.completed && it.dateTime().toLocalDate() == date }
                            Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onDate(date) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(day.toString(), color = if (chosen) MaterialTheme.colorScheme.onPrimary else if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.size(4.dp).background(if (hasTasks) (if (chosen) MaterialTheme.colorScheme.onPrimary else Apricot) else Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(task: Task?, day: LocalDate, busy: Boolean, serverError: String?, onDismiss: () -> Unit, onSave: (Task) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val initial = remember {
        task?.dateTime()?.toLocalDateTime() ?: if (day <= LocalDate.now()) LocalDateTime.now().plusHours(1).withSecond(0).withNano(0) else day.atTime(9, 0)
    }
    var title by rememberSaveable { mutableStateOf(task?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(task?.note ?: "") }
    var dateText by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var timeText by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var lead by rememberSaveable { mutableIntStateOf(task?.leadMinutes ?: 0) }
    var repeatName by rememberSaveable { mutableStateOf((task?.repeat ?: RepeatRule.NONE).name) }
    var validation by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val time = LocalTime.parse(timeText)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background,
                topBar = { TopAppBar(title = { Text(if (task == null) "添加待办" else "编辑待办", fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }, actions = {
                    if (task != null) IconButton(onClick = { confirmDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "删除任务") }
                }) },
                bottomBar = {
                    Surface {
                        Button(onClick = {
                            val due = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val timingChanged = task == null || due != task.dueAt || lead != task.leadMinutes || repeatName != task.repeat.name
                            validation = when {
                                title.isBlank() -> "先给这件事起个名字吧"
                                timingChanged && due <= System.currentTimeMillis() -> "请选择未来的日期和时间"
                                else -> null
                            }
                            if (validation == null) onSave((task ?: Task(title = title, dueAt = due)).copy(title = title, note = note, dueAt = due, leadMinutes = lead, repeat = RepeatRule.valueOf(repeatName)))
                        }, enabled = !busy, modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            else Text("保存待办", fontSize = 16.sp)
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("把惦记的事，先记下来。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = title, onValueChange = { if (it.length <= 100) title = it; validation = null }, label = { Text("准备做什么？") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), isError = validation != null && title.isBlank())
                    OutlinedTextField(value = note, onValueChange = { if (it.length <= 2000) note = it }, label = { Text("备注（选填）") }, minLines = 3, maxLines = 6, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    Text("安排时间", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> dateText = LocalDate.of(y, m + 1, d).toString() }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.CalendarToday, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(date.format(DateTimeFormatter.ofPattern("yyyy/M/d")))
                        }
                        OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> timeText = LocalTime.of(h, m).toString() }, time.hour, time.minute, true).show() }, enabled = !busy) {
                            Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(time.format(timeFormat))
                        }
                    }
                    Text("什么时候提醒你", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "准时", 5 to "提前5分钟", 10 to "提前10分钟", 30 to "提前30分钟", 60 to "提前1小时", -1 to "不提醒").forEach { (minutes, label) ->
                            FilterChip(selected = lead == minutes, onClick = { lead = minutes }, enabled = !busy, label = { Text(label) })
                        }
                    }
                    Text("重复", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatRule.entries.forEach { rule -> FilterChip(selected = repeatName == rule.name, onClick = { repeatName = rule.name }, enabled = !busy, label = { Text(rule.label) }) }
                    }
                    if (repeatName != RepeatRule.NONE.name) Text("按固定时间重复提醒；勾选完成后，任务日期移到下一次。删除任务可停止重复。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lead > 0 && LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - lead * 60_000L <= System.currentTimeMillis()) Text("提前提醒时间已过，保存后会尽快提醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    (validation ?: serverError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这件待办？") }, text = { Text("任务和后续提醒都会删除。") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    }
}
