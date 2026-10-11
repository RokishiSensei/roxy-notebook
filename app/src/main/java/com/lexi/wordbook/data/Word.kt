package com.lexi.wordbook.data

/**
 * A single dictionary sense. One word can hold several senses, which mirrors how real
 * dictionaries present a headword ("bank" -> 银行 / 河岸).
 */
@kotlinx.serialization.Serializable
data class Sense(
    val partOfSpeech: PartOfSpeech = PartOfSpeech.OTHER,
    /** Chinese definition, e.g. 银行；金融机构 */
    val meaningZh: String = "",
    /** English definition, e.g. an institution that keeps money */
    val meaningEn: String = "",
    /** Bilingual example sentence(s); free text. */
    val example: String = ""
) {
    val isBlank: Boolean
        get() = meaningZh.isBlank() && meaningEn.isBlank() && example.isBlank()
}

/** Part of speech with both an English and a Chinese label. */
@kotlinx.serialization.Serializable
enum class PartOfSpeech(val label: String, val labelZh: String) {
    NOUN("n.", "名词"),
    VERB("v.", "动词"),
    ADJECTIVE("adj.", "形容词"),
    ADVERB("adv.", "副词"),
    PRONOUN("pron.", "代词"),
    PREPOSITION("prep.", "介词"),
    CONJUNCTION("conj.", "连词"),
    INTERJECTION("int.", "感叹词"),
    NUMERAL("num.", "数词"),
    ARTICLE("art.", "冠词"),
    AUXILIARY("aux.", "助动词"),
    DETERMINER("det.", "限定词"),
    PHRASE("phr.", "短语"),
    IDIOM("idiom", "习语"),
    ABBREVIATION("abbr.", "缩写"),
    OTHER("—", "其他");

    companion object {
        fun fromName(name: String?): PartOfSpeech =
            entries.firstOrNull { it.name == name } ?: OTHER

        private val AI_ALIASES: Map<String, PartOfSpeech> = buildMap {
            put("n", NOUN); put("noun", NOUN); put("名词", NOUN)
            put("v", VERB); put("vt", VERB); put("vi", VERB); put("verb", VERB); put("动词", VERB)
            put("adj", ADJECTIVE); put("adjective", ADJECTIVE); put("形容词", ADJECTIVE)
            put("adv", ADVERB); put("adverb", ADVERB); put("副词", ADVERB)
            put("pron", PRONOUN); put("pronoun", PRONOUN); put("代词", PRONOUN)
            put("prep", PREPOSITION); put("preposition", PREPOSITION); put("介词", PREPOSITION)
            put("conj", CONJUNCTION); put("conjunction", CONJUNCTION); put("连词", CONJUNCTION)
            put("int", INTERJECTION); put("interj", INTERJECTION); put("感叹词", INTERJECTION)
            put("num", NUMERAL); put("numeral", NUMERAL); put("数词", NUMERAL)
            put("art", ARTICLE); put("article", ARTICLE); put("冠词", ARTICLE)
            put("aux", AUXILIARY); put("auxiliary", AUXILIARY); put("助动词", AUXILIARY)
            put("det", DETERMINER); put("determiner", DETERMINER); put("限定词", DETERMINER)
            put("phr", PHRASE); put("phrase", PHRASE); put("短语", PHRASE)
            put("idiom", IDIOM); put("习语", IDIOM)
            put("abbr", ABBREVIATION); put("abbreviation", ABBREVIATION); put("缩写", ABBREVIATION)
        }

        /** Maps anything an LLM might answer ("n.", "noun", "名词") onto the enum. */
        fun fromAi(raw: String?): PartOfSpeech {
            val cleaned = raw.orEmpty().trim().trim('.', '。', ' ', '（', '）', '(', ')').lowercase()
            if (cleaned.isEmpty()) return OTHER
            entries.firstOrNull { it.name.equals(cleaned, ignoreCase = true) }?.let { return it }
            AI_ALIASES[cleaned]?.let { return it }
            return AI_ALIASES.entries
                .firstOrNull { cleaned.startsWith(it.key) }
                ?.value ?: OTHER
        }
    }
}

/** How well the learner knows a word. Drives review, quiz and statistics. */
@kotlinx.serialization.Serializable
enum class LearningStatus {
    /** Just added, never reviewed. */
    NEW,

    /** Seen, but shaky. */
    LEARNING,

