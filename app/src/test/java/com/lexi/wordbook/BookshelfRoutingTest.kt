package com.lexi.wordbook

import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.WordFilter
import com.lexi.wordbook.ui.screens.ENTERED_ALL
import com.lexi.wordbook.ui.screens.ShelfDestination
import com.lexi.wordbook.ui.screens.UNGROUPED_ID
import com.lexi.wordbook.ui.screens.shelfDestination
import com.lexi.wordbook.ui.screens.shelfFilterGroupId
import com.lexi.wordbook.ui.screens.toRealGroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bookshelf routing — regression cover for 「点未分组却进了别的组」.
 *
 * `null` was doing double duty as both "no group filter" (「全部词条」) and 「未分组」, so the
 * 未分组 card could not be told apart from "nothing open". [shelfDestination] and
 * [shelfFilterGroupId] keep the three cases distinct; these tests pin that down.
 */
class BookshelfRoutingTest {

    private fun word(text: String, groupId: String?): Word = Word(
        id = text,
        text = text,
        senses = listOf(Sense(PartOfSpeech.NOUN, "释义")),
        groupId = groupId
    )

    /** Mimics the repository filter so the round trip can be checked. */
    private fun applyFilter(words: List<Word>, filter: WordFilter): List<Word> = words.filter { word ->
        filter.groupId == null || word.groupId == toRealGroupId(filter.groupId)
    }

    // ------------------------------------------------------------ destination

    @Test
    fun `the ungrouped card is its own destination`() {
        assertEquals(ShelfDestination.Ungrouped, shelfDestination(null))
    }

    @Test
    fun `the all words card is its own destination`() {
        assertEquals(ShelfDestination.All, shelfDestination(ENTERED_ALL))
        // WordRepository.NO_GROUP carries the same value; both spellings must route the same.
        assertEquals(
            ShelfDestination.All,
            shelfDestination(com.lexi.wordbook.data.WordRepository.NO_GROUP)
        )
    }

    @Test
    fun `a real group keeps its id`() {
        assertEquals(ShelfDestination.Group("g1"), shelfDestination("g1"))
    }

    @Test
    fun `all and ungrouped are never the same destination`() {
        // The whole bug in one assertion: both used to be null internally.
        assertTrue(shelfDestination(ENTERED_ALL) != shelfDestination(null))
    }

    // ------------------------------------------------------------ filter

    @Test
    fun `each destination maps to a distinct group filter`() {
        // 全部词条 → no filter.
        assertEquals(null, shelfFilterGroupId(ShelfDestination.All))
        // 未分组 → the sentinel for groupId == null.
        assertEquals(UNGROUPED_ID, shelfFilterGroupId(ShelfDestination.Ungrouped))
        // one group → its id.
        assertEquals("g1", shelfFilterGroupId(ShelfDestination.Group("g1")))
    }

    @Test
    fun `the ungrouped filter really selects only ungrouped words`() {
        val words = listOf(
            word("apple", null),
            word("banana", null),
            word("cherry", "g1")
        )
        val filter = WordFilter(groupId = shelfFilterGroupId(ShelfDestination.Ungrouped))
        assertEquals(listOf("apple", "banana"), applyFilter(words, filter).map { it.text })
    }

    @Test
    fun `the all words filter keeps every group`() {
        val words = listOf(word("apple", null), word("cherry", "g1"))
        val filter = WordFilter(groupId = shelfFilterGroupId(ShelfDestination.All))
        assertEquals(listOf("apple", "cherry"), applyFilter(words, filter).map { it.text })
    }

    @Test
    fun `a group filter selects only that group`() {
        val words = listOf(
            word("apple", null),
            word("cherry", "g1"),
            word("date", "g2")
        )
        val filter = WordFilter(groupId = shelfFilterGroupId(ShelfDestination.Group("g1")))
        assertEquals(listOf("cherry"), applyFilter(words, filter).map { it.text })
    }

    @Test
    fun `a group whose name looks like another group is still selected by id`() {
        // Headwords inside a group page must come from that group only, whatever the names are.
        val words = listOf(
            word("a", "CET4-2022.12第一套"),
            word("b", "CET4-2022.12第二套")
        )
        val filter = WordFilter(groupId = shelfFilterGroupId(ShelfDestination.Group("CET4-2022.12第二套")))
        assertEquals(listOf("b"), applyFilter(words, filter).map { it.text })
    }
}
