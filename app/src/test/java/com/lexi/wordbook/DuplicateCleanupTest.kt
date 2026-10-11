package com.lexi.wordbook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「清理重复词条」 and the add-time duplicate guard.
 *
 * Duplicates are only reachable through older builds (the importer and `upsert` now refuse to
 * create them), so the tests build the bad state directly by writing raw entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DuplicateCleanupTest {

    private fun repository(): WordRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE).edit().clear().commit()
        return WordRepository(context)
    }

    private var seq = 0

    /** Writes a raw entry, bypassing the uniqueness guard, to recreate a legacy duplicate. */
    private fun WordRepository.forceInsert(
        text: String,
        groupId: String? = null,
        meaning: String = "释义",
        phonetic: String = "",
        tags: List<String> = emptyList(),
        status: LearningStatus = LearningStatus.NEW,
        starred: Boolean = false,
        reviewCount: Int = 0,
        createdAt: Long = 1_000L + seq++
    ): Word {
        val word = Word(
            id = "raw-${createdAt}-${text.hashCode()}",
            text = text,
            phonetic = phonetic,
            senses = listOf(Sense(PartOfSpeech.NOUN, meaning)),
            tags = tags,
            status = status,
            starred = starred,
            groupId = groupId,
            createdAt = createdAt,
            updatedAt = createdAt,
            reviewCount = reviewCount
        )
        insertAll(listOf(word))
        return word
    }

    // ------------------------------------------------------------ counting

    @Test
    fun `countDuplicates is zero for a clean library`() {
        val repo = repository()
        repo.upsert(
            com.lexi.wordbook.data.WordDraft(
                id = "a",
                text = "apple",
                senses = listOf(Sense(PartOfSpeech.NOUN, "苹果"))
            )
        )
        assertEquals(0, repo.countDuplicates())
    }

    @Test
    fun `countDuplicates sees a legacy double entry`() {
        val repo = repository()
        val group = repo.createGroup("四级核心", "🎓", 0)
        repo.forceInsert("observed", group.id, "观察")
        repo.forceInsert("observed", group.id, "观察；遵守")
        assertEquals(1, repo.countDuplicates())
        assertEquals(1, repo.countDuplicates(group.id))
        // An unrelated group has none.
        val other = repo.createGroup("阅读", "📖", 1)
        assertEquals(0, repo.countDuplicates(other.id))
    }

    @Test
    fun `the same headword in two groups is not a duplicate`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        val b = repo.createGroup("B组", "📁", 1)
        repo.forceInsert("bank", a.id)
        repo.forceInsert("bank", b.id)
        assertEquals(0, repo.countDuplicates())
        assertEquals(0, repo.countDuplicates(a.id))
    }

    // ------------------------------------------------------------ cleaning

    @Test
    fun `cleaning the whole library removes the extra entry`() {
        val repo = repository()
        val group = repo.createGroup("四级核心", "🎓", 0)
        repo.forceInsert("observed", group.id, "观察")
        repo.forceInsert("observed", group.id, "观察；遵守")
        repo.forceInsert("oil", group.id)

        assertEquals(1, repo.cleanDuplicates())
        assertEquals(2, repo.words.value.size)
        assertEquals(1, repo.words.value.count { it.text == "observed" })
        assertEquals(0, repo.countDuplicates())
    }

    @Test
    fun `cleaning one group leaves the others untouched`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        val b = repo.createGroup("B组", "📁", 1)
        repo.forceInsert("observed", a.id)
        repo.forceInsert("observed", a.id)
        repo.forceInsert("observed", b.id)
        repo.forceInsert("observed", b.id)

        assertEquals(1, repo.cleanDuplicates(a.id))
        assertEquals(1, repo.words.value.count { it.groupId == a.id })
        // B still holds a legacy duplicate — a scoped scan must not touch it.
        assertEquals(2, repo.words.value.count { it.groupId == b.id })
        assertEquals(1, repo.countDuplicates(b.id))
    }

    @Test
    fun `cleaning the ungrouped bucket works too`() {
        val repo = repository()
        repo.forceInsert("apple", null)
        repo.forceInsert("apple", null)
        // WordRepository.NO_GROUP scans everything; the ungrouped bucket is addressed by a null id.
        assertEquals(2, repo.words.value.count { it.groupId == null })
        assertEquals(1, repo.cleanDuplicates(groupId = null))
        assertEquals(1, repo.words.value.size)
    }

    @Test
    fun `the survivor keeps the earliest creation time so the order does not jump`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        val old = repo.forceInsert("observed", group.id, createdAt = 100L)
        repo.forceInsert("observed", group.id, createdAt = 900L)

        repo.cleanDuplicates()
        val survivor = repo.words.value.single()
        assertEquals(old.id, survivor.id)
        assertEquals(100L, survivor.createdAt)
    }

    @Test
    fun `cleaning merges senses tags and phonetic from every copy`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("observed", group.id, meaning = "观察", phonetic = "/əbˈzɜːvd/", tags = listOf("past"))
        repo.forceInsert("observed", group.id, meaning = "遵守", tags = listOf("动词变化", "过去式"))

        repo.cleanDuplicates()
        val merged = repo.words.value.single()
        assertEquals("/əbˈzɜːvd/", merged.phonetic)
        assertEquals(listOf("观察", "遵守"), merged.senses.map { it.meaningZh })
        assertEquals(setOf("past", "动词变化", "过去式"), merged.tags.toSet())
    }

    @Test
    fun `cleaning keeps the most advanced review progress`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("observed", group.id, status = LearningStatus.NEW, reviewCount = 0)
        repo.forceInsert(
            "observed",
            group.id,
            status = LearningStatus.MASTERED,
            reviewCount = 7,
            starred = true
        )

        repo.cleanDuplicates()
        val merged = repo.words.value.single()
        assertEquals(LearningStatus.MASTERED, merged.status)
        assertEquals(7, merged.reviewCount)
        assertTrue(merged.starred)
    }

    @Test
    fun `cleaning a clean library changes nothing and reports zero`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("apple", group.id)
        repo.forceInsert("banana", group.id)
        val before = repo.words.value.map { it.id to it.text }

        assertEquals(0, repo.cleanDuplicates())
        assertEquals(before, repo.words.value.map { it.id to it.text })
    }

    @Test
    fun `three copies collapse to one`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("observed", group.id, "a")
        repo.forceInsert("observed", group.id, "b")
        repo.forceInsert("observed", group.id, "c")

        assertEquals(2, repo.cleanDuplicates())
        assertEquals(1, repo.words.value.size)
        assertEquals(listOf("a", "b", "c"), repo.words.value.single().senses.map { it.meaningZh })
    }

    @Test
    fun `matching is case insensitive like the uniqueness rule`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("Observed", group.id)
        repo.forceInsert("observed", group.id)
        assertEquals(1, repo.countDuplicates())
        assertEquals(1, repo.cleanDuplicates())
        assertEquals(1, repo.words.value.size)
    }

    // ------------------------------------------------------------ add-time guard

    @Test
    fun `findAnyDuplicate prefers the requested group`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        val b = repo.createGroup("B组", "📁", 1)
        repo.forceInsert("bank", a.id)
        repo.forceInsert("bank", b.id)

        assertEquals(a.id, repo.findAnyDuplicate("bank", a.id)?.groupId)
        assertEquals(b.id, repo.findAnyDuplicate("bank", b.id)?.groupId)
    }

    @Test
    fun `findAnyDuplicate falls back to another group and ignores casing`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        repo.forceInsert("bank", a.id)
        val found = repo.findAnyDuplicate("BANK", null)
        assertNotNull(found)
        assertEquals(a.id, found?.groupId)
    }

    @Test
    fun `findAnyDuplicate returns null for an unknown headword`() {
        val repo = repository()
        assertNull(repo.findAnyDuplicate("unheard-of", null))
        assertNull(repo.findAnyDuplicate("   ", null))
    }

    // ------------------------------------------------------------ manual picker

    @Test
    fun `duplicateGroups lists each clash with its bucket and oldest first`() {
        val repo = repository()
        val group = repo.createGroup("四级核心", "🎓", 0)
        val older = repo.forceInsert("observed", group.id, "观察", createdAt = 100L)
        repo.forceInsert("observed", group.id, "遵守", createdAt = 500L)
        repo.forceInsert("oil", group.id)
        repo.forceInsert("bank", null, createdAt = 50L)
        repo.forceInsert("bank", null, createdAt = 60L)

        val groups = repo.duplicateGroups()
        assertEquals(2, groups.size)
        val observed = groups.first { it.headword == "observed" }
        assertEquals("四级核心", observed.groupName)
        assertEquals(2, observed.entries.size)
        assertEquals(1, observed.extraCount)
        // Oldest first, so entries.first() is what an automatic merge keeps.
        assertEquals(older.id, observed.entries.first().id)

        val bank = groups.first { it.headword == "bank" }
        assertEquals("未分组", bank.groupName)
        assertNull(bank.groupId)
    }

    @Test
    fun `scoping the scan to one group hides the others`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        val b = repo.createGroup("B组", "📁", 1)
        repo.forceInsert("observed", a.id)
        repo.forceInsert("observed", a.id)
        repo.forceInsert("bank", b.id)
        repo.forceInsert("bank", b.id)

        assertEquals(listOf("observed"), repo.duplicateGroups(a.id).map { it.headword })
        assertEquals(listOf("bank"), repo.duplicateGroups(b.id).map { it.headword })
        assertEquals(listOf("bank", "observed"), repo.duplicateGroups().map { it.headword })
    }

    @Test
    fun `keeping the newer entry removes the older one`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        val older = repo.forceInsert("observed", group.id, "旧释义", createdAt = 100L)
        val newer = repo.forceInsert("observed", group.id, "新释义", createdAt = 900L)

        val key = repo.duplicateGroups().single().key
        val removed = repo.cleanDuplicatesKeeping(keepIds = mapOf(key to newer.id))

        assertEquals(1, removed)
        val survivor = repo.words.value.single()
        assertEquals(newer.id, survivor.id)
        // The user picked this entry, so its own content is kept verbatim.
        assertEquals("新释义", survivor.senses.single().meaningZh)
        assertTrue(repo.words.value.none { it.id == older.id })
    }

    @Test
    fun `a manually kept entry still inherits every tag so nothing is lost`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("observed", group.id, "旧", tags = listOf("past"), createdAt = 100L)
        val newer = repo.forceInsert(
            "observed",
            group.id,
            "新",
            tags = listOf("动词变化", "过去式"),
            createdAt = 900L
        )

        val key = repo.duplicateGroups().single().key
        repo.cleanDuplicatesKeeping(keepIds = mapOf(key to newer.id))

        val survivor = repo.words.value.single()
        assertEquals("新", survivor.senses.single().meaningZh)
        assertEquals(setOf("past", "动词变化", "过去式"), survivor.tags.toSet())
    }

    @Test
    fun `groups without an explicit pick fall back to the oldest entry`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        val older = repo.forceInsert("observed", group.id, createdAt = 100L)
        repo.forceInsert("observed", group.id, createdAt = 900L)

        // Empty keepIds = accept the automatic choice everywhere.
        assertEquals(1, repo.cleanDuplicatesKeeping(keepIds = emptyMap()))
        assertEquals(older.id, repo.words.value.single().id)
    }

    @Test
    fun `an unknown keeper id falls back instead of deleting everything`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        val older = repo.forceInsert("observed", group.id, createdAt = 100L)
        repo.forceInsert("observed", group.id, createdAt = 900L)

        val key = repo.duplicateGroups().single().key
        assertEquals(1, repo.cleanDuplicatesKeeping(keepIds = mapOf(key to "not-a-real-id")))
        assertEquals(1, repo.words.value.size)
        assertEquals(older.id, repo.words.value.single().id)
    }

    @Test
    fun `choices can differ per duplicate group`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("alpha", group.id, "a-old", createdAt = 100L)
        val alphaNew = repo.forceInsert("alpha", group.id, "a-new", createdAt = 200L)
        val betaOld = repo.forceInsert("beta", group.id, "b-old", createdAt = 300L)
        repo.forceInsert("beta", group.id, "b-new", createdAt = 400L)

        val groups = repo.duplicateGroups().associateBy { it.headword }
        val removed = repo.cleanDuplicatesKeeping(
            keepIds = mapOf(
                groups.getValue("alpha").key to alphaNew.id,
                groups.getValue("beta").key to betaOld.id
            )
        )

        assertEquals(2, removed)
        assertEquals(2, repo.words.value.size)
        assertEquals("a-new", repo.words.value.first { it.text == "alpha" }.senses.single().meaningZh)
        assertEquals("b-old", repo.words.value.first { it.text == "beta" }.senses.single().meaningZh)
    }

    @Test
    fun `the same headword in two groups keeps one entry per group`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        val b = repo.createGroup("B组", "📁", 1)
        repo.forceInsert("bank", a.id, "银行", createdAt = 100L)
        repo.forceInsert("bank", a.id, "河岸", createdAt = 200L)
        repo.forceInsert("bank", b.id, "银行", createdAt = 300L)
        repo.forceInsert("bank", b.id, "河岸", createdAt = 400L)

        // Two groups, not one merged word.
        assertEquals(2, repo.duplicateGroups().size)
        val groups = repo.duplicateGroups()
        assertEquals(setOf(a.id, b.id), groups.map { it.groupId }.toSet())

        assertEquals(2, repo.cleanDuplicatesKeeping(keepIds = emptyMap()))
        assertEquals(2, repo.words.value.size)
        assertEquals(1, repo.words.value.count { it.groupId == a.id })
        assertEquals(1, repo.words.value.count { it.groupId == b.id })
    }

    @Test
    fun `keeping an entry does not touch unrelated words`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        repo.forceInsert("observed", group.id, createdAt = 100L)
        repo.forceInsert("observed", group.id, createdAt = 200L)
        val oil = repo.forceInsert("oil", group.id)
        val apple = repo.forceInsert("apple", null)

        repo.cleanDuplicatesKeeping(keepIds = emptyMap())
        assertEquals(setOf(oil.id, apple.id), repo.words.value.map { it.id }.toSet() - repo.words.value.first { it.text == "observed" }.id)
        assertEquals(3, repo.words.value.size)
    }

    @Test
    fun `upsert refuses to create a second entry in the same group`() {
        val repo = repository()
        val group = repo.createGroup("G", "📁", 0)
        fun draft(id: String, meaning: String) = com.lexi.wordbook.data.WordDraft(
            id = id,
            text = "observed",
            senses = listOf(Sense(PartOfSpeech.VERB, meaning)),
            groupId = group.id
        )

        val first = repo.upsert(draft("a", "观察"))
        val second = repo.upsert(draft("b", "遵守"))

        assertEquals(1, repo.words.value.size)
        assertEquals(first.id, second.id)
        assertEquals(0, repo.countDuplicates())
    }
}
