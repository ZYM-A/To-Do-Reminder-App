package com.richang.todo.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.richang.todo.TodoApplication
import com.richang.todo.TodoViewModel
import com.richang.todo.data.Task
import com.richang.todo.data.TaskDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TodoApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MainNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: TodoViewModel
    private val app get() = (RuntimeEnvironment.getApplication() as TodoApplication)

    @Before fun before() { app.deleteDatabase("richang.db") }
    private fun open(tasks: List<Task> = emptyList()) {
        val db = TaskDatabase(app)
        try { tasks.forEach(db::save) } finally { db.close() }
        model = TodoViewModel(app)
        compose.setContent { RichangTheme { RichangApp(model, true, true, {}, {}, null, {}) } }
        compose.runOnIdle { model.refresh() }
        compose.waitUntil(timeoutMillis = 10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !model.loading.value }
    }
    @Test fun onlyThreeBottomDestinationsAndEachOpensCorrectEditor() {
        open()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("main-navigation"))).assertCountEquals(3)
        compose.onNodeWithTag("nav-日程").assertIsSelected()
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithText("保存待办").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-纪念日").performClick()
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithText("保存纪念日").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-日记本").performClick()
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithText("保存日记").assertExists()
    }
    @Test fun scheduleRetainsTodayCalendarAndAllCompletedViews() {
        val today = LocalDate.now()
        fun due(days: Long) = today.plusDays(days).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        open(listOf(Task(title = "今日任务", dueAt = due(0), leadMinutes = -1),
            Task(title = "未来任务", dueAt = due(1), leadMinutes = -1),
            Task(title = "完成任务", dueAt = due(0), leadMinutes = -1, completed = true)))
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("今日任务"))
        compose.onNodeWithText("今日任务").assertExists()
        compose.onNodeWithText("未来任务").assertDoesNotExist()
        compose.onNodeWithTag("main-list").performScrollToIndex(0)
        compose.onNodeWithTag("schedule-filter-2").performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("未来任务"))
        compose.onNodeWithText("未来任务").assertExists()
        compose.onNodeWithTag("main-list").performScrollToIndex(0)
        compose.onNodeWithTag("schedule-completed").performClick()
        compose.onNodeWithText("完成任务").assertExists()
        compose.onNodeWithText("未来任务").assertDoesNotExist()
        compose.onNodeWithTag("schedule-filter-1").performClick()
        compose.onNodeWithContentDescription("上个月").assertExists()
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("今日任务"))
        compose.onNodeWithText("今日任务").assertExists()
    }
    @Test fun diarySavedFromMainScreenCanBeReopenedAfterTabSwitch() {
        open()
        compose.onNodeWithTag("nav-日记本").performClick()
        compose.onNodeWithTag("add-entry").performClick()
        compose.onNodeWithTag("diary-title").performTextInput("新日记")
        compose.onNodeWithTag("diary-content").performScrollTo().performTextInput("这一天值得记住")
        compose.onNodeWithText("保存日记").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); model.diaries.value.size == 1 && !model.busy.value }
        compose.onNodeWithTag("nav-日程").performClick()
        compose.onNodeWithTag("nav-日记本").performClick()
        compose.onNodeWithTag("main-list").performScrollToNode(hasText("新日记"))
        compose.onNodeWithText("新日记").performClick()
        compose.onNodeWithTag("diary-content").assertTextContains("这一天值得记住")
        val db = TaskDatabase(app)
        try { assertEquals("这一天值得记住", db.allDiaries().single().content) } finally { db.close() }
    }
}
