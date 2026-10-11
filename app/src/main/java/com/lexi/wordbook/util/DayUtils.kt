package com.lexi.wordbook.util

import com.lexi.wordbook.data.Word
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calendar helpers for the 「按日期查看」 feature: grouping entries by the local day they were
 * added, and rendering those days as month grids.
 */
object DayUtils {

    /** yyyy-MM-dd key of the local day that contains [millis]. */
    fun dayKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    fun startOfDay(millis: Long): Long = Dates.startOfDay(millis)

    /** Words created inside the given local day. */
    fun wordsInDay(words: List<Word>, dayStart: Long): List<Word> {
        val end = dayStart + Dates.DAY_MS - 1
        return words.filter { it.createdAt in dayStart..end }
    }

    /** day-start millis -> number of entries added that day. */
    fun countByDay(words: List<Word>): Map<Long, Int> =
        words.groupingBy { Dates.startOfDay(it.createdAt) }.eachCount()

    /** "今天 23:41" / "昨天 14:20" / "2026/10/07 09:15" — shown on every row. */
    fun addedLabel(millis: Long): String {
        if (millis <= 0) return "—"
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
        val days = ((Dates.startOfDay(System.currentTimeMillis()) - Dates.startOfDay(millis)) /
            Dates.DAY_MS).toInt()
        return when {
            days <= 0 -> "今天 $time"
            days == 1 -> "昨天 $time"
            days < 7 -> "${days}天前 $time"
            else -> SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(millis))
        }
    }

    /** Short form for headers: "2026年10月09日 周四". */
    fun longDate(dayStart: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
        val date = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(dayStart))
        val weekday = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }
        return "$date $weekday"
    }

    fun monthTitle(year: Int, month: Int): String = "${year}年${month + 1}月"

    /** First day of the month that contains [millis]. */
    fun monthStartOf(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun shiftMonth(millis: Long, months: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, months)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun yearOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)

    fun monthOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MONTH)

    /** Maximum days in the month that contains [millis]. */
    fun daysInMonth(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.getActualMaximum(Calendar.DAY_OF_MONTH)

    /**
     * Grid cells for the month containing [monthStart]: leading nulls for the days before the
     * 1st (so the first row starts on Monday), then each day-start millis in order.
     */
    fun monthCells(monthStart: Long): List<Long?> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = monthStart
            set(Calendar.DAY_OF_MONTH, 1)
        }
        // Calendar.MONDAY == 2, so this yields 0 for Monday and 6 for Sunday.
        val leading = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val total = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDay = monthStartOf(monthStart)
        val cells = mutableListOf<Long?>()
        repeat(leading) { cells.add(null) }
        for (day in 1..total) {
            cells.add(firstDay + (day - 1).toLong() * Dates.DAY_MS)
        }
        return cells
    }

    /** True when [millis] falls on today. */
    fun isToday(millis: Long): Boolean = Dates.startOfDay(millis) == Dates.startOfDay(System.currentTimeMillis())

    /** "今天" / "昨天" / "3天前" / "2026/09/28" for compact captioning. */
    fun dayCaption(dayStart: Long): String {
        val days = ((Dates.startOfDay(System.currentTimeMillis()) - dayStart) / Dates.DAY_MS).toInt()
        return when {
            days <= 0 -> "今天"
            days == 1 -> "昨天"
            days < 30 -> "${days}天前"
            else -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(dayStart))
        }
    }
}
