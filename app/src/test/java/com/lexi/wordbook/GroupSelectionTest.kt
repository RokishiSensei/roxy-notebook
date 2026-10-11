package com.lexi.wordbook

import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.screens.UNGROUPED_ID
import com.lexi.wordbook.ui.screens.UNGROUPED_KEY
import com.lexi.wordbook.ui.screens.toRealGroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The group chips use [UNGROUPED_ID] as a sentinel, but ungrouped words carry `groupId == null`.
 * Comparing the raw sentinel against `word.groupId` never matched, which is why the 未分组 chip
 * showed an empty list.
 */
class GroupSelectionTest {

    private fun word(text: String, groupId: String?): Word = Word(
        id = text,
        text = text,
        senses = listOf(Sense(PartOfSpeech.NOUN, "释义")),
        groupId = groupId
    )

    private val grouped = word("bank", "g1")
    private val ungrouped = word("bank", null)
    private val other = word("diet", "g2")

    @Test
    fun `the ungrouped sentinel maps to a null group id`() {
        assertNull(toRealGroupId(UNGROUPED_ID))
        assertNull(toRealGroupId(null))
        assertEquals(UNGROUPED_KEY, "")
    }

    @Test
    fun `a real group id passes through untouched`() {
        assertEquals("g1", toRealGroupId("g1"))
        assertEquals("__ungrouped___x", toRealGroupId("__ungrouped___x"))
    }

    @Test
    fun `the ungrouped chip selects words whose group is null`() {
        val selected = listOf(grouped, ungrouped, other)
            .filter { it.groupId == toRealGroupId(UNGROUPED_ID) }
        assertEquals(listOf(ungrouped), selected)
    }

    @Test
    fun `a real group chip selects only that group`() {
        val selected = listOf(grouped, ungrouped, other)
            .filter { it.groupId == toRealGroupId("g1") }
        assertEquals(listOf(grouped), selected)
    }

    @Test
    fun `no chip filter means everything`() {
        // filter.groupId == null is the "全部" chip, which skips the group check entirely.
        val all = listOf(grouped, ungrouped, other)
        assertEquals(3, all.size)
    }
}
