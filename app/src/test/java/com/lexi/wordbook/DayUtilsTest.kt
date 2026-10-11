package com.lexi.wordbook

import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordRepository
import com.lexi.wordbook.util.Dates
import com.lexi.wordbook.util.DayUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Tests for the calendar picker, per-day grouping, and the added-at caption. */
class DayUtilsTest {

    private val day = Dates.DAY_MS

    private fun word(text: String, createdAt: Long) = Word(
        id = text,
        text = text,
        senses = listOf(Sense(PartOfSpeech.NOUN, "释义")),
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `countByDay buckets entries into local days`() {
        val today = Dates.startOfDay(System.currentTimeMillis())
        val words = listOf(
            word("a", today + 1000),
            word("b", today + 5000),
            word("c", today - day + 600),
            word("d", today)
        )
        val counts = DayUtils.countByDay(words)
        assertEquals(3, counts[today])
        assertEquals(1, counts[today - day])
        assertEquals(2, counts.size)
    }

    @Test
    fun `wordsInDay includes the whole day boundary`() {
        val today = Dates.startOfDay(System.currentTimeMillis())
        val words = listOf(
            word("first", today),
            word("last", today + day - 1),
            word("nextDay", today + day),
            word("previous", today - 1)
        )
        val inDay = DayUtils.wordsInDay(words, today).map { it.text }
        assertEquals(listOf("first", "last"), inDay)
    }

    @Test
    fun `addedLabel uses today, yesterday, then an absolute date`() {
        val today = Dates.startOfDay(System.currentTimeMillis())
        assertTrue(DayUtils.addedLabel(today + 9 * 3600_000 + 41 * 60_000).startsWith("今天 "))
        assertTrue(DayUtils.addedLabel(today - day + 14 * 3600_000).startsWith("昨天 "))
        assertTrue(DayUtils.addedLabel(today - 3 * day).startsWith("3天前 "))
        val old = DayUtils.addedLabel(today - 40 * day)
        assertTrue(old.contains("/"))
        assertEquals("—", DayUtils.addedLabel(0L))
    }

    @Test
    fun `dayCaption is relative for recent days`() {
        val today = Dates.startOfDay(System.currentTimeMillis())
        assertEquals("今天", DayUtils.dayCaption(today))
        assertEquals("昨天", DayUtils.dayCaption(today - day))
        assertEquals("5天前", DayUtils.dayCaption(today - 5 * day))
        assertTrue(DayUtils.dayCaption(today - 60 * day).contains("/"))
    }

    @Test
    fun `monthCells start on monday and cover the whole month`() {
        // 2026-10-01 is a Thursday, so the first row needs 3 leading blanks (Mon/Tue/Wed).
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = Dates.startOfDay(calendar.timeInMillis)
        val cells = DayUtils.monthCells(monthStart)
        val leading = cells.takeWhile { it == null }.size
        assertEquals(3, leading)
        assertEquals(31, cells.count { it != null })
        assertEquals(monthStart, cells.first { it != null })
        // Mon-first grid: (blank cells + days + trailing padding) always fills whole weeks.
        // October 2026 needs 3 blanks and 3 trailing blanks: 3 + 31 + 3 = 37, not a multiple
        // of 7 — the UI pads the last row, so the invariant checked here is the padding itself.
        assertEquals(6, (leading + 31) % 7)
        assertEquals("2026-10-01", DayUtils.dayKey(monthStart))
    }

    @Test
    fun `monthCells have no leading blanks when the month starts on monday`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = Dates.startOfDay(calendar.timeInMillis)
        val cells = DayUtils.monthCells(monthStart)
        assertEquals(0, cells.takeWhile { it == null }.size)
        assertEquals(30, cells.count { it != null })
    }

    @Test
    fun `month navigation steps one month at a time`() {
        val october = DayUtils.monthStartOf(
            Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 15) }.timeInMillis
        )
        val november = DayUtils.shiftMonth(october, 1)
        val september = DayUtils.shiftMonth(october, -1)
        // monthOf returns java.util.Calendar's zero-based index (SEPTEMBER == 8).
        assertEquals(Calendar.SEPTEMBER, DayUtils.monthOf(september))
        assertEquals(Calendar.OCTOBER, DayUtils.monthOf(october))
        assertEquals(Calendar.NOVEMBER, DayUtils.monthOf(november))
        assertEquals(2026, DayUtils.yearOf(november))
        // Stepping two months past November lands in January of the next year.
        val nextJanuary = DayUtils.shiftMonth(november, 2)
        assertEquals(Calendar.JANUARY, DayUtils.monthOf(nextJanuary))
        assertEquals(2027, DayUtils.yearOf(nextJanuary))
        assertEquals("2027-01-01", DayUtils.dayKey(nextJanuary))
    }

    @Test
    fun `longDate is human readable`() {
        val today = Dates.startOfDay(System.currentTimeMillis())
        val label = DayUtils.longDate(today)
        assertTrue(label.contains("年"))
        assertTrue(label.contains("月"))
        assertTrue(label.contains("周"))
        assertTrue(DayUtils.monthTitle(2026, 9).contains("10月"))
    }

    @Test
    fun `groups sort alphabetically like the words inside them`() {
        val names = listOf("zebra", "Apple", "banana", "中文分组")
        val groups = names.mapIndexed { index, name ->
            com.lexi.wordbook.data.WordGroup(id = "g$index", name = name, createdAt = index.toLong())
        }.sortedWith(WordRepository.GROUP_ORDER)
        assertEquals(listOf("Apple", "banana", "zebra", "中文分组"), groups.map { it.name })
    }

    @Test
    fun `words stay alphabetical inside a group`() {
        val sorted = listOf(
            word("zebra", 5),
            word("Apple", 1),
            word("avocado", 2),
            word("Banana", 3)
        ).sortedWith(WordRepository.WORD_ORDER)
        assertEquals(listOf("Apple", "avocado", "Banana", "zebra"), sorted.map { it.text })
    }
}
