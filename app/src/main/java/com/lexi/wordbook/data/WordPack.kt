package com.lexi.wordbook.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One row of the bundled word packs stored under assets/wordpacks. */
@Serializable
data class WordPackEntry(
    val word: String,
    val phonetic: String = "",
    /** One string per sense: "n. 中文释义" / "adj. 中文释义 (english)" / "v. 释义 例: example" */
    val senses: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val note: String = ""
)

@Serializable
data class WordPack(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val words: List<WordPackEntry> = emptyList()
)

/**
 * Loads bundled vocabulary packs from the `wordpacks` assets folder and turns them into
 * [Word] entities.
 *
 * Packs are plain JSON so they are easy to extend or replace without touching code.
 */
object WordPackLoader {

    private val json = Json { ignoreUnknownKeys = true }
    private val senseRegex = Regex(
        """^\s*(n|v|vt|vi|adj|adv|pron|prep|conj|int|interj|num|art|aux|det|phr|idiom|abbr)\.?\s+""",
        RegexOption.IGNORE_CASE
    )

    fun load(context: Context, assetPath: String): WordPack? = runCatching {
        context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }.mapCatching { raw ->
        json.decodeFromString(WordPack.serializer(), raw)
    }.getOrNull()

    /** Converts pack entries into repository entities belonging to [groupId]. */
    fun toWords(
        entries: List<WordPackEntry>,
        groupId: String?,
        now: Long = System.currentTimeMillis()
    ): List<Word> = entries.mapIndexedNotNull { index, entry ->
        val text = entry.word.trim()
        if (text.isBlank()) return@mapIndexedNotNull null
        val senses = entry.senses.mapNotNull(::parseSense).ifEmpty { listOf(Sense()) }
        Word(
            id = java.util.UUID.randomUUID().toString(),
            text = text,
            phonetic = entry.phonetic.trim(),
            senses = senses,
            status = LearningStatus.NEW,
            tags = entry.tags.map { it.trim() }.filter { it.isNotEmpty() },
            note = entry.note.trim(),
            groupId = groupId,
            createdAt = now + index,
            updatedAt = now + index,
            nextReviewAt = now
        )
    }

    /** Parses "n. 银行 (a bank) 例: I went to the bank." into a [Sense]. */
    fun parseSense(raw: String): Sense? {
        var body = raw.trim()
        if (body.isBlank()) return null

        var example = ""
        val exampleIndex = body.indexOf("例:")
        if (exampleIndex >= 0) {
            example = body.substring(exampleIndex + 2).trim()
            body = body.substring(0, exampleIndex).trim()
        }

        var english = ""
        Regex("""\(([^)]*[A-Za-z][^)]*)\)""").find(body)?.let { match ->
            english = match.groupValues[1].trim()
            body = body.removeRange(match.range).trim()
        }

        val posMatch = senseRegex.find("$body ")
        val pos = posMatch?.groupValues?.getOrNull(1).orEmpty()
        if (posMatch != null) {
            body = body.substring(posMatch.value.trimEnd().length).trim()
        }
        body = body.trimStart('：', ':', '-', '—', ' ')
        if (body.isBlank()) return null

        return Sense(
            partOfSpeech = PartOfSpeech.fromAi(pos),
            meaningZh = body,
            meaningEn = english,
            example = example
        )
    }
}
