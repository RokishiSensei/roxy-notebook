package com.lexi.wordbook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lexi.wordbook.R
import com.lexi.wordbook.ai.ChatSession
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.screens.AiSettingsScreen
import com.lexi.wordbook.ui.screens.BatchImportScreen
import com.lexi.wordbook.ui.screens.BatchImportState
import com.lexi.wordbook.ui.screens.BookshelfScreen
import com.lexi.wordbook.ui.screens.ChatScreen
import com.lexi.wordbook.ui.screens.CleanBrowseScreen
import com.lexi.wordbook.ui.screens.CleanDuplicatesScreen
import com.lexi.wordbook.ui.screens.FlashcardScreen
import com.lexi.wordbook.ui.screens.GroupPickerDialog
import com.lexi.wordbook.ui.screens.LibraryScreen
import com.lexi.wordbook.ui.screens.QuizBuilder
import com.lexi.wordbook.ui.screens.QuizItem
import com.lexi.wordbook.ui.screens.QuizResultScreen
import com.lexi.wordbook.ui.screens.QuizRunScreen
import com.lexi.wordbook.ui.screens.QuizSetupScreen
import com.lexi.wordbook.ui.screens.ReviewHomeScreen
import com.lexi.wordbook.ui.screens.SettingsScreen
import com.lexi.wordbook.ui.screens.WordEditorScreen
import com.lexi.wordbook.ui.screens.nextStatus
import com.lexi.wordbook.ui.screens.possibleQuestionCount
import com.lexi.wordbook.util.DayUtils
import kotlinx.coroutines.launch

/** Top-level destinations. Labels come from string resources so zh/en both work. */
private enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
    LIBRARY(R.string.tab_library, Icons.AutoMirrored.Rounded.MenuBook),
    REVIEW(R.string.tab_review, Icons.Rounded.School),
    CHAT(R.string.tab_chat, Icons.Rounded.Forum),
    SETTINGS(R.string.tab_settings, Icons.Rounded.Settings)
}

/**
 * Where the library tab is: the bookshelf (level 1) or one bucket (level 2).
 *
 * Three genuinely different destinations that a nullable group id could not express without
 * conflating "no filter" with 「未分组」.
 */
private sealed interface LibraryLocation {
    /** Level 1: the grid of group cards. */
    data object Shelf : LibraryLocation

    /** 「全部词条」: every group, no filter. */
    data object AllWords : LibraryLocation

    /** 「未分组」: only words whose groupId is null. */
    data object Ungrouped : LibraryLocation

    /** One real group. */
    data class Group(val id: String) : LibraryLocation
}

/** Screens pushed on top of the tab shell. */
private sealed interface PushedScreen {
    data class Editor(val isNew: Boolean) : PushedScreen
    data object QuizSetup : PushedScreen
    data class QuizRun(val items: List<QuizItem>, val title: String, val selection: QuizSelection) :
        PushedScreen

    data class QuizResultScreen(
        val result: QuizResult,
        val selection: QuizSelection
    ) : PushedScreen

    data object AiSettings : PushedScreen
    data object BatchImport : PushedScreen

    /** 「清理重复词条」 — scans on entry, then merges automatically or by hand. */
    data class CleanDuplicates(val scope: String?) : PushedScreen
}

