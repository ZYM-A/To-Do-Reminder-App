package com.richang.todo.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.richang.todo.data.DiaryEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DiaryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 13)

    @Test fun emptyBodyIsRejectedButTitleIsOptionalAndMultilineTextIsKept() {
        var saved: DiaryEntry? = null
        compose.setContent { RichangTheme { DiaryEditor(null, today, false, null, {}, { saved = it }, {}) } }
        compose.onNodeWithText("保存日记").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("diary-content").performScrollTo().performTextInput("今日晴朗\n\n走了很远的路 🌿")
        compose.onNodeWithText("保存日记").performClick()
        compose.runOnIdle {
            assertEquals("", saved!!.title)
            assertEquals(today, saved!!.date)
            assertEquals("今日晴朗\n\n走了很远的路 🌿", saved!!.content)
            assertEquals("9月13日的日记", saved!!.displayTitle)
        }
    }

    @Test fun backWarnsBeforeDiscardAndContinueWritingKeepsText() {
        var dismissed = false
        compose.setContent { RichangTheme { DiaryEditor(null, today, false, null, { dismissed = true }, {}, {}) } }
        compose.onNodeWithTag("diary-content").performScrollTo().performTextInput("还没写完")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertExists()
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText("继续写").performClick()
        compose.onNodeWithTag("diary-content").assertTextContains("还没写完")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun editPreservesIdentityAndDeletionRequiresConfirmation() {
        val entry = DiaryEntry(id = "old", date = today.minusDays(1), title = "原标题", content = "旧内容")
        var saved: DiaryEntry? = null
        var deleted = false
        compose.setContent { RichangTheme { DiaryEditor(entry, today, false, null, {}, { saved = it }, { deleted = true }) } }
        compose.onNodeWithTag("diary-title").performScrollTo().performTextReplacement("新标题")
        compose.onNodeWithText("保存日记").performClick()
        compose.runOnIdle { assertEquals(entry.copy(title = "新标题"), saved) }
        compose.onNodeWithContentDescription("删除日记").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithContentDescription("删除日记").performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    @Test fun draftSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var saved: DiaryEntry? = null
        restoration.setContent { RichangTheme { DiaryEditor(null, today, false, null, {}, { saved = it }, {}) } }
        compose.onNodeWithTag("diary-title").performTextInput("草稿")
        compose.onNodeWithTag("diary-content").performScrollTo().performTextInput("旋转后还在")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("保存日记").performClick()
        compose.runOnIdle { assertEquals("草稿", saved!!.title); assertEquals("旋转后还在", saved!!.content) }
    }

    @Test fun searchingBodyAndClearingQueryRestoresList() {
        val entries = listOf(DiaryEntry(id = "walk", date = today, title = "周末", content = "去公园散步"),
            DiaryEntry(id = "work", date = today, title = "工作", content = "完成了项目"))
        compose.setContent {
            var query by remember { mutableStateOf("") }
            RichangTheme { LazyColumn { diaryItems(entries, query, false, { query = it }, {}) } }
        }
        compose.onNodeWithTag("diary-search").performTextInput("散步")
        compose.onNodeWithTag("diary-walk").assertExists()
        compose.onNodeWithTag("diary-work").assertDoesNotExist()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithTag("diary-work").assertExists()
    }
}
