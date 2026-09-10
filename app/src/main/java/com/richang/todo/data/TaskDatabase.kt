package com.richang.todo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Kept behind TaskStore's IO dispatcher and mutex; no database work on the UI thread. */
class TaskDatabase(context: Context) : SQLiteOpenHelper(context, "richang.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tasks (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, note TEXT NOT NULL,
            due_at INTEGER NOT NULL, lead_minutes INTEGER NOT NULL,
            repeat_rule TEXT NOT NULL, completed INTEGER NOT NULL,
            last_notified INTEGER NOT NULL, revision INTEGER NOT NULL
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("A database migration is required: $oldVersion -> $newVersion")
    }
    fun all(): List<Task> = readableDatabase.query("tasks", null, null, null, null, null, "completed ASC, due_at ASC").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.task()) }
    }
    fun find(id: String): Task? = readableDatabase.query("tasks", null, "id = ?", arrayOf(id), null, null, null).use {
        if (it.moveToFirst()) it.task() else null
    }
    fun save(task: Task) {
        val values = ContentValues().apply {
            put("id", task.id); put("title", task.title); put("note", task.note)
            put("due_at", task.dueAt); put("lead_minutes", task.leadMinutes)
            put("repeat_rule", task.repeat.name); put("completed", if (task.completed) 1 else 0)
            put("last_notified", task.lastNotifiedOccurrence); put("revision", task.revision)
        }
        writableDatabase.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_REPLACE).also {
            check(it != -1L) { "无法保存任务" }
        }
    }
    fun delete(id: String) { writableDatabase.delete("tasks", "id = ?", arrayOf(id)) }
    private fun Cursor.task() = Task(
        id = getString(getColumnIndexOrThrow("id")), title = getString(getColumnIndexOrThrow("title")),
        note = getString(getColumnIndexOrThrow("note")), dueAt = getLong(getColumnIndexOrThrow("due_at")),
        leadMinutes = getInt(getColumnIndexOrThrow("lead_minutes")),
        repeat = RepeatRule.valueOf(getString(getColumnIndexOrThrow("repeat_rule"))),
        completed = getInt(getColumnIndexOrThrow("completed")) == 1,
        lastNotifiedOccurrence = getLong(getColumnIndexOrThrow("last_notified")),
        revision = getLong(getColumnIndexOrThrow("revision")),
    )
}
