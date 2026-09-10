package com.richang.todo.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class RepeatRule(val label: String, val days: Long) {
    NONE("不重复", 0), DAILY("每天", 1), WEEKLY("每周", 7)
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val dueAt: Long,
    val leadMinutes: Int = 0,
    val repeat: RepeatRule = RepeatRule.NONE,
    val completed: Boolean = false,
    val lastNotifiedOccurrence: Long = 0,
    val revision: Long = 0,
) {
    val reminderEnabled: Boolean get() = leadMinutes >= 0
}

data class ReminderPlan(val occurrence: Long, val triggerAt: Long)

/** Pure time rules: calendar days (not 24-hour durations), so DST keeps local clock time. */
object TaskRules {
    fun nextReminder(task: Task, now: Long, zone: ZoneId = ZoneId.systemDefault()): ReminderPlan? {
        if (task.completed || !task.reminderEnabled) return null
        val lead = task.leadMinutes * 60_000L
        if (task.repeat == RepeatRule.NONE) {
            if (task.lastNotifiedOccurrence >= task.dueAt) return null
            return ReminderPlan(task.dueAt, maxOf(task.dueAt - lead, now))
        }
        val base = Instant.ofEpochMilli(task.dueAt).atZone(zone)
        val localNow = Instant.ofEpochMilli(now + lead).atZone(zone)
        var index = maxOf(0, ChronoUnit.DAYS.between(base.toLocalDate(), localNow.toLocalDate()) / task.repeat.days)
        fun occurrence(i: Long) = base.plusDays(i * task.repeat.days).toInstant().toEpochMilli()
        if (index > 0 && occurrence(index) - lead > now) index--
        while (occurrence(index + 1) - lead <= now) index++
        // After an outage, deliver only the latest missed occurrence, never a backlog.
        val notifiedIndex = if (task.lastNotifiedOccurrence > task.dueAt) {
            ChronoUnit.DAYS.between(base.toLocalDate(), Instant.ofEpochMilli(task.lastNotifiedOccurrence).atZone(zone).toLocalDate()) / task.repeat.days
        } else 0
        index = maxOf(index, notifiedIndex)
        while (occurrence(index) <= task.lastNotifiedOccurrence) index++
        val due = occurrence(index)
        return ReminderPlan(due, maxOf(due - lead, now))
    }

    fun complete(task: Task, now: Long, zone: ZoneId = ZoneId.systemDefault()): Task {
        if (task.completed) return task.copy(completed = false, revision = task.revision + 1)
        if (task.repeat == RepeatRule.NONE) return task.copy(completed = true, revision = task.revision + 1)
        val base = Instant.ofEpochMilli(task.dueAt).atZone(zone)
        val localNow = Instant.ofEpochMilli(now).atZone(zone)
        var index = maxOf(1, ChronoUnit.DAYS.between(base.toLocalDate(), localNow.toLocalDate()) / task.repeat.days)
        var next = base.plusDays(index * task.repeat.days)
        while (next.toInstant().toEpochMilli() <= now) {
            index++
            next = base.plusDays(index * task.repeat.days)
        }
        return task.copy(dueAt = next.toInstant().toEpochMilli(), revision = task.revision + 1)
    }
}
