package com.richang.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richang.todo.companion.CompanionViewModel
import com.richang.todo.companion.ChatStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionScreen(onDismiss: () -> Unit, model: CompanionViewModel = viewModel(), insets: WindowInsets? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var personaEditor by remember { mutableStateOf(false) }
    var configEditor by remember { mutableStateOf(false) }
    var statsDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val messages = state.messages.filter { it.sessionId == state.sessionId }
    val list = rememberLazyListState()
    LaunchedEffect(state.sessionId, messages.size, state.busy) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    CompanionDialog(onDismiss = onDismiss, insets = insets) {
        Scaffold(modifier = Modifier.fillMaxSize().testTag("companion-page"),
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text(state.persona.name, fontWeight = FontWeight.SemiBold)
                        Text(state.config?.model ?: "请先配置模型", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }, navigationIcon = {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("companion-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                }, actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "伴侣菜单") }
                        DropdownMenu(menu, { menu = false }) {
                            listOf("人设编辑", "模型设置", "聊天统计", "话题记录", "新话题", "删除当前话题").forEachIndexed { index, label ->
                                DropdownMenuItem(text = { Text(label) }, enabled = !state.loading && !state.busy, onClick = {
                                    menu = false; model.clearError()
                                    when (index) {
                                        0 -> personaEditor = true
                                        1 -> configEditor = true
                                        2 -> statsDialog = true
                                        3 -> historyDialog = true
                                        4 -> model.newSession()
                                        5 -> deleteDialog = true
                                    }
                                })
                            }
                        }
                    }
                })
            },
            bottomBar = {
                Column(Modifier.padding(12.dp)) {
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(state.draft, model::updateDraft, enabled = !state.loading && !state.busy,
                            placeholder = { Text("想说些什么…") }, minLines = 1, maxLines = 5,
                            modifier = Modifier.weight(1f).testTag("chat-input"))
                        FilledIconButton(onClick = { model.send(state.draft) }, enabled = !state.loading && !state.busy && state.draft.isNotBlank(),
                            modifier = Modifier.testTag("chat-send")) { Icon(Icons.AutoMirrored.Rounded.Send, "发送") }
                    }
                    Text("内容由 AI 生成 · 聊天保存在本机", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        ) { padding ->
            if (state.loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (messages.isEmpty()) Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("慢慢说，我会认真听。", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text(if (state.config == null) "先设置模型，再开始聊天。你也可以先编辑喜欢的人设。" else "从今天的一件小事开始，也可以只聊聊此刻的心情。")
                if (state.config == null) OutlinedButton(onClick = { configEditor = true }, modifier = Modifier.padding(top = 16.dp)) { Text("配置模型") }
            } else LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("chat-messages"), state = list,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(messages, key = { it.id }) { message ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
                        Text(if (message.role == "user") "我" else "AI · " + message.model, style = MaterialTheme.typography.labelSmall)
                        Surface(color = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 560.dp).testTag("chat-" + message.id)) {
                            SelectionContainer { Text(message.content, Modifier.padding(14.dp)) }
                        }
                        Text(Instant.ofEpochMilli(message.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm")),
                            style = MaterialTheme.typography.labelSmall)
                        if (message.status == "pending") Text("正在等待回复…", style = MaterialTheme.typography.labelSmall)
                        if (message.status == "error") TextButton(onClick = { model.send(message.content, retryId = message.id) }, enabled = !state.busy) { Text("发送未完成 · 手动重试") }
                    }
                }
            }
        }
        if (personaEditor) PersonaEditor(state.persona, state.busy, state.error,
            onDismiss = { personaEditor = false; model.clearError() },
            onSave = { model.savePersona(it) { personaEditor = false } })
        if (configEditor) ModelConfigEditor(state.config, state.busy, state.error,
            onDismiss = { configEditor = false; model.clearError() },
            onSave = { model.saveConfig(it) { configEditor = false } },
            onClear = { model.clearConfig { configEditor = false } }, fetchModels = model::fetchModels)
        state.consentText?.let { text ->
            AlertDialog(onDismissRequest = model::dismissConsent, title = { Text("确认聊天数据发送范围") },
                text = { Text("接收方：${state.config?.destination}\n模型：${state.config?.model}\n\n将发送本次消息、人设和当前话题最多最近 20 条已发送消息（历史正文最多 24000 字）。\n\n不会读取日记、日程或其他话题。服务商可能保存请求并按用量计费；更换服务商会让新的服务商接收上述内容。失败后手动重试也可能再次计费。", modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { model.send(text, state.consentRetryId, consent = true) }) { Text("同意并发送") } },
                dismissButton = { TextButton(onClick = model::dismissConsent) { Text("取消") } })
        }
        if (statsDialog) {
            val stats = remember(state.messages) { ChatStats.from(state.messages) }
            AlertDialog(onDismissRequest = { statsDialog = false }, title = { Text("聊天统计") },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("相识 ${stats.togetherDays} 天 · 活跃 ${stats.activeDays} 天")
                    Text("成功消息 ${stats.messages} 条 · AI 回复 ${stats.replies} 条")
                    Text("服务商已返回用量：${stats.tokens} tokens")
                    Text("用量覆盖 ${stats.measuredReplies} / ${stats.replies} 次回复；不含失败请求可能产生的费用。实际账单请查看服务商，当前不估算金额。", style = MaterialTheme.typography.bodySmall)
                    Text("统计依据本机保留的聊天记录；删除话题后相应统计也会减少。", style = MaterialTheme.typography.bodySmall)
                } }, confirmButton = { TextButton(onClick = { statsDialog = false }) { Text("关闭") } })
        }
        if (historyDialog) AlertDialog(onDismissRequest = { historyDialog = false }, title = { Text("话题记录") },
            text = { LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(state.sessions, key = { it.id }) { session ->
                    TextButton(onClick = { model.selectSession(session.id); historyDialog = false }) { Text(session.title) }
                }
            } }, confirmButton = { TextButton(onClick = { historyDialog = false }) { Text("关闭") } })
        if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("删除当前话题？") },
            text = { Text("会删除本机该话题的全部消息，无法恢复。不会删除服务商可能保存的数据。") },
            confirmButton = { TextButton(onClick = { model.deleteSession(state.sessionId) { deleteDialog = false } }, enabled = !state.busy) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("取消") } })
    }
}
