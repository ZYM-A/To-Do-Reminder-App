package com.richang.todo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Kept behind TaskStore's IO dispatcher and mutex; no database work on the UI thread. */
class TaskDatabase(context: Context) : SQLiteOpenHelper(context, "richang.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE tasks (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, note TEXT NOT NULL,
            due_at INTEGER NOT NULL, lead_minutes INTEGER NOT NULL,
            repeat_rule TEXT NOT NULL, completed INTEGER NOT NULL,
            last_notified INTEGER NOT NULL, revision INTEGER NOT NULL
        )""")
        createAnniversaries(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createAnniversaries(db)
        else if (oldVersion < 3) db.execSQL("ALTER TABLE anniversaries ADD COLUMN calendar_type TEXT NOT NULL DEFAULT 'SOLAR'")
    }
    private fun createAnniversaries(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE anniversaries (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, event_date TEXT NOT NULL,
            note TEXT NOT NULL, yearly INTEGER NOT NULL DEFAULT 0,
            calendar_type TEXT NOT NULL DEFAULT 'SOLAR'
        )""")
    }
    fun allAnniversaries(): List<Anniversary> = readableDatabase.query("anniversaries", null, null, null, null, null, "event_date ASC, id ASC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(Anniversary(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                date = java.time.LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("event_date"))),
                note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                yearly = cursor.getInt(cursor.getColumnIndexOrThrow("yearly")) == 1,
                calendarType = CalendarType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("calendar_type"))),
            ))
        }
    }
    fun saveAnniversary(anniversary: Anniversary) {
        val entry = anniversary.validated()
        val values = ContentValues().apply {
            put("id", entry.id); put("title", entry.title); put("event_date", entry.date.toString())
            put("note", entry.note); put("yearly", if (entry.yearly) 1 else 0)
            put("calendar_type", entry.calendarType.name)
        }
        check(writableDatabase.insertWithOnConflict("anniversaries", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "无法保存纪念日" }
    }
    fun deleteAnniversary(id: String) { writableDatabase.delete("anniversaries", "id = ?", arrayOf(id)) }
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
