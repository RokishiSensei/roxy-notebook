package com.lexi.wordbook

import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.QuizAnswer
import com.lexi.wordbook.ui.QuizPool
import com.lexi.wordbook.ui.QuizSelection
import com.lexi.wordbook.ui.QuizType
import com.lexi.wordbook.ui.screens.QuizBuilder
import com.lexi.wordbook.ui.screens.QuizMode
import com.lexi.wordbook.ui.screens.possibleQuestionCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the quiz generator, the answer evaluation and the retry-wrong-answers flow. */
class QuizBuilderTest {

    private fun pool(size: Int): List<Word> = (1..size).map { index ->
        Word(
            id = "id-$index",
            text = "word$index",
            phonetic = "/w$index/",
            senses = listOf(Sense(PartOfSpeech.NOUN, "释义$index", "meaning $index")),
            status = LearningStatus.NEW,
            createdAt = index.toLong(),
            updatedAt = index.toLong()
        )
    }

    @Test
    fun `choice quiz offers four unique options including the answer`() {
        val items = QuizBuilder.build(pool(10), QuizType.CHOICE_ZH_TO_EN, 5)
        assertEquals(5, items.size)
        items.forEach { item ->
            assertEquals(QuizMode.CHOICE, item.mode)
            assertEquals(4, item.options.size)
            assertEquals(4, item.options.distinct().size)
            assertTrue(item.options.contains(item.answer))
            assertTrue(item.promptIsChinese)
            // Prompt is the Chinese meaning, answer is the English headword.
            assertTrue(item.prompt.startsWith("释义"))
            assertTrue(item.answer.startsWith("word"))
        }
    }

    @Test
    fun `reverse choice asks for the Chinese meaning`() {
        val items = QuizBuilder.build(pool(8), QuizType.CHOICE_EN_TO_ZH, 4)
        assertEquals(4, items.size)
        items.forEach { item ->
            assertFalse(item.promptIsChinese)
            assertTrue(item.prompt.startsWith("word"))
            assertTrue(item.answer.startsWith("释义"))
        }
    }

    @Test
    fun `choice questions never repeat a word`() {
        val items = QuizBuilder.build(pool(12), QuizType.CHOICE_ZH_TO_EN, 12)
        assertEquals(12, items.map { it.wordId }.distinct().size)
    }

    @Test
    fun `spelling quiz hides the answer and keeps the meaning`() {
        val items = QuizBuilder.build(pool(6), QuizType.SPELLING, 4)
        assertEquals(4, items.size)
        items.forEach { item ->
            assertEquals(QuizMode.SPELLING, item.mode)
            assertTrue(item.options.isEmpty())
            assertTrue(item.prompt.startsWith("释义"))
            assertTrue(item.answer.startsWith("word"))
            assertTrue(item.promptIsChinese)
        }
    }

    @Test
    fun `mixed quiz contains both modes`() {
        val items = QuizBuilder.build(pool(20), QuizType.MIXED, 10)
        assertEquals(10, items.size)
        assertTrue(items.any { it.mode == QuizMode.CHOICE })
        assertTrue(items.any { it.mode == QuizMode.SPELLING })
    }

    @Test
    fun `pools that are too small or lack meanings produce nothing`() {
        assertTrue(QuizBuilder.build(emptyList(), QuizType.CHOICE_ZH_TO_EN, 10).isEmpty())
        assertTrue(QuizBuilder.build(pool(1), QuizType.CHOICE_ZH_TO_EN, 10).isEmpty())
        val noMeaning = listOf(Word(id = "x", text = "xyz", senses = listOf(Sense())))
        assertTrue(QuizBuilder.build(noMeaning, QuizType.SPELLING, 10).isEmpty())
    }

    @Test
    fun `question count never exceeds the pool`() {
        val items = QuizBuilder.build(pool(3), QuizType.SPELLING, 50)
        assertEquals(3, items.size)
    }

    @Test
    fun `answer evaluation ignores case and surrounding spaces`() {
        val items = QuizBuilder.build(pool(6), QuizType.SPELLING, 1)
        val item = items.first()
        assertTrue(item.evaluate("  ${item.answer.uppercase()} "))
        assertFalse(item.evaluate(item.answer + "x"))
        assertFalse(item.evaluate(""))
    }

    @Test
    fun `answers are recorded with the correct flag`() {
        val items = QuizBuilder.build(pool(6), QuizType.SPELLING, 2)
        val item = items.first()
        assertTrue(item.toAnswer(item.answer).correct)
        assertFalse(item.toAnswer("nope").correct)
        assertEquals(item.meaningZh, item.toAnswer("nope").meaningZh)
    }

    @Test
    fun `retry builds questions only for the wrong answers`() {
        val words = pool(8)
        val wrong = listOf(
            QuizAnswer(prompt = "释义1", answer = "word1", given = "nope", correct = false),
            QuizAnswer(prompt = "释义2", answer = "word2", given = "nope", correct = false)
        )
        val retry = QuizBuilder.retryFrom(words, wrong, QuizType.SPELLING)
        assertEquals(2, retry.size)
        assertEquals(setOf("word1", "word2"), retry.map { it.answer }.toSet())

        // Options still come from the whole list, so they stay plausible.
        val retryChoice = QuizBuilder.retryFrom(words, wrong, QuizType.CHOICE_ZH_TO_EN)
        assertEquals(2, retryChoice.size)
        assertEquals(setOf("word1", "word2"), retryChoice.map { it.answer }.toSet())
        assertTrue(retryChoice.all { it.options.size == 4 && it.options.contains(it.answer) })
    }

    @Test
    fun `retry tolerates deleted words`() {
        val words = pool(4)
        val wrong = listOf(
            QuizAnswer(prompt = "已删除", answer = "gone", given = "x", correct = false)
        )
        assertTrue(QuizBuilder.retryFrom(words, wrong, QuizType.SPELLING).isEmpty())
    }

    @Test
    fun `possible question count reflects the four-option requirement`() {
        assertEquals(6, possibleQuestionCount(pool(6), QuizType.SPELLING))
        assertEquals(6, possibleQuestionCount(pool(6), QuizType.CHOICE_ZH_TO_EN))
        assertEquals(0, possibleQuestionCount(pool(1), QuizType.CHOICE_ZH_TO_EN))
        assertEquals(0, possibleQuestionCount(pool(3), QuizType.MIXED))
        assertEquals(3, possibleQuestionCount(pool(3), QuizType.SPELLING))
    }

    @Test
    fun `pool labels are human readable`() {
        assertEquals("全部词条", QuizPool.ALL.labelZh)
        assertNotEquals("", QuizSelection().toString())
    }
}
