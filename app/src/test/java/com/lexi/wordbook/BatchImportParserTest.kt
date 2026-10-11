package com.lexi.wordbook

import com.lexi.wordbook.ai.AiWordParser
import com.lexi.wordbook.data.PartOfSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the 「AI 批量导入」 parser: JSON first, lenient line parsing as the fallback. */
class BatchImportParserTest {

    // Keys follow "headword@group" (empty group = 未分组), matching the repository rule.
    private val existing = setOf("bank@", "diet@", "bank@四级核心")

    @Test
    fun `parses a clean json array`() {
        val raw = """
            [
              {"word":"serendipity","phonetic":"/ˌserənˈdɪpəti/",
               "senses":[{"partOfSpeech":"n.","meaningZh":"机缘巧合","meaningEn":"happy accident"}],
               "tags":["雅思"],"group":"阅读生词","note":"易拼错"},
              {"word":"resilient","senses":[{"partOfSpeech":"adj.","meaningZh":"有韧性的"}]}
            ]
        """.trimIndent()

        val result = AiWordParser.parseBatch(raw, existing)
        assertTrue(result.usedJson)
        assertEquals(2, result.words.size)

        val first = result.words.first()
        assertEquals("serendipity", first.draft.text)
        assertEquals("/ˌserənˈdɪpəti/", first.draft.phonetic)
        assertEquals(PartOfSpeech.NOUN, first.draft.senses.first().partOfSpeech)
        assertEquals("机缘巧合", first.draft.senses.first().meaningZh)
        assertEquals("雅思", first.draft.tagsText)
        assertEquals("阅读生词", first.groupName)
        assertEquals("易拼错", first.draft.note)
        assertFalse(first.isDuplicate)
    }

    @Test
    fun `tolerates a markdown fence and surrounding chatter`() {
        val raw = """
            好的，我整理好了：
            ```json
            [{"word":"mitigate","senses":[{"partOfSpeech":"v.","meaningZh":"减轻"}]}]
            ```
            需要我补充例句吗？
        """.trimIndent()
        val result = AiWordParser.parseBatch(raw, existing)
        assertTrue(result.usedJson)
        assertEquals(1, result.words.size)
        assertEquals("mitigate", result.words.single().draft.text)
    }

    @Test
    fun `accepts the flat shape some models return`() {
        val raw = """[{"word":"ephemeral","partOfSpeech":"adj.","meaningZh":"短暂的","english":"short-lived"}]"""
        val result = AiWordParser.parseBatch(raw, existing)
        assertTrue(result.usedJson)
        val sense = result.words.single().draft.senses.single()
        assertEquals(PartOfSpeech.ADJECTIVE, sense.partOfSpeech)
        assertEquals("短暂的", sense.meaningZh)
        assertEquals("short-lived", sense.meaningEn)
    }

    @Test
    fun `accepts a meanings array and a plain tag string`() {
        val raw = """[{"word":"crisp","meanings":["脆的","清爽的"],"tag":"食物 形容词"}]"""
        val result = AiWordParser.parseBatch(raw, existing)
        assertTrue(result.usedJson)
        val word = result.words.single()
        assertEquals(2, word.draft.senses.size)
        assertEquals(PartOfSpeech.OTHER, word.draft.senses.first().partOfSpeech)
        assertEquals("食物, 形容词", word.draft.tagsText)
    }

    @Test
    fun `flags words that are already in the library`() {
        val raw = """[{"word":"Bank","senses":[{"meaningZh":"银行"}]},{"word":"fresh","senses":[{"meaningZh":"新鲜的"}]}]"""
        val result = AiWordParser.parseBatch(raw, existing)
        assertTrue(result.words.first { it.rawWord == "Bank" }.isDuplicate)
        assertFalse(result.words.first { it.rawWord == "fresh" }.isDuplicate)
    }

    @Test
    fun `skips elements without a word or without any meaning`() {
        val raw = """[{"word":"","senses":[{"meaningZh":"x"}]},{"word":"nope","senses":[]},{"word":"ok","senses":[{"meaningZh":"可以"}]}]"""
        val result = AiWordParser.parseBatch(raw, existing)
        assertEquals(1, result.words.size)
        assertEquals("ok", result.words.single().draft.text)
    }

    @Test
    fun `falls back to line parsing when the model ignores json`() {
        val raw = """
            1. diet /ˈdaɪət/ n. 日常饮食
            2. resilient adj. 有韧性的
            - mitigate v. 减轻；缓解
            3. give up phr. 放弃
        """.trimIndent()
        val result = AiWordParser.parseBatch(raw, existing)
        assertFalse(result.usedJson)
        assertEquals(4, result.words.size)
        assertEquals(listOf("diet", "resilient", "mitigate", "give up"), result.words.map { it.rawWord })

        val diet = result.words.first { it.rawWord == "diet" }
        assertEquals("/ˈdaɪət/", diet.draft.phonetic)
        assertEquals(PartOfSpeech.NOUN, diet.draft.senses.first().partOfSpeech)
        assertEquals("日常饮食", diet.draft.senses.first().meaningZh)
        assertTrue(diet.isDuplicate)

        val giveUp = result.words.first { it.rawWord == "give up" }
        assertEquals(PartOfSpeech.PHRASE, giveUp.draft.senses.first().partOfSpeech)
    }

    @Test
    fun `falls back to pipe separated lines`() {
        val raw = "abandon|/əˈbændən/|v. 放弃\nbloom|/bluːm/|n. 花；v. 开花"
        val result = AiWordParser.parseBatch(raw, existing)
        assertFalse(result.usedJson)
        assertEquals(2, result.words.size)
        assertEquals("abandon", result.words[0].rawWord)
        assertEquals("放弃", result.words[0].draft.senses.first().meaningZh)
        assertEquals(2, result.words[1].draft.senses.size)
        assertTrue(result.words[1].draft.senses.first().meaningZh.contains("花"))
    }

    @Test
    fun `garbage input yields no words and a note`() {
        val result = AiWordParser.parseBatch("对不起，我无法完成这个请求。", existing)
        assertTrue(result.words.isEmpty())
        assertTrue(result.note.isNotBlank())
    }

    @Test
    fun `json array extraction handles braces inside strings`() {
        val raw = """[{"word":"brace","senses":[{"meaningZh":"大括号 { } 的意思"}]}]"""
        val extracted = AiWordParser.extractJsonArray(raw)
        assertTrue(extracted != null && extracted.startsWith("[") && extracted.endsWith("]"))
        val result = AiWordParser.parseBatch(raw, existing)
        assertEquals("大括号 { } 的意思", result.words.single().draft.senses.single().meaningZh)
    }
}
