package com.lexi.wordbook

import com.lexi.wordbook.data.CleanSortMode
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.MainViewModel
import com.lexi.wordbook.util.Alphabet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「干净浏览」 list logic: A–Z / by-date ordering plus the in-group search.
 * Pure JVM — [MainViewModel.cleanBrowseList] takes the source list as a parameter.
 */
class CleanBrowseTest {

    private var clock = 1_000L

    private fun word(
        text: String,
        phonetic: String = "",
        senses: List<Sense> = listOf(Sense(PartOfSpeech.NOUN, "释义")),
        tags: List<String> = emptyList(),
        note: String = ""
    ): Word {
        clock += 1_000
        return Word(
            id = text + clock,
            text = text,
            phonetic = phonetic,
            senses = senses,
            tags = tags,
            note = note,
            status = LearningStatus.NEW,
            createdAt = clock,
            updatedAt = clock
        )
    }

    private val apple = word("apple", "/ˈæpl/", listOf(Sense(PartOfSpeech.NOUN, "苹果", "a round fruit")))
    private val banana = word("banana", "/bəˈnɑːnə/", listOf(Sense(PartOfSpeech.NOUN, "香蕉", "a long yellow fruit")))
    private val cherry = word(
        "cherry",
        "/ˈtʃeri/",
        listOf(Sense(PartOfSpeech.NOUN, "樱桃", "a small red fruit")),
        tags = listOf("水果"),
        note = "夏天"
    )
    private val source = listOf(cherry, apple, banana)

    @Test
    fun `alphabet mode orders by headword`() {
        val result = MainViewModel.cleanBrowseList(source, "", CleanSortMode.ALPHABET)
        assertEquals(listOf("apple", "banana", "cherry"), result.map { it.text })
    }

    @Test
    fun `recent mode puts the newest entry first`() {
        val result = MainViewModel.cleanBrowseList(source, "", CleanSortMode.RECENT)
        // cherry was created last.
        assertEquals(listOf("cherry", "banana", "apple"), result.map { it.text })
    }

    @Test
    fun `oldest mode puts the earliest entry first`() {
        val result = MainViewModel.cleanBrowseList(source, "", CleanSortMode.OLDEST)
        assertEquals(listOf("apple", "banana", "cherry"), result.map { it.text })
    }

    @Test
    fun `same timestamp falls back to the headword`() {
        val a = apple.copy(createdAt = 5_000)
        val b = banana.copy(createdAt = 5_000)
        val c = cherry.copy(createdAt = 5_000)
        val result = MainViewModel.cleanBrowseList(listOf(c, b, a), "", CleanSortMode.RECENT)
        assertEquals(listOf("apple", "banana", "cherry"), result.map { it.text })
    }

    @Test
    fun `search matches the headword case-insensitively`() {
        assertEquals(
            listOf("banana"),
            MainViewModel.cleanBrowseList(source, "BAN", CleanSortMode.ALPHABET).map { it.text }
        )
    }

    @Test
    fun `search matches phonetic chinese english tags and note`() {
        assertEquals("apple", single("ˈæpl"))
        assertEquals("cherry", single("樱桃"))
        assertEquals("banana", single("yellow fruit"))
        assertEquals("cherry", single("水果"))
        assertEquals("cherry", single("夏天"))
    }

    @Test
    fun `blank search returns everything and unknown terms return nothing`() {
        assertEquals(3, MainViewModel.cleanBrowseList(source, "   ", CleanSortMode.ALPHABET).size)
        assertTrue(MainViewModel.cleanBrowseList(source, "zzz", CleanSortMode.ALPHABET).isEmpty())
    }

    @Test
    fun `search trims the query`() {
        assertEquals("apple", single("  apple  "))
    }

    @Test
    fun `search combines with the chosen sort order`() {
        val wide = listOf(
            word("apple pie", senses = listOf(Sense(PartOfSpeech.NOUN, "苹果派"))),
            word("pineapple", senses = listOf(Sense(PartOfSpeech.NOUN, "菠萝"))),
            word("apple", senses = listOf(Sense(PartOfSpeech.NOUN, "苹果")))
        )
        val alphabetical = MainViewModel.cleanBrowseList(wide, "apple", CleanSortMode.ALPHABET)
            .map { it.text }
        assertEquals(listOf("apple", "apple pie", "pineapple"), alphabetical)

        val newest = MainViewModel.cleanBrowseList(wide, "apple", CleanSortMode.RECENT)
            .map { it.text }
        assertEquals(listOf("apple", "pineapple", "apple pie"), newest)
    }

    @Test
    fun `header index points at the first entry of every letter`() {
        // The LazyColumn has one leading header item, so the first word sits at index 1.
        val wide = listOf(word("apple"), word("avocado"), word("banana"), word("123 go"))
        val index = MainViewModel.cleanHeaderIndex(wide)
        // Letters come first: 1 header, 2 A-entries, then the B header at index 4.
        assertEquals(1, index["A"])
        assertEquals(4, index["B"])
        // ...and "#" (numeric/symbol headwords) is listed after them, like in the main list.
        assertEquals(6, index["#"])
        assertNull(index["Z"])
    }

    @Test
    fun `header index accounts for the group sizes`() {
        val a = (1..3).map { word("a$it") }
        val b = (1..2).map { word("b$it") }
        val index = MainViewModel.cleanHeaderIndex(a + b)
        assertEquals(1, index["A"])
        // 1 header item + 3 A-entries, then B starts at 5.
        assertEquals(5, index["B"])
    }

    @Test
    fun `a leading item offset shifts every letter header`() {
        // The library puts the search field / group chips / filters in the same LazyColumn, so
        // every header moves down. Tapping a letter used to land three rows too early.
        val a = (1..2).map { word("a$it") }
        val b = (1..1).map { word("b$it") }
        val shifted = MainViewModel.groupedHeaderIndex(
            com.lexi.wordbook.util.Alphabet.group(a + b),
            offset = 3
        )
        assertEquals(3, shifted["A"])
        // 3 controls + 1 A header + 2 A entries
        assertEquals(6, shifted["B"])
    }

    @Test
    fun `the grouped overload matches the word list overload`() {
        val words = listOf(word("apple"), word("avocado"), word("banana"))
        assertEquals(
            MainViewModel.cleanHeaderIndex(words, offset = 4),
            MainViewModel.groupedHeaderIndex(
                com.lexi.wordbook.util.Alphabet.group(words),
                offset = 4
            )
        )
    }

    @Test
    fun `active letters only include letters that exist`() {
        val wide = listOf(word("apple"), word("zebra"), word("123 go"))
        val active = Alphabet.activeKeys(wide)
        assertTrue("A" in active)
        assertTrue("Z" in active)
        assertTrue("#" in active)
        assertTrue("M" !in active)
        // "#" is always last so the rail reads A–Z then #.
        assertEquals("#", active.last())
    }

    @Test
    fun `header index is empty for an empty list`() {
        assertTrue(MainViewModel.cleanHeaderIndex(emptyList()).isEmpty())
    }

    @Test
    fun `sort labels are user facing`() {
        assertEquals("A–Z", CleanSortMode.ALPHABET.labelZh)
        assertEquals("最新在前", CleanSortMode.RECENT.labelZh)
        assertEquals("最早在前", CleanSortMode.OLDEST.labelZh)
        assertEquals(3, CleanSortMode.entries.size)
    }

    private fun single(query: String): String =
        MainViewModel.cleanBrowseList(source, query, CleanSortMode.ALPHABET).single().text
}
