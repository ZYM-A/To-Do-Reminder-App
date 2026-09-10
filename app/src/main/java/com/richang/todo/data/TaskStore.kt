package com.richang.todo.data

import com.richang.todo.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TaskStore(private val database: TaskDatabase, private val scheduler: ReminderScheduler) {
    private val mutex = Mutex()
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _anniversaries = MutableStateFlow<List<Anniversary>>(emptyList())
    val anniversaries = _anniversaries.asStateFlow()

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
    private fun refresh() {
        _tasks.value = database.all()
        _anniversaries.value = database.allAnniversaries()
    }
    suspend fun saveAnniversary(entry: Anniversary) = locked {
        database.saveAnniversary(entry)
        refresh()
    }
    suspend fun deleteAnniversary(id: String) = locked {
        database.deleteAnniversary(id)
        refresh()
    }

    suspend fun reload() = locked {
        refresh()
        _tasks.value.forEach(scheduler::schedule)
    }
    suspend fun save(draft: Task) = locked {
        require(draft.title.isNotBlank()) { "请填写任务名称" }
        require(draft.title.length <= 100) { "任务名称最多 100 字" }
        require(draft.note.length <= 2000) { "备注最多 2000 字" }
        require(draft.leadMinutes in listOf(-1, 0, 5, 10, 30, 60)) { "请选择有效的提醒时间" }
        val old = database.find(draft.id)
        val timingChanged = old == null || old.dueAt != draft.dueAt || old.leadMinutes != draft.leadMinutes || old.repeat != draft.repeat
        if (timingChanged) require(draft.dueAt > System.currentTimeMillis()) { "请选择未来的日期和时间" }
        val saved = draft.copy(title = draft.title.trim(), note = draft.note.trim(),
            revision = (old?.revision ?: 0) + 1,
            lastNotifiedOccurrence = if (timingChanged) 0 else old!!.lastNotifiedOccurrence)
        database.save(saved)
        scheduler.cancel(saved.id)
        scheduler.schedule(saved)
        refresh()
    }
    suspend fun toggle(id: String) = locked {
        val old = database.find(id) ?: return@locked
        val updated = TaskRules.complete(old, System.currentTimeMillis())
        database.save(updated)
        scheduler.cancel(id)
        scheduler.schedule(updated)
        refresh()
    }
    suspend fun delete(id: String) = locked { database.delete(id); scheduler.cancel(id); refresh() }

    suspend fun deliver(id: String, revision: Long, occurrence: Long) = locked {
        val task = database.find(id) ?: return@locked
        if (task.completed || !task.reminderEnabled || task.revision != revision || occurrence <= task.lastNotifiedOccurrence) return@locked
        val plan = TaskRules.nextReminder(task, System.currentTimeMillis()) ?: return@locked
        if (plan.triggerAt > System.currentTimeMillis() + 1000) {
            scheduler.schedule(task)
            return@locked
        }
        if (!scheduler.notify(task, plan.occurrence)) return@locked
        val updated = task.copy(lastNotifiedOccurrence = plan.occurrence)
        database.save(updated)
        scheduler.schedule(updated)
        refresh()
    }
}
