package com.lexi.wordbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordGroup
import com.lexi.wordbook.data.WordRepository
import com.lexi.wordbook.ui.LibraryView
import com.lexi.wordbook.ui.WordFilter
import com.lexi.wordbook.ui.components.EmptyState
import com.lexi.wordbook.ui.components.PosBadge
import com.lexi.wordbook.ui.components.SearchField
import com.lexi.wordbook.ui.components.StatusPill
import com.lexi.wordbook.ui.components.labelZh
import com.lexi.wordbook.ui.theme.StatusColors
import com.lexi.wordbook.util.Alphabet
import com.lexi.wordbook.util.DayUtils
import kotlinx.coroutines.launch

/**
 * The vocabulary list. Supports four organisations (A–Z / recent / added today / custom
 * groups), search, status filters, long-press multi-select with batch actions, and a
 * tap-to-edit row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    words: List<Word>,
    totalCount: Int,
    filter: WordFilter,
    groups: List<WordGroup>,
    groupCounts: Map<String?, Int>,
    /** day-start millis -> entries added that day, for the calendar heat marks. */
    dayCounts: Map<Long, Int>,
    dense: Boolean,
    speaking: String?,
    /** App-bar title for a group page; null keeps the whole-library title. */
    groupTitle: String?,
    /** Non-null turns this into level 2 of the library: a back arrow returns to the bookshelf. */
    onBack: (() -> Unit)?,
    /** Opens 管理分组 straight away — used when the bookshelf's manage button is tapped. */
    showManagerOnStart: Boolean,
    /**
     * Group chip tapped. Takes a [ShelfDestination] payload (an id, [ENTERED_ALL] or null for
     * 「未分组」) so the host can update both the filter and the app-bar title.
     */
    onSelectGroup: (String?) -> Unit,
    onViewChange: (LibraryView) -> Unit,
    onFilterChange: (WordFilter) -> Unit,
    onDeleteMany: (Set<String>) -> Unit,
    onStarMany: (Set<String>, Boolean) -> Unit,
    /** (ids, moveTo, copyTo) — copyTo is null when only moving. */
    onMoveMany: (Set<String>, String?, String?) -> Unit,
    onCopyMany: (Set<String>, String?) -> Unit,
    onCleanBrowse: (String?) -> Unit,
    /** Opens 「清理重复词条」; the value is the group to pre-scan (NO_GROUP = whole library). */
    onOpenCleanDuplicates: (String?) -> Unit,
    /** Duplicate entries in the whole library, shown as a badge on the toolbar button. */
    duplicateCount: Int,
    onCreateGroup: (String, String, Int) -> Unit,
    onRenameGroup: (WordGroup, String, String, Int) -> Unit,
    onDeleteGroup: (WordGroup) -> Unit,
    onCopyGroup: (WordGroup, String?) -> Unit,
    onSpeak: (String) -> Unit,
    onOpenWord: (Word) -> Unit,
    onToggleStar: (String) -> Unit,
    onCycleStatus: (Word) -> Unit,
    onAddWord: () -> Unit,
    onBatchImport: () -> Unit,
    onImportSamples: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val groupsForList = remember(words) { Alphabet.group(words) }
    // True when a chip narrows the list (starred / status / letter), beyond just the group.
    val hasActiveFilter = filter.starredOnly || filter.statuses.isNotEmpty() ||
        filter.letter != null
    val keys = remember(groupsForList) { groupsForList.map { it.first } }
    val letterCounts = remember(words) { Alphabet.counts(words) }

    var showLetterGrid by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showGroupManager by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun toggleSelected(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    // The controls now live inside the same LazyColumn as the words, so every letter header is
    // pushed down by however many control rows are rendered above it.
    val controlRows = if (totalCount == 0) {
        0
    } else {
        3 + // search field, group chips, filter row — always present
            (if (filter.groupId != null) 1 else 0) + // 干净浏览 entry
            (if (filter.day != null) 1 else 0) // day filter banner
    }

    val headerIndex = remember(groupsForList, controlRows) {
        com.lexi.wordbook.ui.MainViewModel.groupedHeaderIndex(groupsForList, controlRows)
    }

    // The system back gesture leaves the group page too, exactly like the app-bar arrow.
    if (onBack != null) {
        androidx.activity.compose.BackHandler(enabled = !selectionMode, onBack = onBack)
    }

    // Entered from the bookshelf's 「管理分组」 button: open the manager as soon as we land.
    LaunchedEffect(showManagerOnStart) {
        if (showManagerOnStart) showGroupManager = true
    }

    var calendarMonth by remember { mutableLongStateOf(DayUtils.monthStartOf(System.currentTimeMillis())) }

    // Show the "back to top" button only after the list has actually been scrolled, so it does
    // not clutter the resting state of a short vocabulary.
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > BACK_TO_TOP_THRESHOLD }
    }

    Box(modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        navigationIcon = {
                            IconButton(onClick = { exitSelection() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "退出多选")
                            }
                        },
                        title = { Text("已选择 ${selected.size} 项") },
                        actions = {
                            IconButton(onClick = {
                                selected = if (selected.size == words.size) {
                                    emptySet()
                                } else {
                                    words.map { it.id }.toSet()
                                }
                            }) {
                                Icon(
                                    Icons.Rounded.SelectAll,
                                    contentDescription = if (selected.size == words.size) "取消全选" else "全选"
                                )
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        navigationIcon = {
                            // Level 2 of the library: a way back to the bookshelf. The system back
                            // gesture does the same thing (see the BackHandler below).
                            if (onBack != null) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回书架"
                                    )
                                }
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    groupTitle ?: stringResource(R.string.library_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    // On a group page show the group's own size — the library
                                    // total would read as "未分组 共 72 个词条" when it holds 0.
                                    text = when {
                                        groupTitle != null -> "共 ${words.size} 个词条"
                                        words.size == totalCount ->
                                            stringResource(R.string.library_subtitle_all, totalCount)
                                        else -> stringResource(
                                            R.string.library_subtitle_filtered,
                                            words.size,
                                            totalCount
                                        )
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showCalendar = true }) {
                                Icon(
                                    Icons.Rounded.CalendarMonth,
                                    contentDescription = "按日期查看",
                                    tint = if (filter.day != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    }
                                )
                            }
                            if (groupsForList.isNotEmpty()) {
                                IconButton(onClick = { showLetterGrid = true }) {
                                    Icon(
                                        Icons.Rounded.SortByAlpha,
                                        contentDescription = stringResource(R.string.action_jump_letter)
                                    )
                                }
                            }
                            if (words.isNotEmpty()) {
                                IconButton(onClick = {
                                    selectionMode = true
                                    selected = emptySet()
                                }) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "多选"
                                    )
                                }
                            }
                            // 「清理重复词条」: the badge counts duplicates across the library, so a
                            // stale duplicate is visible without opening 设置.
                            if (totalCount > 0) {
                                IconButton(onClick = {
                                    onOpenCleanDuplicates(WordRepository.NO_GROUP)
                                }) {
                                    if (duplicateCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$duplicateCount") } }) {
                                            Icon(
                                                Icons.Rounded.AutoFixHigh,
                                                contentDescription = "清理重复词条，发现 $duplicateCount 个",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    } else {
                                        Icon(
                                            Icons.Rounded.AutoFixHigh,
                                            contentDescription = "清理重复词条"
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onAddWord) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.action_add)
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (selectionMode) {
                    SelectionActionBar(
                        selectedCount = selected.size,
                        onDelete = { confirmBatchDelete = true },
                        onMove = { showMoveDialog = true },
                        onCopy = { showCopyDialog = true },
                        onStar = { onStarMany(selected, true) },
                        onUnstar = { onStarMany(selected, false) },
                        onSelectAllToggle = { /* handled in the top bar */ }
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Secondary action: hand a foreign word-list file to the AI.
                        SmallFloatingActionButton(
                            onClick = onBatchImport,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI 批量导入")
                        }
                        // Appears once the list has been scrolled, so a long vocabulary can be
                        // sent back to the top (and the letter headers) with one tap.
                        AnimatedVisibility(
                            visible = showBackToTop,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        // Smooth for short hops; instant when far down, where an
                                        // animated scroll would take several seconds.
                                        if (listState.firstVisibleItemIndex > FAR_SCROLL_ITEMS) {
                                            listState.scrollToItem(0)
                                        } else {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(
                                    Icons.Rounded.VerticalAlignTop,
                                    contentDescription = "回到顶部"
                                )
                            }
                        }
                        ExtendedFloatingActionButton(
                            onClick = onAddWord,
                            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.action_add)) }
                        )
                    }
                }
            }
        ) { padding ->
            // One LazyColumn holds the controls *and* the word list.
            //
            // The controls used to sit in a plain Column above the list: in landscape (or with
            // many chips / a day filter active) they consumed the whole height and the list was
            // squeezed to nothing. Scrolling them together guarantees the list is always
            // reachable, whatever the orientation.
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 34.dp,
                    top = 0.dp,
                    bottom = 120.dp
                ),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item(key = "controls-search") {
                    SearchField(
                        value = filter.query,
                        onValueChange = { onFilterChange(filter.copy(query = it)) },
                        placeholder = stringResource(R.string.search_hint),
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }
                if (totalCount > 0) {
                    item(key = "controls-groups") {
                        GroupChipRow(
                            groups = groups,
                            counts = groupCounts,
                            selectedGroupId = filter.groupId,
                            showUngrouped = filter.groupId == UNGROUPED_ID,
                            onSelect = { selection ->
                                // Route through the destination mapper so the app-bar title
                                // follows the chip. Calling onFilterChange alone changed the list
                                // but left the title showing the previous group.
                                onSelectGroup(
                                    when (selection) {
                                        // The 「全部」 chip passes null for "no group".
                                        null -> ENTERED_ALL
                                        UNGROUPED_ID -> null
                                        else -> selection
                                    }
                                )
                                onViewChange(LibraryView.GROUP)
                            },
                            onManage = { showGroupManager = true },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                // The chips scroll horizontally and carry their own 16dp inset.
                                .padding(start = 0.dp)
                        )
                    }
                    // Explicit 「干净浏览」 entry, shown while one specific group is selected.
                    if (filter.groupId != null) {
                        val targetGroup = groups.firstOrNull { it.id == filter.groupId }
                        item(key = "controls-clean") {
                            CleanBrowseBar(
                                emoji = targetGroup?.emoji ?: "🗂️",
                                groupName = targetGroup?.name ?: "未分组",
                                count = words.size,
                                onEnter = { onCleanBrowse(filter.groupId) },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    // Active day filter, set from the calendar in the app bar.
                    filter.day?.let { day ->
                        item(key = "controls-day") {
                            DayFilterBanner(
                                dayStart = day,
                                count = words.size,
                                onClear = { onFilterChange(filter.copy(day = null)) },
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                    item(key = "controls-filters") {
                        FilterRow(
                            filter = filter,
                            onFilterChange = onFilterChange,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                when {
                    totalCount == 0 -> item(key = "empty-library") {
                        EmptyState(
                            glyph = "🔤",
                            title = stringResource(R.string.empty_library_title),
                            subtitle = stringResource(R.string.empty_library_subtitle),
                            action = {
                                TextButton(onClick = onImportSamples) {
                                    Text(stringResource(R.string.empty_library_action))
                                }
                            }
                        )
                    }

                    // An empty group should say so: a blank page looked like the group was
                    // unreachable, which is exactly how 「未分组」 was reported.
                    words.isEmpty() && groupTitle != null && filter.query.isBlank() &&
                        filter.day == null && !hasActiveFilter -> item(key = "empty-group") {
                        EmptyState(
                            glyph = "📭",
                            title = "「$groupTitle」还没有词条",
                            subtitle = "点下面的按钮添加，或者返回书架换一个分组。",
                            action = {
                                TextButton(onClick = onAddWord) { Text("添加生词") }
                            }
                        )
                    }

                    words.isEmpty() -> item(key = "empty-search") {
                        EmptyState(
                            glyph = "🔍",
                            title = if (filter.day != null) {
                                "这一天没有添加词条"
                            } else {
                                stringResource(R.string.empty_search_title)
                            },
                            subtitle = if (filter.day != null) {
                                "换一个日期，或者点上面的按钮取消日期筛选。"
                            } else {
                                stringResource(R.string.empty_search_subtitle)
                            },
                            action = {
                                TextButton(onClick = {
                                    onFilterChange(WordFilter(query = filter.query))
                                    onViewChange(LibraryView.ALPHABET)
                                }) {
                                    Text(stringResource(R.string.empty_search_action))
                                }
                            }
                        )
                    }

                    // Always grouped A–Z, so group and calendar filters stay alphabetically
                    // ordered as well.
                    else -> groupsForList.forEach { (letter, groupWords) ->
                        item(key = "header-$letter") {
                            LetterHeader(letter = letter, count = groupWords.size)
                        }
                        items(items = groupWords, key = { it.id }) { word ->
                            WordRow(
                                word = word,
                                dense = dense,
                                isSpeaking = speaking == word.text,
                                selectionMode = selectionMode,
                                selected = word.id in selected,
                                groupLabel = groups.firstOrNull { it.id == word.groupId },
                                onClick = {
                                    if (selectionMode) toggleSelected(word.id) else onOpenWord(word)
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selected = setOf(word.id)
                                    }
                                },
                                onSpeak = { onSpeak(word.text) },
                                onToggleStar = { onToggleStar(word.id) },
                                onCycleStatus = { onCycleStatus(word) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (groupsForList.isNotEmpty()) {
            LetterIndexRail(
                activeKeys = keys,
                onLetterSelected = { letter ->
                    headerIndex[letter]?.let { target ->
                        scope.launch { listState.animateScrollToItem(target) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            )
        }

        if (showLetterGrid) {
            LetterGridDialog(
                activeKeys = keys,
                counts = letterCounts,
                onDismiss = { showLetterGrid = false },
                onSelect = { letter ->
                    headerIndex[letter]?.let { target ->
                        scope.launch { listState.animateScrollToItem(target) }
                    }
                }
            )
        }
    }

    if (showCalendar) {
        CalendarPickerDialog(
            monthStart = calendarMonth,
            counts = dayCounts,
            selectedDay = filter.day,
            onMonthChange = { calendarMonth = it },
            onDismiss = { showCalendar = false },
            onPick = { day ->
                onFilterChange(filter.copy(day = day))
                showCalendar = false
                scope.launch { listState.animateScrollToItem(0) }
            }
        )
    }

    if (showCopyDialog) {
        GroupPickerDialog(
            groups = groups,
            counts = groupCounts,
            title = "复制到分组",
            includeUngrouped = false,
            onDismiss = { showCopyDialog = false },
            onPick = { groupId ->
                onCopyMany(selected, groupId)
                showCopyDialog = false
                exitSelection()
            }
        )
    }

    if (showGroupManager) {
        GroupManagerDialog(
            groups = groups,
            counts = groupCounts,
            onDismiss = { showGroupManager = false },
            onCreate = { name, emoji, color -> onCreateGroup(name, emoji, color) },
            onRename = { group, name, emoji, color -> onRenameGroup(group, name, emoji, color) },
            onDelete = { group -> onDeleteGroup(group) },
            onCopyToGroup = { from, target -> onCopyGroup(from, target) }
        )
    }

    if (showMoveDialog) {
        MoveToGroupDialog(
            groups = groups,
            selectedCount = selected.size,
            onDismiss = { showMoveDialog = false },
            onPick = { groupId, alsoCopyTo ->
                onMoveMany(selected, groupId, alsoCopyTo)
                showMoveDialog = false
                exitSelection()
            }
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除 ${selected.size} 个词条？") },
            text = { Text("删除后无法恢复，建议先在设置里导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMany(selected)
                    confirmBatchDelete = false
                    exitSelection()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** Row of batch actions shown while multi-select is active. */
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onUnstar: () -> Unit,
    onSelectAllToggle: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionAction(
                icon = Icons.Rounded.Star,
                label = "标重点",
                enabled = selectedCount > 0,
                onClick = onStar
            )
            SelectionAction(
                icon = Icons.Rounded.StarBorder,
                label = "取消重点",
                enabled = selectedCount > 0,
                onClick = onUnstar
            )
            SelectionAction(
                icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                label = "移动分组",
                enabled = selectedCount > 0,
                onClick = onMove
            )
            SelectionAction(
                icon = Icons.Rounded.DeleteOutline,
                label = "删除",
                enabled = selectedCount > 0,
                destructive = true,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun FilterRow(
    filter: WordFilter,
    onFilterChange: (WordFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = filter.starredOnly,
                onClick = { onFilterChange(filter.copy(starredOnly = !filter.starredOnly)) },
                label = { Text(stringResource(R.string.filter_starred)) },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
        items(LearningStatus.entries.size) { index ->
            val status = LearningStatus.entries[index]
            val selected = status in filter.statuses
            FilterChip(
                selected = selected,
                onClick = {
                    val next = if (selected) filter.statuses - status else filter.statuses + status
                    onFilterChange(filter.copy(statuses = next))
                },
                label = { Text(status.labelZh()) }
            )
        }
    }
}

@Composable
private fun LetterHeader(letter: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WordRow(
    word: Word,
    dense: Boolean,
    isSpeaking: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    groupLabel: WordGroup?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSpeak: () -> Unit,
    onToggleStar: () -> Unit,
    onCycleStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            Modifier.padding(
                start = if (selectionMode) 8.dp else 14.dp,
                end = if (selectionMode) 10.dp else 8.dp,
                top = if (dense) 8.dp else 12.dp,
                bottom = if (dense) 8.dp else 12.dp
            )
        ) {
            // ---- line 1: word (+ phonetic) | fixed-width actions ----------------------
            // The word row claims whatever the action buttons leave (the actions have no
            // weight, so they are measured first). Within that space the word is measured with
            // fill = false: it takes only the width it needs, and the phonetic shrinks first.
            // That is what stops "nutritionist" from being clipped to "nutritio…".
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        word.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (word.phonetic.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            word.phonetic,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!selectionMode) {
                        CompactIconButton(
                            icon = if (isSpeaking) {
                                Icons.Rounded.GraphicEq
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            description = stringResource(R.string.speak),
                            tint = if (isSpeaking) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = onSpeak
                        )
                    }
                    CompactIconButton(
                        icon = if (word.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        description = stringResource(R.string.mark_starred),
                        tint = if (word.starred) {
                            StatusColors.Learning
                        } else if (selectionMode) {
                            MaterialTheme.colorScheme.outlineVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        small = selectionMode,
                        onClick = onToggleStar
                    )
                }
            }

            // ---- line 2: part of speech | FULL-WIDTH meaning -------------------------
            // The date used to sit on this line in a fixed 74dp slot, which squeezed long
            // Chinese meanings into a narrow column. It now shares the tag row at the bottom,
            // so the meaning gets the whole width and wraps instead of being cut off.
            Row(Modifier.padding(top = if (dense) 3.dp else 6.dp)) {
                word.primarySense?.let { sense ->
                    PosBadge(sense.partOfSpeech, compact = true)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = word.meaningZhJoined.ifBlank { stringResource(R.string.meaning_missing) },
                    style = if (dense) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (dense) 3 else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // English definitions, when the user filled them in.
            if (!dense) {
                val englishMeaning = word.senses
                    .mapNotNull { it.meaningEn.takeIf { text -> text.isNotBlank() } }
                    .joinToString(" / ")
                if (englishMeaning.isNotBlank()) {
                    Text(
                        englishMeaning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // ---- line 3: status, group, tags + the added date on the right -----------
            Row(
                Modifier.padding(top = if (dense) 4.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item { StatusPill(word.status, onClick = onCycleStatus) }
                    groupLabel?.let { group ->
                        item {
                            GroupTag(
                            name = "${group.emoji} ${group.name}",
                            colorIndex = group.colorIndex
                        )
                    }
                }
                items(word.tags) { tag ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                }
                // Added date, right-aligned on the tag row so the meaning above keeps full width.
                Spacer(Modifier.width(8.dp))
                Text(
                    DayUtils.addedLabel(word.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 78.dp)
                )
            }

            // Note preview: keeps the user's own hint visible while reviewing.
            if (!dense && word.note.isNotBlank()) {
                Text(
                    "📝 ${word.note}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 30–36dp icon button so the title line keeps as much room as possible. */
@Composable
private fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    small: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(if (small) 30.dp else 36.dp)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(if (small) 17.dp else 20.dp)
        )
    }
}
/** Vertical A–Z rail. Tapping a letter jumps to that group. */
@Composable
fun LetterIndexRail(
    activeKeys: List<String>,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = Alphabet.LETTERS + "#"
    var touched by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .widthIn(min = 22.dp)
            .fillMaxHeight(0.72f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            val enabled = letter in activeKeys
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 16.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled) {
                        touched = letter
                        onLetterSelected(letter)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = with(density) { 10.dp.toSp() }
                    ),
                    color = when {
                        touched == letter -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    fontWeight = if (touched == letter) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/** A compact "jump to letter" grid; opens from the toolbar for quick navigation. */
@Composable
fun LetterGridDialog(
    activeKeys: List<String>,
    counts: Map<String, Int>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
        title = { Text(stringResource(R.string.letter_grid_title)) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeKeys.forEach { letter ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            onSelect(letter)
                            onDismiss()
                        }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(letter, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${counts[letter] ?: 0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}

/** Cycling helper shared by list rows: 新词 → 学习中 → 熟悉 → 已掌握 → 新词. */
fun nextStatus(current: LearningStatus): LearningStatus = when (current) {
    LearningStatus.NEW -> LearningStatus.LEARNING
    LearningStatus.LEARNING -> LearningStatus.FAMILIAR
    LearningStatus.FAMILIAR -> LearningStatus.MASTERED
    LearningStatus.MASTERED -> LearningStatus.NEW
}

/** Items scrolled past before the "back to top" button appears. */
private const val BACK_TO_TOP_THRESHOLD = 2

/** Beyond this depth an animated scroll takes too long, so the jump is made instantly. */
private const val FAR_SCROLL_ITEMS = 40