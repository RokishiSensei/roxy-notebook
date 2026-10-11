package com.lexi.wordbook

import com.lexi.wordbook.ai.AiWordParser
import com.lexi.wordbook.ui.ai.AiAssistant
import com.lexi.wordbook.ui.screens.SECRET_MARKER
import com.lexi.wordbook.ui.screens.looksLikeSecret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the reported "average，regardless，miles，thinner still does not work".
 *
 * Two independent bugs combined to produce a single nonsense entry:
 *
 * 1. [looksLikeSecret] treated the full-width-comma list as one long unbroken token — full-width
 *    punctuation is not ASCII whitespace and the string is 36 characters — so the whole list was
 *    replaced by [SECRET_MARKER] before the request was even sent.
 * 2. The model then answered with that placeholder, and the parser accepted `__SECRET__` as a
 *    headword, so the preview offered to import it.
 */
class BatchImportSecretGuardTest {

    /** The exact text from the report. */
    private val reported = "average，regardless，miles，thinner"

    @Test
    fun `a full-width comma separated list is not a secret`() {
        assertFalse(looksLikeSecret(reported))
    }

    @Test
    fun `the list splits into the four words the user typed`() {
        assertEquals(
            listOf("average", "regardless", "miles", "thinner"),
            AiAssistant.wordUnits(reported)
        )
        assertEquals(4, AiAssistant.countWordsInLine(reported))
    }

    @Test
    fun `ascii punctuation lists and space lists are not secrets either`() {
        assertFalse(looksLikeSecret("average,regardless,miles,thinner"))
        assertFalse(looksLikeSecret("average regardless miles thinner"))
        assertFalse(looksLikeSecret("average;regardless;miles"))
        assertFalse(looksLikeSecret("average、regardless、miles"))
        // Even a long single word is a word.
        assertFalse(looksLikeSecret("internationalization"))
        assertFalse(looksLikeSecret("a".repeat(40)))
    }

    @Test
    fun `real api keys are still caught`() {
        assertTrue(looksLikeSecret("sk-proj-9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c"))
        assertTrue(looksLikeSecret("sk_abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(looksLikeSecret("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue(looksLikeSecret("aizaSyABCDEFGHIJKLMNOPQRSTUVWXYZ012345"))
        assertTrue(looksLikeSecret("gsk_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))
        assertTrue(looksLikeSecret("xai-ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"))
        // A base64-ish blob: letters plus digits is enough structure to be a key.
        assertTrue(looksLikeSecret("9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c0d1e2f3a"))
        assertTrue(looksLikeSecret("abcdefghijklmnop0123456789qrstuv"))
    }

    @Test
    fun `short or empty input is never a secret`() {
        assertFalse(looksLikeSecret(""))
        assertFalse(looksLikeSecret("   "))
        assertFalse(looksLikeSecret("apple"))
        assertFalse(looksLikeSecret("apple\nbanana"))
    }

    // ------------------------------------------------------------ parser guard

    @Test
    fun `the secret placeholder is rejected as a headword`() {
        assertFalse(AiWordParser.isPlausibleHeadword(SECRET_MARKER))
        assertFalse(AiWordParser.isPlausibleHeadword("__secret__"))
        assertFalse(AiWordParser.isPlausibleHeadword("Secret"))
    }

    @Test
    fun `the placeholder never reaches the preview`() {
        // What the user saw: the model answered the placeholder and it became a row.
        val reply = """
            [
              {"word":"__SECRET__","senses":[{"partOfSpeech":"—","meaningZh":"无法识别的词条"}]}
            ]
        """.trimIndent()
        assertTrue(AiWordParser.parseBatch(reply, emptySet()).words.isEmpty())
        assertTrue(AiWordParser.parseBatchLoose(reply.lines().toString(), emptySet()).isEmpty())
    }

    @Test
    fun `a model explaining itself is not a headword`() {
        // "无法识别的词条（疑似占位符，非标准英文单词，未提供释义）" came back in the same answer.
        assertFalse(
            AiWordParser.isPlausibleHeadword("无法识别的词条（疑似占位符，非标准英文单词，未提供释义）")
        )
        // A short Chinese gloss is still allowed, in case a user really wants a Chinese entry.
        assertTrue(AiWordParser.isPlausibleHeadword("苹果"))
    }

    @Test
    fun `obvious junk is rejected but normal headwords pass`() {
        assertFalse(AiWordParser.isPlausibleHeadword(""))
        assertFalse(AiWordParser.isPlausibleHeadword("   "))
        assertFalse(AiWordParser.isPlausibleHeadword("---"))
        assertFalse(AiWordParser.isPlausibleHeadword("http://example.com"))
        assertFalse(AiWordParser.isPlausibleHeadword("a".repeat(200)))
        assertTrue(AiWordParser.isPlausibleHeadword("average"))
        assertTrue(AiWordParser.isPlausibleHeadword("thickening agent"))
        assertTrue(AiWordParser.isPlausibleHeadword("in contrast to"))
        assertTrue(AiWordParser.isPlausibleHeadword("take up"))
        assertTrue(AiWordParser.isPlausibleHeadword("well-known"))
    }

    @Test
    fun `a normal four word answer still parses`() {
        val reply = """
            [
              {"word":"average","senses":[{"partOfSpeech":"n.","meaningZh":"平均"}]},
              {"word":"regardless","senses":[{"partOfSpeech":"adv.","meaningZh":"不管"}]},
              {"word":"miles","senses":[{"partOfSpeech":"n.","meaningZh":"英里（复数）"}]},
              {"word":"thinner","senses":[{"partOfSpeech":"adj.","meaningZh":"更瘦的"}]}
            ]
        """.trimIndent()
        val words = AiWordParser.parseBatch(reply, emptySet(), reported).words
        assertEquals(
            listOf("average", "regardless", "miles", "thinner"),
            words.map { it.rawWord }
        )
        // None of them are model additions — they all came from the user's line.
        assertTrue(
            "unexpected new flags: " + words.filter { it.isNew }.map { it.rawWord },
            words.none { it.isNew }
        )
    }
}
