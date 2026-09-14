package com.richang.todo.ui

import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.richang.todo.companion.ModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ModelConfigEditor(
    current: ModelConfig?, busy: Boolean, serverError: String?, onDismiss: () -> Unit,
    onSave: (ModelConfig) -> Unit, onClear: () -> Unit, fetchModels: suspend (ModelConfig) -> List<String>,
) {
    // Deliberately not rememberSaveable: no plaintext key in Android saved-state bundles.
    var base by remember { mutableStateOf(current?.baseUrl ?: "") }
    var model by remember { mutableStateOf(current?.model ?: "") }
    var key by remember { mutableStateOf(current?.apiKey ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var fetching by remember { mutableStateOf(false) }
    var confirmFetch by remember { mutableStateOf<ModelConfig?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val disabled = busy || fetching
    Dialog(onDismissRequest = { if (!disabled) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !disabled)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text("模型设置") }, navigationIcon = { IconButton(onClick = onDismiss, enabled = !disabled) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } })
        }, bottomBar = {
            Button(onClick = {
                val checked = runCatching { ModelConfig(base, model, key).validated() }
                error = checked.exceptionOrNull()?.message
                checked.getOrNull()?.let(onSave)
            }, enabled = !disabled, modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(20.dp)) { Text("保存配置") }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("使用你自己的服务商账户，费用由服务商收取。保存配置不会发起网络请求。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DeepSeek" to "https://api.deepseek.com", "硅基流动" to "https://api.siliconflow.cn/v1", "自定义" to "").forEach { (label, url) ->
                        OutlinedButton(onClick = { base = url; key = ""; model = ""; models = emptyList(); error = null; result = null }, enabled = !disabled) { Text(label) }
                    }
                }
                OutlinedTextField(base, { base = it; models = emptyList(); result = null }, label = { Text("HTTPS 基础地址") },
                    placeholder = { Text("https://服务商域名/v1") }, singleLine = true, enabled = !disabled, modifier = Modifier.fillMaxWidth().testTag("model-base"))
                OutlinedTextField(key, { if (it.length <= 4096) key = it }, label = { Text("API 密钥") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !disabled, modifier = Modifier.fillMaxWidth().testTag("model-key"))
                OutlinedTextField(model, { if (it.length <= 200) model = it }, label = { Text("模型 ID") }, singleLine = true,
                    enabled = !disabled, modifier = Modifier.fillMaxWidth().testTag("model-id"))
                OutlinedButton(onClick = {
                    val checked = runCatching { ModelConfig(base, model, key).validated(false) }
                    error = checked.exceptionOrNull()?.message
                    checked.getOrNull()?.let { confirmFetch = it }
                }, enabled = !disabled) { Text(if (fetching) "正在获取…" else "测试连接并获取模型") }
                if (models.isNotEmpty()) Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("从列表选择模型（${models.size}）") }
                    DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        models.forEach { id -> DropdownMenuItem(text = { Text(id) }, onClick = { model = id; expanded = false }) }
                    }
                }
                result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                (error ?: serverError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("密钥和接口配置通过 Android Keystore 加密保存在本机。本页禁止截图，离开后未保存的输入会丢失。")
                Text("支持兼容 Chat Completions 的文字模型。获取列表失败时可以手动填写 ID；测试列表成功不代表每个模型都能聊天。")
                TextButton(onClick = { confirmClear = true }, enabled = !disabled) { Text("清除本机模型配置") }
            }
        }
        confirmFetch?.let { config ->
            AlertDialog(onDismissRequest = { confirmFetch = null }, title = { Text("验证这个服务商？") },
                text = { Text("将把 API 密钥发送到 ${config.destination} 获取模型列表。不会发送人设或聊天内容。请确认这是你信任的地址。") },
                confirmButton = { TextButton(onClick = {
                    confirmFetch = null; fetching = true; error = null; result = null
                    scope.launch {
                        try { models = fetchModels(config); result = if (models.isEmpty()) "连接成功，但未返回可用模型，请手动填写 ID" else "连接成功，已获取模型列表" }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "获取失败，请检查 HTTPS 地址、密钥和网络，也可手动填写模型 ID" }
                        finally { fetching = false }
                    }
                }) { Text("确认并测试") } },
                dismissButton = { TextButton(onClick = { confirmFetch = null }) { Text("取消") } })
        }
        if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清除模型配置？") },
            text = { Text("会删除本机保存的接口和密钥，不删除聊天记录。") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
    }
}
