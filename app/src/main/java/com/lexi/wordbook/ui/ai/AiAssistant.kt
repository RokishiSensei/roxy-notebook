package com.lexi.wordbook.ui.ai

import com.lexi.wordbook.ai.AiClient
import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ai.AiWordParser
import com.lexi.wordbook.ai.BatchParseResult
import com.lexi.wordbook.ai.BatchWord
import com.lexi.wordbook.ai.ChatMessage
import com.lexi.wordbook.ai.StoredMessage
import com.lexi.wordbook.data.AiWordResult
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.WordDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Async state of an 「AI 查询」 request. */
sealed interface AiLookupState {
    data object Idle : AiLookupState
    data class Loading(val query: String) : AiLookupState
    data class Success(
        val query: String,
        val raw: String,
        val structured: AiWordResult?,
        val parsed: Boolean
    ) : AiLookupState

    data class Failure(val query: String, val message: String) : AiLookupState
}

/** Result of an 「AI 批量导入」 run. */
data class BatchImportOutcome(
    val words: List<BatchWord> = emptyList(),
    val usedJson: Boolean = true,
    val note: String = "",
    val error: String? = null,
    /** Entries the model returned more than once for the same group, folded into one row. */
    val mergedDuplicates: Int = 0
)

/**
 * Shared AI plumbing: builds prompts, calls the configured endpoint, turns answers into
 * [WordDraft]s and keeps the chat transcript in sync.
 */
