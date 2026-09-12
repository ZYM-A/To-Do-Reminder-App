package com.richang.todo.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AnniversaryDatabaseTest {
    private inline fun <T : SQLiteOpenHelper> T.withDatabase(block: (T) -> Unit) {
        try { block(this) } finally { close() }
    }
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setUp() { context.deleteDatabase("richang.db") }
    @After fun tearDown() { context.deleteDatabase("richang.db") }

    @Test fun upgradingVersionOnePreservesEveryTaskField() {
        val oldTask = Task(id = "old-task", title = "已有待办", note = "不要丢失", dueAt = 1_900_000_000_000,
            leadMinutes = 10, repeat = RepeatRule.WEEKLY, completed = true, lastNotifiedOccurrence = 1_890_000_000_000, revision = 7)
        val legacy = object : SQLiteOpenHelper(context, "richang.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("""CREATE TABLE tasks (
                    id TEXT PRIMARY KEY, title TEXT NOT NULL, note TEXT NOT NULL,
                    due_at INTEGER NOT NULL, lead_minutes INTEGER NOT NULL,
                    repeat_rule TEXT NOT NULL, completed INTEGER NOT NULL,
                    last_notified INTEGER NOT NULL, revision INTEGER NOT NULL
                )""")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        legacy.withDatabase {
            it.writableDatabase.execSQL("INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(oldTask.id, oldTask.title, oldTask.note, oldTask.dueAt, oldTask.leadMinutes, oldTask.repeat.name, 1, oldTask.lastNotifiedOccurrence, oldTask.revision))
        }
        TaskDatabase(context).withDatabase {
            assertEquals(oldTask, it.find(oldTask.id))
            assertTrue(it.allAnniversaries().isEmpty())
            it.saveAnniversary(Anniversary(title = "生日", date = LocalDate.of(2000, 2, 29), yearly = true))
            assertEquals(3, it.readableDatabase.version)
            assertEquals(oldTask, it.find(oldTask.id))
        }
    }

    @Test fun anniversariesSurviveReopeningAndCanBeEditedAndDeletedIndependently() {
        val entry = Anniversary(title = "相识纪念日", date = LocalDate.of(2020, 9, 10), note = "一起走过的日子 🌿", yearly = true)
        val task = Task(title = "普通任务", dueAt = 1_900_000_000_000)
        TaskDatabase(context).withDatabase { it.save(task); it.saveAnniversary(entry) }
        TaskDatabase(context).withDatabase {
            assertEquals(listOf(entry), it.allAnniversaries())
            val edited = entry.copy(title = "旅行倒数", date = LocalDate.of(2027, 1, 1), yearly = false)
            it.saveAnniversary(edited)
            assertEquals(listOf(edited), it.allAnniversaries())
            it.deleteAnniversary(entry.id)
            assertTrue(it.allAnniversaries().isEmpty())
            assertEquals(task, it.find(task.id))
        }
    }

    @Test fun invalidAnniversaryDoesNotOverwriteSavedEntry() {
        val entry = Anniversary(title = "原来的名字", date = LocalDate.of(2026, 9, 10))
        TaskDatabase(context).withDatabase {
            it.saveAnniversary(entry)
            assertThrows(IllegalArgumentException::class.java) { it.saveAnniversary(entry.copy(title = " ")) }
            assertEquals(listOf(entry), it.allAnniversaries())
        }
    }

    @Test fun upgradingVersionTwoPreservesAnniversaryAndDefaultsToSolar() {
        val entry = Anniversary(id = "legacy", title = "旧纪念日", date = LocalDate.of(2020, 2, 29), note = "保留备注", yearly = true)
        TaskDatabase(context).withDatabase {
            it.save(Task(id = "kept-task", title = "已有待办", dueAt = 1_900_000_000_000))
            // Recreate the exact v2 anniversary schema while retaining the original tasks table.
            it.writableDatabase.execSQL("DROP TABLE anniversaries")
            it.writableDatabase.execSQL("""CREATE TABLE anniversaries (
                id TEXT PRIMARY KEY, title TEXT NOT NULL, event_date TEXT NOT NULL,
                note TEXT NOT NULL, yearly INTEGER NOT NULL DEFAULT 0
            )""")
            it.writableDatabase.execSQL("INSERT INTO anniversaries VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(entry.id, entry.title, entry.date.toString(), entry.note, 1))
            it.writableDatabase.version = 2
        }
        TaskDatabase(context).withDatabase {
            assertEquals(listOf(entry), it.allAnniversaries())
            assertEquals(CalendarType.SOLAR, it.allAnniversaries().single().calendarType)
            assertEquals("已有待办", it.find("kept-task")!!.title)
            assertEquals(3, it.readableDatabase.version)
        }
    }

    @Test fun lunarLeapDateAndCalendarChangesSurviveReopening() {
        val entry = Anniversary(title = "闰月生日", date = LocalDate.of(2025, 7, 25), yearly = true, calendarType = CalendarType.LUNAR)
        TaskDatabase(context).withDatabase { it.saveAnniversary(entry) }
        TaskDatabase(context).withDatabase {
            assertEquals(listOf(entry), it.allAnniversaries())
            assertEquals(LunarDate(2025, -6, 1), LunarDates.fromSolar(it.allAnniversaries().single().date))
            it.saveAnniversary(entry.copy(calendarType = CalendarType.SOLAR))
        }
        TaskDatabase(context).withDatabase {
            assertEquals(listOf(entry.copy(calendarType = CalendarType.SOLAR)), it.allAnniversaries())
        }
    }

}
