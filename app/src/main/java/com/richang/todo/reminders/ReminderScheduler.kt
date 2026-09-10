package com.richang.todo.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.richang.todo.MainActivity
import com.richang.todo.R
import com.richang.todo.data.Task
import com.richang.todo.data.TaskRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "待办提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "日程与待办事项的到点提醒"
            enableVibration(true)
        })
    }
    fun canNotify(): Boolean = notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    private fun alarmIntent(id: String) = Intent(context, ReminderReceiver::class.java).apply {
        action = ACTION_REMIND
        data = "richang://reminder/$id".toUri()
        putExtra("taskId", id)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(task: Task) {
        cancelAlarm(task.id)
        val plan = TaskRules.nextReminder(task, System.currentTimeMillis()) ?: return
        val intent = alarmIntent(task.id).putExtra("revision", task.revision).putExtra("occurrence", plan.occurrence)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val trigger = maxOf(plan.triggerAt, System.currentTimeMillis() + 1000)
        if (canScheduleExact()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                return
            } catch (_: SecurityException) { /* Permission may be revoked between checking and scheduling. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    private fun cancelAlarm(id: String) {
        val pending = PendingIntent.getBroadcast(context, 0, alarmIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarms.cancel(pending); pending.cancel() }
    }
    fun cancel(id: String) { cancelAlarm(id); notifications.cancel(id, 0) }

    @SuppressLint("MissingPermission")
    fun notify(task: Task, occurrence: Long): Boolean {
        if (!canNotify()) return false
        val content = Intent(context, MainActivity::class.java).apply {
            data = "richang://task/${task.id}".toUri()
            putExtra("taskId", task.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val open = PendingIntent.getActivity(context, 0, content, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val time = Instant.ofEpochMilli(occurrence).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        val body = if (task.note.isBlank()) "$time · 点按查看任务" else "$time · ${task.note}"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(task.title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        return try { notifications.notify(task.id, 0, notification); true } catch (_: SecurityException) { false }
    }
    companion object {
        const val CHANNEL = "task_reminders"
        const val ACTION_REMIND = "com.richang.todo.REMIND"
    }
}
