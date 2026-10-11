package com.lexi.wordbook

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lexi.wordbook.ai.BatchWord
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A word must never land in the library twice inside the same group.
 *
 * The reported bug: the AI returned `observed` twice (a truncated chunk is retried in halves, so
 * the same input line can be answered twice), and both rows were kept in the preview because the
 * de-duplication only compared against the *existing library*, never within the batch itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BatchDuplicateTest {

    private fun repository(): WordRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE).edit().clear().commit()
        return WordRepository(context)
    }

    private fun batchWord(
        text: String,
        group: String = "CET4-2022.12第一套",
        meanings: List<String> = listOf("释义"),
        note: String = ""
    ) = BatchWord(
        draft = WordDraft(
            id = java.util.UUID.randomUUID().toString(),
            text = text,
            phonetic = "/test/",
            senses = meanings.map { Sense(PartOfSpeech.VERB, it) },
            note = note
        ),
        rawWord = text,
        groupName = group
    )

    @Test
    fun `writing the same headword twice into one group keeps a single entry`() {
        val repo = repository()
        val first = batchWord("observed", meanings = listOf("观察"))
        val a = repo.upsert(first.draft)
        assertEquals(1, repo.words.value.size)
        assertEquals("observed", a.text)

        // The second row carries different tags/notes but the same headword and group.
        val second = batchWord("observed", meanings = listOf("观察；遵守"), note = "过去式")
        val b = repo.upsert(second.draft.copy(groupId = a.groupId))

        // One entry, and it is the same row (same id) — not a second one appended.
        assertEquals(1, repo.words.value.size)
        assertEquals(a.id, b.id)
        assertEquals(a.id, repo.words.value.single().id)
        // The newer content won.
        assertEquals("观察；遵守", repo.words.value.single().senses.single().meaningZh)
        assertEquals("过去式", repo.words.value.single().note)
    }

    @Test
    fun `the same headword in two different groups stays two entries`() {
        val repo = repository()
        val a = repo.createGroup("A组", "📁", 0)
        val b = repo.createGroup("B组", "📁", 1)

        repo.upsert(batchWord("observed", "A组").draft.copy(groupId = a.id))
        repo.upsert(batchWord("observed", "B组").draft.copy(groupId = b.id))

        assertEquals(2, repo.words.value.size)
        assertEquals(1, repo.words.value.count { it.groupId == a.id })
        assertEquals(1, repo.words.value.count { it.groupId == b.id })
    }

    @Test
    fun `upsertAll collapses duplicates that arrive in one batch`() {
        val repo = repository()
        val group = repo.createGroup("批次组", "📁", 0)
        val drafts = listOf(
            batchWord("observed", "批次组", listOf("观察")).draft.copy(groupId = group.id),
            batchWord("observed", "批次组", listOf("观察；遵守")).draft.copy(groupId = group.id),
            batchWord("observe", "批次组").draft.copy(groupId = group.id)
        )
        // The importer materialises drafts into entities exactly like this.
        val entities = drafts.mapIndexed { index, draft -> draft.applyTo(null, 100L + index) }

        repo.upsertAll(entities)
        // Two rows for one headword must not both survive.
        assertEquals(1, repo.words.value.count { it.text == "observed" })
        assertEquals(1, repo.words.value.count { it.text == "observe" })
        assertEquals(2, repo.words.value.size)
    }

    @Test
    fun `a duplicate batch row is still reported so the preview can explain it`() {
        // The parser keeps every row (it cannot know the answer was for the same line), and the
        // assistant folds them. This pins the identity used for the fold.
        assertEquals(
            com.lexi.wordbook.ai.AiWordParser.dedupKey("observed", "CET4-2022.12第一套"),
            com.lexi.wordbook.ai.AiWordParser.dedupKey("Observed", "cet4-2022.12第一套 ")
        )
        assertTrue(
            com.lexi.wordbook.ai.AiWordParser.dedupKey("observed", "A组") !=
                com.lexi.wordbook.ai.AiWordParser.dedupKey("observed", "B组")
        )
    }
}
