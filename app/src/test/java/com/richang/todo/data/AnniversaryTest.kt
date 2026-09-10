package com.richang.todo.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnniversaryTest {
    private fun date(value: String) = LocalDate.parse(value)
    private fun entry(value: String, yearly: Boolean = false) = Anniversary(title = "纪念日", date = date(value), yearly = yearly)
    @Test fun futureDateCountsNaturalDays() {
        assertEquals(1L, entry("2026-09-11").countdown(date("2026-09-10")).days)
    }
    @Test fun todayIsZero() {
        assertEquals(0L, entry("2026-09-10").countdown(date("2026-09-10")).days)
    }
    @Test fun pastDateShowsElapsedDays() {
        assertEquals(-365L, entry("2025-09-10").countdown(date("2026-09-10")).days)
    }
    @Test fun annualDateAfterTodayUsesThisYear() {
        assertEquals(date("2026-10-01"), entry("2000-10-01", true).countdown(date("2026-09-10")).target)
    }
    @Test fun annualDateOnTodayDoesNotSkipToNextYear() {
        assertEquals(0L, entry("2000-09-10", true).countdown(date("2026-09-10")).days)
    }
    @Test fun annualDateAlreadyPassedUsesNextYear() {
        assertEquals(date("2027-01-01"), entry("2000-01-01", true).countdown(date("2026-09-10")).target)
    }
    @Test fun futureStartDoesNotInventAnEarlierAnniversary() {
        assertEquals(date("2030-01-01"), entry("2030-01-01", true).countdown(date("2026-09-10")).target)
    }
    @Test fun leapBirthdayUsesFebruary28InCommonYear() {
        assertEquals(0L, entry("2024-02-29", true).countdown(date("2026-02-28")).days)
    }
    @Test fun leapBirthdayReturnsToFebruary29InLeapYear() {
        assertEquals(date("2028-02-29"), entry("2024-02-29", true).countdown(date("2027-03-01")).target)
    }
    @Test fun crossingLeapDayCountsIt() {
        assertEquals(2L, entry("2028-03-01").countdown(date("2028-02-28")).days)
    }
    @Test fun december31ToJanuary1IsOneDay() {
        assertEquals(1L, entry("2027-01-01").countdown(date("2026-12-31")).days)
    }
    @Test fun blankNameIsRejectedAndSurroundingWhitespaceIsTrimmed() {
        assertThrows(IllegalArgumentException::class.java) { entry("2026-09-10").copy(title = "  ").validated() }
        assertEquals("生日", entry("2026-09-10").copy(title = " 生日 ").validated().title)
    }
}
