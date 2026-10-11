package com.lexi.wordbook

import com.lexi.wordbook.ui.screens.looksLikeSecret
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the 「AI 批量导入」 input screen.
 *
 * In practice a copied API key was auto-pasted into the word list and then sent to the model:
 * useless, and a privacy leak. The screen now refuses to auto-paste a credential and turns a
 * manual paste into a clear error instead of a cloud call.
 */
class BatchImportInputTest {

    @Test
    fun `an openai style key is recognised`() {
        assertTrue(looksLikeSecret("sk-c18c1afcd8da4ef89df9856b1d039c57"))
        assertTrue(looksLikeSecret("  sk-proj-abcdefghijklmnopqrstuvwxyz012345  "))
        assertTrue(looksLikeSecret("sk_live_abcdefghijklmnop"))
    }

    @Test
    fun `other provider key shapes are recognised`() {
        assertTrue(looksLikeSecret("gsk_abcdefghijklmnopqrstuvwxyz"))
        assertTrue(looksLikeSecret("xai-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(looksLikeSecret("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue(looksLikeSecret("AIzaSyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6"))
    }

    @Test
    fun `a long unbroken token is treated as a secret`() {
        assertTrue(looksLikeSecret("0123456789abcdef0123456789abcdef"))
        // A single long word of plain letters is a word, not a key.
        assertTrue(!looksLikeSecret("a".repeat(40)))
    }

    @Test
    fun `real word lists are never mistaken for secrets`() {
        val list = """
            diet
            nutritionist
            extra
            nourishing
            variety
            thickening agent
        """.trimIndent()
        assertFalse(looksLikeSecret(list))

        // Single short words are fine.
        assertFalse(looksLikeSecret("diet"))
        assertFalse(looksLikeSecret("thickening agent"))
        // A long line that contains spaces is a sentence, not a token.
        assertFalse(looksLikeSecret("the quick brown fox jumps over the lazy dog every morning"))
        // A blank box is not a secret either — the caller handles emptiness separately.
        assertFalse(looksLikeSecret("   "))
    }

    @Test
    fun `a multi line list that happens to start with sk is still a list`() {
        assertFalse(looksLikeSecret("ski\nskill\nskate"))
    }

    @Test
    fun `an ai answer with duplicate headwords is parsed once but keeps every row`() {
        // The preview list is keyed by index, so duplicates can no longer collide and crash it.
        val raw = """
            [
              {"word":"Apple","senses":[{"meaningZh":"苹果"}]},
              {"word":"apple","senses":[{"meaningZh":"苹果(重复)"}]},
              {"word":"banana","senses":[{"meaningZh":"香蕉"}]}
            ]
        """.trimIndent()
        val words = com.lexi.wordbook.ai.AiWordParser.parseBatch(raw, emptySet()).words
        assertEquals(3, words.size)
        // Two rows share the same lowercase headword: exactly the case that used to break the
        // LazyColumn keys.
        val keys = words.map { it.rawWord.lowercase() }
        assertEquals(keys.size, keys.distinct().size + 1)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())
    }
}
