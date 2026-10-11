package com.lexi.wordbook

import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ai.AiWordParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inflected input ("ran", "apples", "studies") makes the model emit both the form the user typed
 * and its dictionary form. The second kind is marked `isNew` so the preview can highlight it and
 * let the user drop it.
 */
class BatchInflectionTest {

    private val answer = """
        [
          {"word":"ran","phonetic":"/ræn/","senses":[{"partOfSpeech":"v.","meaningZh":"run 的过去式；跑"}]},
          {"word":"run","phonetic":"/rʌn/","senses":[{"partOfSpeech":"v.","meaningZh":"跑；经营"}]},
          {"word":"apples","phonetic":"/ˈæplz/","senses":[{"partOfSpeech":"n.","meaningZh":"apple 的复数；苹果"}]},
          {"word":"apple","phonetic":"/ˈæpl/","senses":[{"partOfSpeech":"n.","meaningZh":"苹果"}]}
        ]
    """.trimIndent()

    @Test
    fun `the typed form is kept and the dictionary form is flagged as new`() {
        val source = "ran\napples"
        val words = AiWordParser.parseBatch(answer, emptySet(), source).words

        // Everything the model returned is kept, in order.
        assertEquals(listOf("ran", "run", "apples", "apple"), words.map { it.rawWord })

        // "ran" and "apples" came from the paste; "run" and "apple" were added by the model.
        assertFalse(words.first { it.rawWord == "ran" }.isNew)
        assertFalse(words.first { it.rawWord == "apples" }.isNew)
        assertTrue(words.first { it.rawWord == "run" }.isNew)
        assertTrue(words.first { it.rawWord == "apple" }.isNew)
    }

    @Test
    fun `without the source text nothing is marked as new`() {
        // Older callers (and the JSON unit path) pass no source, so no row is highlighted.
        val words = AiWordParser.parseBatch(answer, emptySet()).words
        assertEquals(4, words.size)
        assertTrue(words.none { it.isNew })
    }

    @Test
    fun `a source line with extra columns still matches its headword`() {
        val source = """
            1. apples /ˈæplz/ n. 苹果
            ran, v. 跑
        """.trimIndent()
        val words = AiWordParser.parseBatch(answer, emptySet(), source).words
        assertFalse(words.first { it.rawWord == "apples" }.isNew)
        assertFalse(words.first { it.rawWord == "ran" }.isNew)
        assertTrue(words.first { it.rawWord == "apple" }.isNew)
        assertTrue(words.first { it.rawWord == "run" }.isNew)
    }

    @Test
    fun `matching ignores case and surrounding punctuation`() {
        val source = "\"Apples.\"\n(RAN)"
        val sourceWords = AiWordParser.headwordsOf(source)
        assertTrue("apples" in sourceWords)
        assertTrue("ran" in sourceWords)
    }

    @Test
    fun `a compound phrase from the input is not marked as new`() {
        val source = "thickening agent\nthickening"
        val reply = """
            [
              {"word":"thickening agent","senses":[{"meaningZh":"增稠剂"}]},
              {"word":"thickening","senses":[{"meaningZh":"增稠"}]},
              {"word":"thicken","senses":[{"meaningZh":"使变稠"}]}
            ]
        """.trimIndent()
        val words = AiWordParser.parseBatch(reply, emptySet(), source).words
        assertFalse(words.first { it.rawWord == "thickening agent" }.isNew)
        assertFalse(words.first { it.rawWord == "thickening" }.isNew)
        assertTrue(words.first { it.rawWord == "thicken" }.isNew)
    }

    @Test
    fun `the line based fallback also marks additions`() {
        // No JSON at all: the loose reader must apply the same rule.
        val raw = "ran v. run 的过去式\nrun v. 跑"
        val words = AiWordParser.parseBatchLoose(raw, emptySet(), AiWordParser.headwordsOf("ran"))
        assertEquals(listOf("ran", "run"), words.map { it.rawWord })
        assertFalse(words.first().isNew)
        assertTrue(words.last().isNew)
    }

    @Test
    fun `normaliseWord strips numbering and leading punctuation`() {
        assertEquals("apples.", AiWordParser.normaliseWord("  1. Apples.  "))
        assertEquals("thickening agent", AiWordParser.normaliseWord("2) thickening   agent"))
        assertEquals("ran\"", AiWordParser.normaliseWord("\"ran\""))
        assertEquals("", AiWordParser.normaliseWord("   "))
        // A trailing token must survive, or a phrase would collapse into a different word.
        assertEquals("thickening agent", AiWordParser.normaliseWord("thickening agent"))
    }

    @Test
    fun `list items are matched without their trailing punctuation`() {
        val source = "ran,\napples.\n\"studies\""
        val hw = AiWordParser.headwordsOf(source)
        assertTrue("ran" in hw)
        assertTrue("apples" in hw)
        assertTrue("studies" in hw)
    }

    @Test
    fun `the prompt asks for both the inflected form and its base`() {
        val prompt = AiConfig.DEFAULT_BATCH_PROMPT
        assertTrue(prompt.contains("原词和原形都要保留"))
        assertTrue(prompt.contains("过去式"))
        assertTrue(prompt.contains("复数"))
        // And it must not open the door to inventing unrelated words.
        assertTrue(prompt.contains("不要添加输入里没有的其他单词"))
    }

    @Test
    fun `duplicate detection still works alongside the new flag`() {
        val existing = setOf("apple@")
        val source = "apples"
        val words = AiWordParser.parseBatch(answer, existing, source).words
        val apple = words.first { it.rawWord == "apple" }
        // It is both model-added and already in the library.
        assertTrue(apple.isNew)
        assertTrue(apple.isDuplicate)
    }
}
