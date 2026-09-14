package com.richang.todo.companion

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Separate local database: never queries the diary or schedule database. */
class CompanionDatabase(context: Context) : SQLiteOpenHelper(context, "companion.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE persona (id INTEGER PRIMARY KEY CHECK(id=1), name TEXT NOT NULL, nickname TEXT NOT NULL, personality TEXT NOT NULL, style TEXT NOT NULL)")
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE messages (
            id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
            role TEXT NOT NULL CHECK(role IN ('user','assistant')), content TEXT NOT NULL,
            status TEXT NOT NULL CHECK(status IN ('pending','sent','error')),
            created_at INTEGER NOT NULL, model TEXT NOT NULL, tokens INTEGER
        )""")
        db.execSQL("CREATE INDEX message_session ON messages(session_id, created_at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun persona(): Persona = readableDatabase.rawQuery("SELECT * FROM persona WHERE id=1", null).use {
        if (it.moveToFirst()) Persona(it.text("name"), it.text("nickname"), it.text("personality"), it.text("style")) else Persona()
    }
    fun savePersona(draft: Persona) {
        val p = draft.validated()
        val values = ContentValues().apply { put("id", 1); put("name", p.name); put("nickname", p.nickname); put("personality", p.personality); put("style", p.style) }
        check(writableDatabase.insertWithOnConflict("persona", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    fun sessions(): List<ChatSession> = readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY created_at DESC, rowid DESC", null).use {
        buildList { while (it.moveToNext()) add(ChatSession(it.text("id"), it.text("title"), it.long("created_at"))) }
    }
    fun messages(): List<ChatMessage> = readableDatabase.rawQuery("SELECT * FROM messages ORDER BY created_at, rowid", null).use {
        buildList { while (it.moveToNext()) add(ChatMessage(it.text("id"), it.text("session_id"), it.text("role"), it.text("content"),
            it.text("status"), it.long("created_at"), it.text("model"), if (it.isNull(it.getColumnIndexOrThrow("tokens"))) null else it.long("tokens"))) }
    }
    fun createSession(session: ChatSession) {
        check(writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
            put("id", session.id); put("title", session.title); put("created_at", session.createdAt)
        }) != -1L)
    }
    fun insert(message: ChatMessage) {
        require(message.role in listOf("user", "assistant") && message.content.isNotBlank())
        val values = ContentValues().apply {
            put("id", message.id); put("session_id", message.sessionId); put("role", message.role); put("content", message.content)
            put("status", message.status); put("created_at", message.createdAt); put("model", message.model)
            if (message.tokens == null) putNull("tokens") else put("tokens", message.tokens)
        }
        writableDatabase.insertOrThrow("messages", null, values)
    }
    fun pending(message: ChatMessage) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (db.update("messages", ContentValues().apply { put("status", "pending"); put("created_at", message.createdAt) }, "id = ?", arrayOf(message.id)) == 0) insert(message.copy(status = "pending"))
            db.execSQL("UPDATE sessions SET title=? WHERE id=? AND title='新话题'", arrayOf(message.content.take(30), message.sessionId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun complete(user: ChatMessage, reply: ChatReply, model: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            check(db.update("messages", ContentValues().apply { put("status", "sent") }, "id = ? AND status='pending'", arrayOf(user.id)) == 1)
            insert(ChatMessage(sessionId = user.sessionId, role = "assistant", content = reply.content, model = model, tokens = reply.tokens))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun fail(id: String) { writableDatabase.execSQL("UPDATE messages SET status='error' WHERE id=?", arrayOf(id)) }
    fun recoverInterrupted() { writableDatabase.execSQL("UPDATE messages SET status='error' WHERE status='pending'") }
    fun deleteSession(id: String) { writableDatabase.delete("sessions", "id=?", arrayOf(id)) }
    private fun Cursor.text(name: String) = getString(getColumnIndexOrThrow(name))
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
}
