package com.lexi.wordbook

import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ui.ai.AiAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch importer must handle input that is **not** one word per line.
 *
 * Reported bug: pasting `apple, banana, cherry` on a single line only recognised one word. The
 * chunker counted *lines*, so a wide line looked like one entry while the model returned three —
 * which also made every such chunk look truncated and get split over and over.
 */
class MultiWordLineTest {

    // ------------------------------------------------------------ splitting

    @Test
    fun `comma separated words on one line are three units`() {
        assertEquals(
            listOf("apple", "banana", "cherry"),
            AiAssistant.wordUnits("apple, banana, cherry")
        )
    }

    @Test
    fun `every separator people actually use is recognised`() {
        val expected = listOf("apple", "banana", "cherry")
        assertEquals(expected, AiAssistant.wordUnits("apple,banana,cherry"))
        assertEquals(expected, AiAssistant.wordUnits("apple, banana; cherry"))
        assertEquals(expected, AiAssistant.wordUnits("apple\tbanana\tcherry"))
        assertEquals(expected, AiAssistant.wordUnits("apple | banana | cherry"))
        assertEquals(expected, AiAssistant.wordUnits("apple/banana/cherry"))
        // Chinese punctuation.
        assertEquals(expected, AiAssistant.wordUnits("apple，banana，cherry"))
        assertEquals(expected, AiAssistant.wordUnits("apple；banana；cherry"))
        assertEquals(expected, AiAssistant.wordUnits("apple、banana、cherry"))
    }

    @Test
    fun `a line without a separator stays whole so phrases survive`() {
        // Splitting on bare spaces would tear "law of the sea" apart, and there is no way to tell
        // a phrase from a word list by counting — the model decides (prompt rule 6/10).
        assertEquals(
            listOf("apple banana cherry"),
            AiAssistant.wordUnits("apple banana cherry")
        )
        assertEquals(listOf("law of the sea"), AiAssistant.wordUnits("law of the sea"))
    }

    @Test
    fun `a plain phrase line stays in one piece`() {
        // Rule 6 of the prompt: a compound phrase is a single entry.
        assertEquals(listOf("thickening agent"), AiAssistant.wordUnits("thickening agent"))
        assertEquals(
            listOf("in contrast to"),
            AiAssistant.wordUnits("in contrast to")
        )
        assertEquals(listOf("take up"), AiAssistant.wordUnits("take up"))
    }

    @Test
    fun `one word per line keeps working`() {
        assertEquals(
            listOf("apple", "banana", "cherry"),
            AiAssistant.wordUnits("apple\nbanana\ncherry")
        )
    }

    @Test
    fun `a mixture of packing styles is handled`() {
        assertEquals(
            listOf("apple", "banana", "cherry", "diet", "produce"),
            AiAssistant.wordUnits("apple, banana\ncherry\ndiet, produce")
        )
    }

    @Test
    fun `list numbering is not treated as a word`() {
        assertEquals(listOf("apple", "banana"), AiAssistant.wordUnits("1. apple\n2) banana"))
        assertEquals(listOf("apple", "banana"), AiAssistant.wordUnits("1. apple, 2. banana"))
        assertEquals(listOf("apple"), AiAssistant.wordUnits("3、apple"))
    }

    @Test
    fun `blank lines and stray whitespace are ignored`() {
        assertEquals(listOf("apple"), AiAssistant.wordUnits("\n\n  apple  \n\n"))
        assertTrue(AiAssistant.wordUnits("   \n\t\n").isEmpty())
        assertTrue(AiAssistant.wordUnits("").isEmpty())
    }

    // ------------------------------------------------------------ counting

    @Test
    fun `a wide line counts as many words`() {
        // This is the number compared against the model's answer to detect truncation.
        assertEquals(3, AiAssistant.countWordsInLine("apple, banana, cherry"))
        assertEquals(1, AiAssistant.countWordsInLine("apple"))
        assertEquals(1, AiAssistant.countWordsInLine("thickening agent"))
        assertEquals(0, AiAssistant.countWordsInLine("   "))
    }

    @Test
    fun `counting matches the number of units produced`() {
        val line = "apple, banana; cherry\tdiet produce"
        assertEquals(AiAssistant.wordUnits(line).size, AiAssistant.countWordsInLine(line))
    }

    // ------------------------------------------------------------ prompt

    @Test
    fun `the prompt tells the model to split several words on one line`() {
        val prompt = AiConfig.DEFAULT_BATCH_PROMPT
        assertTrue(prompt.contains("一行里可能有多个单词"))
        assertTrue(prompt.contains("每一个单词都要单独产出一个元素"))
        // It must still protect compound phrases.
        assertTrue(prompt.contains("复合词组或固定搭配"))
    }
}
