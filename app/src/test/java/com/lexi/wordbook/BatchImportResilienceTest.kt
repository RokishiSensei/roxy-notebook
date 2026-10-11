package com.lexi.wordbook

import com.lexi.wordbook.ai.AiApiFormat
import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ai.AiWordParser
import com.lexi.wordbook.ui.ai.AiAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「AI 批量导入」 robustness.
 *
 * The reported "batch import does nothing" traced back to a truncated model answer: with the
 * default 2048-token budget a 40-line chunk is cut off mid-JSON, `extractJsonArray` never finds
 * a closing bracket, and the line-based fallback cannot read JSON at all — so zero entries were
 * produced and the preview looked empty.
 */
class BatchImportResilienceTest {

    private fun config(maxTokens: Int) = AiConfig(
        apiFormat = AiApiFormat.OPENAI,
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
        maxTokens = maxTokens
    )

    // ---------------------------------------------------------------- truncated JSON

    @Test
    fun `a cut off array still yields the entries that arrived`() {
        // Three complete objects, then the model ran out of tokens mid-way through the fourth.
        val truncated = """
            [
              {"word":"apple","phonetic":"/ˈæpl/","senses":[{"partOfSpeech":"n.","meaningZh":"苹果"}]},
              {"word":"banana","phonetic":"/bəˈnɑːnə/","senses":[{"partOfSpeech":"n.","meaningZh":"香蕉"}]},
              {"word":"cherry","senses":[{"partOfSpeech":"n.","meaningZh":"樱桃"}]},
              {"word":"dragonfruit","senses":[{"partOfSpeech":"n.","mean
        """.trimIndent()

        val result = AiWordParser.parseBatch(truncated, emptySet())
        assertEquals(3, result.words.size)
        assertEquals(listOf("apple", "banana", "cherry"), result.words.map { it.rawWord })
        assertTrue(result.usedJson)
    }

    @Test
    fun `salvage ignores prose before and after the array`() {
        val raw = """
            好的，我整理好了：
            ```json
            [
              {"word":"apple","senses":[{"meaningZh":"苹果"}]},
              {"word":"banana","senses":[{"meaningZh":"香蕉"}]}
            ```
            以上共 2 个词条。
        """.trimIndent()
        val result = AiWordParser.parseBatch(raw, emptySet())
        assertEquals(listOf("apple", "banana"), result.words.map { it.rawWord })
    }

    @Test
    fun `salvage keeps nested objects intact`() {
        val raw = """
            [{"word":"bank","senses":[{"partOfSpeech":"n.","meaningZh":"银行","example":"a {test} string"}]},
             {"word":"diet","senses":[{"meaningZh":"饮食"}]}
        """.trimIndent()
        val result = AiWordParser.parseBatch(raw, emptySet())
        assertEquals(2, result.words.size)
        // The braces inside the example string must not be mistaken for structure.
        assertEquals("a {test} string", result.words[0].draft.senses.first().example)
    }

    @Test
    fun `salvage of a complete array behaves like the normal path`() {
        val raw = """[{"word":"apple","senses":[{"meaningZh":"苹果"}]}]"""
        assertEquals(1, AiWordParser.salvageObjects(raw).size)
        assertEquals(1, AiWordParser.parseBatch(raw, emptySet()).words.size)
    }

    @Test
    fun `no json at all falls back to the line reader`() {
        val raw = "apple /ˈæpl/ n. 苹果\nbanana n. 香蕉"
        val result = AiWordParser.parseBatch(raw, emptySet())
        assertEquals(2, result.words.size)
        assertTrue(!result.usedJson)
    }

    @Test
    fun `a compound phrase is kept as one entry`() {
        val raw = """
            [
              {"word":"thickening agent","phonetic":"/ˈθɪkənɪŋ ˈeɪdʒənt/","senses":[{"partOfSpeech":"phr.","meaningZh":"增稠剂"}]},
              {"word":"come away","senses":[{"partOfSpeech":"phr.","meaningZh":"离开；脱落"}]},
              {"word":"thickening","senses":[{"partOfSpeech":"n.","meaningZh":"增稠"}]}
            ]
        """.trimIndent()
        val result = AiWordParser.parseBatch(raw, emptySet())
        // "thickening agent" must NOT be split into "thickening" + "agent".
        assertEquals(listOf("thickening agent", "come away", "thickening"), result.words.map { it.rawWord })
        assertEquals(3, result.words.size)
        // The phrase carries its own part of speech and meaning.
        val phrase = result.words.first { it.rawWord == "thickening agent" }
        assertEquals("增稠剂", phrase.draft.senses.single().meaningZh)
    }

