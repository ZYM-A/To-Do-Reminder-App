package com.richang.todo

import android.app.Application
import com.richang.todo.data.TaskDatabase
import com.richang.todo.data.TaskStore
import com.richang.todo.reminders.ReminderScheduler

class TodoApplication : Application() {
    val scheduler by lazy { ReminderScheduler(this) }
    val store by lazy { TaskStore(TaskDatabase(this), scheduler) }
    override fun onCreate() { super.onCreate(); scheduler.createChannel() }
}