class AiAssistant(
    private val configProvider: () -> AiConfig,
    private val appendMessage: (role: String, content: String, error: Boolean) -> StoredMessage,
    private val updateMessage: (id: String, content: String, error: Boolean) -> Unit,
    private val appendPending: () -> StoredMessage
) {

    private val _lookup = MutableStateFlow<AiLookupState>(AiLookupState.Idle)
    val lookup: StateFlow<AiLookupState> = _lookup.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val _modelsBusy = MutableStateFlow(false)
    val modelsBusy: StateFlow<Boolean> = _modelsBusy.asStateFlow()

    fun resetLookup() {
        _lookup.value = AiLookupState.Idle
    }

    fun client(): AiClient = AiClient(configProvider())

    /**
     * Asks the endpoint for its model list (used by the settings screen).
     *
     * Returns (chat models, error, hidden count) — hidden counts the embeddings / TTS / image
     * entries that were filtered out, so the picker can explain the shorter list.
     */
    suspend fun listModels(): Triple<List<String>, String?, Int> {
        val config = configProvider()
        if (config.baseUrl.isBlank()) return Triple(emptyList(), "请先填写 API 地址", 0)
        _modelsBusy.value = true
        val result = AiClient(config).listModels()
        _modelsBusy.value = false
        return Triple(result.models, result.error, result.hiddenCount)
    }

    // ------------------------------------------------------------------ batch import

    /**
     * Sends raw pasted/file text to the model and turns the answer into importable drafts.
     *
     * Long inputs are split into chunks sized to the configured output budget (a truncated JSON
     * array used to yield nothing at all), and any chunk that comes back short is retried in
     * halves before the result is accepted.
     */
    suspend fun batchImport(
        raw: String,
        existingKeys: Set<String>,
        onProgress: (done: Int, total: Int) -> Unit
    ): BatchImportOutcome {
        val config = configProvider()
        if (!config.isConfigured) {
            return BatchImportOutcome(error = "还没有配置 AI 接口，请到「设置 → AI 接口」填写。")
        }
        val chunks = chunkInput(raw, chunkSizeFor(config))
        if (chunks.isEmpty()) return BatchImportOutcome(error = "没有可整理的内容")

        // Keyed by "headword@group" — the same identity the repository uses — so a word cannot
        // enter the preview twice. Merging instead of appending is what keeps the duplicate
        // `observed` rows out of the list.
        val collected = LinkedHashMap<String, BatchWord>()
        val seenCounts = mutableMapOf<String, Int>()
        var usedJson = true
        var note = ""
        var failedChunks = 0
        var truncatedChunks = 0
        var done = 0

        fun accept(words: List<BatchWord>) {
            words.forEach { word ->
                val key = AiWordParser.dedupKey(word.rawWord, word.groupName)
                seenCounts[key] = (seenCounts[key] ?: 0) + 1
                val current = collected[key]
                collected[key] = when {
                    current == null -> word
                    // The later answer usually carries the fuller entry (it came from the retry
                    // of a truncated chunk), so fold the richer one in.
                    word.draft.senses.size >= current.draft.senses.size -> word.copy(
                        isNew = current.isNew && word.isNew
                    )
                    else -> current.copy(isNew = current.isNew && word.isNew)
                }
            }
        }

        // The queue starts with every chunk and grows when a short answer is split in half.
        val queue = ArrayDeque(chunks)
        while (queue.isNotEmpty()) {
            val chunk = queue.removeFirst()
            onProgress(done, done + queue.size + 1)
            val result = requestChunk(config, chunk, existingKeys)

            if (result.words.isEmpty() && result.note.isNotBlank() && !result.usedJson) {
                // Nothing parsed at all: report it rather than passing an empty result through.
                failedChunks++
                note = result.note
                done++
                continue
            }
            if (!result.usedJson) usedJson = false
            if (result.words.isEmpty() && result.note.isNotBlank()) note = result.note

            // A cut-off answer: split the input and try again rather than lose the words.
            //
            // The comparison counts word *units*, not lines: the input may pack several words
            // into one line ("apple, banana, cherry"), and counting lines then expected 1 answer
            // where the model returns 3 — every such chunk looked short and was split forever.
            val expected = countWordUnits(chunk)
            val short = result.words.size < expected
            val lines = chunk.lines().filter { it.isNotBlank() }
            if (short && lines.size > MIN_SPLIT_LINES) {
                truncatedChunks++
                val half = lines.size / 2
                queue.addFirst(lines.drop(half).joinToString("\n"))
                queue.addFirst(lines.take(half).joinToString("\n"))
                done++
                continue
            }

            accept(result.words)
            done++
        }
        onProgress(done, done)

        // Entries the model returned more than once for the same group; the preview folds them
        // into one row and reports how many were merged.
        val mergedCount = seenCounts.values.count { it > 1 }
        val deduped = collected.values
            .sortedWith(
                compareBy(
                    { it.draft.text.firstOrNull()?.uppercaseChar() ?: '#' },
                    { it.draft.text.lowercase() }
                )
            )

        return BatchImportOutcome(
            words = deduped,
            usedJson = usedJson,
            mergedDuplicates = mergedCount,
            note = when {
                deduped.isEmpty() -> note.ifBlank { "没有解析出词条，可以试试换个模型或调整提示词" }
                failedChunks > 0 -> "有 $failedChunks 段内容没能整理出来：$note"
                truncatedChunks > 0 -> "有 $truncatedChunks 段被模型截断，已自动拆分重试并保留已识别的词条。"
                note.isNotBlank() -> note
                else -> ""
            }
        )
    }

    /** One model round trip for a chunk of input text. */
    private suspend fun requestChunk(
        config: AiConfig,
        chunk: String,
        existingKeys: Set<String>,
        sourceText: String = chunk
    ): BatchParseResult {
        if (chunk.isBlank()) return BatchParseResult(emptyList(), usedJson = true)
        val response = AiClient(config).chat(
            listOf(
                ChatMessage(ChatMessage.SYSTEM, config.batchPrompt),
                ChatMessage(ChatMessage.USER, chunk)
            )
        )
        if (!response.isSuccess) {
            // `note` doubles as the failure channel for this helper.
            return BatchParseResult(emptyList(), usedJson = false, note = response.error.orEmpty())
        }
        val parsed = AiWordParser.parseBatch(response.text, existingKeys, sourceText)
        return if (parsed.words.isEmpty() && parsed.note.isBlank()) {
            parsed.copy(note = "模型没有返回可解析的词条")
        } else {
            parsed
        }
    }

    /** Splits the input into fixed-size line groups so each request stays small. */
    /**
     * Groups the input into chunks of at most [maxLines] word units.
     *
     * Counting units rather than lines matters for wide input: 20 comma-separated words on one
     * line is one line but twenty entries, and used to be sent as a single oversized request.
     */
    private fun chunkInput(raw: String, maxLines: Int): List<String> {
        val units = wordUnits(raw)
        if (units.isEmpty()) return emptyList()
        return units.chunked(maxLines).map { it.joinToString("\n") }
    }


    /** One-shot dictionary lookup used by the word editor. */
    suspend fun lookup(query: String): AiLookupState {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val state = AiLookupState.Failure(query, "请先输入要查询的英文单词")
            _lookup.value = state
            return state
        }
        val config = configProvider()
        if (!config.isConfigured) {
            val state = AiLookupState.Failure(trimmed, "还没有配置 AI 接口，请到「设置 → AI 接口」填写。")
            _lookup.value = state
            return state
        }

        _lookup.value = AiLookupState.Loading(trimmed)
        val response = AiClient(config).chat(
            listOf(
                ChatMessage(ChatMessage.SYSTEM, config.lookupPrompt),
                ChatMessage(ChatMessage.USER, "请查询这个单词：$trimmed")
            )
        )
        val state = if (!response.isSuccess) {
            AiLookupState.Failure(trimmed, response.error ?: "请求失败")
        } else {
            val structured = AiWordParser.parse(response.text)
            AiLookupState.Success(
                query = trimmed,
                raw = response.text,
                structured = structured ?: AiWordParser.parseLoose(response.text, trimmed),
                parsed = structured != null
            )
        }
        _lookup.value = state
        return state
    }

    /**
     * Sends one chat turn. A pending assistant row is inserted first so the UI can render a
     * "正在思考…" bubble, then replaced by the reply (or by the error text).
     */
    suspend fun sendChat(text: String, history: List<ChatMessage>): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val config = configProvider()
        if (!config.isConfigured) {
            appendMessage(
                ChatMessage.ASSISTANT,
                "还没有配置 AI 接口，请到「设置 → AI 接口」填写地址和密钥。",
                true
            )
            return false
        }

        _chatBusy.value = true
        val pending = appendPending()
        pendingId.value = pending.id

        val response = AiClient(config).chat(
            buildList {
                add(ChatMessage(ChatMessage.SYSTEM, config.chatPrompt))
                addAll(history.takeLast(MAX_HISTORY))
                add(ChatMessage(ChatMessage.USER, trimmed))
            }
        )
        _chatBusy.value = false

        if (response.isSuccess) {
            updateMessage(pending.id, response.text, false)
            return true
        }
        updateMessage(pending.id, response.error ?: "请求失败", true)
        return false
    }

    /** Id of the in-flight assistant bubble, so screens can animate it. */
    private val pendingId = MutableStateFlow<String?>(null)
    val pendingMessageId: StateFlow<String?> = pendingId.asStateFlow()

    /** Keeps at most [MAX_HISTORY] earlier turns in the prompt to bound token use. */
    private val MAX_HISTORY = 12

    companion object {
        /** Rough output cost of one finished dictionary entry (word + senses + tags). */
        /**
         * Rough output cost of one finished entry: headword + phonetic + two senses with Chinese
         * and English meanings + an example sentence. Measured against real 4–6 letter entries;
         * underestimating this is what truncates a batch.
         */
        const val TOKENS_PER_ENTRY = 300
        /** Fallback when the profile leaves the output budget unset. */
        val DEFAULT_BATCH_TOKENS = AiConfig.DEFAULT_MAX_TOKENS
        const val MIN_LINES_PER_CHUNK = 5
        const val MAX_LINES_PER_CHUNK = 60

        /** Ceiling for a manually chosen batch size. */
        const val HARD_MAX_LINES_PER_CHUNK = 300

        /** Never split a chunk shorter than this, or the retry loop would never settle. */
        const val MIN_SPLIT_LINES = 6

        /**
         * Chunk size for a given output-token budget.
         *
         * 80% of the budget is spent on entries (the rest absorbs JSON scaffolding and the odd
         * long example sentence), and one entry is assumed to cost [TOKENS_PER_ENTRY].
         */
        fun linesPerChunkFor(maxTokens: Int): Int {
            val budget = if (maxTokens > 0) maxTokens else DEFAULT_BATCH_TOKENS
            val estimated = budget * 4 / 5 / TOKENS_PER_ENTRY
            return estimated.coerceIn(MIN_LINES_PER_CHUNK, MAX_LINES_PER_CHUNK)
        }

        /**
         * Effective input lines per 「AI 批量导入」 request.
         *
         * A manual [AiConfig.batchSize] always wins; otherwise the chunk follows the configured
         * `maxTokens`, because a chunk larger than the output budget gets cut off mid-JSON and
         * the tail of the list silently disappears.
         *
         * Named `chunkSizeFor` rather than `linesPerChunk` on purpose: an instance method with
         * the same name silently shadowed this one and recursed until the stack overflowed.
         */
        fun chunkSizeFor(config: AiConfig): Int =
            if (config.batchSize > 0) {
                config.batchSize.coerceIn(MIN_LINES_PER_CHUNK, HARD_MAX_LINES_PER_CHUNK)
            } else {
                linesPerChunkFor(config.maxTokens)
            }

        /** How many word units the AI is expected to produce for a chunk (see [wordUnits]). */
        fun countWordUnits(chunk: String): Int = chunk.lineSequence().sumOf(::countWordsInLine)

        /** How many word units one line holds. A blank line contributes nothing. */
        fun countWordsInLine(line: String): Int =
            if (line.isBlank()) 0 else wordUnits(line).size.coerceAtLeast(1)

        /**
         * Splits text into the individual words the AI must handle.
         *
         * The input is not necessarily one word per line: people paste
         * `apple, banana, cherry` / `apple;banana` / `apple | banana` just as often, and a
         * line-counting chunker mis-sized every one of those.
         *
         * Only explicit separators split a line. A line without one stays whole on purpose, so a
         * compound phrase such as `thickening agent` or `in contrast to` is not torn into
         * fragments — telling a phrase from a bare list of words is the model's job (rule 6/10 of
         * the prompt), because no word-count heuristic can do it.
         */
        fun wordUnits(text: String): List<String> = text.lines().flatMap { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@flatMap emptyList()
            // Strip "1." / "2)" / "3、" *before* validating: "2. banana" still has a space in it.
            val parts = trimmed.split(',', '，', ';', '；', '\t', '|', '/', '、')
                .map(::stripIndex)
                .filter(::looksLikeHeadword)
            val units = if (parts.size > 1) parts else listOf(stripIndex(trimmed))
            units.filter { it.isNotEmpty() }
        }

        /** Removes leading list numbering: "1. apple" / "2) banana" / "3、cherry". */
        private fun stripIndex(token: String): String =
            token.trim().replace(Regex("""^\d+\s*[.、)\]]\s*"""), "").trim()

        /** A plausible headword: short, no spaces, and not a stray punctuation mark. */
        private fun looksLikeHeadword(token: String): Boolean {
            val value = token.trim().trim('-', '.', '"', '\'', '(', ')', '[', ']', '“', '”')
            if (value.length !in 2..40) return false
            if (value.any { it.isWhitespace() }) return false
            // Skip numbers and pure punctuation such as "1." or "—".
            if (value.none { it.isLetter() }) return false
            return true
        }

        /** Text of the in-flight assistant bubble before the reply arrives. */
        const val PENDING_TEXT = "\u200B"

        /** Merges AI output into the working draft, keeping anything the user already typed. */
        fun merge(result: AiWordResult, current: WordDraft): WordDraft {
            val aiSenses = result.senses.map { it.toSense() }.filterNot { it.isBlank }
            val existingSenses = current.senses.filterNot { it.isBlank }
            val senses: List<Sense> = when {
                aiSenses.isEmpty() -> existingSenses.ifEmpty { current.senses }
                existingSenses.isEmpty() -> aiSenses
                else -> aiSenses + existingSenses
            }.distinct()

            return current.copy(
                text = current.text.ifBlank { result.word },
                phonetic = result.phonetic.ifBlank { current.phonetic },
                senses = senses.ifEmpty { listOf(Sense()) },
                tagsText = if (current.tagsText.isBlank()) {
                    result.tags.joinToString(", ")
                } else {
                    current.tagsText
                },
                note = when {
                    result.note.isBlank() -> current.note
                    current.note.isBlank() -> result.note
                    current.note.contains(result.note) -> current.note
                    else -> current.note + "\n" + result.note
                }
            )
        }
    }
}
