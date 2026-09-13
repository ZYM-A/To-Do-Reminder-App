package com.richang.todo.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.richang.todo.data.DiaryEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val diaryDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINESE)

internal fun LazyListScope.diaryItems(
    entries: List<DiaryEntry>, query: String, loading: Boolean, onQuery: (String) -> Unit, onEdit: (DiaryEntry) -> Unit,
) {
    val search = query.trim()
    val shown = entries.filter { search.isEmpty() || it.title.contains(search, ignoreCase = true) || it.content.contains(search, ignoreCase = true) }
    item {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().testTag("diary-search"),
            label = { Text("搜索日记") }, placeholder = { Text("标题或正文中的关键词") }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(16.dp),
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "清空搜索") } })
    }
    item {
        Text(if (search.isEmpty()) "共 ${entries.size} 篇日记" else "找到 ${shown.size} 篇日记",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (loading) item { CircularProgressIndicator(Modifier.padding(32.dp)) }
    else if (shown.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (search.isEmpty()) "把平常的一天，留在这里。" else "没有找到相关日记", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(if (search.isEmpty()) "点「写日记」，记录今天或补写过去的日子。" else "试试其他关键词，或清空搜索。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
    items(shown, key = { it.id }) { entry ->
        Card(onClick = { onEdit(entry) }, modifier = Modifier.fillMaxWidth().testTag("diary-" + entry.id),
            shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.date.format(diaryDateFormat), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(entry.displayTitle, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text("点按阅读与编辑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (entries.isNotEmpty()) item {
        Text("按日记日期从近到远排列 · 保存在本机", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryEditor(
    entry: DiaryEntry?, today: LocalDate, busy: Boolean, serverError: String?,
    onDismiss: () -> Unit, onSave: (DiaryEntry) -> Unit, onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val draftId = rememberSaveable { entry?.id ?: java.util.UUID.randomUUID().toString() }
    val initialDate = rememberSaveable { (entry?.date ?: today).toString() }
    var dateText by rememberSaveable { mutableStateOf(initialDate) }
    var title by rememberSaveable { mutableStateOf(entry?.title ?: "") }
    var content by rememberSaveable { mutableStateOf(entry?.content ?: "") }
    var validation by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val changed = dateText != initialDate || title != (entry?.title ?: "") || content != (entry?.content ?: "")
    fun close() { if (!busy) { if (changed) discard = true else onDismiss() } }
    val date = LocalDate.parse(dateText)
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = !busy, dismissOnClickOutside = false)) {
        BackHandler(enabled = !busy, onBack = ::close)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(title = { Text(if (entry == null) "写日记" else "阅读与编辑", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = { IconButton(onClick = ::close, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                        actions = { if (entry != null) IconButton(onClick = { confirmDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "删除日记") } })
                },
                bottomBar = {
                    Button(onClick = {
                        val draft = (entry ?: DiaryEntry(id = draftId, date = date)).copy(date = date, title = title, content = content)
                        val result = runCatching { draft.validated() }
                        validation = result.exceptionOrNull()?.message
                        result.getOrNull()?.let(onSave)
                    }, enabled = !busy,
                        modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp).height(52.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("保存日记", fontSize = 16.sp)
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    (validation ?: serverError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = {
                        DatePickerDialog(context, { _, year, month, day -> dateText = LocalDate.of(year, month + 1, day).toString() },
                            date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("diary-date")) {
                        Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(date.format(diaryDateFormat))
                    }
                    OutlinedTextField(value = title, onValueChange = { if (it.length <= 100) title = it },
                        label = { Text("标题（选填）") }, placeholder = { Text("给今天起个名字") },
                        modifier = Modifier.fillMaxWidth().testTag("diary-title"), enabled = !busy, singleLine = true,
                        shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(value = content, onValueChange = { if (it.length <= 50_000) content = it; validation = null },
                        label = { Text("日记正文") }, placeholder = { Text("今天发生了什么？有什么想留给以后的自己？") },
                        modifier = Modifier.fillMaxWidth().testTag("diary-content"), enabled = !busy, minLines = 12,
                        shape = RoundedCornerShape(16.dp), isError = validation != null && content.isBlank())
                    Text("${content.length} / 50000 字", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("写好后点「保存日记」。日记仅保存在本机，卸载或清除应用数据会删除记录。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false },
            title = { Text("放弃未保存的修改？") }, text = { Text("返回后，这次未保存的内容会丢失。") },
            confirmButton = { TextButton(onClick = { discard = false; onDismiss() }) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("继续写") } })
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("删除这篇日记？") }, text = { Text("删除后无法恢复。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    }
}
