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

    @Test fun switchingCalendarsPreservesActualDateAndOtherFields() {
        var saved: Anniversary? = null
        val entry = Anniversary(title = "春节", date = LocalDate.of(2026, 2, 17), note = "团聚", yearly = true)
        compose.setContent { RichangTheme { AnniversaryEditor(entry, entry.date, false, null, {}, { saved = it }, {}) } }
        compose.onNodeWithTag("calendar-LUNAR").performScrollTo().performClick()
        compose.onNodeWithTag("anniversary-date").performScrollTo().assertTextContains("2026年正月初一")
        compose.onNodeWithText("保存纪念日").performClick()
        compose.runOnIdle { assertEquals(entry.copy(calendarType = com.richang.todo.data.CalendarType.LUNAR), saved) }
        compose.onNodeWithTag("calendar-SOLAR").performScrollTo().performClick()
        compose.onNodeWithText("保存纪念日").performClick()
        compose.runOnIdle { assertEquals(entry, saved) }
    }

    @Test fun lunarPickerSelectsLeapMonthAndSavesActualSolarDate() {
        var saved: Anniversary? = null
        val entry = Anniversary(title = "闰月生日", date = LocalDate.of(2025, 6, 25))
        compose.setContent { RichangTheme { AnniversaryEditor(entry, entry.date, false, null, {}, { saved = it }, {}) } }
        compose.onNodeWithTag("calendar-LUNAR").performScrollTo().performClick()
        compose.onNodeWithTag("anniversary-date").performScrollTo().performClick()
        compose.onNodeWithTag("农历月").performClick()
        compose.onNodeWithTag("农历月--6").performScrollTo().performClick()
        compose.onNodeWithText("确定日期").performClick()
        compose.onNodeWithText("保存纪念日").performClick()
        compose.runOnIdle {
            assertEquals(LocalDate.of(2025, 7, 25), saved!!.date)
            assertEquals(com.richang.todo.data.CalendarType.LUNAR, saved!!.calendarType)
        }
    }

    @Test fun pickerClampsThirtiethAndHidesNonexistentDay() {
        var selected: LocalDate? = null
        compose.setContent { RichangTheme { LunarDatePicker(LocalDate.of(2025, 7, 24), {}, { selected = it }) } }
        compose.onNodeWithTag("农历月").performClick()
        compose.onNodeWithTag("农历月--6").performScrollTo().performClick()
        compose.onNodeWithTag("农历日").assertTextContains("廿九").performClick()
        compose.onNodeWithTag("农历日-30").assertDoesNotExist()
        compose.onNodeWithTag("农历日-29").performScrollTo().performClick()
        compose.onNodeWithText("确定日期").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2025, 8, 22), selected) }
    }

    @Test fun pickerChangingYearHandlesMissingLeapMonth() {
        var selected: LocalDate? = null
        compose.setContent { RichangTheme { LunarDatePicker(LocalDate.of(2025, 7, 25), {}, { selected = it }) } }
        compose.onNodeWithTag("农历年").performClick()
        compose.onNodeWithTag("农历年-2026").performScrollTo().performClick()
        compose.onNodeWithTag("农历月").assertTextContains("六月")
        compose.onNodeWithText("确定日期").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 7, 14), selected) }
    }

}
