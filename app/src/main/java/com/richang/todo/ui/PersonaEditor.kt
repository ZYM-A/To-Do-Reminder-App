package com.richang.todo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.richang.todo.companion.Persona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonaEditor(persona: Persona, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (Persona) -> Unit) {
    var name by rememberSaveable { mutableStateOf(persona.name) }
    var nickname by rememberSaveable { mutableStateOf(persona.nickname) }
    var personality by rememberSaveable { mutableStateOf(persona.personality) }
    var style by rememberSaveable { mutableStateOf(persona.style) }
    var validation by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val draft = Persona(name, nickname, personality, style)
    fun close() { if (!busy) { if (draft != persona) discard = true else onDismiss() } }
    CompanionDialog(onDismiss = ::close, dismissOnBackPress = !busy) {
        BackHandler(enabled = !busy, onBack = ::close)
        Scaffold(topBar = {
            TopAppBar(title = { Text("人设编辑") }, navigationIcon = { IconButton(onClick = ::close, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } })
        }, bottomBar = {
            Button(onClick = {
                val checked = runCatching { draft.validated() }
                validation = checked.exceptionOrNull()?.message
                checked.getOrNull()?.let(onSave)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("保存人设") }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("设置你喜欢的相处方式。保存只写入本机；聊天发送前会说明人设的分享范围。")
                OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text("伴侣名字") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("persona-name"))
                OutlinedTextField(nickname, { if (it.length <= 40) nickname = it }, label = { Text("对你的称呼（选填）") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("persona-nickname"))
                OutlinedTextField(personality, { if (it.length <= 4000) personality = it }, label = { Text("性格特点") }, minLines = 4,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("persona-personality"))
                OutlinedTextField(style, { if (it.length <= 2000) style = it }, label = { Text("说话风格") }, minLines = 3,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("persona-style"))
                (validation ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的人设修改？") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
    }
}
