package com.richang.todo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.richang.todo.ui.RichangApp
import com.richang.todo.ui.RichangTheme

class MainActivity : ComponentActivity() {
    private var requestedTask by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTask = intent.getStringExtra("taskId")
        enableEdgeToEdge()
        setContent {
            val model: TodoViewModel = viewModel()
            val scheduler = (application as TodoApplication).scheduler
            var notificationsAllowed by remember { mutableStateOf(scheduler.canNotify()) }
            var exactAllowed by remember { mutableStateOf(scheduler.canScheduleExact()) }
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                notificationsAllowed = scheduler.canNotify()
                model.refresh()
            }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationsAllowed = scheduler.canNotify()
                        exactAllowed = scheduler.canScheduleExact()
                        model.refresh()
                    }
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(Unit) { model.refresh() }
            RichangTheme {
                RichangApp(model, notificationsAllowed, exactAllowed,
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
                            getPreferences(MODE_PRIVATE).edit { putBoolean("notificationAsked", true) }
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                        }
                    },
                    onExact = {
                        if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:$packageName".toUri()))
                    },
                    requestedTask = requestedTask,
                    onTaskOpened = { requestedTask = null })
            }
        }
    }
    private fun openSettings(settings: Intent) {
        try { startActivity(settings) }
        catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTask = intent.getStringExtra("taskId")
    }
}