    /** Familiar; needs an occasional reminder. */
    FAMILIAR,

    /** Confidently known. */
    MASTERED;

    companion object {
        fun fromName(name: String?): LearningStatus =
            entries.firstOrNull { it.name == name } ?: NEW
    }
}

/** Headword vs. multi-word expression, used for badges in the list. */
@kotlinx.serialization.Serializable
enum class WordKind(val labelZh: String) {
    WORD("单词"),
    PHRASE("词组"),
    SENTENCE("句子");

    companion object {
        fun fromName(name: String?): WordKind = entries.firstOrNull { it.name == name } ?: WORD
    }
}

/**
 * The persisted vocabulary entry. Kept intentionally flat and primitive-typed so that
 * kotlinx.serialization round-trips it without any custom adapters.
 */
@kotlinx.serialization.Serializable
data class Word(
    val id: String,
    /** The English headword, stored trimmed but with original casing. */
    val text: String = "",
    /** Pronunciation hint, e.g. /bæŋk/ */
    val phonetic: String = "",
    val senses: List<Sense> = emptyList(),
    val status: LearningStatus = LearningStatus.NEW,
    val starred: Boolean = false,
    /** Where the word came from: 阅读/听力/课堂/App... */
    val source: String = "",
    val tags: List<String> = emptyList(),
    /** Free-form personal note, e.g. 我总是把它和 blank 搞混 */
    val note: String = "",
    val kind: WordKind = WordKind.WORD,
    /** Id of the custom [WordGroup] this entry belongs to, or null for "未分组". */
    val groupId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val reviewCount: Int = 0,
    /** Epoch millis of the next scheduled review (Leitner-style). */
    val nextReviewAt: Long = 0L,
    val lastReviewedAt: Long = 0L
) {
    /** First character used for A–Z grouping. */
    val initial: String
        get() = initialOf(text)

    /** All Chinese meanings joined, used for search and statistics. */
    val meaningZhJoined: String
        get() = senses.joinToString("；") { it.meaningZh }.trim('；', ' ')

    val primarySense: Sense?
        get() = senses.firstOrNull { !it.isBlank }

    companion object {
        /** Bucket key for the alphabetical index: A–Z, or # for anything else. */
        fun initialOf(text: String): String {
            val c = text.trim().firstOrNull()?.uppercaseChar() ?: return "#"
            return if (c in 'A'..'Z') c.toString() else "#"
        }
    }
}

/** Editable working copy used by the add/edit sheet. */
data class WordDraft(
    val id: String,
    val text: String = "",
    val phonetic: String = "",
    val senses: List<Sense> = listOf(Sense()),
    val source: String = "",
    val tagsText: String = "",
    val note: String = "",
    val starred: Boolean = false,
    val kind: WordKind = WordKind.WORD,
    val status: LearningStatus = LearningStatus.NEW,
    val groupId: String? = null
) {
    val isValid: Boolean
        get() = text.isNotBlank() && senses.any { !it.isBlank }

    val tags: List<String>
        get() = tagsText.split(',', '，', ';', '；', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    companion object {
        fun from(word: Word): WordDraft = WordDraft(
            id = word.id,
            text = word.text,
            phonetic = word.phonetic,
            senses = word.senses.ifEmpty { listOf(Sense()) },
            source = word.source,
            tagsText = word.tags.joinToString(", "),
            note = word.note,
            starred = word.starred,
            kind = word.kind,
            status = word.status,
            groupId = word.groupId
        )
    }

    fun applyTo(existing: Word?, now: Long): Word {
        val cleanSenses = senses
            .filterNot { it.isBlank }
            .ifEmpty { listOf(Sense(meaningZh = "", meaningEn = "")) }
        return Word(
            id = id,
            text = text.trim(),
            phonetic = phonetic.trim(),
            senses = cleanSenses,
            status = status,
            starred = starred,
            source = source.trim(),
            tags = tags,
            note = note.trim(),
            kind = if (text.trim().contains(' ')) {
                if (text.trim().count { it == ' ' } >= 4) WordKind.SENTENCE else WordKind.PHRASE
            } else {
                WordKind.WORD
            },
            groupId = groupId,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            reviewCount = existing?.reviewCount ?: 0,
            nextReviewAt = existing?.nextReviewAt ?: now,
            lastReviewedAt = existing?.lastReviewedAt ?: 0L
        )
    }
}
