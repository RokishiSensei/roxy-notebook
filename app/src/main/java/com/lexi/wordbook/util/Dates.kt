package com.lexi.wordbook.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date helpers for streaks, "due today" checks and grouped statistics. */
object Dates {

    const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Midnight of the local day containing [millis].
     *
     * A fresh [Calendar] is created on every call: reusing a cached instance could keep fields
     * from a previous computation (e.g. lenient day-of-month overflow) and drift a day.
     */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(millis: Long): Long = startOfDay(millis) + DAY_MS - 1

    fun isToday(millis: Long): Boolean =
        millis > 0 && startOfDay(millis) == startOfDay(System.currentTimeMillis())

    fun formatDate(millis: Long, pattern: String = "yyyy-MM-dd"): String =
        if (millis <= 0) "—" else SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

    fun formatDateTime(millis: Long): String =
        if (millis <= 0) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    fun relativeDay(millis: Long): String {
        if (millis <= 0) return "从未"
        val days = ((startOfDay(System.currentTimeMillis()) - startOfDay(millis)) / DAY_MS).toInt()
        return when {
            days <= 0 -> "今天"
            days == 1 -> "昨天"
            days < 7 -> "${days}天前"
            days < 30 -> "${days / 7}周前"
            else -> formatDate(millis)
        }
    }

    /** Last [count] day-start timestamps, oldest first, ending today. */
    fun recentDayStarts(count: Int): List<Long> {
        val today = startOfDay(System.currentTimeMillis())
        return (count - 1 downTo 0).map { today - it * DAY_MS }
    }
}
