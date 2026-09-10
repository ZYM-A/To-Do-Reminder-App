package com.richang.todo.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class Anniversary(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: LocalDate,
    val note: String = "",
    val yearly: Boolean = false,
) {
    /** Count calendar days: today is zero, tomorrow is one, regardless of DST. */
    fun countdown(today: LocalDate): Countdown {
        val target = if (!yearly || date >= today) date else {
            // Start from the original date so February 29 returns in leap years.
            val thisYear = date.withYear(today.year)
            if (thisYear >= today) thisYear else date.withYear(today.year + 1)
        }
        return Countdown(target, ChronoUnit.DAYS.between(today, target))
    }
    fun validated(): Anniversary {
        require(title.isNotBlank()) { "请填写纪念日名称" }
        require(title.length <= 100) { "纪念日名称最多 100 字" }
        require(note.length <= 2000) { "备注最多 2000 字" }
        require(date.year in 1..9999) { "请选择有效日期" }
        return copy(title = title.trim(), note = note.trim())
    }
}
data class Countdown(val target: LocalDate, val days: Long)
