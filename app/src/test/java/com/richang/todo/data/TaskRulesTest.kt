package com.richang.todo.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(value: String, inZone: ZoneId = zone) = LocalDateTime.parse(value).atZone(inZone).toInstant().toEpochMilli()
    private fun task(due: String = "2026-09-10T10:00", repeat: RepeatRule = RepeatRule.NONE, lead: Int = 0) =
        Task(title = "测试", dueAt = at(due), repeat = repeat, leadMinutes = lead)

    @Test fun schedulesLeadTime() {
        val task = task(lead = 10)
        assertEquals(at("2026-09-10T09:50"), TaskRules.nextReminder(task, at("2026-09-10T09:00"), zone)!!.triggerAt)
    }
    @Test fun missedOneTimeReminderIsDeliveredOnce() {
        val task = task()
        val now = at("2026-09-11T12:00")
        assertEquals(now, TaskRules.nextReminder(task, now, zone)!!.triggerAt)
        assertNull(TaskRules.nextReminder(task.copy(lastNotifiedOccurrence = task.dueAt), now, zone))
    }
    @Test fun disabledAndCompletedTasksHaveNoAlarm() {
        val now = at("2026-09-10T09:00")
        assertNull(TaskRules.nextReminder(task(lead = -1), now, zone))
        assertNull(TaskRules.nextReminder(task().copy(completed = true), now, zone))
    }
    @Test fun dailyReminderContinuesWithoutCompletingTask() {
        val task = task(repeat = RepeatRule.DAILY)
        val plan = TaskRules.nextReminder(task.copy(lastNotifiedOccurrence = task.dueAt), task.dueAt, zone)!!
        assertEquals(at("2026-09-11T10:00"), plan.occurrence)
    }
    @Test fun outageCoalescesManyMissedDaysIntoOneReminder() {
        val plan = TaskRules.nextReminder(task(repeat = RepeatRule.DAILY), at("2026-09-20T12:00"), zone)!!
        assertEquals(at("2026-09-20T10:00"), plan.occurrence)
        assertEquals(at("2026-09-20T12:00"), plan.triggerAt)
    }
    @Test fun beforeTodaysTriggerCatchesUpOnlyPreviousOccurrence() {
        val task = task(repeat = RepeatRule.DAILY)
        assertEquals(at("2026-09-19T10:00"), TaskRules.nextReminder(task, at("2026-09-20T09:00"), zone)!!.occurrence)
        assertEquals(at("2026-09-20T10:00"), TaskRules.nextReminder(task.copy(lastNotifiedOccurrence = at("2026-09-19T10:00")), at("2026-09-20T09:00"), zone)!!.occurrence)
    }
    @Test fun reminderCanFallOnPreviousDate() {
        val task = task("2026-09-11T00:05", RepeatRule.DAILY, 10)
        assertEquals(at("2026-09-10T23:55"), TaskRules.nextReminder(task, at("2026-09-10T22:00"), zone)!!.triggerAt)
    }
    @Test fun weeklyRecurrenceKeepsWeekdayAcrossMonths() {
        val task = task("2026-09-30T10:00", RepeatRule.WEEKLY)
        assertEquals(at("2026-10-07T10:00"), TaskRules.complete(task, at("2026-09-30T11:00"), zone).dueAt)
    }
    @Test fun completingOldRecurringTaskSkipsMissedDates() {
        assertEquals(at("2026-09-21T10:00"), TaskRules.complete(task(repeat = RepeatRule.DAILY), at("2026-09-20T12:00"), zone).dueAt)
    }
    @Test fun earlyCompletionMovesExactlyOneOccurrence() {
        assertEquals(at("2026-09-11T10:00"), TaskRules.complete(task(repeat = RepeatRule.DAILY), at("2026-09-09T12:00"), zone).dueAt)
    }
    @Test fun oneTimeCompletionCanBeUndone() {
        val completed = TaskRules.complete(task(), at("2026-09-10T12:00"), zone)
        assertTrue(completed.completed)
        assertFalse(TaskRules.complete(completed, at("2026-09-10T12:01"), zone).completed)
        assertEquals(1L, completed.revision)
    }
    @Test fun daylightSavingKeepsNineOClockAcrossSpringTransition() {
        val newYork = ZoneId.of("America/New_York")
        val due = at("2026-03-07T09:00", newYork)
        val task = Task(title = "晨间任务", dueAt = due, repeat = RepeatRule.DAILY, lastNotifiedOccurrence = due)
        val next = TaskRules.nextReminder(task, due, newYork)!!
        assertEquals(at("2026-03-08T09:00", newYork), next.occurrence)
        assertEquals(23 * 60 * 60 * 1000L, next.occurrence - due)
    }
    @Test fun daylightSavingKeepsNineOClockAcrossFallTransition() {
        val newYork = ZoneId.of("America/New_York")
        val due = at("2026-10-31T09:00", newYork)
        val task = Task(title = "晨间任务", dueAt = due, repeat = RepeatRule.DAILY, lastNotifiedOccurrence = due)
        assertEquals(25 * 60 * 60 * 1000L, TaskRules.nextReminder(task, due, newYork)!!.occurrence - due)
    }
    @Test fun clockMovedBackwardDoesNotRepeatAnAlreadyDeliveredOccurrence() {
        val task = task(repeat = RepeatRule.DAILY).copy(lastNotifiedOccurrence = at("2026-09-20T10:00"))
        assertEquals(at("2026-09-21T10:00"), TaskRules.nextReminder(task, at("2026-09-15T10:00"), zone)!!.occurrence)
    }
}
