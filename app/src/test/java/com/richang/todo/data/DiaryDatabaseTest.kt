package com.richang.todo.data

import android.app.Application
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DiaryDatabaseTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun database(block: (TaskDatabase) -> Unit) {
        val db = TaskDatabase(context)
        try { block(db) } finally { db.close() }
    }
    @Before fun before() { context.deleteDatabase("richang.db") }
    @After fun after() { context.deleteDatabase("richang.db") }

    @Test fun versionThreeUpgradePreservesTasksAndLunarAnniversaries() {
        val task = Task(id = "task", title = "待办", note = "详细备注", dueAt = 1_900_000_000_000,
            repeat = RepeatRule.WEEKLY, completed = true, leadMinutes = 30, revision = 6, lastNotifiedOccurrence = 123)
        val anniversary = Anniversary(title = "闰月生日", date = LocalDate.of(2025, 7, 25), note = "原备注",
            yearly = true, calendarType = CalendarType.LUNAR)
        database {
            it.save(task); it.saveAnniversary(anniversary)
            it.writableDatabase.execSQL("DROP TABLE diaries")
            it.writableDatabase.version = 3
        }
        database {
            assertEquals(4, it.readableDatabase.version)
            assertEquals(task, it.find(task.id))
            assertEquals(listOf(anniversary), it.allAnniversaries())
            assertTrue(it.allDiaries().isEmpty())
            it.saveDiary(DiaryEntry(date = LocalDate.of(2026, 9, 13), content = "升级后写日记"))
            assertEquals(1, it.allDiaries().size)
            assertEquals(task, it.find(task.id))
        }
    }

    @Test fun multilineDiarySurvivesReopeningEditingAndDeletion() {
        val entry = DiaryEntry(date = LocalDate.of(2026, 9, 13), title = "周末", content = "第一行 🌿\n\n" + "记录生活。".repeat(2000))
        var createdAt = 0L
        database { it.saveDiary(entry) }
        database {
            val saved = it.allDiaries().single()
            assertEquals(entry.content, saved.content)
            assertEquals(entry.date, saved.date)
            assertEquals(entry.title, saved.title)
            createdAt = saved.createdAt
            assertTrue(createdAt > 0)
            it.saveDiary(saved.copy(title = "", content = "改写\n仍有换行", date = entry.date.minusDays(2), createdAt = 1))
        }
        database {
            val saved = it.allDiaries().single()
            assertEquals(createdAt, saved.createdAt)
            assertEquals("改写\n仍有换行", saved.content)
            assertEquals(entry.date.minusDays(2), saved.date)
            assertEquals("", saved.title)
            assertTrue(saved.updatedAt >= createdAt)
            it.deleteDiary(entry.id)
        }
        database { assertTrue(it.allDiaries().isEmpty()) }
    }

    @Test fun multipleEntriesOnSameDateRemainSeparateAndSortNewestDateFirst() {
        val today = LocalDate.of(2026, 9, 13)
        database {
            it.saveDiary(DiaryEntry(id = "older", date = today.minusDays(1), content = "昨天"))
            it.saveDiary(DiaryEntry(id = "first", date = today, content = "早晨"))
            it.saveDiary(DiaryEntry(id = "second", date = today, content = "晚上"))
            val entries = it.allDiaries()
            assertEquals(3, entries.size)
            assertEquals("older", entries.last().id)
            assertEquals(setOf("first", "second"), entries.take(2).map { entry -> entry.id }.toSet())
        }
    }

    @Test fun invalidSaveCannotOverwriteDiaryAndDeleteDoesNotTouchOtherTables() {
        val diary = DiaryEntry(date = LocalDate.of(2026, 9, 13), content = "原正文")
        val task = Task(title = "待办", dueAt = 1_900_000_000_000)
        val anniversary = Anniversary(title = "纪念日", date = diary.date)
        database {
            it.save(task); it.saveAnniversary(anniversary); it.saveDiary(diary)
            assertThrows(IllegalArgumentException::class.java) { it.saveDiary(diary.copy(content = " \n ")) }
            assertThrows(IllegalArgumentException::class.java) { it.saveDiary(diary.copy(content = "字".repeat(50_001))) }
            assertEquals("原正文", it.allDiaries().single().content)
            it.deleteDiary(diary.id)
            assertEquals(task, it.find(task.id))
            assertEquals(listOf(anniversary), it.allAnniversaries())
        }
    }
}
