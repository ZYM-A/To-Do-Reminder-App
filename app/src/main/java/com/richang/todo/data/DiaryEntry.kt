package com.richang.todo.data

import java.time.LocalDate
import java.util.UUID

data class DiaryEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    val displayTitle: String get() = title.ifBlank { "${date.monthValue}月${date.dayOfMonth}日的日记" }
    fun validated(): DiaryEntry {
        require(date.year in 1..9999) { "请选择有效日期" }
        require(title.length <= 100) { "日记标题最多 100 字" }
        require(content.isNotBlank()) { "写点内容再保存吧" }
        require(content.length <= 50_000) { "日记内容最多 50000 字" }
        return copy(title = title.trim(), content = content.trim())
    }
}
