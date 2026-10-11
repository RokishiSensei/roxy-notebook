package com.lexi.wordbook

import com.lexi.wordbook.ai.AiWordParser
import com.lexi.wordbook.data.AiSense
import com.lexi.wordbook.data.AiWordResult
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.ui.ai.AiAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the AI answer parsing and the mapping onto the app's data model. */
class AiParsingTest {

    private val goodJson = """
        {
          "word": "bank",
          "phonetic": "/bæŋk/",
          "senses": [
            {"partOfSpeech": "n.", "meaningZh": "银行", "meaningEn": "a financial institution", "example": "I went to the bank."},
            {"partOfSpeech": "n.", "meaningZh": "河岸", "meaningEn": "land alongside a river", "example": ""},
            {"partOfSpeech": "v.", "meaningZh": "把钱存入银行", "meaningEn": "to deposit money", "example": ""}
          ],
          "note": "注意区分 bank 与 blank",
          "tags": ["考研", "多义词"]
        }
    """.trimIndent()

    @Test
    fun `parses a plain json answer`() {
        val result = AiWordParser.parse(goodJson)
        assertNotNull(result)
        assertEquals("bank", result!!.word)
        assertEquals("/bæŋk/", result.phonetic)
        assertEquals(3, result.senses.size)
        assertEquals("银行", result.senses.first().meaningZh)
        assertEquals(listOf("考研", "多义词"), result.tags)
    }

    @Test
    fun `parses a fenced json answer with surrounding prose`() {
        val messy = """
            好的，这是查询结果：
            ```json
            {"word":"resilient","phonetic":"/rɪˈzɪliənt/","senses":[{"partOfSpeech":"adj.","meaningZh":"有韧性的"}]}
            ```
            希望有帮助！
        """.trimIndent()
        val result = AiWordParser.parse(messy)
        assertNotNull(result)
        assertEquals("resilient", result!!.word)
        assertEquals(1, result.senses.size)
    }

    @Test
    fun `nested braces inside strings do not break extraction`() {
        val tricky = """{"word":"brace","senses":[{"partOfSpeech":"n.","meaningZh":"大括号 { } 的意思","meaningEn":"a { brace }"}]}"""
        val result = AiWordParser.parse(tricky)
        assertNotNull(result)
        assertEquals("大括号 { } 的意思", result!!.senses.first().meaningZh)
    }

    @Test
    fun `empty senses are rejected`() {
        assertNull(AiWordParser.parse("""{"word":"x","senses":[]}"""))
        assertNull(AiWordParser.parse("完全不是 JSON"))
        assertNull(AiWordParser.parse(""))
    }

    @Test
    fun `loose parsing keeps plain text answers usable`() {
        val text = """
            bank /bæŋk/
            n. 银行；金融机构
            n. 河岸
            v. 把钱存入银行
        """.trimIndent()
        val result = AiWordParser.parseLoose(text, "bank")
        assertEquals("/bæŋk/", result.phonetic)
        assertEquals(3, result.senses.size)
        assertEquals("银行", result.senses.first().meaningZh)
        assertEquals("金融机构", result.senses.first().meaningEn)
    }

    @Test
    fun `part of speech aliases map onto the enum`() {
        assertEquals(PartOfSpeech.NOUN, PartOfSpeech.fromAi("n."))
        assertEquals(PartOfSpeech.NOUN, PartOfSpeech.fromAi("Noun"))
        assertEquals(PartOfSpeech.NOUN, PartOfSpeech.fromAi("名词"))
        assertEquals(PartOfSpeech.VERB, PartOfSpeech.fromAi("vt."))
        assertEquals(PartOfSpeech.ADJECTIVE, PartOfSpeech.fromAi(" adj. "))
        assertEquals(PartOfSpeech.PHRASE, PartOfSpeech.fromAi("phr."))
        assertEquals(PartOfSpeech.ABBREVIATION, PartOfSpeech.fromAi("abbr"))
        assertEquals(PartOfSpeech.OTHER, PartOfSpeech.fromAi(""))
        assertEquals(PartOfSpeech.OTHER, PartOfSpeech.fromAi("unknown-thing"))
    }

    @Test
    fun `merge fills the draft without discarding typed content`() {
        val current = WordDraft(
            id = "draft",
            text = "",
            senses = listOf(Sense(PartOfSpeech.NOUN, "我自己写的释义")),
            note = "我的笔记"
        )
        val merged = AiAssistant.merge(
            AiWordResult(
                word = "bank",
                phonetic = "/bæŋk/",
                senses = listOf(AiSense("n.", "银行", "a financial institution")),
                note = "AI 提示",
                tags = listOf("雅思")
            ),
            current
        )

        assertEquals("bank", merged.text)
        assertEquals("/bæŋk/", merged.phonetic)
        assertEquals("雅思", merged.tagsText)
        assertTrue(merged.note.contains("我的笔记"))
        assertTrue(merged.note.contains("AI 提示"))
        assertEquals(2, merged.senses.size)
        assertEquals("银行", merged.senses.first().meaningZh)
    }

    @Test
    fun `merge keeps the word the user already typed`() {
        val current = WordDraft(id = "draft", text = "give up")
        val merged = AiAssistant.merge(
            AiWordResult(word = "give", senses = listOf(AiSense("phr.", "放弃"))),
            current
        )
        assertEquals("give up", merged.text)
        assertEquals(1, merged.senses.size)
    }
}
