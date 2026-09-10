package com.richang.todo.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.richang.todo.data.Anniversary
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AnniversaryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyTitleIsRejectedThenEditorSavesEnteredNameAndRepeatChoice() {
        var saved: Anniversary? = null
        val today = LocalDate.of(2026, 9, 10)
        compose.setContent {
            RichangTheme { AnniversaryEditor(null, today, false, null, {}, { saved = it }, {}) }
        }
        compose.onNodeWithText("保存纪念日").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("anniversary-title").performTextInput("我的生日")
        compose.onNodeWithTag("anniversary-yearly").performScrollTo().performClick()
        compose.onNodeWithText("保存纪念日").performClick()
        compose.runOnIdle {
            assertEquals("我的生日", saved!!.title)
            assertEquals(today, saved!!.date)
            assertTrue(saved!!.yearly)
        }
    }
    @Test fun cardDisplaysCountdownAndCanOpenEditor() {
        var opened = false
        val entry = Anniversary(id = "trip", title = "去旅行", date = LocalDate.of(2026, 10, 1))
        compose.setContent {
            RichangTheme { AnniversaryCard(entry, LocalDate.of(2026, 9, 10), true) { opened = true } }
        }
        compose.onNodeWithText("21", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("anniversary-trip").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
