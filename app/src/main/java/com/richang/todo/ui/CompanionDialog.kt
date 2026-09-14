package com.richang.todo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

/** One owner for system bars and IME space; children receive consumed insets. */
@Composable
internal fun CompanionDialog(
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    insets: WindowInsets? = null,
    securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = securePolicy,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = false,
        ),
    ) {
        // Read real insets inside this dialog's composition, not its parent Activity.
        Surface(Modifier.fillMaxSize().testTag("companion-window")) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(insets ?: WindowInsets.safeDrawing)) { content() }
        }
    }
}
