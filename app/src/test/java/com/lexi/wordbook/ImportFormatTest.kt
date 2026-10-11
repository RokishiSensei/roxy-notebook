package com.lexi.wordbook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Imports run against a real [WordRepository] (Robolectric gives us SharedPreferences), so the
 * delimiter detection, sense parsing and group creation are all exercised end to end.
 *
 * Pinned to SDK 34 and a plain Application: Robolectric only ships runtime jars up to SDK 36,
 * and the repositories under test never touch the real [com.lexi.wordbook.WordBookApp].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImportFormatTest {

    private fun repository(): WordRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE).edit().clear().commit()
        return WordRepository(context)
    }

    @Test
    fun `tab separated rows with a header are imported`() {
        val repo = repository()
        val payload = """
            word	phonetic	senses	tags	group	note	status
            diet	/ˈdaɪət/	n. 日常饮食；n. 规定饮食；v. 节食	健康 减肥	饮食	注意区分 die	NEW
        """.trimIndent()

        assertEquals(1, repo.importCsv(payload))
        val word = repo.words.value.single()
        assertEquals("diet", word.text)
        assertEquals("/ˈdaɪət/", word.phonetic)
        assertEquals(3, word.senses.size)
        assertEquals(PartOfSpeech.NOUN, word.senses[0].partOfSpeech)
        assertEquals("日常饮食", word.senses[0].meaningZh)
        assertEquals(PartOfSpeech.VERB, word.senses[2].partOfSpeech)
        assertEquals(listOf("健康", "减肥"), word.tags)
        assertEquals("注意区分 die", word.note)
        assertEquals(1, repo.groups.value.size)
        assertEquals("饮食", repo.groups.value.single().name)
        assertEquals(repo.groups.value.single().id, word.groupId)
    }

    @Test
    fun `a simple two column line works without a header`() {
        val repo = repository()
        assertEquals(2, repo.importCsv("apple|苹果\nbanana|香蕉"))
        val words = repo.words.value
        assertEquals(2, words.size)
        assertEquals("apple", words.first().text)
        assertEquals("苹果", words.first().senses.first().meaningZh)
    }

    @Test
    fun `english definitions in brackets and examples are extracted`() {
        val repo = repository()
        repo.importCsv("bank\t/bæŋk/\tn. 银行 (a financial institution) 例: I went to the bank.\tv")
        val sense = repo.words.value.single().senses.single()
        assertEquals(PartOfSpeech.NOUN, sense.partOfSpeech)
        assertEquals("银行", sense.meaningZh)
        assertEquals("a financial institution", sense.meaningEn)
        assertEquals("I went to the bank.", sense.example)
    }

    @Test
    fun `headerless rows fall back to positional columns`() {
        val repo = repository()
        repo.importCsv("resilient\t/rɪˈzɪliənt/\tadj. 有韧性的\t考研\t阅读\t\tNEW")
        val word = repo.words.value.single()
        assertEquals("resilient", word.text)
        assertEquals(PartOfSpeech.ADJECTIVE, word.senses.first().partOfSpeech)
        assertEquals(listOf("考研"), word.tags)
    }

    @Test
    fun `a single column list imports bare words`() {
        val repo = repository()
        assertEquals(3, repo.importCsv("apple\nbanana\ncherry"))
        assertEquals(listOf("apple", "banana", "cherry"), repo.words.value.map { it.text })
    }

    @Test
    fun `re-importing the same word merges senses instead of duplicating`() {
        val repo = repository()
        repo.importCsv("bank\tn. 银行")
        repo.importCsv("bank\tn. 河岸")
        val word = repo.words.value.single()
        assertEquals(2, word.senses.size)
        assertEquals("bank", word.text)
    }

    @Test
    fun `exported table can be imported again unchanged`() {
        val repo = repository()
        repo.importCsv("diet\t/ˈdaɪət/\tn. 日常饮食；v. 节食\t健康\t饮食\t注意区分\tNEW")
        val exported = repo.exportCsv()

        val second = repository()
        second.importCsv(exported)
        val word = second.words.value.single()
        assertEquals("diet", word.text)
        assertEquals("/ˈdaɪət/", word.phonetic)
        assertEquals(2, word.senses.size)
        assertEquals("日常饮食", word.senses[0].meaningZh)
        assertEquals(listOf("健康"), word.tags)
        assertEquals("饮食", second.groups.value.single().name)
    }

    @Test
    fun `csv quoting is honoured when the comma is the delimiter`() {
        val repo = repository()
        repo.importCsv("word,phonetic,senses,tags\n\"bank\",\"/bæŋk/\",\"n. 银行, 河岸\",\"a,b\"")
        val word = repo.words.value.single()
        assertEquals("bank", word.text)
        assertEquals(listOf("a", "b"), word.tags)
        assertTrue(word.senses.isNotEmpty())
    }

    @Test
    fun `empty or garbage input imports nothing and does not crash`() {
        val repo = repository()
        assertEquals(0, repo.importCsv(""))
        assertEquals(0, repo.importCsv("   \n  \n"))
        assertEquals(0, repo.words.value.size)
    }

    @Test
    fun `template constant round trips through the importer`() {
        val repo = repository()
        val count = repo.importCsv(WordRepository.IMPORT_TEMPLATE)
        assertEquals(3, count)
        val serendipity = repo.words.value.first { it.text == "serendipity" }
        assertNotNull(serendipity.phonetic)
        assertTrue(serendipity.senses.first().meaningZh.contains("机缘巧合"))
        assertTrue(repo.groups.value.any { it.name == "阅读生词" })

        // The simplified template is equally valid.
        val second = repository()
        assertEquals(3, second.importCsv(WordRepository.IMPORT_TEMPLATE_SIMPLE))
        assertFalse(second.words.value.any { it.text.isBlank() })
    }
}
