package com.richang.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Entry page only. No model connection, user data, or chat state is used here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompanionScreen(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("companion-page"), color = MaterialTheme.colorScheme.background) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(title = { Text("AI 伴侣", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, modifier = Modifier.testTag("companion-back")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                            }
                        })
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Rounded.FavoriteBorder, null, Modifier.padding(24.dp).size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("给心情留个位置", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("这里将用于陪伴聊天，记录想说的话。", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Text("聊天功能尚未开放", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = onDismiss) { Text("返回日常") }
                }
            }
        }
    }
}