    @Test
    fun `the batch prompt asks for compound phrases and no skipped lines`() {
        val prompt = AiConfig.DEFAULT_BATCH_PROMPT
        assertTrue(prompt.contains("复合词组算一个词条"))
        assertTrue(prompt.contains("每一行都必须产出一个元素"))
        assertTrue(prompt.contains("phr."))
    }

    @Test
    fun `duplicate detection uses headword plus group`() {
        val existing = setOf("apple@四级核心", "banana@")
        val raw = """
            [
              {"word":"Apple","group":"四级核心","senses":[{"meaningZh":"苹果"}]},
              {"word":"banana","senses":[{"meaningZh":"香蕉"}]},
              {"word":"cherry","senses":[{"meaningZh":"樱桃"}]}
            ]
        """.trimIndent()
        val result = AiWordParser.parseBatch(raw, existing)
        assertTrue(result.words.first { it.rawWord == "Apple" }.isDuplicate)
        assertTrue(result.words.first { it.rawWord == "banana" }.isDuplicate)
        assertTrue(!result.words.first { it.rawWord == "cherry" }.isDuplicate)
        // apple exists in 四级核心 but not ungrouped, so an ungrouped apple is NOT a duplicate.
        val ungroupedApple = AiWordParser.parseBatch(
            """[{"word":"apple","senses":[{"meaningZh":"苹果"}]}]""",
            existing
        )
        assertTrue(!ungroupedApple.words.single().isDuplicate)
    }

    // ---------------------------------------------------------------- chunk sizing

    @Test
    fun `chunk size follows the configured output budget`() {
        // 2048 tokens ≈ 2048*4/5/300 = 5 entries per request.
        assertEquals(5, AiAssistant.linesPerChunkFor(2048))
        // The default budget fits a 20+ word list in a couple of requests.
        assertEquals(21, AiAssistant.linesPerChunkFor(8192))
        // A bigger budget allows bigger chunks…
        assertEquals(43, AiAssistant.linesPerChunkFor(16384))
        // …but a huge one is still capped so a single bad answer cannot lose everything.
        assertEquals(60, AiAssistant.linesPerChunkFor(1_000_000))
        // A tiny budget keeps at least a few lines per request.
        assertEquals(5, AiAssistant.linesPerChunkFor(100))
    }

    @Test
    fun `a zero budget falls back to the default instead of producing zero lines`() {
        // 0 means "unset", so it behaves like the shipped default rather than the minimum.
        assertEquals(
            AiAssistant.linesPerChunkFor(AiConfig.DEFAULT_MAX_TOKENS),
            AiAssistant.linesPerChunkFor(0)
        )
        assertEquals(21, AiAssistant.linesPerChunkFor(0))
    }

    @Test
    fun `chunk sizing is monotonic in the budget`() {
        // 0 is the "unset" sentinel, so only positive budgets form the monotonic series.
        val sizes = listOf(512, 1024, 2048, 4096, 8192, 16384, 32768)
            .map(AiAssistant::linesPerChunkFor)
        assertEquals(sizes.sorted(), sizes)
    }

    @Test
    fun `every automatic chunk size stays within the supported range`() {
        listOf(0, 1, 100, 2048, 1_000_000).forEach { budget ->
            val lines = AiAssistant.linesPerChunkFor(budget)
            assertTrue("budget=$budget -> $lines", lines in 5..60)
        }
    }

    @Test
    fun `computing the chunk size terminates instead of recursing`() {
        // Regression: an instance method named `linesPerChunk` shadowed the companion helper
        // and called itself, so tapping 交给 AI 整理 failed with "stack size 8188KB"
        // (StackOverflowError). Reaching the assertion at all proves the call returns.
        val config = config(8192).copy(batchSize = 23)
        val size = AiAssistant.chunkSizeFor(config)
        assertEquals(23, size)
        // Calling it repeatedly must stay cheap and finite.
        assertEquals(size, AiAssistant.chunkSizeFor(config))
    }

    @Test
    fun `a manual batch size overrides the automatic one`() {
        // 23 words should be importable in a single request when the user asks for it.
        val manual = config(8192).copy(batchSize = 23)
        assertEquals(23, AiAssistant.chunkSizeFor(manual))
        // The automatic value is used when batchSize is 0.
        assertEquals(21, AiAssistant.chunkSizeFor(config(8192)))
        // Extremes are clamped so the request stays sane.
        assertEquals(5, AiAssistant.chunkSizeFor(config(8192).copy(batchSize = 1)))
        assertEquals(300, AiAssistant.chunkSizeFor(config(8192).copy(batchSize = 10_000)))
    }

    @Test
    fun `the shipped default handles a long word list without falling back to the minimum`() {
        // Regression guard: the old 2048 default produced 5-line requests.
        val lines = AiAssistant.linesPerChunkFor(AiConfig.DEFAULT_MAX_TOKENS)
        assertTrue("default chunk = $lines", lines >= 20)
    }
}
