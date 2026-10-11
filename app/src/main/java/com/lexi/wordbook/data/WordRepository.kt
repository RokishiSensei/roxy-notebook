package com.lexi.wordbook.data

import android.content.Context
import androidx.core.content.edit
import com.lexi.wordbook.util.Csv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Local-first storage for the vocabulary list.
 *
 * Uses SharedPreferences with a single JSON document so the payload stays human readable,
 * trivially exportable, and dependency-light. Writes are serialized on a background
 * dispatcher by the caller (the ViewModel's coroutine scope).
 */
class WordRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lexi_wordbook", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _words = MutableStateFlow(load())
    val words: StateFlow<List<Word>> = _words.asStateFlow()

    private val _groups = MutableStateFlow(loadGroups())
    val groups: StateFlow<List<WordGroup>> = _groups.asStateFlow()

    private fun load(): List<Word> {
        val raw = prefs.getString(KEY_WORDS, null) ?: return emptyList()
        val parsed = runCatching {
            json.decodeFromString<List<Word>>(raw)
        }.getOrElse { emptyList() }
        // Guard the uniqueness rule for data written before it existed: keep the first entry
        // per (headword, group) pair.
        val seen = mutableSetOf<String>()
        val unique = parsed.filter { seen.add(dedupKey(it.text, it.groupId)) }
        return unique.sortedWith(WORD_ORDER)
    }

    private fun loadGroups(): List<WordGroup> {
        val raw = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<WordGroup>>(raw) }
            .getOrElse { emptyList() }
            .sortedWith(GROUP_ORDER)
    }

    // ------------------------------------------------------------------ groups

    fun createGroup(name: String, emoji: String = "📁", colorIndex: Int = 0): WordGroup {
        val group = WordGroup(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "未命名分组" },
            emoji = emoji,
            colorIndex = colorIndex.coerceIn(0, WordGroup.COLOR_COUNT - 1),
            createdAt = System.currentTimeMillis()
        )
        persistGroups(_groups.value + group)
        return group
    }

    fun updateGroup(group: WordGroup) {
        persistGroups(_groups.value.map { if (it.id == group.id) group else it })
    }

    /**
     * Removing a group deletes the entries that live **only** in that group.
     *
     * Words that also exist in another group survive there and are *not* copied into 未分组 —
     * otherwise the same headword would appear twice, which the app never allows.
     */
    fun deleteGroup(id: String) {
        persistGroups(_groups.value.filterNot { it.id == id })
        persist(_words.value.filterNot { it.groupId == id })
    }

    /**
     * Moves entries to [groupId] and, when [copyToId] is given, also leaves a copy of each
     * selected entry in that group. Returns the number of copies created.
     */
    fun moveAndCopy(wordIds: Set<String>, groupId: String?, copyToId: String?): Int {
        if (wordIds.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val selected = _words.value.filter { it.id in wordIds }
        val moved = _words.value.map {
            if (it.id in wordIds) it.copy(groupId = groupId, updatedAt = now) else it
        }
        if (copyToId == null) {
            persist(moved)
            return 0
        }
        // Never create a second entry for the same headword inside the target group.
        val keys = moved.map { dedupKey(it.text, it.groupId) }.toMutableSet()
        val copies = mutableListOf<Word>()
        selected.forEachIndexed { index, word ->
            if (keys.add(dedupKey(word.text, copyToId))) {
                copies += word.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    groupId = copyToId,
                    createdAt = now + index,
                    updatedAt = now + index
                )
            }
        }
        persist(moved + copies)
        return copies.size
    }

    /** Copies the given entries into [groupId]; returns how many were created. */
    fun copyInto(wordIds: Set<String>, groupId: String?): Int {
        if (wordIds.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val keys = _words.value.map { dedupKey(it.text, it.groupId) }.toMutableSet()
        val copies = mutableListOf<Word>()
        _words.value.filter { it.id in wordIds }.forEachIndexed { index, word ->
            if (keys.add(dedupKey(word.text, groupId))) {
                copies += word.asFreshCopy(groupId, now + index)
            }
        }
        if (copies.isNotEmpty()) persist(_words.value + copies)
        return copies.size
    }

    /**
     * A copy starts its own learning history.
     *
     * Only the dictionary content travels; the review progress (star, status, counts, next due
     * date) was earned on the original entry, so the new group does not inherit it.
     */
    private fun Word.asFreshCopy(targetGroupId: String?, now: Long): Word = copy(
        id = java.util.UUID.randomUUID().toString(),
        groupId = targetGroupId,
        starred = false,
        status = LearningStatus.NEW,
        reviewCount = 0,
        nextReviewAt = now,
        lastReviewedAt = 0L,
        createdAt = now,
        updatedAt = now
    )

    private fun persistGroups(list: List<WordGroup>) {
        val sorted = list.sortedWith(GROUP_ORDER)
        _groups.value = sorted
        runCatching { prefs.edit { putString(KEY_GROUPS, json.encodeToString(sorted)) } }
    }

    private fun persist(list: List<Word>) {
        val sorted = list.sortedWith(WORD_ORDER)
        _words.value = sorted
        runCatching { prefs.edit { putString(KEY_WORDS, json.encodeToString(sorted)) } }
    }

    /**
     * Inserts or updates one draft.
     *
     * Uniqueness rule (see [dedupKey]): a headword may appear **once per group**, so the same
     * word can live in 「四级核心」 and 「阅读生词」 at the same time, while one group never holds
     * two identical entries.
     *
     * Matching is by id **and** by (headword, group). When the key matches an existing entry the
     * draft is written onto that entry's id, so the row is replaced rather than appended — this is
     * what used to let the AI batch importer (which mints a fresh UUID per entry) store the same
     * word twice. Review metadata of the surviving entry is preserved.
     */
    fun upsert(draft: WordDraft, now: Long = System.currentTimeMillis()): Word {
        val current = _words.value
        val key = dedupKey(draft.text, draft.groupId)
        val existing = current.firstOrNull { it.id == draft.id }
            ?: current.firstOrNull { dedupKey(it.text, it.groupId) == key }
        // applyTo keeps the draft's own id, so adopt the existing id explicitly; otherwise the
        // replace below would not find anything to replace.
        val target = if (existing != null && existing.id != draft.id) {
            draft.copy(id = existing.id)
        } else {
            draft
        }
        val word = target.applyTo(existing, now)
        val known = current.any { it.id == word.id }
        val next = if (known) current.map { if (it.id == word.id) word else it } else current + word
        persist(next)
        return word
    }

    /**
     * Identity used for duplicate detection: the headword (case-insensitive) inside its group.
     * A null group means the 「未分组」 bucket.
     */
    fun dedupKey(text: String, groupId: String?): String =
        text.trim().lowercase() + "@" + (groupId ?: "")

    /** Every stored (headword, group) identity — used by the AI batch preview. */
    fun allDedupKeys(): Set<String> = _words.value.map { dedupKey(it.text, it.groupId) }.toSet()

    /** Is there already an entry with this headword in [groupId] (or outside groups)? */
    fun findDuplicate(text: String, groupId: String?, exceptId: String? = null): Word? {
        val key = dedupKey(text, groupId)
        return _words.value.firstOrNull { it.id != exceptId && dedupKey(it.text, it.groupId) == key }
    }

    /**
     * Looks for the same headword **anywhere** in the library, preferring the requested group.
     *
     * The same headword may live in several groups, so "already in this group" and "exists in
     * another group" are reported differently when a new word is added.
     */
    fun findAnyDuplicate(text: String, groupId: String? = null, exceptId: String? = null): Word? {
        val headword = text.trim().lowercase()
        if (headword.isEmpty()) return null
        val candidates = _words.value.filter {
            it.id != exceptId && it.text.trim().lowercase() == headword
        }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.groupId == groupId }
            ?: candidates.firstOrNull { it.groupId == null }
            ?: candidates.first()
    }

    /**
     * One headword that exists more than once in the same bucket.
     *
     * [entries] is ordered oldest first, so `entries.first()` is the one an automatic merge keeps.
     */
    data class DuplicateGroup(
        val headword: String,
        val groupId: String?,
        val groupName: String,
        val entries: List<Word>
    ) {
        val extraCount: Int get() = entries.size - 1

        /** Stable identity for the manual picker: headword plus its bucket. */
        val key: String get() = headword.trim().lowercase() + "@" + (groupId ?: "")
    }

    /**
     * Every duplicate in the given bucket, for the preview and the manual picker.
     *
     * @param groupId a group id, `null` for 「未分组」, or [NO_GROUP] for the whole library.
     */
    fun duplicateGroups(groupId: String? = NO_GROUP): List<DuplicateGroup> {
        val names = _groups.value.associate { it.id to it.name }
        return bucket(groupId)
            .groupBy { dedupKey(it.text, it.groupId) }
            .filter { (_, entries) -> entries.size > 1 }
            .map { (_, entries) ->
                val ordered = entries.sortedBy { it.createdAt }
                DuplicateGroup(
                    headword = ordered.first().text,
                    groupId = ordered.first().groupId,
                    groupName = ordered.first().groupId?.let { names[it] } ?: "未分组",
                    entries = ordered
                )
            }
            .sortedWith(compareBy({ it.headword.lowercase() }, { it.groupName }))
    }

    /**
     * Scans one bucket and folds duplicate entries together.
     *
     * A duplicate is two entries with the same headword in the same bucket — the state older
     * builds could produce when a model returned a word twice. The survivor keeps the earliest
     * creation time so the list order does not jump, and the richest content wins (senses, tags
     * and phonetic are unioned, progress is the most advanced of the run).
     *
     * @param groupId the bucket to scan: a group id, `null` for 「未分组」, or [NO_GROUP] (the
     *   default) for the whole library.
     * @return how many entries were removed.
     */
    fun cleanDuplicates(groupId: String? = NO_GROUP): Int =
        applyMerges(duplicateGroups(groupId).associateWith { mergeDuplicates(it.entries) })

    /**
     * Merges duplicates, letting the user override which entry survives.
     *
     * [keepIds] maps a duplicate's key (see [DuplicateGroup.key]) to the id of the entry to keep.
     * Anything not named there falls back to the automatic choice (oldest entry).
     *
     * @return how many entries were removed.
     */
    fun cleanDuplicatesKeeping(
        groupId: String? = NO_GROUP,
        keepIds: Map<String, String>
    ): Int {
        val merges = duplicateGroups(groupId).associate { group ->
            val entries = group.entries
            val chosen = keepIds[group.key]?.let { id -> entries.firstOrNull { it.id == id } }
            val merged = if (chosen == null) {
                mergeDuplicates(entries)
            } else {
                // The user's pick keeps its own content; only tags are unioned so no tag is lost.
                chosen.copy(tags = entries.flatMap { it.tags }.distinct())
            }
            group to merged
        }
        return applyMerges(merges)
    }

    /** Replaces each merged entry with its survivor and deletes the entries it absorbed. */
    private fun applyMerges(merges: Map<DuplicateGroup, Word>): Int {
        if (merges.isEmpty()) return 0
        val removed = mutableSetOf<String>()
        val list = _words.value.toMutableList()

        merges.forEach { (group, survivor) ->
            val index = list.indexOfFirst { it.id == survivor.id }
            if (index >= 0) list[index] = survivor
            group.entries.filter { it.id != survivor.id }.forEach { removed += it.id }
        }

        if (removed.isEmpty()) return 0
        persist(list.filterNot { it.id in removed })
        return removed.size
    }

    /** How many entries [cleanDuplicates] would remove, for the confirmation dialog. */
    fun countDuplicates(groupId: String? = NO_GROUP): Int =
        duplicateGroups(groupId).sumOf { it.extraCount }

    /** The words [cleanDuplicates] would consider: whole library, one group, or 未分组. */
    private fun bucket(groupId: String?): List<Word> = when (groupId) {
        NO_GROUP -> _words.value
        else -> _words.value.filter { it.groupId == groupId }
    }

    /** Combines a duplicate run into one entry: union of content, most advanced progress. */
    private fun mergeDuplicates(entries: List<Word>): Word {
        val base = entries.first()
        return base.copy(
            phonetic = entries.firstOrNull { it.phonetic.isNotBlank() }?.phonetic ?: base.phonetic,
            senses = entries.flatMap { it.senses }.distinct(),
            tags = entries.flatMap { it.tags }.distinct(),
            note = entries.firstOrNull { it.note.isNotBlank() }?.note ?: base.note,
            starred = entries.any { it.starred },
            status = entries.maxByOrNull { STATUS_RANK[it.status] ?: 0 }?.status ?: base.status,
            reviewCount = entries.maxOf { it.reviewCount },
            lastReviewedAt = entries.maxOf { it.lastReviewedAt },
            nextReviewAt = entries.minOf { it.nextReviewAt },
            updatedAt = entries.maxOf { it.updatedAt }
        )
    }

    /** Copy of [word] placed in [targetGroupId] (null = 未分组) with a fresh id. */
    fun duplicateInto(
        word: Word,
        targetGroupId: String?,
        now: Long = System.currentTimeMillis()
    ): Word {
        val copy = word.asFreshCopy(targetGroupId, now)
        persist(_words.value + copy)
        return copy
    }

    /** Batch insert used by the bundled 四级 word pack and the AI batch importer. */
    fun insertAll(words: List<Word>): Int {
        if (words.isEmpty()) return 0
        persist(_words.value + words)
        return words.size
    }

    /** Finds (or creates) the group used by the bundled 四级 word pack. */
    fun ensureCet4Group(): WordGroup =
        _groups.value.firstOrNull { it.name == CET4_GROUP_NAME }
            ?: createGroup(CET4_GROUP_NAME, "🎓", 0)

    /** Plain move, kept for callers that do not need the copy variant. */
    fun assignGroup(wordIds: Set<String>, groupId: String?) {
        moveAndCopy(wordIds, groupId, copyToId = null)
    }

    fun upsertAll(words: List<Word>): Int {
        if (words.isEmpty()) return 0
        val byId = _words.value.associateByTo(LinkedHashMap()) { it.id }
        val idByKey = _words.value.associateTo(mutableMapOf()) { dedupKey(it.text, it.groupId) to it.id }
        var written = 0
        words.forEach { incoming ->
            val key = dedupKey(incoming.text, incoming.groupId)
            val targetId = idByKey[key]
            if (targetId == null) {
                byId[incoming.id] = incoming
                idByKey[key] = incoming.id
                written++
                return@forEach
            }
            // Already have this headword in this group: fold the new senses in.
            val target = byId[targetId] ?: incoming
            byId[targetId] = if (target.id == incoming.id) {
                incoming
            } else {
                target.copy(
                    phonetic = target.phonetic.ifBlank { incoming.phonetic },
                    senses = (target.senses + incoming.senses).distinct(),
                    tags = (target.tags + incoming.tags).distinct(),
                    note = target.note.ifBlank { incoming.note },
                    updatedAt = maxOf(target.updatedAt, incoming.updatedAt)
                )
            }
            written++
        }
        persist(byId.values.toList())
        return written
    }

    fun delete(id: String) {
        persist(_words.value.filterNot { it.id == id })
    }

    fun deleteMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        persist(_words.value.filterNot { it.id in ids })
    }

    fun get(id: String): Word? = _words.value.firstOrNull { it.id == id }

    /** Star / unstar a batch in one write. */
    fun setStarred(wordIds: Set<String>, starred: Boolean) {
        if (wordIds.isEmpty()) return
        val now = System.currentTimeMillis()
        persist(
            _words.value.map {
                if (it.id in wordIds) it.copy(starred = starred, updatedAt = now) else it
            }
        )
    }

    /** Entries added since local midnight. */
    fun addedToday(): List<Word> {
        val start = com.lexi.wordbook.util.Dates.startOfDay(System.currentTimeMillis())
        return _words.value.filter { it.createdAt >= start }
    }

    fun clearAll() = persist(emptyList())

    /** Snapshot used by the repository to record a review outcome. */
    fun updateWord(id: String, transform: (Word) -> Word) {
        val current = _words.value
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val next = current.toMutableList()
        next[index] = transform(current[index])
        persist(next)
    }

    /**
     * Full backup: groups **and** words.
     *
     * The previous version exported only the words. Their `groupId` values point at group ids in
     * the old install, so restoring into a fresh app left every word attached to a group that
     * did not exist — the group list stayed empty and the chips showed "0 个词条".
     */
    fun exportJson(): String = json.encodeToString(
        BackupPayload.serializer(),
        BackupPayload(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            groups = _groups.value,
            words = _words.value
        )
    )

    /** What a JSON backup contains. */
    @Serializable
    data class BackupPayload(
        val version: Int = 2,
        val exportedAt: Long = 0L,
        val groups: List<WordGroup> = emptyList(),
        val words: List<Word> = emptyList()
    )

    /**
     * Restores a JSON backup and returns how many words were written.
     *
     * Accepts both the current object payload and the bare `[…]` array written by older builds;
     * a legacy array carries no group definitions, so its dangling `groupId`s are dropped rather
     * than pointing at groups that do not exist.
     */
    fun importJson(payload: String, merge: Boolean = true): Int {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return 0

        val restoredGroups: List<WordGroup>
        val parsedWords: List<Word>
        when (root) {
            is JsonArray -> {
                // Legacy backup: words only. Keep the ids so entries stay internally consistent,
                // but re-point them at nothing (see below).
                restoredGroups = emptyList()
                parsedWords = runCatching {
                    json.decodeFromJsonElement(ListSerializer(Word.serializer()), root)
                }.getOrNull().orEmpty()
            }

            is JsonObject -> {
                val backup = runCatching {
                    json.decodeFromJsonElement(BackupPayload.serializer(), root)
                }.getOrNull() ?: return 0
                restoredGroups = backup.groups
                parsedWords = backup.words
            }

            else -> return 0
        }

        if (restoredGroups.isNotEmpty()) {
            // Merge by name so restoring onto an existing library reuses its groups instead of
            // creating duplicates, and remap the backup's ids onto the real ones.
            val existingByName = _groups.value.associate { it.name.lowercase() to it.id }.toMutableMap()
            val idRemap = mutableMapOf<String, String>()
            restoredGroups.forEach { group ->
                val key = group.name.lowercase()
                val target = existingByName[key] ?: run {
                    val created = createGroup(group.name, group.emoji, group.colorIndex)
                    existingByName[key] = created.id
                    created.id
                }
                idRemap[group.id] = target
            }
            val remapped = parsedWords.map { word ->
                val sourceId = word.groupId
                when {
                    sourceId == null -> word
                    idRemap[sourceId] != null -> word.copy(groupId = idRemap[sourceId])
                    // The backup referenced a group it did not ship: drop the dangling id.
                    else -> word.copy(groupId = null)
                }
            }
            return writeBackup(remapped, merge)
        }

        val known = _groups.value.map { it.id }.toSet()
        val safe = parsedWords.map { word ->
            when {
                word.groupId == null -> word
                word.groupId in known -> word
                else -> word.copy(groupId = null)
            }
        }
        return writeBackup(safe, merge)
    }

    private fun writeBackup(words: List<Word>, merge: Boolean): Int {
        val cleaned = words.filter { it.id.isNotBlank() && it.text.isNotBlank() }
        if (!merge) {
            persist(cleaned)
            return cleaned.size
        }
        return upsertAll(cleaned)
    }

    fun exportCsv(): String = Csv.encode(IMPORT_HEADER, _words.value.map { word ->
        val groupName = word.groupId?.let { id -> _groups.value.firstOrNull { it.id == id }?.name }
        listOf(
            word.text,
            word.phonetic,
            formatSenses(word.senses.ifEmpty { listOf(Sense()) }),
            word.tags.joinToString(" "),
            groupName.orEmpty(),
            word.note,
            word.status.name
        )
    })

    /** One "pos 释义" segment per sense, joined with '；' — compact and easy to type by hand. */
    private fun formatSenses(senses: List<Sense>): String = senses.joinToString("；") { sense ->
        val pos = sense.partOfSpeech.takeIf { it != PartOfSpeech.OTHER }?.label.orEmpty()
        val en = sense.meaningEn.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val example = sense.example.takeIf { it.isNotBlank() }?.let { " 例: $it" }.orEmpty()
        listOf(pos, sense.meaningZh).filter { it.isNotBlank() }.joinToString(" ") + en + example
    }

    /** Parses "n. 银行；v. 存入 (to deposit)" back into structured senses. */
    private fun parseSenses(raw: String): List<Sense> {
        if (raw.isBlank()) return emptyList()
        return raw.split('；', ';', '|', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { segment ->
                var rest = segment
                var example = ""
                val exampleIndex = rest.indexOf("例:")
                if (exampleIndex >= 0) {
                    example = rest.substring(exampleIndex + 2).trim()
                    rest = rest.substring(0, exampleIndex).trim()
                }
                var meaningEn = ""
                val englishMatch = Regex("""\(([^)]*[A-Za-z][^)]*)\)""").find(rest)
                if (englishMatch != null) {
                    meaningEn = englishMatch.groupValues[1].trim()
                    rest = rest.removeRange(englishMatch.range).trim()
                }
                val posMatch = Regex(
                    """^(n|v|vt|vi|adj|adv|pron|prep|conj|int|interj|num|art|aux|det|phr|idiom|abbr)\.?\s+""",
                    RegexOption.IGNORE_CASE
                ).find("$rest ")
                val pos = posMatch?.let { match ->
                    PartOfSpeech.entries.firstOrNull {
                        it.label.equals(match.groupValues[1] + ".", ignoreCase = true)
                    } ?: PartOfSpeech.fromAi(match.groupValues[1])
                } ?: PartOfSpeech.OTHER
                if (posMatch != null) rest = rest.substring(posMatch.valueLength().coerceAtMost(rest.length)).trim()

                Sense(
                    partOfSpeech = pos,
                    meaningZh = rest.trim(),
                    meaningEn = meaningEn,
                    example = example
                )
            }
            .filterNot { it.isBlank }
    }

    private fun MatchResult.valueLength(): Int = value.trimEnd().length

    /**
     * Imports a table in the app's own format — or almost any hand-made table.
     *
     * Supported delimiters: tab (recommended), `|`, `｜`, `,`, `，`, `;`.
     * Every row is one word; senses live in a single cell as `n. 释义；v. 释义`.
     * A header row is optional: when the first cell of the first line is not an English
     * word ("word", "单词", "英文"…) it is treated as a header.
     */
    fun importCsv(payload: String): Int {
        val rows = parseDelimited(payload)
        if (rows.isEmpty()) return 0

        val header = rows.first().map { it.trim().lowercase() }
        val hasHeader = header.any { it in HEADER_ALIASES } && header.none { it.matches(Regex("[a-zA-Z][a-zA-Z' -]{1,30}")) && it !in HEADER_ALIASES }
        val body = if (hasHeader) rows.drop(1) else rows

        fun indexOf(vararg names: String): Int {
            if (!hasHeader) return -1
            names.forEach { name -> header.indexOf(name).takeIf { it >= 0 }?.let { return it } }
            return -1
        }

        // Column positions: fall back to the app's canonical order when there is no header.
        val iWord = if (hasHeader) indexOf("word", "单词", "英文", "headword") else 0
        val iPhone = if (hasHeader) indexOf("phonetic", "音标", "ipa") else 1
        val iSenses = if (hasHeader) indexOf("senses", "释义", "meaning", "meaningzh", "中文") else 2
        val iTags = if (hasHeader) indexOf("tags", "标签") else 3
        val iGroup = if (hasHeader) indexOf("group", "分组", "分类") else 4
        val iNote = if (hasHeader) indexOf("note", "备注", "笔记") else 5
        val iStatus = if (hasHeader) indexOf("status", "状态") else 6
        if (iWord < 0) return 0

        // Headerless data is usually just "word|meaning", so only trust the canonical column
        // order when the rows actually look like the full template.
        val columnCount = body.maxOfOrNull { it.size } ?: 0
        val twoColumnList = !hasHeader && columnCount <= 2

        fun cell(row: List<String>, index: Int): String =
            if (index in row.indices) row[index].trim() else ""

        val now = System.currentTimeMillis()
        // Merge into what is already stored, matching on (headword, group), so re-importing the
        // same word updates that entry (same id) instead of creating a duplicate — while the
        // same word in a *different* group stays a separate entry.
        val storedByKey = _words.value.associateBy { dedupKey(it.text, it.groupId) }
        val groupIdByName = _groups.value.associate { it.name.lowercase() to it.id }.toMutableMap()
        fun resolveGroup(name: String): String? {
            if (name.isBlank()) return null
            val key = name.lowercase()
            return groupIdByName[key] ?: createGroup(name).let { group ->
                groupIdByName[key] = group.id
                group.id
            }
        }

        val parsed = LinkedHashMap<String, Word>()
        body.forEach { row ->
            val text = cell(row, iWord).trim().trim('"')
            if (text.isBlank()) return@forEach
            val rowGroupId = if (twoColumnList) {
                null
            } else {
                cell(row, iGroup).takeIf { it.isNotBlank() }?.let(::resolveGroup)
            }
            val key = dedupKey(text, rowGroupId)
            val existing = parsed[key] ?: storedByKey[key]
            val sensesRaw = if (twoColumnList) cell(row, 1) else cell(row, iSenses)
            val newSenses = parseSenses(sensesRaw)
            val senses = when {
                newSenses.isEmpty() -> existing?.senses.orEmpty().ifEmpty { listOf(Sense()) }
                existing == null -> newSenses
                else -> (existing.senses + newSenses).distinct()
            }
            parsed[key] = Word(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                text = if (existing != null) existing.text else text,
                phonetic = if (twoColumnList) {
                    existing?.phonetic.orEmpty()
                } else {
                    cell(row, iPhone).ifBlank { existing?.phonetic.orEmpty() }
                },
                senses = senses,
                status = if (twoColumnList) {
                    existing?.status ?: LearningStatus.NEW
                } else {
                    LearningStatus.fromName(cell(row, iStatus))
                        .takeIf { cell(row, iStatus).isNotBlank() }
                        ?: existing?.status ?: LearningStatus.NEW
                },
                starred = existing?.starred ?: false,
                source = existing?.source.orEmpty(),
                tags = if (twoColumnList) {
                    existing?.tags.orEmpty()
                } else {
                    cell(row, iTags)
                        .split(' ', ',', '，', ';', '；')
                        .map { it.trim().removePrefix("#") }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { existing?.tags.orEmpty() }
                },
                note = if (twoColumnList) {
                    existing?.note.orEmpty()
                } else {
                    cell(row, iNote).ifBlank { existing?.note.orEmpty() }
                },
                kind = existing?.kind ?: WordKind.WORD,
                groupId = rowGroupId ?: existing?.groupId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                reviewCount = existing?.reviewCount ?: 0,
                nextReviewAt = existing?.nextReviewAt ?: now,
                lastReviewedAt = existing?.lastReviewedAt ?: 0L
            )
        }

        return upsertAll(parsed.values.toList())
    }

    /** Splits the payload into rows, picking the delimiter that appears most in the first line. */
    private fun parseDelimited(payload: String): List<List<String>> {
        val text = payload.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return emptyList()
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val candidates = listOf('\t', '|', '｜', ',', '，')
        val delimiter = candidates
            .maxByOrNull { candidate -> firstLine.count { it == candidate } }
            ?.takeIf { firstLine.contains(it) }

        if (delimiter == null) {
            // Single-column list: one word per line.
            return text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { listOf(it) }
        }
        return if (delimiter == ',') {
            // Commas appear inside meanings, so honour RFC4180 quoting.
            Csv.decode(text)
        } else {
            text.lines()
                .filter { it.isNotBlank() }
                .map { line -> line.split(delimiter).map { cell -> cell.trim().trim('"') } }
        }
    }

    companion object {
        private const val KEY_WORDS = "words_json_v1"
        private const val KEY_GROUPS = "groups_json_v1"

        /** Group created by 「添加四级单词」. */
        const val CET4_GROUP_NAME = "四级核心"

        /** Sentinel for 「清理重复词条」: scan the whole library rather than one group. */
        const val NO_GROUP = "__all__"

        /** Progress order used when merging duplicates; the furthest along wins. */
        private val STATUS_RANK = mapOf(
            LearningStatus.NEW to 0,
            LearningStatus.LEARNING to 1,
            LearningStatus.FAMILIAR to 2,
            LearningStatus.MASTERED to 3
        )

        /** Canonical import/export columns — one row per word. */
        val IMPORT_HEADER = listOf("word", "phonetic", "senses", "tags", "group", "note", "status")

        /** Cells that mark a first row as a header rather than data. */
        private val HEADER_ALIASES = setOf(
            "word", "单词", "英文", "headword", "phonetic", "音标", "ipa",
            "senses", "释义", "meaning", "meaningzh", "meaningen", "中文", "英文释义",
            "tags", "标签", "group", "分组", "分类", "note", "备注", "笔记",
            "status", "状态", "partofspeech", "词性", "example", "例句"
        )

        /** Sample text offered in the import screen so users can see the expected shape. */
        val IMPORT_TEMPLATE: String = buildString {
            appendLine(IMPORT_HEADER.joinToString("\t"))
            appendLine("serendipity\t/ˌserənˈdɪpəti/\tn. 机缘巧合；意外发现珍奇事物的运气 (happy accident)\t雅思 高级\t阅读生词\t我总是拼错这个词")
            appendLine("bank\t/bæŋk/\tn. 银行 (financial institution)；n. 河岸 (riverside)；v. 把钱存入银行\t考研\t\t")
            appendLine("give up\t/ɡɪv ʌp/\tphr. 放弃；认输 (stop trying)\t口语\t短语\t")
        }

        /** A minimal, hand-typable variant that also works. */
        const val IMPORT_TEMPLATE_SIMPLE: String = """diet|/ˈdaɪət/|n. 日常饮食；规定饮食|健康 减肥
resilient|/rɪˈzɪliənt/|adj. 有韧性的；能快速恢复的|考研
give up|/ɡɪv ʌp/|phr. 放弃；认输|口语"""

        /** Alphabetical by initial, then case-insensitive by headword. */
        val WORD_ORDER: Comparator<Word> = compareBy<Word> { it.initial }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.text }

        /** Groups are always listed alphabetically by name, like the words inside them. */
        val GROUP_ORDER: Comparator<WordGroup> = Comparator { a, b ->
            val byName = String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
            if (byName != 0) byName else a.createdAt.compareTo(b.createdAt)
        }
    }
}
