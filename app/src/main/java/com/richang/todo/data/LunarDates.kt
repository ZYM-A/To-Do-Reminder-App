package com.richang.todo.data

import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import java.time.LocalDate
import kotlin.math.abs

enum class CalendarType(val label: String) { SOLAR("公历"), LUNAR("农历") }

data class LunarDate(val year: Int, val month: Int, val day: Int) {
    val isLeap: Boolean get() = month < 0
    fun display(): String = year.toString() + "年" + LunarDates.monthLabel(month) + LunarDates.dayLabel(day)
}
data class LunarMonthOption(val month: Int, val days: Int)

object LunarDates {
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100
    private val monthNames = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val digits = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    fun monthLabel(month: Int): String = (if (month < 0) "闰" else "") + monthNames[abs(month) - 1] + "月"
    fun dayLabel(day: Int): String = when (day) {
        in 1..10 -> "初" + digits[day - 1]
        in 11..19 -> "十" + digits[day - 11]
        20 -> "二十"
        in 21..29 -> "廿" + digits[day - 21]
        30 -> "三十"
        else -> error("无效农历日期")
    }
    fun fromSolar(date: LocalDate): LunarDate {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        return LunarDate(lunar.year, lunar.month, lunar.day)
    }
    fun months(year: Int): List<LunarMonthOption> = LunarYear.fromYear(year).months
        .filter { it.year == year }.map { LunarMonthOption(it.month, it.dayCount) }

    /** Strict input: never silently convert an invalid leap month or day. */
    fun toSolar(date: LunarDate): LocalDate {
        val month = months(date.year).find { it.month == date.month }
        require(month != null) { "所选年份没有这个农历月份" }
        require(date.day in 1..month.days) { "所选农历月份没有这一天" }
        val solar = Lunar.fromYmd(date.year, date.month, date.day).solar
        return LocalDate.of(solar.year, solar.month, solar.day)
    }
    fun selectable(date: LocalDate): Boolean = fromSolar(date).year in MIN_YEAR..MAX_YEAR

    /** Annual policy: leap month falls back to regular month; day 30 clamps to 29. */
    fun annualOccurrence(original: LunarDate, year: Int): LocalDate {
        val choices = months(year)
        val month = choices.find { it.month == original.month }
            ?: choices.first { it.month == abs(original.month) }
        return toSolar(LunarDate(year, month.month, minOf(original.day, month.days)))
    }
    fun nextAnnual(originalSolar: LocalDate, today: LocalDate): LocalDate {
        if (originalSolar >= today) return originalSolar
        val original = fromSolar(originalSolar)
        val year = maxOf(original.year, fromSolar(today).year)
        val candidate = annualOccurrence(original, year)
        return if (candidate >= today) candidate else annualOccurrence(original, year + 1)
    }
}
