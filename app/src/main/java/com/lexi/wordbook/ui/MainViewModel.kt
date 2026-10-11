package com.lexi.wordbook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lexi.wordbook.WordBookApp
import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ai.ChatMessage
import com.lexi.wordbook.ai.StoredMessage
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.UserPreferences
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.data.WordGroup
import com.lexi.wordbook.data.WordRepository
import com.lexi.wordbook.speech.SpeechPlayer
import com.lexi.wordbook.ui.ai.AiAssistant
import com.lexi.wordbook.ui.ai.AiLookupState
import com.lexi.wordbook.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/** User-triggered transient messages surfaced as snackbars. */
data class UiMessage(val id: Long, val text: String)

/**
 * How the library list is organised. The segmented switcher was removed from the UI: the list
 * is always grouped A–Z, and groups / days are applied as filters instead.
 */
enum class LibraryView(val labelZh: String) {
    ALPHABET("按字母"),
    RECENT("最近添加"),
    TODAY("今天新增"),
    GROUP("分组")
}

/** Non-persisted filters applied to the vocabulary list. */
data class WordFilter(
    val query: String = "",
    val starredOnly: Boolean = false,
    val statuses: Set<LearningStatus> = emptySet(),
    val letter: String? = null,
    val groupId: String? = null,
    /** Start-of-day millis picked from the calendar; null = every date. */
    val day: Long? = null
) {
    val isActive: Boolean
        get() = starredOnly || statuses.isNotEmpty() || letter != null ||
            groupId != null || day != null
}

/** Everything the review / quiz sessions need. */
data class ReviewSession(
    val queue: List<String> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val correct: Int = 0,
    val total: Int = 0
) {
    val currentId: String? get() = queue.getOrNull(index)
    val isFinished: Boolean get() = queue.isNotEmpty() && index >= queue.size
    val progress: Float get() = if (queue.isEmpty()) 0f else index.toFloat() / queue.size
}

/** Immutable snapshot returned by the quiz generator. */
enum class QuizType(val labelZh: String) {
    CHOICE_ZH_TO_EN("中译英选择"),
    CHOICE_EN_TO_ZH("英译中选择"),
    SPELLING("拼写"),
    MIXED("混合出题")
}

enum class QuizPool(val labelZh: String) {
    ALL("全部词条"),
    DUE("待复习"),
    STARRED("重点词"),
    TODAY("今天新增"),
    GROUP("指定分组")
}

/** Where the quiz draws its words from. */
data class QuizSelection(
    val pool: QuizPool = QuizPool.ALL,
    val groupId: String? = null,
    val type: QuizType = QuizType.CHOICE_ZH_TO_EN,
    val questionCount: Int = 10
)

/** One answered question, kept so the result screen can explain mistakes. */
data class QuizAnswer(
    val prompt: String,
    val answer: String,
    val given: String,
    val correct: Boolean,
    val meaningZh: String = "",
    val phonetic: String = ""
)

