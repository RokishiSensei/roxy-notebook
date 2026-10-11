package com.lexi.wordbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.ui.components.SearchField
import com.lexi.wordbook.data.WordGroup

/**
 * Sentinel for the 「全部词条」 card.
 *
 * A nullable group id cannot express the three cases the shelf needs — "every group", 「未分组」
 * and one specific group — so the first gets its own value instead of overloading null.
 */
const val ENTERED_ALL = "__all__"

/** Where a bookshelf card leads. */
sealed interface ShelfDestination {
    /** 「全部词条」: no group filter. */
    data object All : ShelfDestination

    /** 「未分组」: words whose groupId is null. */
    data object Ungrouped : ShelfDestination

    /** One real group. */
    data class Group(val id: String) : ShelfDestination
}

/**
 * Maps a card's payload to its destination.
 *
 * `null` means 「未分组」 here, so it must never double as "nothing selected" — conflating those
 * two is what made the 未分组 card open the wrong page.
 */
fun shelfDestination(groupId: String?): ShelfDestination = when (groupId) {
    ENTERED_ALL -> ShelfDestination.All
    com.lexi.wordbook.data.WordRepository.NO_GROUP -> ShelfDestination.All
    null -> ShelfDestination.Ungrouped
    else -> ShelfDestination.Group(groupId)
}

/**
 * Group filter to apply for a destination; null means "no filter, show every group".
 *
 * 「未分组」 maps to [UNGROUPED_ID] because ungrouped words carry `groupId == null`, which is also
 * what "no filter" looks like — hence the sentinel.
 */
fun shelfFilterGroupId(destination: ShelfDestination): String? = when (destination) {
    ShelfDestination.All -> null
    ShelfDestination.Ungrouped -> UNGROUPED_ID
    is ShelfDestination.Group -> destination.id
}

/**
 * Level 1 of the library: a bookshelf of groups.
 *
 * Each group is a card with its emoji, name and entry count; tapping one opens the word list for
 * that group (level 2, [LibraryScreen]). 「全部词条」 and 「未分组」 sit on the same shelf so the
 * two shortcuts are never more than one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    groups: List<WordGroup>,
    modifier: Modifier = Modifier,
    /** Entries per group id; the null key is 「未分组」. */
    groupCounts: Map<String?, Int>,
    totalCount: Int,
    duplicateCount: Int,
    cet4Count: Int,
    onOpenGroup: (String?) -> Unit,
    onOpenAll: () -> Unit,
    onOpenUngrouped: () -> Unit,
    onManageGroups: () -> Unit,
    onSearch: () -> Unit,
    onImportCet4: () -> Unit,
    onCleanDuplicates: () -> Unit,
    onAddWord: () -> Unit,
    onBatchImport: () -> Unit
) {
    val ungroupedCount = groupCounts[null] ?: 0
    var query by remember { mutableStateOf("") }
    val trimmedQuery = query.trim()
    // The shelf search filters the cards themselves: a group is found by name or by emoji.
    val visibleGroups = remember(groups, trimmedQuery) {
        val sorted = groups.sortedBy { it.name.lowercase() }
        if (trimmedQuery.isEmpty()) {
            sorted
        } else {
            sorted.filter {
                it.name.contains(trimmedQuery, ignoreCase = true) ||
                    it.emoji.contains(trimmedQuery)
            }
        }
    }
    val noMatch = trimmedQuery.isNotEmpty() && visibleGroups.isEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                title = {
                    Column {
                        Text(
                            "我的书架",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (trimmedQuery.isEmpty()) {
                                "${groups.size} 个分组 · 共 $totalCount 个词条"
                            } else {
                                "找到 ${visibleGroups.size} 个分组"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (totalCount > 0) {
                        IconButton(onClick = onSearch) {
                            Icon(
                                Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = "搜索全部词条"
                            )
                        }
                    }
                    IconButton(onClick = onManageGroups) {
                        Icon(Icons.AutoMirrored.Rounded.LibraryBooks, contentDescription = "管理分组")
                    }
                    if (totalCount > 0) {
                        IconButton(onClick = onCleanDuplicates) {
                            if (duplicateCount > 0) {
                                BadgedBox(badge = { Badge { Text("$duplicateCount") } }) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = "清理重复词条，发现 $duplicateCount 个",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = "清理重复词条"
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onBatchImport,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI 批量导入")
                }
                ExtendedFloatingActionButton(
                    onClick = onAddWord,
                    icon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                    text = { Text(stringResourceAdd()) }
                )
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Shelf search: filters the group cards below by name (or emoji).
            item(key = "shelf-search", span = { GridItemSpan(maxLineSpan) }) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索分组名",
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item(key = "all") {
                ShelfCard(
                    emoji = "📚",
                    title = "全部词条",
                    count = totalCount,
                    subtitle = "所有分组放在一起看",
                    highlighted = true,
                    onClick = onOpenAll
                )
            }
            item(key = "ungrouped") {
                ShelfCard(
                    emoji = "🗂️",
                    title = "未分组",
                    count = ungroupedCount,
                    subtitle = if (ungroupedCount == 0) "还没有未分组的词" else "还没放进分组的词",
                    onClick = onOpenUngrouped
                )
            }
            items(visibleGroups, key = { it.id }) { group ->
                ShelfCard(
                    emoji = group.emoji,
                    title = group.name,
                    count = groupCounts[group.id] ?: 0,
                    subtitle = if ((groupCounts[group.id] ?: 0) == 0) "空分组" else null,
                    onClick = { onOpenGroup(group.id) }
                )
            }
            if (noMatch) {
                item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("没有匹配「$trimmedQuery」的分组", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "换个关键词，或者点上面的「全部词条」按单词内容搜索。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.TextButton(onClick = { query = "" }) {
                                Text("清除搜索")
                            }
                        }
                    }
                }
            }
            if (totalCount == 0) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyShelfHint(onImportCet4 = onImportCet4, onBatchImport = onBatchImport)
                }
            }
        }
    }
}

/** Group tile: emoji, name, entry count. */
@Composable
private fun ShelfCard(
    emoji: String,
    title: String,
    count: Int,
    subtitle: String?,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyShelfHint(onImportCet4: () -> Unit, onBatchImport: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("书架还是空的", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "点右下角「添加生词」新建词条，或者把现成的单词表交给 AI 整理。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onImportCet4) {
                    Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加四级词汇")
                }
                androidx.compose.material3.OutlinedButton(onClick = onBatchImport) {
                    Text("AI 批量导入")
                }
            }
        }
    }
}

@Composable
private fun stringResourceAdd(): String =
    androidx.compose.ui.res.stringResource(R.string.action_add)
