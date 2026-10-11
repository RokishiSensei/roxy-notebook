package com.lexi.wordbook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backup round trip: export → import into a fresh install must recreate the same groups and put
 * every word back into the group it was exported from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupRoundTripTest {

    private fun repository(): WordRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE).edit().clear().commit()
        return WordRepository(context)
    }

    private fun draft(id: String, text: String, groupId: String? = null, meaning: String = "释义") =
        WordDraft(
            id = id,
            text = text,
            phonetic = "/test/",
            senses = listOf(Sense(PartOfSpeech.NOUN, meaning, "english", "an example")),
            tagsText = "标签A 标签B",
            note = "备注",
            groupId = groupId
        )

    /** Groups, words in two groups, an ungrouped word, tags, notes and status. */
    private fun seed(repo: WordRepository) {
        val cet4 = repo.createGroup("四级核心", "🎓", 0)
        val reading = repo.createGroup("阅读生词", "📖", 1)
        repo.upsert(draft("a", "apple", cet4.id, "苹果").copy(status = LearningStatus.MASTERED))
        repo.upsert(draft("b", "bank", reading.id, "银行"))
        repo.upsert(draft("c", "cherry", null, "樱桃"))
    }

    @Test
    fun `csv export writes the group name for every word`() {
        val repo = repository()
        seed(repo)

        val csv = repo.exportCsv()
        val lines = csv.trim().lines()
        assertEquals("word,phonetic,senses,tags,group,note,status", lines.first())
        assertTrue(lines.any { it.startsWith("apple,") && it.contains(",四级核心,") })
        assertTrue(lines.any { it.startsWith("bank,") && it.contains(",阅读生词,") })
        // The ungrouped word exports an empty group column.
        assertTrue(lines.any { it.startsWith("cherry,") && it.contains(",,") })
    }

    @Test
    fun `importing an export into an empty app recreates the groups`() {
        val source = repository()
        seed(source)
        val csv = source.exportCsv()

        val fresh = repository()
        assertEquals(3, fresh.importCsv(csv))

        // Both groups exist again…
        val names = fresh.groups.value.map { it.name }.toSet()
        assertEquals(setOf("四级核心", "阅读生词"), names)

        // …and every word is in the right one.
        val cet4 = fresh.groups.value.first { it.name == "四级核心" }
        val reading = fresh.groups.value.first { it.name == "阅读生词" }
        assertEquals(
            listOf("apple"),
            fresh.words.value.filter { it.groupId == cet4.id }.map { it.text }
        )
        assertEquals(
            listOf("bank"),
            fresh.words.value.filter { it.groupId == reading.id }.map { it.text }
        )
        assertEquals(
            listOf("cherry"),
            fresh.words.value.filter { it.groupId == null }.map { it.text }
        )
    }

    @Test
    fun `group counts are non zero after a round trip`() {
        val source = repository()
        seed(source)
        val csv = source.exportCsv()

        val fresh = repository()
        fresh.importCsv(csv)

        // The reported bug: groups appeared with "0 个词条" after importing a backup.
        fresh.groups.value.forEach { group ->
            val count = fresh.words.value.count { it.groupId == group.id }
            assertTrue("「${group.name}」显示 $count 个词条", count > 0)
        }
    }

    @Test
    fun `content survives the round trip`() {
        val source = repository()
        seed(source)
        val csv = source.exportCsv()

        val fresh = repository()
        fresh.importCsv(csv)

        val apple = fresh.words.value.first { it.text == "apple" }
        assertEquals("/test/", apple.phonetic)
        assertEquals("苹果", apple.senses.first().meaningZh)
        assertEquals("english", apple.senses.first().meaningEn)
        assertEquals("an example", apple.senses.first().example)
        assertEquals(setOf("标签A", "标签B"), apple.tags.toSet())
        assertEquals("备注", apple.note)
        assertEquals(LearningStatus.MASTERED, apple.status)
    }

    @Test
    fun `the csv import result equals the exported word count`() {
        val source = repository()
        seed(source)
        val csv = source.exportCsv()

        val fresh = repository()
        assertEquals(source.words.value.size, fresh.importCsv(csv))
        assertEquals(source.words.value.size, fresh.words.value.size)
    }

    @Test
    fun `the same headword exported from two groups imports into both`() {
        val source = repository()
        val a = source.createGroup("A组", "📁", 0)
        val b = source.createGroup("B组", "📁", 1)
        source.upsert(draft("a", "bank", a.id, "银行"))
        source.upsert(draft("b", "bank", b.id, "河岸"))
        val csv = source.exportCsv()

        val fresh = repository()
        assertEquals(2, fresh.importCsv(csv))
        assertEquals(2, fresh.words.value.count { it.text == "bank" })
        assertEquals(setOf("A组", "B组"), fresh.groups.value.map { it.name }.toSet())
    }

    @Test
    fun `importing is idempotent for an unchanged backup`() {
        val source = repository()
        seed(source)
        val csv = source.exportCsv()

        val fresh = repository()
        fresh.importCsv(csv)
        val afterFirst = fresh.words.value.size
        val groups = fresh.groups.value.size

        fresh.importCsv(csv)
        assertEquals(afterFirst, fresh.words.value.size)
        assertEquals(groups, fresh.groups.value.size)
    }

    // ---------------------------------------------------------------- JSON backup

    @Test
    fun `the json backup carries the groups as well as the words`() {
        val repo = repository()
        seed(repo)
        val payload = repo.exportJson()

        assertTrue(payload.contains("\"groups\""))
        assertTrue(payload.contains("\"words\""))
        assertTrue(payload.contains("四级核心"))
        assertTrue(payload.contains("阅读生词"))
    }

    @Test
    fun `restoring a json backup into an empty app recreates the groups`() {
        val source = repository()
        seed(source)
        val payload = source.exportJson()

        val fresh = repository()
        assertEquals(3, fresh.importJson(payload))

        // The reported bug: words came back but every group chip read "0 个词条" because the
        // backup shipped word.groupId values pointing at groups that were never restored.
        assertEquals(setOf("四级核心", "阅读生词"), fresh.groups.value.map { it.name }.toSet())
        fresh.groups.value.forEach { group ->
            val count = fresh.words.value.count { it.groupId == group.id }
            assertTrue("「${group.name}」显示 $count 个词条", count > 0)
        }

        val cet4 = fresh.groups.value.first { it.name == "四级核心" }
        assertEquals("apple", fresh.words.value.first { it.groupId == cet4.id }.text)
    }

    @Test
    fun `a restored group keeps its emoji and colour`() {
        val source = repository()
        val group = source.createGroup("收藏", "⭐", 3)
        source.upsert(draft("a", "apple", group.id))
        val payload = source.exportJson()

        val fresh = repository()
        fresh.importJson(payload)

        val restored = fresh.groups.value.single()
        assertEquals("收藏", restored.name)
        assertEquals("⭐", restored.emoji)
        assertEquals(3, restored.colorIndex)
    }

    @Test
    fun `restoring onto an existing library reuses same named groups`() {
        val source = repository()
        seed(source)
        val payload = source.exportJson()

        // The target already has 四级核心: it must be reused, not duplicated.
        val target = repository()
        val existing = target.createGroup("四级核心", "🎓", 0)
        assertEquals(3, target.importJson(payload))

        assertEquals(2, target.groups.value.size)
        assertEquals(1, target.groups.value.count { it.name == "四级核心" })
        // The imported apple landed in the pre-existing group.
        assertEquals(
            "apple",
            target.words.value.first { it.groupId == existing.id }.text
        )
    }

    @Test
    fun `a legacy words only backup still imports and drops dangling group ids`() {
        val repo = repository()
        // What the previous build wrote: a bare array of words, no group definitions.
        val legacy = """
            [{"id":"a","text":"apple","groupId":"gone-forever",
              "senses":[{"partOfSpeech":"NOUN","meaningZh":"苹果"}]}]
        """.trimIndent()

        assertEquals(1, repo.importJson(legacy))
        assertEquals("apple", repo.words.value.single().text)
        // No group can be invented, so the word becomes ungrouped instead of pointing nowhere.
        assertEquals(null, repo.words.value.single().groupId)
        assertEquals(0, repo.groups.value.size)
    }

    @Test
    fun `json and csv backups are equally complete`() {
        val source = repository()
        seed(source)

        val viaJson = repository().also { it.importJson(source.exportJson()) }
        val viaCsv = repository().also { it.importCsv(source.exportCsv()) }

        assertEquals(
            viaJson.words.value.map { it.text }.sorted(),
            viaCsv.words.value.map { it.text }.sorted()
        )
        assertEquals(
            viaJson.groups.value.map { it.name }.sorted(),
            viaCsv.groups.value.map { it.name }.sorted()
        )
    }

    @Test
    fun `import format is detected from the content not the file name`() {
        // Storage access framework URIs rarely end in .csv, which is why a CSV backup used to be
        // handed to the JSON reader.
        assertTrue(com.lexi.wordbook.ui.screens.looksLikeJson("""{"words":[]}"""))
        assertTrue(com.lexi.wordbook.ui.screens.looksLikeJson("  [\n {\"id\":\"a\"} ]"))
        assertTrue(com.lexi.wordbook.ui.screens.looksLikeJson("\uFEFF{\"words\":[]}"))
        assertTrue(!com.lexi.wordbook.ui.screens.looksLikeJson("word,phonetic,senses"))
        assertTrue(!com.lexi.wordbook.ui.screens.looksLikeJson("apple 苹果\nbanana 香蕉"))
    }

    @Test
    fun `a headerless two column list does not invent groups`() {
        val repo = repository()
        assertEquals(2, repo.importCsv("apple 苹果\nbanana 香蕉"))
        assertEquals(2, repo.words.value.size)
        // No group column was present, so nothing is invented.
        assertTrue(repo.words.value.all { it.groupId == null })
        assertEquals(0, repo.groups.value.size)
    }
}