data class QuizResult(
    val answers: List<QuizAnswer>,
    val type: QuizType,
    val poolLabel: String
) {
    val correctCount: Int get() = answers.count { it.correct }
    val total: Int get() = answers.size
    val accuracy: Float get() = if (total == 0) 0f else correctCount.toFloat() / total
    val wrong: List<QuizAnswer> get() = answers.filterNot { it.correct }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as WordBookApp).container

    val words: StateFlow<List<Word>> = container.words.words
    val groups: StateFlow<List<WordGroup>> = container.words.groups
    val preferences: StateFlow<UserPreferences> = container.preferences.state
    val speech: SpeechPlayer = container.speech
    val isSpeaking: StateFlow<String?> = container.speech.speaking

    val aiConfig: StateFlow<AiConfig> = container.aiConfig.state
    val aiProfiles: StateFlow<List<com.lexi.wordbook.ai.AiProfile>> = container.aiConfig.profiles
    val activeAiProfileId: StateFlow<String?> = container.aiConfig.activeProfileId
    val chatMessages: StateFlow<List<StoredMessage>> = container.chat.messages
    val chatSessions: StateFlow<List<com.lexi.wordbook.ai.ChatSession>> = container.chat.sessions
    val activeChatId: StateFlow<String?> = container.chat.activeId

    private val assistant = AiAssistant(
        configProvider = { container.aiConfig.state.value },
        appendMessage = { role, content, error -> container.chat.append(role, content, error) },
        updateMessage = { id, content, error -> container.chat.update(id, content, error) },
        appendPending = { container.chat.appendPending() }
    )

    val aiChatBusy: StateFlow<Boolean> = assistant.chatBusy
    val aiModelsBusy: StateFlow<Boolean> = assistant.modelsBusy
    val aiPendingMessageId: StateFlow<String?> = assistant.pendingMessageId
    val aiLookup: StateFlow<AiLookupState> = assistant.lookup

    private val _filter = MutableStateFlow(WordFilter())
    val filter: StateFlow<WordFilter> = _filter.asStateFlow()

    private val _view = MutableStateFlow(LibraryView.ALPHABET)
    val view: StateFlow<LibraryView> = _view.asStateFlow()

    /** Ids of entries created since local midnight — used for the 「今天新增」 badge. */
    val todayWordIds: StateFlow<Set<String>> = words
        .map { list ->
            val start = Dates.startOfDay(System.currentTimeMillis())
            list.filter { it.createdAt >= start }.map { it.id }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _review = MutableStateFlow(ReviewSession())
    val review: StateFlow<ReviewSession> = _review.asStateFlow()

    /** Words that should be reviewed today or are still new. */
    val dueToday: StateFlow<List<Word>> = words
        .map { list ->
            val endOfToday = Dates.endOfDay(System.currentTimeMillis())
            list.filter { it.status != LearningStatus.MASTERED && it.nextReviewAt <= endOfToday }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Words reviewed at least once today — drives the daily-goal ring. */
    val reviewedTodayCount: StateFlow<Int> = words
        .map { list -> list.count { Dates.isToday(it.lastReviewedAt) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val filteredWords: StateFlow<List<Word>> =
        combine(words, _filter, _view) { list, query, _ -> applyFilter(list, query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------------------------------------------------------- mutations

    /** Outcome of trying to add or update a word. */
    sealed interface SaveResult {
        /** Added a new word (or updated an existing one). */
        data class Saved(val word: Word) : SaveResult

        /** Nothing was written: the same headword is already in this group. */
        data class DuplicateInGroup(val existing: Word, val groupName: String) : SaveResult

        /** Nothing was written: the same headword exists, but in another group. */
        data class DuplicateElsewhere(val existing: Word, val groupName: String) : SaveResult
    }

    /**
     * Adds or updates a word, refusing to create a second entry for a headword that is already
     * present in the target group.
     *
     * Editing an existing entry (same id) always wins, so renaming and re-saving keeps working.
     */
    fun saveWord(draft: WordDraft): SaveResult {
        val existingById = container.words.get(draft.id)
        if (existingById == null && draft.text.isNotBlank()) {
            // Adding, not editing: a headword already in this group must not be added again.
            container.words.findDuplicate(draft.text, draft.groupId)?.let { clash ->
                val name = groupLabel(clash.groupId)
                _message.value = UiMessage(
                    System.currentTimeMillis(),
                    "「${clash.text}」已经在${name}里了，没有重复添加。"
                )
                return SaveResult.DuplicateInGroup(clash, name)
            }
            // Same word in a *different* group: allowed, but say so — it may be unintentional.
            container.words.findAnyDuplicate(draft.text, draft.groupId, exceptId = draft.id)
                ?.let { other ->
                    val name = groupLabel(other.groupId)
                    _message.value = UiMessage(
                        System.currentTimeMillis(),
                        "「${other.text}」已存在于${name}，这里再添加一份。"
                    )
                    val word = container.words.upsert(draft)
                    return SaveResult.DuplicateElsewhere(word, name)
                }
        }

        val word = container.words.upsert(draft)
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (draft.text.isBlank()) "已保存" else "已保存 “${word.text}”"
        )
        return SaveResult.Saved(word)
    }

    /** Display name of a group id, including the 未分组 bucket. */
    private fun groupLabel(groupId: String?): String =
        groupId?.let { id -> container.words.groups.value.firstOrNull { it.id == id }?.name }
            ?: "未分组"

    /** @return the new word's id so the caller can navigate to it. */
    fun saveDraft(draft: WordDraft): String {
        val result = saveWord(draft)
        return when (result) {
            is SaveResult.Saved -> result.word.id
            is SaveResult.DuplicateInGroup -> result.existing.id
            is SaveResult.DuplicateElsewhere -> result.existing.id
        }
    }

    /** How many duplicate entries exist in [groupId]; [WordRepository.NO_GROUP] scans everything. */
    fun duplicateCount(groupId: String = WordRepository.NO_GROUP): Int =
        container.words.countDuplicates(groupId)

    /** Removes duplicate entries from [groupId]; returns how many were deleted. */
    fun cleanDuplicates(groupId: String? = WordRepository.NO_GROUP): Int {
        val removed = container.words.cleanDuplicates(groupId)
        val scope = if (groupId == WordRepository.NO_GROUP) {
            "全部词条"
        } else {
            groupLabel(groupId)
        }
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (removed == 0) "${scope}里没有重复词条" else "已从${scope}清理 $removed 个重复词条"
        )
        return removed
    }

    // ---------------------------------------------------------------- duplicate cleanup page

    /** Which step of 「清理重复词条」 is on screen. */
    enum class CleanStage { SCAN, MANUAL, DONE }

    /** State of the 「清理重复词条」 page. */
    data class CleanState(
        val stage: CleanStage = CleanStage.SCAN,
        /** Group id, `null` for 未分组, or [WordRepository.NO_GROUP] for the whole library. */
        val scope: String? = WordRepository.NO_GROUP,
        val groups: List<WordRepository.DuplicateGroup> = emptyList(),
        /** Duplicate key -> id of the entry the user chose to keep. */
        val keepIds: Map<String, String> = emptyMap(),
        val removed: Int = 0
    )

    private val _cleanState = MutableStateFlow(CleanState())
    val cleanState: StateFlow<CleanState> = _cleanState.asStateFlow()

    /** Scans [scope] and shows what was found. */
    fun scanDuplicates(scope: String? = WordRepository.NO_GROUP) {
        val found = container.words.duplicateGroups(scope)
        _cleanState.value = CleanState(
            stage = CleanStage.SCAN,
            scope = scope,
            groups = found,
            // Every duplicate defaults to the oldest entry — what an automatic merge keeps.
            keepIds = found.associate { it.key to it.entries.first().id }
        )
    }

    /** Auto-merge: union the content of each duplicate run, keeping the oldest entry. */
    fun cleanDuplicatesNow(): Int {
        val scope = _cleanState.value.scope
        val removed = container.words.cleanDuplicates(scope)
        _cleanState.value = _cleanState.value.copy(
            stage = CleanStage.DONE,
            groups = emptyList(),
            removed = removed
        )
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (removed == 0) "没有发现重复词条" else "已清理 $removed 个重复词条"
        )
        return removed
    }

    /** Switches to the manual picker, keeping the current choices. */
    fun startManualClean() {
        _cleanState.value = _cleanState.value.copy(stage = CleanStage.MANUAL)
    }

    /** Manual merge: keep exactly the entries the user picked. */
    fun cleanDuplicatesKeeping(): Int {
        val state = _cleanState.value
        val removed = container.words.cleanDuplicatesKeeping(state.scope, state.keepIds)
        _cleanState.value = state.copy(
            stage = CleanStage.DONE,
            groups = emptyList(),
            removed = removed
        )
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (removed == 0) "没有需要清理的重复词条" else "已按你的选择清理 $removed 个重复词条"
        )
        return removed
    }

    /** Chooses which entry of one duplicate group survives. */
    fun setDuplicateKeeper(groupKey: String, wordId: String) {
        val state = _cleanState.value
        _cleanState.value = state.copy(keepIds = state.keepIds + (groupKey to wordId))
    }

    /** Points every duplicate group back at its oldest entry. */
    fun resetDuplicateKeepers() {
        val state = _cleanState.value
        _cleanState.value = state.copy(
            keepIds = state.groups.associate { it.key to it.entries.first().id }
        )
    }

    fun closeCleanDuplicates() {
        _cleanState.value = CleanState()
    }

    fun newDraft(): WordDraft = WordDraft(id = UUID.randomUUID().toString())

    fun draftFor(word: Word): WordDraft = WordDraft.from(word)

    fun deleteWord(id: String) {
        container.words.delete(id)
        _message.value = UiMessage(System.currentTimeMillis(), "已删除")
    }

    fun deleteWords(ids: Set<String>) {
        container.words.deleteMany(ids)
        _message.value = UiMessage(System.currentTimeMillis(), "已删除 ${ids.size} 个词条")
    }

    fun toggleStar(id: String) {
        container.words.updateWord(id) { it.copy(starred = !it.starred, updatedAt = System.currentTimeMillis()) }
    }

    // ---------------------------------------------------------------- groups

    fun setLibraryView(view: LibraryView) {
        _view.value = view
        if (view != LibraryView.GROUP) {
            _filter.value = _filter.value.copy(groupId = null)
        }
    }

    fun setGroupFilter(groupId: String?) {
        _filter.value = _filter.value.copy(groupId = groupId)
        _view.value = LibraryView.GROUP
    }

    fun createGroup(name: String, emoji: String, colorIndex: Int): WordGroup {
        val group = container.words.createGroup(name, emoji, colorIndex)
        _message.value = UiMessage(System.currentTimeMillis(), "已创建分组「${group.name}」")
        return group
    }

    fun renameGroup(group: WordGroup, name: String, emoji: String, colorIndex: Int) {
        container.words.updateGroup(
            group.copy(
                name = name.trim().ifBlank { group.name },
                emoji = emoji,
                colorIndex = colorIndex
            )
        )
    }

    fun deleteGroup(group: WordGroup) {
        container.words.deleteGroup(group.id)
        if (_filter.value.groupId == group.id) {
            _filter.value = _filter.value.copy(groupId = null)
        }
        _message.value = UiMessage(
            System.currentTimeMillis(),
            "已删除分组「${group.name}」，词条回到未分组"
        )
    }

    fun assignGroup(wordIds: Set<String>, groupId: String?) {
        container.words.assignGroup(wordIds, groupId)
        val name = groupId?.let { id -> groups.value.firstOrNull { it.id == id }?.name }
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (name == null) "已移出分组" else "已移动到「$name」"
        )
    }

    fun groupName(groupId: String?): String? =
        groupId?.let { id -> groups.value.firstOrNull { it.id == id }?.name }

    /** Words of one group, A–Z — used by 「干净浏览」. */
    fun groupWords(groupId: String?): List<Word> =
        words.value
            .filter { it.groupId == groupId }
            .sortedWith(com.lexi.wordbook.data.WordRepository.WORD_ORDER)

    /**
     * 「干净浏览」 list for [groupId]: filtered by [query] and ordered by [mode].
     * Pure so it can be unit-tested without the UI.
     */
    fun cleanBrowseWords(
        groupId: String?,
        query: String,
        mode: com.lexi.wordbook.data.CleanSortMode
    ): List<Word> = cleanBrowseList(words.value.filter { it.groupId == groupId }, query, mode)

    // ---------------------------------------------------------------- bundled packs

    /**
     * Adds the bundled 四级 word pack into the 「四级核心」 group. Headwords already present in
     * that group are skipped, so tapping twice never duplicates anything.
     */
    fun importCet4Pack() {
        val app = getApplication<Application>()
        val pack = com.lexi.wordbook.data.WordPackLoader.load(app, CET4_ASSET)
        if (pack == null) {
            _message.value = UiMessage(System.currentTimeMillis(), "词库文件读取失败")
            return
        }
        val group = container.words.ensureCet4Group()
        val existing = container.words.words.value
            .filter { it.groupId == group.id }
            .map { it.text.lowercase() }
            .toMutableSet()
        val fresh = pack.words.filter { existing.add(it.word.trim().lowercase()) }
        if (fresh.isEmpty()) {
            _message.value = UiMessage(
                System.currentTimeMillis(),
                "「${group.name}」已经是最新的，共 ${existing.size} 个词条"
            )
            return
        }
        val entities = com.lexi.wordbook.data.WordPackLoader.toWords(fresh, group.id)
        val added = container.words.insertAll(entities)
        _message.value = UiMessage(
            System.currentTimeMillis(),
            "已向「${group.name}」添加 $added 个四级单词"
        )
    }

    // ---------------------------------------------------------------- background

    /**
     * Copies the picked image into app storage so it survives the source being deleted.
     * [prefix] selects which background the image belongs to (main interface vs 干净浏览).
     */
    fun saveBackgroundImage(uri: android.net.Uri, clean: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val folder = if (clean) "background_clean" else "background_main"
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(app.filesDir, folder).apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val target = java.io.File(dir, "background.jpg")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
            if (path.isNullOrBlank()) {
                _message.value = UiMessage(System.currentTimeMillis(), "图片读取失败")
                return@launch
            }
            updateBackground(clean) {
                it.copy(
                    source = com.lexi.wordbook.data.BackgroundSource.IMAGE,
                    imagePath = path
                )
            }
            _message.value = UiMessage(
                System.currentTimeMillis(),
                if (clean) "干净浏览背景已更新" else "主页背景已更新"
            )
        }
    }

    fun clearBackground(clean: Boolean) {
        val app = getApplication<Application>()
        val folder = if (clean) "background_clean" else "background_main"
        java.io.File(app.filesDir, folder).listFiles()?.forEach { it.delete() }
        updateBackground(clean) {
            com.lexi.wordbook.data.BackgroundConfig(source = com.lexi.wordbook.data.BackgroundSource.SOLID)
        }
        _message.value = UiMessage(System.currentTimeMillis(), "已恢复默认背景")
    }

    fun setBackgroundColor(color: Long, clean: Boolean) {
        updateBackground(clean) {
            it.copy(source = com.lexi.wordbook.data.BackgroundSource.SOLID, color = color)
        }
    }

    /** 背景浓度 is 0–100: 0 = background invisible, 100 = background at full strength. */
    fun setBackgroundOpacity(value: Int, clean: Boolean) {
        updateBackground(clean) { it.copy(opacity = value.coerceIn(0, 100)) }
    }

    /**
     * Explicit save. Every change is already written synchronously, so this mainly gives the
     * user a confirmation — and re-writes both backgrounds so nothing can be missed.
     */
    fun saveBackground(clean: Boolean) {
        val prefs = container.preferences.state.value
        container.preferences.update { it }
        val config = if (clean) prefs.cleanBackground else prefs.mainBackground
        _message.value = UiMessage(
            System.currentTimeMillis(),
            (if (clean) "干净浏览背景已保存" else "主页背景已保存") + "（浓度 ${config.opacity}%）"
        )
    }

    fun setCleanSortMode(mode: com.lexi.wordbook.data.CleanSortMode) {
        container.preferences.update { it.copy(cleanSortMode = mode) }
    }

    fun setCleanTextScale(scale: Float) {
        container.preferences.update { it.copy(cleanTextScale = scale.coerceIn(0.8f, 1.8f)) }
    }

    /** Remembers which word sat at the top of 干净浏览 for [groupKey] ("" = 未分组). */
    fun rememberCleanPosition(groupKey: String, wordId: String?) {
        container.preferences.update { prefs ->
            val next = prefs.cleanLastRead.toMutableMap()
            if (wordId.isNullOrBlank()) next.remove(groupKey) else next[groupKey] = wordId
            prefs.copy(cleanLastRead = next)
        }
    }

    /** Word to jump back to when the reader returns, or null when nothing was recorded. */
    fun cleanResumeWordId(groupKey: String): String? =
        container.preferences.state.value.cleanLastRead[groupKey]

    fun setBackgroundEnabled(enabled: Boolean) {
        container.preferences.update { it.copy(backgroundEnabled = enabled) }
    }

    private fun updateBackground(
        clean: Boolean,
        transform: (com.lexi.wordbook.data.BackgroundConfig) -> com.lexi.wordbook.data.BackgroundConfig
    ) {
        container.preferences.update { prefs ->
            if (clean) {
                prefs.copy(cleanBackground = transform(prefs.cleanBackground))
            } else {
                prefs.copy(mainBackground = transform(prefs.mainBackground))
            }
        }
    }

    /** Copies the main background over to 干净浏览 (or the other way round). */
    fun copyBackground(fromCleanToMain: Boolean) {
        container.preferences.update { prefs ->
            if (fromCleanToMain) {
                prefs.copy(mainBackground = prefs.cleanBackground)
            } else {
                prefs.copy(cleanBackground = prefs.mainBackground)
            }
        }
        _message.value = UiMessage(System.currentTimeMillis(), "背景已复制")
    }

    // ---------------------------------------------------------------- batch operations

    fun setStarred(ids: Set<String>, starred: Boolean) {
        container.words.setStarred(ids, starred)
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (starred) "已标记 ${ids.size} 个词条为重点" else "已取消 ${ids.size} 个词条的标记"
        )
    }

    /** Moves the selection into [groupId] and optionally leaves copies in [copyToId]. */
    fun moveAndCopy(ids: Set<String>, groupId: String?, copyToId: String?) {
        val copies = container.words.moveAndCopy(ids, groupId, copyToId)
        val target = groupName(groupId) ?: "未分组"
        _message.value = UiMessage(
            System.currentTimeMillis(),
            buildString {
                append("已把 ${ids.size} 个词条移动到「$target」")
                if (copyToId != null) {
                    val copyTarget = groupName(copyToId) ?: "未分组"
                    append("，并复制 $copies 个到「$copyTarget」")
                }
            }
        )
    }

    /** Copies every word of [from] into [toGroupId] (null = 未分组). */
    fun copyGroupInto(from: WordGroup, toGroupId: String?) {
        val ids = words.value.filter { it.groupId == from.id }.map { it.id }.toSet()
        if (ids.isEmpty()) {
            _message.value = UiMessage(System.currentTimeMillis(), "「${from.name}」还没有词条")
            return
        }
        val copies = container.words.copyInto(ids, toGroupId)
        val target = groupName(toGroupId) ?: "未分组"
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (copies == 0) {
                "「$target」里已经有这些词了"
            } else {
                "已把「${from.name}」的 $copies 个词条复制到「$target」"
            }
        )
    }

    /** Copies the selection into [groupId] without moving the originals. */
    fun copyWords(ids: Set<String>, groupId: String?) {
        val copies = container.words.copyInto(ids, groupId)
        val target = groupName(groupId) ?: "未分组"
        _message.value = UiMessage(
            System.currentTimeMillis(),
            if (copies == 0) "「$target」里已经有这些词了" else "已复制 $copies 个词条到「$target」"
        )
    }

    fun setStatusFor(ids: Set<String>, status: LearningStatus) {
        ids.forEach { id -> setStatus(id, status) }
        _message.value = UiMessage(System.currentTimeMillis(), "已更新 ${ids.size} 个词条的状态")
    }

    // ---------------------------------------------------------------- AI

    fun updateAiConfig(transform: (AiConfig) -> AiConfig) {
        container.aiConfig.updateActive(transform)
    }

    fun addAiProfile(name: String) {
        val profile = container.aiConfig.addProfile(name)
        _message.value = UiMessage(System.currentTimeMillis(), "已新增配置「${profile.displayName}」")
    }

    fun duplicateAiProfile(id: String) {
        container.aiConfig.duplicateProfile(id)
        _message.value = UiMessage(System.currentTimeMillis(), "已复制配置")
    }

    fun activateAiProfile(id: String) {
        container.aiConfig.setActiveProfile(id)
        val name = container.aiConfig.profiles.value.firstOrNull { it.id == id }?.displayName
        _message.value = UiMessage(System.currentTimeMillis(), "已切换到「$name」")
    }

    fun renameAiProfile(id: String, name: String) {
        container.aiConfig.renameProfile(id, name)
    }

    fun deleteAiProfile(id: String) {
        container.aiConfig.deleteProfile(id)
        _message.value = UiMessage(System.currentTimeMillis(), "已删除该配置")
    }

    /** Batch import drafts straight into the library, creating groups on demand. */
    /**
     * Writes the previewed batch into the library.
     *
     * [overrideGroupId] replaces whatever group the model suggested:
     * - `null`  → keep the AI's own grouping (default)
     * - [UNGROUPED_SELECTION] → force everything into 未分组
     * - any id  → force everything into that group
     */
    fun importBatchWords(
        words: List<com.lexi.wordbook.ai.BatchWord>,
        skipDuplicates: Boolean,
        overrideGroupId: String? = null,
        forceUngrouped: Boolean = false
    ): Int {
        val targets = if (skipDuplicates) words.filterNot { it.isDuplicate } else words
        if (targets.isEmpty()) {
            _message.value = UiMessage(System.currentTimeMillis(), "没有需要导入的词条")
            return 0
        }
        // A failure here must never crash the app: report it through the snackbar instead.
        val count = runCatching {
            val groupIdByName = groups.value.associate { it.name.lowercase() to it.id }.toMutableMap()
            val now = System.currentTimeMillis()
            val entities = targets.mapIndexed { index, item ->
                val groupId = when {
                    forceUngrouped -> null
                    overrideGroupId != null -> overrideGroupId
                    else -> item.groupName
                        .takeIf { it.isNotBlank() }
                        ?.let { name ->
                            val key = name.lowercase()
                            groupIdByName[key] ?: createGroup(name, "📁", 0).let { group ->
                                groupIdByName[key] = group.id
                                group.id
                            }
                        }
                }
                item.draft.copy(groupId = groupId).applyTo(null, now + index)
            }
            container.words.upsertAll(entities)
        }.getOrElse { error ->
            _message.value = UiMessage(
                System.currentTimeMillis(),
                "导入失败：${error.message ?: error::class.java.simpleName}"
            )
            return 0
        }
        _message.value = UiMessage(System.currentTimeMillis(), "已导入 $count 个词条")
        return count
    }

    fun applyAiFormatDefaults(format: com.lexi.wordbook.ai.AiApiFormat) {
        container.aiConfig.applyFormatDefaults(format)
    }

    fun resetAiPrompts() {
        container.aiConfig.resetPrompts()
        _message.value = UiMessage(System.currentTimeMillis(), "提示词已恢复默认")
    }

    fun testAiConnection() {
        viewModelScope.launch {
            val config = container.aiConfig.state.value
            if (!config.isConfigured) {
                _message.value = UiMessage(System.currentTimeMillis(), "请先填写接口地址")
                return@launch
            }
            _message.value = UiMessage(System.currentTimeMillis(), "正在测试连接…")
            val response = com.lexi.wordbook.ai.AiClient(config).chat(
                listOf(
                    ChatMessage(ChatMessage.SYSTEM, "你是连接测试助手，只回复「连接成功」四个字。"),
                    ChatMessage(ChatMessage.USER, "测试连接")
                )
            )
            _message.value = UiMessage(
                System.currentTimeMillis(),
                if (response.isSuccess) {
                    "连接成功：${response.text.take(60)}"
                } else {
                    "连接失败：${response.error}"
                }
            )
        }
    }

    /** Fetches the endpoint's model list; the settings screen shows the picker. */
    fun fetchModels(onResult: (List<String>, String?, Int) -> Unit) {
        viewModelScope.launch {
            val (models, error, hidden) = assistant.listModels()
            onResult(models, error, hidden)
        }
    }

    /** Runs the AI batch import; returns the parsed drafts for the preview screen. */
    suspend fun runBatchImport(
        raw: String,
        onProgress: (Int, Int) -> Unit
    ): com.lexi.wordbook.ui.ai.BatchImportOutcome {
        if (raw.isBlank()) {
            return com.lexi.wordbook.ui.ai.BatchImportOutcome(error = "请先粘贴或选择要整理的单词内容")
        }
        // The parser compares "headword@group" keys, so plain lowercase words never matched and
        // every imported entry was flagged as new even when it already existed.
        val existing = container.words.allDedupKeys()
        // Anything unexpected is reported in the UI instead of taking the whole app down.
        // `runCatching` covers Throwable, so even an Error (e.g. a StackOverflowError from a
        // programming mistake) surfaces as a message rather than killing the process.
        return runCatching { assistant.batchImport(raw, existing, onProgress) }
            .getOrElse { error ->
                com.lexi.wordbook.ui.ai.BatchImportOutcome(
                    error = when (error) {
                        is StackOverflowError ->
                            "整理失败：内部处理出错（StackOverflowError），请把这条信息反馈给开发者。"
                        is OutOfMemoryError ->
                            "整理失败：内容太多导致内存不足，请把单词分几批导入。"
                        else -> "整理失败：${error.message ?: error::class.java.simpleName}"
                    }
                )
            }
    }

    /** Looks a word up with the configured AI and returns the resulting draft. */
    fun askAiForWord(query: String, onResult: (WordDraft?) -> Unit) {
        viewModelScope.launch {
            when (val state = assistant.lookup(query)) {
                is AiLookupState.Success -> {
                    val base = WordDraft(id = UUID.randomUUID().toString())
                    val merged = AiAssistant.merge(state.structured!!, base)
                    _message.value = UiMessage(
                        System.currentTimeMillis(),
                        if (state.parsed) {
                            "AI 已返回 ${state.structured.senses.size} 个义项"
                        } else {
                            "AI 返回的不是标准格式，已尽力解析"
                        }
                    )
                    onResult(merged)
                }

                is AiLookupState.Failure -> {
                    _message.value = UiMessage(System.currentTimeMillis(), state.message)
                    onResult(null)
                }

                else -> onResult(null)
            }
        }
    }

    fun clearAiLookup() = assistant.resetLookup()

    // ---------------------------------------------------------------- chat sessions

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val history = container.chat.messages.value.map { ChatMessage(it.role, it.content) }
        viewModelScope.launch {
            container.chat.append(ChatMessage.USER, trimmed)
            assistant.sendChat(trimmed, history)
        }
    }

    fun newChatSession() {
        container.chat.newSession()
        _message.value = UiMessage(System.currentTimeMillis(), "已开始新对话")
    }

    fun openChatSession(id: String) {
        container.chat.setActive(id)
    }

    fun renameChatSession(id: String, title: String) {
        container.chat.renameSession(id, title)
    }

    fun deleteChatSession(id: String) {
        container.chat.deleteSession(id)
        _message.value = UiMessage(System.currentTimeMillis(), "已删除该对话")
    }

    /** Clears the active conversation but keeps the session itself. */
    fun clearActiveChat() {
        container.chat.clearActiveMessages()
        _message.value = UiMessage(System.currentTimeMillis(), "已清空当前对话")
    }

    fun clearChat() {
        container.chat.clear()
        _message.value = UiMessage(System.currentTimeMillis(), "已清空全部对话")
    }

    fun exportChat(): String = container.chat.exportAllText()

    fun exportActiveChat(): String = container.chat.exportText()

    fun setStatus(id: String, status: LearningStatus) {
        container.words.updateWord(id) {
            it.copy(
                status = status,
                reviewCount = it.reviewCount + 1,
                lastReviewedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                nextReviewAt = System.currentTimeMillis() + intervalFor(status)
            )
        }
    }

    fun markReviewed(id: String, remembered: Boolean) {
        container.words.updateWord(id) { word ->
            val next = when {
                word.status == LearningStatus.NEW ->
                    if (remembered) LearningStatus.LEARNING else LearningStatus.NEW
                remembered -> when (word.status) {
                    LearningStatus.LEARNING -> LearningStatus.FAMILIAR
                    LearningStatus.FAMILIAR -> LearningStatus.MASTERED
                    else -> word.status
                }
                else -> LearningStatus.LEARNING
            }
            word.copy(
                status = next,
                reviewCount = word.reviewCount + 1,
                lastReviewedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                nextReviewAt = System.currentTimeMillis() + intervalFor(next, remembered)
            )
        }
    }

    fun setLetterFilter(letter: String?) {
        _filter.value = _filter.value.copy(letter = letter)
    }

    /** Single entry point used by the library screen for search + chips. */
    fun setQueryAndFilters(filter: WordFilter) {
        _filter.value = filter
    }

    fun clearFilters() {
        _filter.value = WordFilter(query = _filter.value.query)
    }

    fun consumeMessage(id: Long) {
        if (_message.value?.id == id) _message.value = null
    }

    fun postMessage(text: String) {
        _message.value = UiMessage(System.currentTimeMillis(), text)
    }

    fun speak(text: String, slow: Boolean = false) {
        speech.speak(text, if (slow) SpeechPlayer.RATE_SLOW else SpeechPlayer.RATE_NORMAL)
    }

    fun stopSpeaking() = speech.stop()

    // ---------------------------------------------------------------- review

    fun startReview(wordsToReview: List<Word>) {
        val queue = wordsToReview.map { it.id }.shuffled()
        _review.value = ReviewSession(queue = queue, total = queue.size)
    }

    fun reveal() {
        _review.value = _review.value.copy(revealed = true)
    }

    fun submitReview(remembered: Boolean) {
        val id = _review.value.currentId ?: return
        markReviewed(id, remembered)
        _review.value = _review.value.copy(
            index = _review.value.index + 1,
            revealed = false,
            correct = _review.value.correct + if (remembered) 1 else 0
        )
    }

    fun endReview() {
        _review.value = ReviewSession()
    }

    // ---------------------------------------------------------------- quiz

    /** Resolves the word pool a quiz should draw from. */
    fun quizPool(selection: QuizSelection): List<Word> {
        val all = words.value
        val startOfToday = Dates.startOfDay(System.currentTimeMillis())
        return when (selection.pool) {
            QuizPool.ALL -> all
            QuizPool.DUE -> all.filter {
                it.status != LearningStatus.MASTERED && it.nextReviewAt <= Dates.endOfDay(System.currentTimeMillis())
            }

            QuizPool.STARRED -> all.filter { it.starred }
            QuizPool.TODAY -> all.filter { it.createdAt >= startOfToday }
            QuizPool.GROUP -> if (selection.groupId == null) {
                all
            } else {
                all.filter { it.groupId == selection.groupId }
            }
        }
    }

    fun quizPoolLabel(selection: QuizSelection): String = when (selection.pool) {
        QuizPool.GROUP -> groupName(selection.groupId)?.let { "分组「$it」" } ?: "指定分组"
        else -> selection.pool.labelZh
    }

    /** Builds the questions for a quiz session. Returns an empty list when the pool is too small. */
    fun buildQuiz(selection: QuizSelection, poolOverride: List<Word>? = null): List<com.lexi.wordbook.ui.screens.QuizItem> {
        val pool = poolOverride ?: quizPool(selection)
        return com.lexi.wordbook.ui.screens.QuizBuilder.build(pool, selection.type, selection.questionCount)
    }

    fun quizResult(answers: List<QuizAnswer>, selection: QuizSelection): QuizResult =
        QuizResult(answers = answers, type = selection.type, poolLabel = quizPoolLabel(selection))

    /**
     * Deterministic "word of the day": the same word for the whole day, picked from the
     * current list by seeding the RNG with the day number.
     */
    fun pickWordOfTheDay(): Word? {
        val pool = words.value
        if (pool.isEmpty()) return null
        val day = Dates.startOfDay(System.currentTimeMillis()) / Dates.DAY_MS
        return pool[Random(day).nextInt(pool.size)]
    }

    // ---------------------------------------------------------------- import / export

    fun exportJson(): String = container.words.exportJson()

    fun exportCsv(): String = container.words.exportCsv()

    /** @return pair of (importedCount, errorMessage) */
    fun importPayload(payload: String, csv: Boolean): Pair<Int, String?> = runCatching {
        val count = if (csv) container.words.importCsv(payload) else container.words.importJson(payload)
        count to null
    }.getOrElse { 0 to (it.message ?: "导入失败") }

    fun importSampleWords(): Int {
        val now = System.currentTimeMillis()
        val sample = SAMPLE_WORDS.mapIndexed { index, word ->
            word.copy(id = UUID.randomUUID().toString(), createdAt = now - index * 1000L, updatedAt = now)
        }
        val count = container.words.upsertAll(sample)
        _message.value = UiMessage(now, "已导入 $count 个示例词条")
        return count
    }

    fun clearAllWords() {
        container.words.clearAll()
        _message.value = UiMessage(System.currentTimeMillis(), "已清空全部词条")
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        container.preferences.update(transform)
    }

    private fun intervalFor(status: LearningStatus, remembered: Boolean = true): Long = when (status) {
        LearningStatus.NEW -> 0L
        LearningStatus.LEARNING -> if (remembered) Dates.DAY_MS else Dates.DAY_MS / 4
        LearningStatus.FAMILIAR -> 3 * Dates.DAY_MS
        LearningStatus.MASTERED -> 7 * Dates.DAY_MS
    }

    private fun applyFilter(list: List<Word>, filter: WordFilter): List<Word> {
        val prefs = preferences.value
        // Everything is alphabetical by default; "最近添加" (and the optional preference switch)
        // goes newest-first. Groups and calendar days are filters, so the order inside them
        // stays A–Z, which is what makes them easy to review.
        val base = when (_view.value) {
            LibraryView.RECENT, LibraryView.TODAY -> list.sortedByDescending { it.createdAt }
            else -> if (prefs.sortByRecent) list.sortedByDescending { it.createdAt } else list
        }
        val todayStart = Dates.startOfDay(System.currentTimeMillis())
        val query = filter.query.trim().lowercase()
        return base.filter { word ->
            if (_view.value == LibraryView.TODAY && word.createdAt < todayStart) return@filter false
            if (filter.day != null) {
                val end = filter.day + Dates.DAY_MS - 1
                if (word.createdAt !in filter.day..end) return@filter false
            }
            if (filter.groupId != null && word.groupId != com.lexi.wordbook.ui.screens.toRealGroupId(filter.groupId)) {
                return@filter false
            }
            if (filter.starredOnly && !word.starred) return@filter false
            if (filter.statuses.isNotEmpty() && word.status !in filter.statuses) return@filter false
            if (filter.letter != null && word.initial != filter.letter) return@filter false
            if (query.isEmpty()) return@filter true
            word.text.lowercase().contains(query) ||
                word.phonetic.lowercase().contains(query) ||
                word.meaningZhJoined.lowercase().contains(query) ||
                word.senses.any { it.meaningEn.lowercase().contains(query) } ||
                word.tags.any { it.lowercase().contains(query) } ||
                word.note.lowercase().contains(query) ||
                word.source.lowercase().contains(query)
        }
    }

    override fun onCleared() {
        container.speech.shutdown()
    }

    companion object {
        /** Bundled 四级 pack shipped inside the APK. */
        const val CET4_ASSET = "wordpacks/cet4-core.json"

        /**
         * Ordering + search + letter filter used by 「干净浏览」.
         *
         * [letter] comes from the A–Z grid and narrows the list before anything else.
         * [CleanSortMode.ALPHABET] keeps the A–Z headword order; the other two sort by the
         * moment the entry was added. Search matches headword, phonetic, meanings, tags and note.
         */
        fun cleanBrowseList(
            source: List<Word>,
            query: String,
            mode: com.lexi.wordbook.data.CleanSortMode
        ): List<Word> {
            val sorted = when (mode) {
                com.lexi.wordbook.data.CleanSortMode.ALPHABET ->
                    source.sortedWith(com.lexi.wordbook.data.WordRepository.WORD_ORDER)
                com.lexi.wordbook.data.CleanSortMode.RECENT ->
                    source.sortedWith(
                        compareByDescending<Word> { it.createdAt }.thenBy { it.text.lowercase() }
                    )
                com.lexi.wordbook.data.CleanSortMode.OLDEST ->
                    source.sortedWith(
                        compareBy<Word> { it.createdAt }.thenBy { it.text.lowercase() }
                    )
            }
            val term = query.trim()
            if (term.isEmpty()) return sorted
            return sorted.filter { word -> word.matchesClean(term) }
        }

        /**
         * Flat LazyColumn index of the first entry for each letter in 「干净浏览」.
         *
         * [offset] is the number of leading items before the first letter header (clean browse
         * has one: the screen header). Each group occupies its header plus its entries.
         */
        fun cleanHeaderIndex(words: List<Word>, offset: Int = 1): Map<String, Int> =
            groupedHeaderIndex(
                com.lexi.wordbook.util.Alphabet.group(words),
                offset
            )

        /**
         * Same mapping for a list that is already grouped.
         *
         * Used by the library, where the search field / chips / filters are items of the very
         * same LazyColumn and therefore shift every header down by [offset].
         */
        fun groupedHeaderIndex(
            groups: List<Pair<String, List<Word>>>,
            offset: Int
        ): Map<String, Int> {
            val index = mutableMapOf<String, Int>()
            var cursor = offset
            groups.forEach { (key, group) ->
                index[key] = cursor
                cursor += group.size + 1
            }
            return index
        }

        private fun Word.matchesClean(term: String): Boolean {
            if (text.contains(term, ignoreCase = true)) return true
            if (phonetic.contains(term, ignoreCase = true)) return true
            if (note.contains(term, ignoreCase = true)) return true
            if (tags.any { it.contains(term, ignoreCase = true) }) return true
            return senses.any { sense ->
                sense.meaningZh.contains(term, ignoreCase = true) ||
                    sense.meaningEn.contains(term, ignoreCase = true) ||
                    sense.example.contains(term, ignoreCase = true)
            }
        }

        val SAMPLE_WORDS: List<Word> = listOf(
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "serendipity",
                phonetic = "/ˌserənˈdɪpəti/",
                senses = listOf(
                    Sense(PartOfSpeech.NOUN, "意外发现珍奇事物的运气；机缘巧合", "the occurrence of happy events by chance"),
                    Sense(PartOfSpeech.NOUN, "意外发现的有价值之物", "a lucky discovery")
                ),
                source = "阅读",
                tags = listOf("雅思", "高级")
            ),
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "resilient",
                phonetic = "/rɪˈzɪliənt/",
                senses = listOf(
                    Sense(PartOfSpeech.ADJECTIVE, "有韧性的；能快速恢复的", "able to recover quickly from difficulties")
                ),
                source = "听力",
                tags = listOf("考研")
            ),
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "meticulous",
                phonetic = "/məˈtɪkjələs/",
                senses = listOf(
                    Sense(PartOfSpeech.ADJECTIVE, "一丝不苟的；极其仔细的", "showing great attention to detail")
                ),
                source = "写作",
                tags = listOf("写作替换")
            ),
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "mitigate",
                phonetic = "/ˈmɪtɪɡeɪt/",
                senses = listOf(
                    Sense(PartOfSpeech.VERB, "减轻；缓解（痛苦、损失）", "to make something less severe")
                ),
                source = "新闻",
                tags = listOf("动词")
            ),
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "ephemeral",
                phonetic = "/ɪˈfemərəl/",
                senses = listOf(
                    Sense(PartOfSpeech.ADJECTIVE, "短暂的；转瞬即逝的", "lasting for a very short time")
                ),
                source = "文学",
                tags = listOf("高级")
            ),
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "bank",
                phonetic = "/bæŋk/",
                senses = listOf(
                    Sense(PartOfSpeech.NOUN, "银行；库", "an institution that keeps money"),
                    Sense(PartOfSpeech.NOUN, "河岸；堤", "the land alongside a river"),
                    Sense(PartOfSpeech.VERB, "把钱存入银行", "to keep money in a bank")
                ),
                source = "课堂"
            ),
            Word(
                id = java.util.UUID.randomUUID().toString(),
                text = "give up",
                phonetic = "/ɡɪv ʌp/",
                senses = listOf(
                    Sense(PartOfSpeech.PHRASE, "放弃；认输", "to stop trying"),
                    Sense(PartOfSpeech.PHRASE, "戒除（习惯）", "to stop doing something")
                ),
                source = "口语",
                tags = listOf("短语")
            )
        )
    }
}