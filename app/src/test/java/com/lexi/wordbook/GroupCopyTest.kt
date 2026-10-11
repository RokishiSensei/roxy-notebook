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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Copying words between groups.
 *
 * Covers the two entry points added in 1.10.0: picking a target group for an AI batch import,
 * and copying a whole group's words into another group from 分组管理.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GroupCopyTest {

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
    fun `copying a whole group leaves the originals in place`() {
        val repo = repository()
        val from = repo.createGroup("四级核心")
        val to = repo.createGroup("阅读生词")
        repeat(3) { index -> repo.upsert(draft("w$index", "word$index", from.id)) }

        val ids = repo.words.value.filter { it.groupId == from.id }.map { it.id }.toSet()
        val copies = repo.copyInto(ids, to.id)

        assertEquals(3, copies)
        assertEquals(6, repo.words.value.size)
        assertEquals(3, repo.words.value.count { it.groupId == from.id })
        assertEquals(3, repo.words.value.count { it.groupId == to.id })
    }

    @Test
    fun `copying a group twice does not duplicate inside the target`() {
        val repo = repository()
        val from = repo.createGroup("A组")
        val to = repo.createGroup("B组")
        repo.upsert(draft("w1", "apple", from.id))
        val ids = repo.words.value.map { it.id }.toSet()

        assertEquals(1, repo.copyInto(ids, to.id))
        // Second run: the headword already exists in B组, so nothing new is created.
        assertEquals(0, repo.copyInto(ids, to.id))
        assertEquals(2, repo.words.value.size)
    }

    @Test
    fun `copies are independent of the original for review state`() {
        val repo = repository()
        val from = repo.createGroup("A组")
        val to = repo.createGroup("B组")
        val original = repo.upsert(draft("w1", "apple", from.id))
        repo.updateWord(original.id) { it.copy(starred = true, status = LearningStatus.MASTERED) }

        repo.copyInto(setOf(original.id), to.id)

        val copy = repo.words.value.first { it.groupId == to.id }
        assertTrue(!copy.starred)
        assertEquals(LearningStatus.NEW, copy.status)
        // The original keeps its own progress.
        assertTrue(repo.get(original.id)!!.starred)
    }

    @Test
    fun `copying to ungrouped works and stays unique`() {
        val repo = repository()
        val from = repo.createGroup("A组")
        repo.upsert(draft("w1", "apple", from.id))
        repo.upsert(draft("w2", "banana", null))

        val ids = repo.words.value.filter { it.groupId == from.id }.map { it.id }.toSet()
        assertEquals(1, repo.copyInto(ids, null))

        assertEquals(2, repo.words.value.count { it.groupId == null })
        // "apple" now exists both in A组 and ungrouped.
        assertEquals(2, repo.words.value.count { it.text == "apple" })
    }

    @Test
    fun `copying an empty group is a no-op`() {
        val repo = repository()
        val from = repo.createGroup("空组")
        val to = repo.createGroup("目标组")
        val ids = repo.words.value.filter { it.groupId == from.id }.map { it.id }.toSet()
        assertEquals(0, ids.size)
        assertEquals(0, repo.copyInto(ids, to.id))
        assertEquals(0, repo.words.value.size)
    }

    // ---------------------------------------------------------------- import overrides

    @Test
    fun `an import override replaces the group the model suggested`() {
        val repo = repository()
        val aiGroup = repo.createGroup("AI 建议的分组")
        val chosen = repo.createGroup("我选的分组")

        // Mimics importBatchWords: the override wins over the per-word group name.
        val draftWithAiGroup = draft("w1", "apple", aiGroup.id)
        val overridden = draftWithAiGroup.copy(groupId = chosen.id)
        repo.upsert(overridden)

        assertEquals(chosen.id, repo.words.value.single().groupId)
        assertEquals(0, repo.words.value.count { it.groupId == aiGroup.id })
        assertNotNull(repo.groups.value.firstOrNull { it.id == chosen.id })
    }

    @Test
    fun `importing without an override keeps the ai grouping`() {
        val repo = repository()
        val aiGroup = repo.createGroup("AI 建议的分组")
        repo.upsert(draft("w1", "apple", aiGroup.id))
        assertEquals(aiGroup.id, repo.words.value.single().groupId)
    }

    @Test
    fun `importing as ungrouped clears the group`() {
        val repo = repository()
        val aiGroup = repo.createGroup("AI 建议的分组")
        repo.upsert(draft("w1", "apple", aiGroup.id).copy(groupId = null))
        assertNull(repo.words.value.single().groupId)
    }
}
