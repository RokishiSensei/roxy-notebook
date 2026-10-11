package com.lexi.wordbook.ai

import com.lexi.wordbook.data.AiSense
import com.lexi.wordbook.data.AiWordResult
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.ui.ai.AiAssistant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** One entry produced by the batch importer, ready to be saved. */
data class BatchWord(
    val draft: WordDraft,
    val rawWord: String,
    val groupName: String = "",
    val duplicateOf: String? = null,
    /**
     * True when the model added this entry on its own — e.g. the input had "ran" or "apples"
     * and the model also supplied the dictionary form "run" / "apple". The UI highlights these
     * so the user can decide whether to keep them.
     */
    val isNew: Boolean = false
) {
    val isDuplicate: Boolean get() = duplicateOf != null
}

/** Outcome of parsing a batch answer. */
data class BatchParseResult(
    val words: List<BatchWord>,
    val usedJson: Boolean,
    val note: String = ""
)

/**
 * Turns the model's answer into a structured [AiWordResult].
 *
 * The prompt asks for bare JSON, but models sometimes wrap it in prose or a Markdown fence,
 * so the parser locates the outermost JSON object and reads it defensively.
 */
object AiWordParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Duplicate-detection identity: headword (case-insensitive) inside a group.
     * Mirrors `WordRepository.dedupKey` so the batch preview flags exactly what the repository
     * would treat as a duplicate.
     */
    fun dedupKey(text: String, group: String?): String =
        text.trim().lowercase() + "@" + group.orEmpty().trim().lowercase()

    /**
     * Loose identity for comparing a headword against the pasted list: lower case, no leading
     * numbering, single-spaced.
     *
     * Trailing punctuation is deliberately *kept*: it is the marker for a multi-word phrase, so
     * a list line "thickening agent" must not collapse into "thickening agen". Punctuation on
     * the surrounding list items is trimmed by [plainToken] instead.
     */
    fun normaliseWord(word: String): String = word
        .trim()
        .lowercase()
        .removePrefix("-")
        .replace(Regex("""^\d+\s*[.、)\]]\s*"""), "")
        .trimStart(' ', '.', ',', ';', ':', '"', '\'', '“', '”', '‘', '’', '(', ')', '[', ']')
        .replace(Regex("""\s+"""), " ")

    /** A single list item stripped of wrapping punctuation, e.g. "ran," -> "ran". */
    private fun plainToken(token: String): String =
        token.trim('.', ',', ';', ':', '!', '?', '"', '\'', '“', '”', '‘', '’', '(', ')', '。', '，', '！', '？')

    /**
     * Every headword the user supplied, for telling the model's own additions apart from theirs.
     *
     * A line is first broken into word units, so a packed line
     * (`average，regardless，miles，thinner`) is seen as the four words it is. Without this every
     * entry of such a line looked like a model invention and was flagged 「新增」.
     */
    fun headwordsOf(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val result = linkedSetOf<String>()
        text.lines().forEach { line ->
            val units = AiAssistant.wordUnits(line).ifEmpty { listOf(line) }
            units.forEach { unit ->
                val withoutIndex = normaliseWord(unit)
                if (withoutIndex.isEmpty()) return@forEach
                // The whole unit (a bare phrase matches as-is)…
                result.add(withoutIndex)
                // …its leading token, which is the headword even when the line carries extra
                // columns ("1. apples /ˈæplz/ n. 苹果") — the usual shape of an exported list…
                val firstToken = plainToken(withoutIndex.substringBefore(' '))
                if (firstToken.isNotEmpty()) result.add(firstToken)
                // …and the Latin-script tokens after it. Chinese meanings and IPA are skipped by
                // the [A-Za-z] filter; a unit with no spaces has nothing extra to iterate.
                withoutIndex.split(' ').drop(1).forEach { raw ->
                    val token = plainToken(raw)
                    if (token.length > 1 && token.all { it.isLetter() && it.code < 128 }) {
                        result.add(token)
                    }
                }
            }
        }
        return result
    }

    fun parse(raw: String): AiWordResult? {
        val candidate = extractJsonObject(raw) ?: return null
        val result = runCatching { json.decodeFromString(AiWordResult.serializer(), candidate) }
            .getOrNull()
            ?: return null
        val cleaned = result.copy(
            word = result.word.trim(),
            phonetic = result.phonetic.trim(),
            senses = result.senses.filter { it.meaningZh.isNotBlank() || it.meaningEn.isNotBlank() },
            note = result.note.trim(),
            tags = result.tags.map { it.trim() }.filter { it.isNotEmpty() }
        )
        return if (cleaned.hasContent) cleaned else null
    }

    // ------------------------------------------------------------------ batch import

    /**
     * Parses the array answer used by 「AI 批量导入」.
     *
     * Tries strict JSON first (tolerating ```json fences), then a lenient line-based reader so
     * a chatty model still produces something usable.
     */
    fun parseBatch(
        raw: String,
        existingKeys: Set<String>,
        /** The text the user submitted, used to tell model-added entries from theirs. */
        sourceText: String = ""
    ): BatchParseResult {
        val sourceWords = headwordsOf(sourceText)
        val objects = extractJsonObjects(raw)
        if (objects.isNotEmpty()) {
            val words = objects.mapNotNull { element ->
                toBatchWord(element, existingKeys, sourceWords)
            }
            if (words.isNotEmpty()) {
                return BatchParseResult(words = words, usedJson = true)
            }
        }
        val loose = parseBatchLoose(raw, existingKeys, sourceWords)
        return if (loose.isEmpty()) {
            BatchParseResult(words = emptyList(), usedJson = false, note = "没有解析出任何词条")
        } else {
            BatchParseResult(
                words = loose,
                usedJson = false,
                note = "模型返回的不是标准 JSON，已按文本逐行解析"
            )
        }
    }

    /** Builds a batch entry from one JSON element, normalising whatever shape the model used. */
    /**
     * True when [word] could be a real headword.
     *
     * Defence against the model echoing infrastructure back as an entry: it once answered the
     * `__SECRET__` placeholder (and phrases like "无法识别的词条") as if it were a word, and the
     * preview then offered to import it.
     */
    fun isPlausibleHeadword(word: String): Boolean {
        val value = word.trim()
        if (value.isEmpty()) return false
        if (value.length > MAX_HEADWORD_LENGTH) return false
        // Control characters, and anything that is not a letter/digit/space/phrase punctuation.
        if (value.any { it.isISOControl() }) return false
        if (value.startsWith("http://") || value.startsWith("https://")) return false
        // The secret placeholder must never reach the library.
        if (value.replace("_", "").equals("SECRET", ignoreCase = true)) return false
        // "无法识别的词条（疑似占位符…）" — a model explaining itself is not a word.
        if (value.length > 20 && value.none { it.code < 128 }) return false
        // At least one letter, and not a wall of punctuation.
        if (value.none { it.isLetter() }) return false
        return true
    }

    /** Longest headword accepted; longer strings are sentences or model noise. */
    private const val MAX_HEADWORD_LENGTH = 120

    private fun toBatchWord(
        element: JsonObject,
        existingKeys: Set<String>,
        sourceWords: Set<String>
    ): BatchWord? {
        val word = element.string("word").ifBlank { element.string("text") }.trim()
        if (!isPlausibleHeadword(word)) return null

        val phonetic = element.string("phonetic").ifBlank { element.string("ipa") }.trim()

        val senses = mutableListOf<Sense>()
        element["senses"]?.let { node ->
            val array = runCatching { node.jsonArray }.getOrNull()
            if (array != null) {
                array.forEach { senseNode ->
                    val obj = runCatching { senseNode.jsonObject }.getOrNull() ?: return@forEach
                    val sense = Sense(
                        partOfSpeech = PartOfSpeech.fromAi(
                            obj.string("partOfSpeech").ifBlank { obj.string("pos") }
                        ),
                        meaningZh = obj.string("meaningZh").ifBlank { obj.string("meaning") }.trim(),
                        meaningEn = obj.string("meaningEn").ifBlank { obj.string("english") }.trim(),
                        example = obj.string("example").ifBlank { obj.string("sentence") }.trim()
                    )
                    if (!sense.isBlank) senses.add(sense)
                }
            }
        }
        // Accept a flat shape too: {"word":"x","partOfSpeech":"n.","meaningZh":"..."}
        if (senses.isEmpty()) {
            val flat = Sense(
                partOfSpeech = PartOfSpeech.fromAi(
                    element.string("partOfSpeech").ifBlank { element.string("pos") }
                ),
                meaningZh = element.string("meaningZh").ifBlank { element.string("meaning") }.trim(),
                meaningEn = element.string("meaningEn").ifBlank { element.string("english") }.trim(),
                example = element.string("example").trim()
            )
            if (!flat.isBlank) senses.add(flat)
        }
        // Accept {"word":"x","meanings":["a","b"]}
        if (senses.isEmpty()) {
            element["meanings"]?.let { node ->
                runCatching { node.jsonArray }.getOrNull()?.forEach { item ->
                    val text = runCatching { item.jsonPrimitive.contentOrNull }.getOrNull().orEmpty()
                    if (text.isNotBlank()) senses.add(Sense(PartOfSpeech.OTHER, text.trim()))
                }
            }
        }
        if (senses.isEmpty()) return null

        val tags = mutableListOf<String>()
        element["tags"]?.let { node ->
            runCatching { node.jsonArray }.getOrNull()?.forEach { item ->
                runCatching { item.jsonPrimitive.contentOrNull }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { tags.add(it.trim()) }
            }
        }
        element["tag"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { raw ->
            tags.addAll(raw.split(',', '，', ' ', ';', '；').map { it.trim() }.filter { it.isNotEmpty() })
        }

        val groupName = element.string("group").ifBlank { element.string("category") }.trim()

        val draft = WordDraft(
            id = UUID.randomUUID().toString(),
            text = word,
            phonetic = phonetic,
            senses = senses,
            tagsText = tags.distinct().joinToString(", "),
            note = element.string("note").ifBlank { element.string("remark") }.trim()
        )

        return BatchWord(
            draft = draft,
            rawWord = word,
            groupName = groupName,
            duplicateOf = if (dedupKey(word, groupName) in existingKeys) word else null,
            isNew = sourceWords.isNotEmpty() && normaliseWord(word) !in sourceWords
        )
    }

    /**
     * Line-based fallback. Understands the common shapes:
     * `word /phonetic/ n. 释义`, `1. word|n.|释义`, `word 释义`, `word - 释义`,
     * and several senses per line separated by `；` or `|`.
     */
    fun parseBatchLoose(
        raw: String,
        existingKeys: Set<String>,
        sourceWords: Set<String> = emptySet()
    ): List<BatchWord> {
        // Part of speech as it appears in the remainder, e.g. "n. 银行" / "adj 有韧性的".
        // The trailing separator is required so "v." is consumed but a stray "s" is not.
        val sensePosRegex = Regex(
            """^\s*(n|v|vt|vi|adj|adv|pron|prep|conj|int|interj|num|art|aux|det|phr|idiom|abbr)\s*[.．、]?\s+""",
            RegexOption.IGNORE_CASE
        )
        val indexPrefix = Regex("""^\s*\d+\s*[.、)\]]\s*""")
        // IPA only: no whitespace inside, so "diet /ˈdaɪət/ n. …" keeps the word intact.
        val phoneticRegex = Regex("""\[[^\]\s]{1,40}]|/[^/\s]{1,40}/""")
        // One word plus up to two following words that do NOT look like a part of speech.
        // The lookahead is what keeps "diet n. 饮食" from becoming the headword "diet n".
        val headwordRegex = Regex(
            """[A-Za-z][A-Za-z'’-]*(?:\s+[A-Za-z][A-Za-z'’-]*){0,2}(?=\s*[^\sA-Za-z'’-]|\s*$)"""
        )
        val leadSeparators = charArrayOf('-', '*', '•', '·', '—', '–')

        /** "n.", "adj", "phr" … — never part of a headword. */
        fun looksLikePos(token: String): Boolean =
            token.trim().trimEnd('.', '．').lowercase() in POS_TOKENS

        /** Removes the phonetic hint; replacing with "" would corrupt the text when absent. */
        fun stripPhonetic(line: String, phonetic: String): String =
            if (phonetic.isBlank()) line else line.replace(phonetic, " ")

        /** Reads the English headword from the start of a line. */
        fun readHeadword(line: String, phonetic: String): String? {
            val withoutPhonetic = stripPhonetic(line, phonetic).trimStart(*leadSeparators).trim()
            if (withoutPhonetic.isEmpty()) return null
            val start = withoutPhonetic.indexOfFirst { it.isLetter() && it.code < 0x0250 }
            if (start < 0) return null
            val match = headwordRegex.find(withoutPhonetic.substring(start))?.value ?: return null
            val tokens = match.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            // Keep trailing words only while they are not a part of speech.
            val kept = mutableListOf(tokens.first())
            for (index in 1 until tokens.size) {
                if (looksLikePos(tokens[index])) break
                kept.add(tokens[index])
            }
            val candidate = kept.joinToString(" ").trim().trimEnd('-', '\'', '’')
            if (candidate.count { it.isLetter() } < 2) return null
            return candidate
        }

        /** Turns "n. 银行 (a bank)；v. 存钱" into as many senses as it contains. */
        fun readSenses(rest: String): List<Sense> {
            if (rest.isBlank()) return emptyList()
            val segments = rest
                .split('；', ';', '｜')
                .flatMap { it.split('|') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val senses = segments.mapNotNull { segment ->
                var body = segment
                var en = ""
                Regex("""\(([^)]*)\)""").find(body)?.let { match ->
                    en = match.groupValues[1].trim()
                    body = body.removeRange(match.range).trim()
                }
                val posMatch = sensePosRegex.find("$body ")
                val pos = posMatch?.groupValues?.getOrNull(1).orEmpty()
                if (posMatch != null) {
                    body = body.substring(posMatch.value.trimEnd().length).trim()
                }
                body = body.trimStart('：', ':', '-', '—', ' ', '\t')
                if (body.isBlank()) return@mapNotNull null
                Sense(
                    partOfSpeech = PartOfSpeech.fromAi(pos),
                    meaningZh = body,
                    meaningEn = en
                )
            }
            if (senses.isNotEmpty()) return senses
            // No sense marker at all: treat the whole remainder as one meaning, or fall back to
            // taking the last pipe-separated column (the "word|phonetic|meaning" shape).
            val fallback = rest.substringAfterLast('|').trim()
            return if (fallback.isNotBlank()) listOf(Sense(PartOfSpeech.OTHER, fallback)) else emptyList()
        }

        return raw.lines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("```") || it.startsWith("{") || it.startsWith("}") || it.startsWith("[") }
            .mapNotNull { line ->
                val cleaned = indexPrefix.replace(line, "").trim()
                if (cleaned.isBlank()) return@mapNotNull null
                val phonetic = phoneticRegex.find(cleaned)?.value.orEmpty()
                val word = readHeadword(cleaned, phonetic) ?: return@mapNotNull null
                if (!isPlausibleHeadword(word)) return@mapNotNull null
                // Drop the headword itself, then everything up to it (leading dashes, indexes).
                val afterPhonetic = stripPhonetic(cleaned, phonetic)
                val headIndex = afterPhonetic.indexOf(word)
                val rest = if (headIndex >= 0) {
                    afterPhonetic.substring(headIndex + word.length)
                } else {
                    afterPhonetic
                }.trim()
                val senses = readSenses(rest)
                if (senses.isEmpty()) return@mapNotNull null
                BatchWord(
                    draft = WordDraft(
                        id = UUID.randomUUID().toString(),
                        text = word,
                        phonetic = phonetic,
                        senses = senses
                    ),
                    rawWord = word,
                    duplicateOf = if (dedupKey(word, null) in existingKeys) word else null,
                    isNew = sourceWords.isNotEmpty() && normaliseWord(word) !in sourceWords
                )
            }
            .distinctBy { it.rawWord.lowercase() }
            .toList()
    }

    /** Extracts every top-level {...} inside the first JSON array (or a bare object). */
    private fun extractJsonObjects(raw: String): List<JsonObject> {
        val arrayText = extractJsonArray(raw)
        if (arrayText != null) {
            val parsed = runCatching { json.parseToJsonElement(arrayText) }.getOrNull()
            val array = parsed as? JsonArray
            if (array != null) {
                return array.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            }
        }
        // The array may be *unterminated* because the model ran out of output tokens. Salvage
        // every complete object before the cut instead of discarding the whole answer — this
        // used to make the batch importer look like it did nothing at all.
        val salvaged = salvageObjects(raw)
        if (salvaged.isNotEmpty()) return salvaged

        // A single object, or several objects separated by newlines.
        val objects = mutableListOf<JsonObject>()
        var searchFrom = 0
        while (true) {
            val next = extractJsonObject(raw.substring(searchFrom)) ?: break
            val parsed = runCatching { json.parseToJsonElement(next).jsonObject }.getOrNull()
            if (parsed != null) objects.add(parsed)
            searchFrom += raw.substring(searchFrom).indexOf(next) + next.length
            if (searchFrom >= raw.length) break
        }
        return objects
    }

    /**
     * Reads every complete `{…}` block out of a possibly truncated `[…]` array.
     *
     * Walks the text with the same string/escape awareness as [extractJsonArray] and emits each
     * balanced object. An unfinished trailing object is dropped, which is exactly what is wanted
     * when a long answer is cut off: keep the words that did arrive.
     */
    fun salvageObjects(raw: String): List<JsonObject> {
        val text = stripFences(raw)
        val objects = mutableListOf<JsonObject>()
        var index = 0
        while (index < text.length) {
            if (text[index] != '{') {
                index++
                continue
            }
            var depth = 0
            var inString = false
            var escaped = false
            var end = -1
            var cursor = index
            while (cursor < text.length) {
                val c = text[cursor]
                when {
                    escaped -> escaped = false
                    c == '\\' && inString -> escaped = true
                    c == '"' -> inString = !inString
                    !inString && c == '{' -> depth++
                    !inString && c == '}' -> {
                        depth--
                        if (depth == 0) {
                            end = cursor
                            break
                        }
                    }
                }
                cursor++
            }
            if (end < 0) break // truncated: nothing more to salvage
            val candidate = text.substring(index, end + 1)
            runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull()?.let(objects::add)
            index = end + 1
        }
        return objects
    }

    /** Finds the first balanced [...] block, tolerating ```json fences and prose. */
    fun extractJsonArray(raw: String): String? {
        val text = stripFences(raw)
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val c = text[index]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    /** Finds the first balanced {...} block, tolerating ```json fences and surrounding prose. */
    fun extractJsonObject(raw: String): String? {
        val text = stripFences(raw)
        val start = text.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val c = text[index]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun stripFences(raw: String): String = raw.trim()
        .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        .removeSuffix("```")
        .trim()

    /**
     * Best-effort reader for answers that are not JSON (e.g. a plain dictionary paragraph).
     * Keeps the app useful even when the model ignores the format instruction.
     */
    fun parseLoose(raw: String, fallbackWord: String): AiWordResult {
        val phonetic = Regex("""/[^/\n]{2,60}/""").find(raw)?.value.orEmpty()
        val lines = raw.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotEmpty() }

        val posRegex = Regex(
            """^(n|v|vt|vi|adj|adv|pron|prep|conj|int|interj|num|art|aux|det|phr|idiom|abbr)\."""
        )

        val senses = lines.mapNotNull { line ->
            val match = posRegex.find(line.lowercase()) ?: return@mapNotNull null
            val pos = line.substring(0, match.value.length)
            val rest = line.substring(match.value.length).trim().trimStart('：', ':', ' ')
            if (rest.isBlank()) return@mapNotNull null
            val parts = rest.split('；', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }
            val zh = parts.firstOrNull().orEmpty()
            val en = parts.drop(1).joinToString("；")
            AiSense(partOfSpeech = pos, meaningZh = zh, meaningEn = en)
        }

        return AiWordResult(
            word = fallbackWord.trim(),
            phonetic = phonetic,
            senses = senses,
            note = if (senses.isEmpty()) raw.trim() else ""
        )
    }

    /** Convenience for tests and the UI: the draft's headword initial. */
    fun initial(word: String): String = Word.initialOf(word)

    /** Part-of-speech tokens that must never be absorbed into a headword. */
    private val POS_TOKENS = setOf(
        "n", "v", "vt", "vi", "adj", "adv", "pron", "prep", "conj", "int", "interj",
        "num", "art", "aux", "det", "phr", "idiom", "abbr", "pl", "sing", "pret", "pp"
    )

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}