package com.lexi.wordbook

import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.data.WordKind
import com.lexi.wordbook.data.WordRepository
import com.lexi.wordbook.util.Alphabet
import com.lexi.wordbook.util.Csv
import com.lexi.wordbook.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the grouping, ordering, CSV and draft logic. */
class WordBookLogicTest {

    // ---------------------------------------------------------------- alphabet

    @Test
    fun `initial buckets use A-Z and hash for everything else`() {
        assertEquals("A", Word.initialOf("apple"))
        assertEquals("S", Word.initialOf("  serendipity"))
        assertEquals("G", Word.initialOf("Give up"))
        assertEquals("#", Word.initialOf("123"))
        assertEquals("#", Word.initialOf(""))
        assertEquals("#", Word.initialOf("中文"))
    }

    @Test
    fun `grouping is alphabetical and skips empty letters`() {
        val words = listOf(
            word("banana"),
            word("apple"),
            word("cherry"),
            word("123number")
        )
        val groups = Alphabet.group(words)
        assertEquals(listOf("A", "B", "C", "#"), groups.map { it.first })
        assertEquals(listOf("apple"), groups.first().second.map { it.text })
    }

    @Test
    fun `repository order is case insensitive within a letter`() {
        // The comparator itself is plain ASCII ("#" sorts first); the UI puts the "#"
        // bucket last via Alphabet.group, which is asserted separately below.
        val sorted = listOf(word("zebra"), word("Apple"), word("avocado"))
            .sortedWith(WordRepository.WORD_ORDER)
        assertEquals(listOf("Apple", "avocado", "zebra"), sorted.map { it.text })

        val withSymbol = listOf(word("zebra"), word("123"), word("Apple"))
            .sortedWith(WordRepository.WORD_ORDER)
        assertEquals("#", withSymbol.first().initial)
    }

    // ---------------------------------------------------------------- draft

    @Test
    fun `draft validation requires a headword and at least one meaning`() {
        assertTrue(!WordDraft(id = "1").isValid)
        assertTrue(!WordDraft(id = "1", text = "apple").isValid)
        assertTrue(
            WordDraft(
                id = "1",
                text = "apple",
                senses = listOf(Sense(PartOfSpeech.NOUN, "苹果"))
            ).isValid
        )
    }

    @Test
    fun `draft splits tags on commas and spaces and detects kind`() {
        val draft = WordDraft(
            id = "1",
            text = "give up on",
            tagsText = "口语, 短语 高频，口语",
            senses = listOf(Sense(PartOfSpeech.PHRASE, "放弃"))
        )
        assertEquals(listOf("口语", "短语", "高频"), draft.tags)

        val now = 1_000L
        val phrase = draft.applyTo(null, now)
        assertEquals(WordKind.PHRASE, phrase.kind)
        assertEquals(now, phrase.createdAt)

        val sentence = draft.copy(text = "the quick brown fox jumps over the lazy dog")
            .applyTo(null, now)
        assertEquals(WordKind.SENTENCE, sentence.kind)

        val single = draft.copy(text = "apple").applyTo(null, now)
        assertEquals(WordKind.WORD, single.kind)
    }

    @Test
    fun `editing keeps creation time and review counters`() {
        val original = word("apple").copy(
            createdAt = 111L,
            reviewCount = 4,
            starred = true,
            lastReviewedAt = 222L
        )
        val edited = WordDraft.from(original)
            .copy(senses = listOf(Sense(PartOfSpeech.NOUN, "苹果（水果）")))
            .applyTo(original, now = 999L)

        assertEquals("apple", edited.text)
        assertEquals(111L, edited.createdAt)
        assertEquals(4, edited.reviewCount)
        assertEquals(222L, edited.lastReviewedAt)
        assertEquals(999L, edited.updatedAt)
        assertTrue(edited.starred)
        assertEquals("苹果（水果）", edited.meaningZhJoined)
    }

    // ---------------------------------------------------------------- part of speech round trip

    @Test
    fun `part of speech survives a name lookup`() {
        PartOfSpeech.entries.forEach { pos ->
            assertEquals(pos, PartOfSpeech.fromName(pos.name))
            assertNotEquals("", pos.labelZh)
            assertNotEquals("", pos.label)
        }
        assertEquals(PartOfSpeech.OTHER, PartOfSpeech.fromName("nonsense"))
        assertEquals(LearningStatus.NEW, LearningStatus.fromName(null))
    }

    // ---------------------------------------------------------------- csv

    @Test
    fun `csv round trips quotes commas and newlines`() {
        val encoded = Csv.encode(
            listOf("a", "b"),
            listOf(listOf("plain", "with,comma"), listOf("with \"quote\"", "line1\nline2"))
        )
        val table = Csv.decode(encoded)
        assertEquals(listOf("a", "b"), table[0])
        assertEquals(listOf("plain", "with,comma"), table[1])
        assertEquals(listOf("with \"quote\"", "line1\nline2"), table[2])
    }

    @Test
    fun `csv decode tolerates crlf and a bom`() {
        val table = Csv.decode("\uFEFFword,meaningZh\r\napple,苹果\r\n")
        assertEquals(listOf("word", "meaningZh"), table[0])
        assertEquals(listOf("apple", "苹果"), table[1])
        assertEquals(2, table.size)
    }

    // ---------------------------------------------------------------- dates

    @Test
    fun `date helpers produce stable buckets`() {
        val today = System.currentTimeMillis()
        assertTrue(Dates.isToday(today))
        assertTrue(!Dates.isToday(0L))
        assertEquals(7, Dates.recentDayStarts(7).size)
        assertEquals(Dates.startOfDay(today), Dates.recentDayStarts(7).last())
        assertEquals("从未", Dates.relativeDay(0L))
        assertEquals("今天", Dates.relativeDay(today))
    }

    private fun word(text: String): Word = Word(
        id = text.lowercase(),
        text = text,
        senses = listOf(Sense(PartOfSpeech.NOUN, "释义")),
        createdAt = 1L,
        updatedAt = 1L
    )
}
