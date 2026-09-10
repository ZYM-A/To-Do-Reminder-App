package com.richang.todo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.richang.todo.data.Task
import com.richang.todo.data.TaskDatabase
import com.richang.todo.data.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDatabaseTest {
    @Test fun taskSurvivesReopenAndCanBeUpdatedAndDeleted() {
        // Instrumentation uses the test package's database, never the user's app database.
        val context = InstrumentationRegistry.getInstrumentation().context
        val task = Task(title = "中文任务", note = "备注与 emoji 🌿", dueAt = 1_900_000_000_000, leadMinutes = 10, repeat = RepeatRule.WEEKLY)
        var database = TaskDatabase(context)
        try {
            database.save(task)
            database.close()
            database = TaskDatabase(context)
            assertEquals(task, database.find(task.id))
            database.save(task.copy(completed = true, revision = 1))
            assertTrue(database.find(task.id)!!.completed)
            database.delete(task.id)
            assertNull(database.find(task.id))
        } finally { database.delete(task.id); database.close() }
    }
}