@Composable
fun MainScreen(
    /** Lets the host know 干净浏览 took over, so the main background steps aside. */
    onCleanBrowseVisibilityChange: (Boolean) -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val words by viewModel.words.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val filteredWords by viewModel.filteredWords.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val libraryView by viewModel.view.collectAsStateWithLifecycle()
    val todayWordIds by viewModel.todayWordIds.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val dueWords by viewModel.dueToday.collectAsStateWithLifecycle()
    val reviewedToday by viewModel.reviewedTodayCount.collectAsStateWithLifecycle()
    val reviewSession by viewModel.review.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val speaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val cleanState by viewModel.cleanState.collectAsStateWithLifecycle()
    val aiProfiles by viewModel.aiProfiles.collectAsStateWithLifecycle()
    val activeAiProfileId by viewModel.activeAiProfileId.collectAsStateWithLifecycle()
    val aiLookup by viewModel.aiLookup.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val activeChatId by viewModel.activeChatId.collectAsStateWithLifecycle()
    val chatBusy by viewModel.aiChatBusy.collectAsStateWithLifecycle()
    val chatPendingId by viewModel.aiPendingMessageId.collectAsStateWithLifecycle()
    val modelsBusy by viewModel.aiModelsBusy.collectAsStateWithLifecycle()

    // LocalClipboardManager is deprecated in favour of the suspend LocalClipboard API; the
    // synchronous version is still the simplest fit for a one-shot "copy" action.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var tab by rememberSaveable { mutableStateOf(HomeTab.LIBRARY.name) }
    var pushed by remember { mutableStateOf<PushedScreen?>(null) }
    var editorDraft by remember { mutableStateOf<com.lexi.wordbook.data.WordDraft?>(null) }
    var quizSelection by remember { mutableStateOf(QuizSelection()) }
    var reviewGroupPicker by remember { mutableStateOf(false) }
    var batchState by remember { mutableStateOf<BatchImportState>(BatchImportState.Input) }
    val scope = rememberCoroutineScope()

    /** Runs the AI batch import and moves the screen into preview/error state. */
    fun launchBatchImport(raw: String) {
        batchState = BatchImportState.Working(0, 1)
        scope.launch {
            val outcome = viewModel.runBatchImport(raw) { done, total ->
                batchState = BatchImportState.Working(done, total)
            }
            batchState = if (outcome.error != null) {
                BatchImportState.Error(outcome.error)
            } else {
                BatchImportState.Preview(
                    words = outcome.words,
                    note = outcome.note,
                    usedJson = outcome.usedJson,
                    mergedDuplicates = outcome.mergedDuplicates
                )
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current.text)
        viewModel.consumeMessage(current.id)
    }

    val groupCounts = remember(words, groups) {
        buildMap<String?, Int> {
            put(null, words.count { it.groupId == null })
            groups.forEach { group -> put(group.id, words.count { it.groupId == group.id }) }
        }
    }

    // Entries per local day, for the calendar picker in the library app bar.
    val dayCounts = remember(words) { DayUtils.countByDay(words) }


    // The bundled 四级 pack always lands in this group.
    val cet4GroupId = remember(groups) {
        groups.firstOrNull { it.name == com.lexi.wordbook.data.WordRepository.CET4_GROUP_NAME }?.id
    }

    // 「干净浏览」 target: null = closed, otherwise the selected chip key — the real group id,
    // or UNGROUPED_KEY for the 未分组 chip whose words carry groupId == null.
    var cleanBrowseTarget by remember { mutableStateOf<String?>(null) }
    var cleanQuery by remember { mutableStateOf("") }

    // Library navigation. A sealed location instead of nullable flags: using null for both "no
    // group filter" and "the group is 未分组" is what made the 未分组 card render the wrong title,
    // because `openGroupId == null` could not be told apart from "nothing open yet".
    var libraryLocation by remember { mutableStateOf<LibraryLocation>(LibraryLocation.Shelf) }
    var manageGroupsRequested by remember { mutableStateOf(false) }

    // Local functions rather than lambdas so call sites can use named arguments.
    fun openGroup(groupId: String?, keepQuery: Boolean) {
        val destination = com.lexi.wordbook.ui.screens.shelfDestination(groupId)
        libraryLocation = when (destination) {
            com.lexi.wordbook.ui.screens.ShelfDestination.All -> LibraryLocation.AllWords
            com.lexi.wordbook.ui.screens.ShelfDestination.Ungrouped -> LibraryLocation.Ungrouped
            is com.lexi.wordbook.ui.screens.ShelfDestination.Group ->
                LibraryLocation.Group(destination.id)
        }
        manageGroupsRequested = false
        viewModel.setQueryAndFilters(
            WordFilter(
                // Switching groups from a chip keeps the search; entering from the shelf starts
                // clean, because the shelf search is about group names, not words.
                query = if (keepQuery) viewModel.filter.value.query else "",
                groupId = com.lexi.wordbook.ui.screens.shelfFilterGroupId(destination)
            )
        )
    }

    fun leaveGroup() {
        libraryLocation = LibraryLocation.Shelf
        manageGroupsRequested = false
        viewModel.setQueryAndFilters(WordFilter())
    }

    fun searchAll() {
        libraryLocation = LibraryLocation.AllWords
        manageGroupsRequested = false
        viewModel.setQueryAndFilters(WordFilter())
    }

    val location = libraryLocation
    val locationTitle: String? = when (location) {
        LibraryLocation.Shelf -> null
        LibraryLocation.AllWords -> "📚 全部词条"
        LibraryLocation.Ungrouped -> "🗂️ 未分组"
        is LibraryLocation.Group -> groups.firstOrNull { it.id == location.id }
            ?.let { "${it.emoji} ${it.name}" }
    }

    var cleanResumeId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cleanBrowseTarget != null) {
        onCleanBrowseVisibilityChange(cleanBrowseTarget != null)
    }

    // ---------------------------------------------------------------- overlays

    val activeReviewWord = reviewSession.currentId?.let { id -> words.firstOrNull { it.id == id } }
    if (reviewSession.queue.isNotEmpty() &&
        (reviewSession.isFinished || activeReviewWord != null)
    ) {
        FlashcardScreen(
            session = reviewSession,
            word = activeReviewWord,
            frontIsChinese = preferences.reviewFrontIsChinese,
            isSpeaking = speaking == activeReviewWord?.text,
            hapticsEnabled = preferences.hapticsEnabled,
            autoSpeak = preferences.autoSpeak,
            onSpeak = { text, slow -> viewModel.speak(text, slow) },
            onReveal = { viewModel.reveal() },
            onGrade = { remembered -> viewModel.submitReview(remembered) },
            onQuit = { viewModel.endReview() }
        )
        return
    }

    when (val current = pushed) {
        PushedScreen.QuizSetup -> {
            val pool = viewModel.quizPool(quizSelection)
            QuizSetupScreen(
                selection = quizSelection,
                groups = groups,
                availableCount = possibleQuestionCount(pool, quizSelection.type),
                todayCount = todayWordIds.size,
                onSelectionChange = { quizSelection = it },
                onStart = {
                    val items = viewModel.buildQuiz(quizSelection)
                    if (items.isEmpty()) {
                        viewModel.postMessage("所选范围内词条不足，换个范围或题型试试")
                    } else {
                        pushed = PushedScreen.QuizRun(
                            items = items,
                            title = viewModel.quizPoolLabel(quizSelection),
                            selection = quizSelection
                        )
                    }
                },
                onClose = { pushed = null }
            )
            return
        }

        is PushedScreen.QuizRun -> {
            QuizRunScreen(
                items = current.items,
                title = current.title,
                isSpeaking = speaking == current.items.firstOrNull()?.answer,
                onSpeak = { viewModel.speak(it) },
                onFinish = { answers ->
                    pushed = PushedScreen.QuizResultScreen(
                        result = viewModel.quizResult(answers, current.selection),
                        selection = current.selection
                    )
                },
                onQuit = { pushed = null }
            )
            return
        }

        is PushedScreen.QuizResultScreen -> {
            QuizResultScreen(
                result = current.result,
                onRetryWrong = {
                    val items = QuizBuilder.retryFrom(
                        pool = words,
                        wrong = current.result.wrong,
                        type = current.selection.type
                    )
                    if (items.isEmpty()) {
                        viewModel.postMessage("错题无法重新出题，请检查词条是否被删除")
                    } else {
                        pushed = PushedScreen.QuizRun(
                            items = items,
                            title = "错题重练",
                            selection = current.selection
                        )
                    }
                },
                onRestart = {
                    val items = viewModel.buildQuiz(current.selection)
                    pushed = if (items.isEmpty()) {
                        PushedScreen.QuizSetup
                    } else {
                        PushedScreen.QuizRun(
                            items = items,
                            title = viewModel.quizPoolLabel(current.selection),
                            selection = current.selection
                        )
                    }
                },
                onClose = { pushed = PushedScreen.QuizSetup }
            )
            return
        }

        PushedScreen.AiSettings -> {
            AiSettingsScreen(
                config = aiConfig,
                profiles = aiProfiles,
                activeProfileId = activeAiProfileId,
                modelsBusy = modelsBusy,
                onConfigChange = { transform -> viewModel.updateAiConfig(transform) },
                onFormatChange = { format -> viewModel.applyAiFormatDefaults(format) },
                onTest = { viewModel.testAiConnection() },
                onFetchModels = { onResult -> viewModel.fetchModels(onResult) },
                onResetPrompts = { viewModel.resetAiPrompts() },
                onAddProfile = { name -> viewModel.addAiProfile(name) },
                onActivateProfile = { id -> viewModel.activateAiProfile(id) },
                onDuplicateProfile = { id -> viewModel.duplicateAiProfile(id) },
                onRenameProfile = { id, name -> viewModel.renameAiProfile(id, name) },
                onDeleteProfile = { id -> viewModel.deleteAiProfile(id) },
                onClose = { pushed = null }
            )
            return
        }

        PushedScreen.BatchImport -> {
            // A file picked inside the screen is copied into the text box by the screen itself;
            // once words come back we hand the drafts straight to the repository.
            BatchImportScreen(
                state = batchState,
                prompt = aiConfig.batchPrompt,
                aiConfigured = aiConfig.isConfigured,
                aiModel = aiConfig.model,
                groups = groups,
                onPromptChange = { value -> viewModel.updateAiConfig { it.copy(batchPrompt = value) } },
                onResetPrompt = { viewModel.resetAiPrompts() },
                onRun = { raw -> launchBatchImport(raw) },
                onImport = { list, skipDuplicates, targetGroupId, forceUngrouped ->
                    viewModel.importBatchWords(
                        words = list,
                        skipDuplicates = skipDuplicates,
                        overrideGroupId = targetGroupId,
                        forceUngrouped = forceUngrouped
                    )
                    batchState = BatchImportState.Input
                    pushed = null
                },
                onReset = { batchState = BatchImportState.Input },
                onOpenAiSettings = { pushed = PushedScreen.AiSettings },
                onClose = { pushed = null }
            )
            return
        }

        is PushedScreen.CleanDuplicates -> {
            CleanDuplicatesScreen(
                state = cleanState,
                groups = groups,
                onScan = { scope -> viewModel.scanDuplicates(scope) },
                onCleanAll = {
                    viewModel.cleanDuplicatesNow()
                    // The list changed underneath us; refresh the derived counts.
                    viewModel.scanDuplicates(current.scope)
                },
                onStartManual = { viewModel.startManualClean() },
                onPickKeeper = { key, id -> viewModel.setDuplicateKeeper(key, id) },
                onResetKeepers = { viewModel.resetDuplicateKeepers() },
                onCleanKeeping = {
                    viewModel.cleanDuplicatesKeeping()
                    viewModel.scanDuplicates(current.scope)
                },
                onClose = {
                    viewModel.closeCleanDuplicates()
                    pushed = null
                }
            )
            return
        }

        is PushedScreen.Editor -> {
            val draft = editorDraft
            if (draft == null) {
                pushed = null
            } else {
                WordEditorScreen(
                    initial = draft,
                    isNew = current.isNew,
                    speaking = speaking,
                    groups = groups,
                    aiConfigured = aiConfig.isConfigured,
                    aiLookup = aiLookup,
                    onSpeak = { viewModel.speak(it) },
                    onAskAi = { word -> viewModel.askAiForWord(word) { result -> editorDraft = result } },
                    onClearAiResult = { viewModel.clearAiLookup() },
                    onOpenAiSettings = { pushed = PushedScreen.AiSettings },
                    onSave = { updated ->
                        viewModel.saveDraft(updated)
                        viewModel.clearAiLookup()
                        pushed = null
                        editorDraft = null
                    },
                    onDelete = if (current.isNew) {
                        null
                    } else {
                        {
                            viewModel.deleteWord(draft.id)
                            pushed = null
                            editorDraft = null
                        }
                    },
                    onClose = {
                        viewModel.clearAiLookup()
                        pushed = null
                        editorDraft = null
                    }
                )
            }
            return
        }

        null -> Unit
    }

    val startQuizSetup: () -> Unit = { pushed = PushedScreen.QuizSetup }

    val openEditor: (Word?) -> Unit = { word ->
        editorDraft = if (word == null) viewModel.newDraft() else viewModel.draftFor(word)
        viewModel.clearAiLookup()
        pushed = PushedScreen.Editor(word == null)
    }

    val tabContent: @Composable () -> Unit = {
        TabContent(
            tab = HomeTab.entries.firstOrNull { it.name == tab } ?: HomeTab.LIBRARY,
            words = words,
            filteredWords = filteredWords,
            filter = filter,
            libraryView = libraryView,
            groups = groups,
            groupCounts = groupCounts,
            todayWordIds = todayWordIds,
            dayCounts = dayCounts,
            cet4GroupId = cet4GroupId,
            onCleanBrowse = { groupId ->
                cleanBrowseTarget = groupId ?: com.lexi.wordbook.ui.screens.UNGROUPED_KEY
            },
            dueWords = dueWords,
            reviewedToday = reviewedToday,
            preferences = preferences,
            speaking = speaking,
            aiConfigured = aiConfig.isConfigured,
            aiConfig = aiConfig,
            chatMessages = chatMessages,
            chatSessions = chatSessions,
            activeChatId = activeChatId,
            chatBusy = chatBusy,
            chatPendingId = chatPendingId,
            snackbarHostState = snackbarHostState,
            viewModel = viewModel,
            onOpenQuizSetup = startQuizSetup,
            onOpenEditor = openEditor,
            onNewWord = { openEditor(null) },
            onOpenAiSettings = { pushed = PushedScreen.AiSettings },
            onOpenBatchImport = {
                batchState = BatchImportState.Input
                pushed = PushedScreen.BatchImport
            },
            onReviewGroup = { reviewGroupPicker = true },
            onCopyText = { text ->
                clipboard.setText(AnnotatedString(text))
                viewModel.postMessage("已复制到剪贴板")
            },
            onOpenCleanDuplicates = { scope ->
                viewModel.scanDuplicates(scope)
                pushed = PushedScreen.CleanDuplicates(scope)
            },
            bookshelfGroup = location,
            bookshelfGroupLabel = locationTitle,
            onEnterGroup = { groupId -> openGroup(groupId, keepQuery = false) },
            onLeaveGroup = { leaveGroup() },
            onManageGroups = { manageGroupsRequested = true },
            onSearchAll = { searchAll() },
            onSelectGroup = { selection -> openGroup(selection, keepQuery = true) },
            onImportCet4 = { viewModel.importCet4Pack() },
            showManagerOnStart = manageGroupsRequested
        )
    }

    if (reviewGroupPicker) {
        GroupPickerDialog(
            groups = groups,
            counts = groupCounts,
            title = "按分组随机抽背",
            onDismiss = { reviewGroupPicker = false },
            onPick = { groupId ->
                reviewGroupPicker = false
                val pool = if (groupId == null) {
                    words.filter { it.groupId == null }
                } else {
                    words.filter { it.groupId == groupId }
                }
                if (pool.isEmpty()) {
                    viewModel.postMessage("该分组还没有词条")
                } else {
                    viewModel.startReview(pool.shuffled())
                }
            }
        )
    }

    // 「干净浏览」 overlay: one group, typography only, its own background and controls.
    if (cleanBrowseTarget != null) {
        val targetId = com.lexi.wordbook.ui.screens.toRealGroupId(cleanBrowseTarget)
        val targetGroup = groups.firstOrNull { it.id == targetId }
        // "" is the 未分组 bucket inside preferences, so the resume position is stored per
        // real group rather than per chip key.
        val resumeKey = targetId ?: com.lexi.wordbook.ui.screens.UNGROUPED_KEY
        // Remember where the reader left off, and offer a jump back to it.
        LaunchedEffect(resumeKey) { cleanResumeId = viewModel.cleanResumeWordId(resumeKey) }
        DisposableEffect(resumeKey) {
            onDispose { cleanResumeId = viewModel.cleanResumeWordId(resumeKey) }
        }
        val visible = viewModel.cleanBrowseWords(
            groupId = targetId,
            query = cleanQuery,
            mode = preferences.cleanSortMode
        )
        // A–Z rail data: which letters exist and where each group starts in the flat list.
        val letters = remember(visible, cleanQuery, preferences.cleanSortMode) {
            com.lexi.wordbook.util.Alphabet.activeKeys(visible)
        }
        val headerIndex = remember(visible, cleanQuery, preferences.cleanSortMode) {
            MainViewModel.cleanHeaderIndex(visible)
        }
        // 二级页面：系统返回键先退出干净浏览，回到生词本。
        BackHandler {
            cleanQuery = ""
            cleanBrowseTarget = null
        }
        CleanBrowseScreen(
            title = targetGroup?.name ?: "未分组",
            emoji = targetGroup?.emoji ?: "🗂️",
            words = visible,
            background = if (preferences.backgroundEnabled) {
                preferences.cleanBackground
            } else {
                com.lexi.wordbook.data.BackgroundConfig()
            },
            query = cleanQuery,
            activeLetters = letters,
            headerIndex = headerIndex,
            sortMode = preferences.cleanSortMode,
            textScale = preferences.cleanTextScale,
            isSpeaking = speaking != null,
            speakingText = speaking,
            resumeWordId = cleanResumeId,
            onQueryChange = { cleanQuery = it },

            onTopWordChange = { wordId -> viewModel.rememberCleanPosition(resumeKey, wordId) },
            onSortModeChange = { mode -> viewModel.setCleanSortMode(mode) },
            onTextScaleChange = { scale -> viewModel.setCleanTextScale(scale) },
            onSpeak = { text -> viewModel.speak(text) },
            onOpenBackgroundSettings = {
                cleanBrowseTarget = null
                tab = HomeTab.SETTINGS.name
            },
            onExit = {
                cleanQuery = ""
                cleanBrowseTarget = null
            }
        )
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Five destinations: a bottom bar on phones, a side rail from 600dp up.
        val wide = maxWidth >= 600.dp
        val currentTab = HomeTab.entries.firstOrNull { it.name == tab } ?: HomeTab.LIBRARY

        if (wide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.padding(top = 12.dp))
                    HomeTab.entries.forEach { entry ->
                        NavigationRailItem(
                            selected = currentTab == entry,
                            onClick = { tab = entry.name },
                            icon = {
                                Icon(entry.icon, contentDescription = stringResource(entry.labelRes))
                            },
                            label = {
                                Text(
                                    stringResource(entry.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                VerticalDivider()
                Box(Modifier.weight(1f)) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { padding ->
                        Box(Modifier.padding(padding)) { tabContent() }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        HomeTab.entries.forEach { entry ->
                            NavigationBarItem(
                                selected = currentTab == entry,
                                onClick = { tab = entry.name },
                                icon = {
                                    Icon(entry.icon, contentDescription = stringResource(entry.labelRes))
                                },
                                label = {
                                    Text(
                                        stringResource(entry.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) { tabContent() }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: HomeTab,
    words: List<Word>,
    filteredWords: List<Word>,
    filter: WordFilter,
    libraryView: LibraryView,
    groups: List<com.lexi.wordbook.data.WordGroup>,
    groupCounts: Map<String?, Int>,
    todayWordIds: Set<String>,
    dayCounts: Map<Long, Int>,
    cet4GroupId: String?,
    onCleanBrowse: (String?) -> Unit,
    dueWords: List<Word>,
    reviewedToday: Int,
    preferences: com.lexi.wordbook.data.UserPreferences,
    speaking: String?,
    aiConfigured: Boolean,
    aiConfig: com.lexi.wordbook.ai.AiConfig,
    chatMessages: List<com.lexi.wordbook.ai.StoredMessage>,
    chatSessions: List<ChatSession>,
    activeChatId: String?,
    chatBusy: Boolean,
    chatPendingId: String?,
    snackbarHostState: SnackbarHostState,
    viewModel: MainViewModel,
    onOpenQuizSetup: () -> Unit,
    onOpenEditor: (Word) -> Unit,
    onNewWord: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenBatchImport: () -> Unit,
    onReviewGroup: () -> Unit,
    onCopyText: (String) -> Unit,
    onOpenCleanDuplicates: (String?) -> Unit,
    /** Where the library tab is pointing. */
    bookshelfGroup: LibraryLocation,
    /** App-bar title for the current location; null on the bookshelf. */
    bookshelfGroupLabel: String?,
    onEnterGroup: (String?) -> Unit,
    onLeaveGroup: (() -> Unit)?,
    onManageGroups: () -> Unit,
    onSearchAll: () -> Unit,
    onImportCet4: () -> Unit,
    /** Open 管理分组 as soon as the group page appears. */
    showManagerOnStart: Boolean,
    /** Group chip tapped on a group page: moves to that bucket and retitles the app bar. */
    onSelectGroup: (String?) -> Unit
) {
    when (tab) {
        HomeTab.LIBRARY -> if (bookshelfGroup is LibraryLocation.Shelf) {
            // Level 1: the bookshelf of groups.
            BookshelfScreen(
                groups = groups,
                groupCounts = groupCounts,
                totalCount = words.size,
                duplicateCount = remember(words) { viewModel.duplicateCount() },
                cet4Count = words.count { it.groupId == cet4GroupId },
                onOpenGroup = { groupId: String? -> onEnterGroup(groupId) },
                onOpenAll = { onEnterGroup(com.lexi.wordbook.ui.screens.ENTERED_ALL) },
                onOpenUngrouped = { onEnterGroup(null) },
                onManageGroups = { onManageGroups() },
                onSearch = { onSearchAll() },
                onImportCet4 = onImportCet4,
                onCleanDuplicates = { onOpenCleanDuplicates(com.lexi.wordbook.data.WordRepository.NO_GROUP) },
                onAddWord = onNewWord,
                onBatchImport = onOpenBatchImport
            )
        } else LibraryScreen(
            words = filteredWords,
            totalCount = words.size,
            filter = filter,
            groupTitle = bookshelfGroupLabel,
            onBack = onLeaveGroup,
            showManagerOnStart = showManagerOnStart,
            // Chip taps move within level 2, so keep the current search text.
            onSelectGroup = onSelectGroup,
            groups = groups,
            groupCounts = groupCounts,
            dayCounts = dayCounts,
            dense = preferences.listDensityCompact,
            speaking = speaking,
            onViewChange = { view -> viewModel.setLibraryView(view) },
            onFilterChange = { viewModel.setQueryAndFilters(it) },
            onDeleteMany = { ids -> viewModel.deleteWords(ids) },
            onStarMany = { ids, starred -> viewModel.setStarred(ids, starred) },
            onMoveMany = { ids, groupId, copyToId ->
                viewModel.moveAndCopy(ids, groupId, copyToId)
            },
            onCopyMany = { ids, groupId -> viewModel.copyWords(ids, groupId) },
            onCleanBrowse = { groupId -> onCleanBrowse(groupId) },
            onOpenCleanDuplicates = onOpenCleanDuplicates,
            duplicateCount = remember(words) { viewModel.duplicateCount() },
            onCreateGroup = { name, emoji, color -> viewModel.createGroup(name, emoji, color) },
            onRenameGroup = { group, name, emoji, color ->
                viewModel.renameGroup(group, name, emoji, color)
            },
            onDeleteGroup = { group -> viewModel.deleteGroup(group) },
            onCopyGroup = { group, target -> viewModel.copyGroupInto(group, target) },
            onSpeak = { viewModel.speak(it) },
            onOpenWord = onOpenEditor,
            onToggleStar = { viewModel.toggleStar(it) },
            onCycleStatus = { word -> viewModel.setStatus(word.id, nextStatus(word.status)) },
            onAddWord = onNewWord,
            onBatchImport = onOpenBatchImport,
            onImportSamples = { viewModel.importSampleWords() }
        )

        HomeTab.REVIEW -> ReviewHomeScreen(
            totalWords = words.size,
            dueWords = dueWords,
            starredWords = words.filter { it.starred },
            todayWords = words.filter { it.id in todayWordIds },
            groups = groups,
            reviewedToday = reviewedToday,
            dailyGoal = preferences.dailyGoal,
            onStartDue = { viewModel.startReview(dueWords.ifEmpty { words.shuffled().take(10) }) },
            onStartStarred = { viewModel.startReview(words.filter { it.starred }) },
            onStartShuffleAll = { viewModel.startReview(words.shuffled()) },
            onStartToday = { viewModel.startReview(words.filter { it.id in todayWordIds }) },
            onStartGroup = onReviewGroup,
            onStartQuiz = onOpenQuizSetup
        )

        HomeTab.CHAT -> ChatScreen(
            messages = chatMessages,
            sessions = chatSessions,
            activeSessionId = activeChatId,
            busy = chatBusy,
            pendingId = chatPendingId,
            aiConfigured = aiConfigured,
            onSend = { text -> viewModel.sendChat(text) },
            onNewChat = { viewModel.newChatSession() },
            onOpenSession = { id -> viewModel.openChatSession(id) },
            onRenameSession = { id, title -> viewModel.renameChatSession(id, title) },
            onDeleteSession = { id -> viewModel.deleteChatSession(id) },
            onClearActive = { viewModel.clearActiveChat() },
            onOpenAiSettings = onOpenAiSettings,
            onCopy = onCopyText
        )
        HomeTab.SETTINGS -> SettingsScreen(
            preferences = preferences,
            totalWords = words.size,
            aiConfig = aiConfig,
            chatMessageCount = chatMessages.size,
            cet4Count = words.count { it.groupId == cet4GroupId },
            onImportCet4 = { viewModel.importCet4Pack() },
            onPickBackground = { uri, clean -> viewModel.saveBackgroundImage(uri, clean) },
            onClearBackground = { clean -> viewModel.clearBackground(clean) },
            onSetBackgroundColor = { color, clean -> viewModel.setBackgroundColor(color, clean) },
            onSetBackgroundOpacity = { value, clean -> viewModel.setBackgroundOpacity(value, clean) },
            onSaveBackground = { clean -> viewModel.saveBackground(clean) },
            onSetBackgroundEnabled = { enabled -> viewModel.setBackgroundEnabled(enabled) },
            onCopyBackground = { cleanToMain -> viewModel.copyBackground(cleanToMain) },
            onOpenAiSettings = onOpenAiSettings,
            onExportChat = { viewModel.exportChat() },
            onClearChat = { viewModel.clearChat() },
            onPreferencesChange = { transform -> viewModel.updatePreferences(transform) },
            onExportJson = { viewModel.exportJson() },
            onExportCsv = { viewModel.exportCsv() },
            onImportPayload = { payload, csv -> viewModel.importPayload(payload, csv) },
            // Recomputed from the live word list, so the row refreshes after a cleanup.
            duplicateCount = remember(words) { viewModel.duplicateCount() },
            onDuplicateCountFor = { groupId -> viewModel.duplicateCount(groupId) },
            onOpenCleanDuplicates = onOpenCleanDuplicates,
            groups = groups,
            onClearAll = { viewModel.clearAllWords() },
            onCopyText = onCopyText,
            onImportSamples = { viewModel.importSampleWords() }
        )
    }
}
