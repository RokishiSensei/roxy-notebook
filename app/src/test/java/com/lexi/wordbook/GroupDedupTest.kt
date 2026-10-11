package com.lexi.wordbook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The uniqueness rule: a headword may exist **once per group**, because different groups are
 * meant to be able to hold the same word (四级核心 vs 阅读生词), while the list as a whole must
 * never contain two identical entries in the same bucket.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GroupDedupTest {

    private fun repository(): WordRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE).edit().clear().commit()
        return WordRepository(context)
    }

    private fun draft(id: String, text: String, groupId: String? = null) = WordDraft(
        id = id,
        text = text,
        senses = listOf(Sense(PartOfSpeech.NOUN, "释义")),
        groupId = groupId
    )

    @Test
    fun `dedup key combines headword and group`() {
        val repo = repository()
        assertEquals("bank@", repo.dedupKey("Bank", null))
        assertEquals("bank@g1", repo.dedupKey(" bank ", "g1"))
        assertFalse(repo.dedupKey("bank", "g1") == repo.dedupKey("bank", "g2"))
        assertFalse(repo.dedupKey("bank", null) == repo.dedupKey("bank", "g1"))
    }

    @Test
    fun `the same word can live in two different groups`() {
        val repo = repository()
        val g1 = repo.createGroup("四级核心")
        val g2 = repo.createGroup("阅读生词")

        repo.upsert(draft("a", "bank", g1.id))
        repo.upsert(draft("b", "bank", g2.id))

        assertEquals(2, repo.words.value.size)
        assertEquals(setOf(g1.id, g2.id), repo.words.value.mapNotNull { it.groupId }.toSet())
    }

    @Test
    fun `the same word cannot be duplicated inside one group`() {
        val repo = repository()
        val group = repo.createGroup("四级核心")
        repo.upsert(draft("a", "bank", group.id))
        val duplicate = repo.findDuplicate("BANK", group.id)
        assertNotNull(duplicate)
        assertEquals("bank", duplicate!!.text)
        // A different group is not a duplicate.
        assertNull(repo.findDuplicate("bank", null))
        // The entry itself is not its own duplicate.
        assertNull(repo.findDuplicate("bank", group.id, exceptId = duplicate.id))
    }

    @Test
    fun `copies share nothing but the headword`() {
        val repo = repository()
        val g1 = repo.createGroup("四级核心")
        val g2 = repo.createGroup("AI 导入")
        val original = repo.upsert(draft("a", "bank", g1.id))
        repo.updateWord(original.id) { it.copy(starred = true, status = LearningStatus.FAMILIAR) }

        val copy = repo.duplicateInto(repo.get(original.id)!!, g2.id)
        assertFalse(copy.id == original.id)
        assertEquals("bank", copy.text)
        assertEquals(g2.id, copy.groupId)

        // The copy starts a fresh learning history; only the dictionary content travels.
        assertFalse(copy.starred)
        assertEquals(LearningStatus.NEW, copy.status)
        assertEquals(0, copy.reviewCount)

        // The copy starts a fresh learning history; only the dictionary content travels.
        assertFalse(copy.starred)
        assertEquals(LearningStatus.NEW, copy.status)
        assertEquals(0, copy.reviewCount)

        // Review metadata is independent per entry.
        repo.updateWord(copy.id) { it.copy(reviewCount = 5) }
        assertEquals(0, repo.get(original.id)!!.reviewCount)
        assertEquals(5, repo.get(copy.id)!!.reviewCount)
        assertTrue(repo.get(original.id)!!.starred)
    }

    @Test
    fun `copyInto skips headwords already present in the target group`() {
        val repo = repository()
        val g1 = repo.createGroup("A组")
        val g2 = repo.createGroup("B组")
        val a = repo.upsert(draft("a", "apple", g1.id))
        val b = repo.upsert(draft("b", "banana", g1.id))
        repo.upsert(draft("c", "apple", g2.id))

        val created = repo.copyInto(setOf(a.id, b.id), g2.id)
        // "apple" already exists in B组, only "banana" is copied.
        assertEquals(1, created)
        assertEquals(4, repo.words.value.size)
        assertEquals(2, repo.words.value.count { it.text == "banana" })
    }

    @Test
    fun `moveAndCopy moves and leaves copies behind`() {
        val repo = repository()
        val g1 = repo.createGroup("A组")
        val g2 = repo.createGroup("B组")
        val g3 = repo.createGroup("C组")
        val a = repo.upsert(draft("a", "apple", g1.id))
        val b = repo.upsert(draft("b", "banana", g1.id))

        val copies = repo.moveAndCopy(setOf(a.id, b.id), g2.id, copyToId = g3.id)
        assertEquals(2, copies)
        assertEquals(4, repo.words.value.size)
        assertEquals(2, repo.words.value.count { it.groupId == g2.id })
        assertEquals(2, repo.words.value.count { it.groupId == g3.id })
    }

    @Test
    fun `deleting a group removes the entries that live only there`() {
        val repo = repository()
        val g1 = repo.createGroup("临时组")
        val g2 = repo.createGroup("保留组")
        repo.upsert(draft("a", "bank", g1.id))
        repo.upsert(draft("b", "bank", g2.id))
        repo.upsert(draft("c", "only-here", g1.id))

        repo.deleteGroup(g1.id)

        // "bank" survives in the remaining group; "only-here" disappears with its group.
        // Nothing is copied into 未分组, so the list never holds a duplicate.
        assertEquals(1, repo.words.value.size)
        assertEquals("bank", repo.words.value.single().text)
        assertEquals(g2.id, repo.words.value.single().groupId)
    }

    @Test
    fun `deleting a group never produces two entries with the same headword`() {
        val repo = repository()
        val group = repo.createGroup("临时组")
        repo.upsert(draft("a", "bank", group.id))
        repo.upsert(draft("b", "bank", null))

        repo.deleteGroup(group.id)

        // The grouped copy is dropped because an ungrouped one already exists.
        assertEquals(1, repo.words.value.size)
        assertNull(repo.words.value.single().groupId)
    }

    @Test
    fun `csv import respects groups so the same word imports into two groups`() {
        val repo = repository()
        val payload = """
            word	senses	group
            bank	n. 银行	四级核心
            bank	n. 河岸	阅读生词
            bank	n. 银行	四级核心
        """.trimIndent()

        assertEquals(2, repo.importCsv(payload))
        assertEquals(2, repo.words.value.size)
        assertEquals(2, repo.groups.value.size)
        // Re-importing into the same group merges instead of adding a third entry.
        assertEquals(2, repo.importCsv(payload))
        assertEquals(2, repo.words.value.size)
    }

    @Test
    fun `loading drops legacy duplicates inside the same bucket`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Simulate data written before the rule existed: two "bank" rows with no group.
        val legacy = List(2) { index ->
            Word(
                id = "legacy-$index",
                text = "bank",
                senses = listOf(Sense(PartOfSpeech.NOUN, "银行")),
                createdAt = index.toLong()
            )
        }
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        prefs.edit()
            .putString("words_json_v1", json.encodeToString(legacy))
            .commit()

        val repo = WordRepository(context)
        assertEquals(1, repo.words.value.size)
    }

    @Test
    fun `bundled CET4 pack parses and lands in its own group`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pack = com.lexi.wordbook.data.WordPackLoader.load(context, "wordpacks/cet4-core.json")
        assertNotNull(pack)
        assertTrue(pack!!.words.size > 200)

        val group = repository().let { repo ->
            val created = repo.createGroup(WordRepository.CET4_GROUP_NAME, "🎓", 0)
            val words = com.lexi.wordbook.data.WordPackLoader.toWords(pack.words, created.id)
            repo.insertAll(words)
            assertEquals(pack.words.size, repo.words.value.size)
            assertTrue(repo.words.value.all { it.groupId == created.id })
            // Every entry must carry at least one sense with a Chinese meaning.
            assertTrue(repo.words.value.all { word -> word.senses.any { it.meaningZh.isNotBlank() } })
            created
        }
        assertEquals(WordRepository.CET4_GROUP_NAME, group.name)
    }

    @Test
    fun `pack sense parsing keeps part of speech, english and examples`() {
        val noun = com.lexi.wordbook.data.WordPackLoader.parseSense("n. 银行 (a financial institution)")
        assertNotNull(noun)
        assertEquals(PartOfSpeech.NOUN, noun!!.partOfSpeech)
        assertEquals("银行", noun.meaningZh)
        assertEquals("a financial institution", noun.meaningEn)

        val verb = com.lexi.wordbook.data.WordPackLoader.parseSense("v. 放弃 例: He abandoned the plan.")
        assertEquals(PartOfSpeech.VERB, verb!!.partOfSpeech)
        assertEquals("放弃", verb.meaningZh)
        assertEquals("He abandoned the plan.", verb.example)

        assertNull(com.lexi.wordbook.data.WordPackLoader.parseSense("   "))
    }
}
