package com.richang.todo.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LunarDatesTest {
    private fun date(value: String) = LocalDate.parse(value)
    private fun annual(original: String) = Anniversary(title = "农历纪念日", date = date(original), yearly = true, calendarType = CalendarType.LUNAR)

    @Test fun conversionsMatchHongKongObservatoryTables() {
        // Independent fixtures: https://www.hko.gov.hk/tc/gts/time/calendar/text/files/T2025c.txt
        // and https://www.hko.gov.hk/tc/gts/time/calendar/text/files/T2026c.txt
        val fixtures = listOf(
            "2025-07-25" to LunarDate(2025, -6, 1),
            "2025-08-22" to LunarDate(2025, -6, 29),
            "2026-01-19" to LunarDate(2025, 12, 1),
            "2026-02-16" to LunarDate(2025, 12, 29),
            "2026-02-17" to LunarDate(2026, 1, 1),
            "2026-03-03" to LunarDate(2026, 1, 15),
            "2026-06-19" to LunarDate(2026, 5, 5),
            "2026-07-14" to LunarDate(2026, 6, 1),
            "2026-09-25" to LunarDate(2026, 8, 15),
        )
        fixtures.forEach { (solar, lunar) ->
            assertEquals(solar, lunar, LunarDates.fromSolar(date(solar)))
            assertEquals(solar, date(solar), LunarDates.toSolar(lunar))
        }
    }

    @Test fun everySupportedMonthRoundTripsAtBothBoundaries() {
        for (year in LunarDates.MIN_YEAR..LunarDates.MAX_YEAR) {
            val months = LunarDates.months(year)
            assertTrue(months.size in 12..13)
            assertEquals(months.size, months.map { it.month }.distinct().size)
            months.forEach { month ->
                assertTrue(month.days in 29..30)
                listOf(1, month.days).forEach { day ->
                    val lunar = LunarDate(year, month.month, day)
                    assertEquals(lunar, LunarDates.fromSolar(LunarDates.toSolar(lunar)))
                }
            }
        }
    }

    @Test fun nonexistentLeapMonthAndDayAreRejected() {
        assertTrue(LunarDates.months(2025).any { it.month == -6 })
        assertFalse(LunarDates.months(2026).any { it.month == -6 })
        assertThrows(IllegalArgumentException::class.java) { LunarDates.toSolar(LunarDate(2026, -6, 1)) }
        assertThrows(IllegalArgumentException::class.java) { LunarDates.toSolar(LunarDate(2025, -6, 30)) }
        assertThrows(IllegalArgumentException::class.java) { LunarDates.toSolar(LunarDate(2025, 1, 0)) }
    }

    @Test fun lunarYearBeforeSpringFestivalIsNotSkipped() {
        val origin = LunarDates.toSolar(LunarDate(2024, 12, 1))
        val entry = annual(origin.toString())
        assertEquals(Countdown(date("2026-01-19"), 9), entry.countdown(date("2026-01-10")))
        assertEquals(Countdown(date("2026-01-19"), 0), entry.countdown(date("2026-01-19")))
        assertEquals(LunarDates.toSolar(LunarDate(2026, 12, 1)), entry.countdown(date("2026-01-20")).target)
    }

    @Test fun springFestivalRepeatsOnLunarDateInsteadOfSolarDate() {
        val entry = annual(LunarDates.toSolar(LunarDate(2025, 1, 1)).toString())
        assertEquals(Countdown(date("2026-02-17"), 1), entry.countdown(date("2026-02-16")))
        assertEquals(Countdown(date("2026-02-17"), 0), entry.countdown(date("2026-02-17")))
        assertEquals(LunarDates.toSolar(LunarDate(2027, 1, 1)), entry.countdown(date("2026-02-18")).target)
    }

    @Test fun leapAnniversaryFallsBackAndReturnsToLeapWhenAvailable() {
        val original = LunarDate(2025, -6, 1)
        assertEquals(date("2026-07-14"), LunarDates.annualOccurrence(original, 2026))
        assertEquals(date("2025-07-25"), LunarDates.annualOccurrence(original, 2025))
        assertEquals(date("2026-07-14"), annual("2025-07-25").countdown(date("2026-01-01")).target)
    }

    @Test fun regularMonthDoesNotRepeatAgainInLeapMonth() {
        val entry = annual(LunarDates.toSolar(LunarDate(2024, 6, 1)).toString())
        assertEquals(date("2026-07-14"), entry.countdown(date("2025-07-01")).target)
    }

    @Test fun thirtiethClampsToSmallMonthAndReturnsInBigMonth() {
        val original = LunarDate(2025, 6, 30)
        assertEquals(date("2025-08-22"), LunarDates.annualOccurrence(LunarDate(2025, -6, 30), 2025))
        val smallYear = (2026..2040).first { year -> LunarDates.months(year).first { it.month == 6 }.days == 29 }
        assertEquals(LunarDate(smallYear, 6, 29), LunarDates.fromSolar(LunarDates.annualOccurrence(original, smallYear)))
        assertEquals(original, LunarDates.fromSolar(LunarDates.annualOccurrence(original, 2025)))
    }

    @Test fun futureOriginalIsNeverBroughtForwardAndOneOffCanBePast() {
        val entry = annual("2026-09-25")
        assertEquals(date("2026-09-25"), entry.countdown(date("2025-01-01")).target)
        assertEquals(Countdown(date("2026-09-25"), -1), entry.copy(yearly = false).countdown(date("2026-09-26")))
    }

    @Test fun validationUsesLunarYearAtSupportedRangeEdges() {
        for (year in listOf(1900, 2100)) {
            val entry = annual(LunarDates.toSolar(LunarDate(year, 12, 1)).toString())
            assertEquals(entry, entry.validated())
        }
        assertThrows(IllegalArgumentException::class.java) {
            annual(LunarDates.toSolar(LunarDate(1899, 12, 1)).toString()).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            annual(LunarDates.toSolar(LunarDate(2101, 1, 1)).toString()).validated()
        }
    }

    @Test fun lunarLabelsDistinguishLeapMonthAndSpecialDays() {
        assertEquals("2025年闰六月初一", LunarDate(2025, -6, 1).display())
        assertEquals(listOf("初十", "十一", "十九", "二十", "廿一", "廿九", "三十"),
            listOf(10, 11, 19, 20, 21, 29, 30).map(LunarDates::dayLabel))
    }
}
