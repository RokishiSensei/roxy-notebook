package com.lexi.wordbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.data.WordGroup

/** Palette used for group chips and avatars. */
val GROUP_COLORS: List<Color> = listOf(
    Color(0xFF4C5BD4),
    Color(0xFF00897B),
    Color(0xFFE0518A),
    Color(0xFFE0A32E),
    Color(0xFF7E57C2),
    Color(0xFF2E9E7E)
)

fun groupColor(index: Int): Color = GROUP_COLORS[index.coerceIn(0, GROUP_COLORS.lastIndex)]

/** Horizontal, scrollable list of group chips plus a "new group" affordance. */
@Composable
fun GroupChipRow(
    groups: List<WordGroup>,
    counts: Map<String?, Int>,
    selectedGroupId: String?,
    showUngrouped: Boolean,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            GroupChip(
                emoji = "📚",
                name = "全部",
                count = counts.values.sum(),
                color = MaterialTheme.colorScheme.primary,
                selected = selectedGroupId == null && !showUngrouped,
                onClick = { onSelect(null) }
            )
        }
        if (counts.containsKey(null)) {
            item {
                GroupChip(
                    emoji = "🗂️",
                    name = "未分组",
                    count = counts[null] ?: 0,
                    color = MaterialTheme.colorScheme.outline,
                    selected = selectedGroupId == UNGROUPED_ID && showUngrouped,
                    onClick = { onSelect(UNGROUPED_ID) }
                )
            }
        }
        items(groups, key = { it.id }) { group ->
            GroupChip(
                emoji = group.emoji,
                name = group.name,
                count = counts[group.id] ?: 0,
                color = groupColor(group.colorIndex),
                selected = selectedGroupId == group.id,
                onClick = { onSelect(group.id) },
            )
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable(onClick = onManage)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("管理分组", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Sentinel id meaning "entries without a group". */
const val UNGROUPED_ID = "__ungrouped__"

/**
 * Maps a UI selection key to the group id actually stored on the words.
 *
 * The chips use [UNGROUPED_ID] as a sentinel, but ungrouped words carry groupId == null,
 * so comparing the sentinel straight against word.groupId never matches anything.
 */
/** Preferences key used for the 未分组 bucket (an empty group id). */
const val UNGROUPED_KEY = ""

fun toRealGroupId(selection: String?): String? =
    if (selection == null || selection == UNGROUPED_ID) null else selection

@Composable
fun GroupChip(
    emoji: String,
    name: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                color = color.copy(alpha = if (selected) 0.9f else 0.15f),
                contentColor = if (selected) Color.White else color,
                shape = CircleShape
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Prominent entry point for 「干净浏览」.
 *
 * Shown under the chips whenever one specific group is selected, so the mode is discoverable
 * instead of hiding inside a chip.
 */
@Composable
fun CleanBrowseBar(
    emoji: String,
    groupName: String,
    count: Int,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEnter)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "干净浏览「$groupName」",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$emoji $count 个词条 · 去掉一切干扰，只留单词和释义",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Create / rename / delete groups. */
@Composable
fun GroupManagerDialog(
    groups: List<WordGroup>,
    counts: Map<String?, Int>,
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, colorIndex: Int) -> Unit,
    onRename: (WordGroup, String, String, Int) -> Unit,
    onDelete: (WordGroup) -> Unit,
    /** Copies every word of one group into another group. */
    onCopyToGroup: (from: WordGroup, toGroupId: String?) -> Unit = { _, _ -> }
) {
    var editing by remember { mutableStateOf<WordGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WordGroup?>(null) }
    var copying by remember { mutableStateOf<WordGroup?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分组管理") },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(
                        "还没有分组。分组可以按学习目标、来源或考试类型把词条分开，例如「雅思核心」「阅读生词」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groups.forEach { group ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(groupColor(group.colorIndex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(group.emoji, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${counts[group.id] ?: 0} 个词条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { copying = group }) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "复制单词到别的分组",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { editing = group }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "重命名",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { pendingDelete = group }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { creating = true }) { Text("新建分组") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        }
    )

    if (creating) {
        GroupEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onConfirm = { name, emoji, color ->
                onCreate(name, emoji, color)
                creating = false
            }
        )
    }

    editing?.let { group ->
        GroupEditorDialog(
            initial = group,
            onDismiss = { editing = null },
            onConfirm = { name, emoji, color ->
                onRename(group, name, emoji, color)
                editing = null
            }
        )
    }

    // Copy the whole group into another one; the same headword may live in several groups.
    copying?.let { group ->
        GroupPickerDialog(
            groups = groups.filterNot { it.id == group.id },
            counts = counts,
            title = "把「${group.name}」的词复制到",
            includeUngrouped = true,
            allowEmptyGroups = true,
            onDismiss = { copying = null },
            onPick = { targetId ->
                onCopyToGroup(group, targetId)
                copying = null
            }
        )
    }

    pendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分组「${group.name}」？") },
            text = { Text("分组里的词条不会被删除，只是回到「未分组」。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(group)
                    pendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** Name + emoji + colour picker for one group. */
@Composable
fun GroupEditorDialog(
    initial: WordGroup?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String, colorIndex: Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: WordGroup.EMOJIS.first()) }
    var colorIndex by remember { mutableIntStateOf(initial?.colorIndex ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建分组" else "编辑分组") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分组名称") },
                    placeholder = { Text("雅思核心 / 阅读生词 / 课堂笔记") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text("图标", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(WordGroup.EMOJIS) { item ->
                        Surface(
                            color = if (item == emoji) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { emoji = item }
                        ) {
                            Text(
                                item,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GROUP_COLORS.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { colorIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == colorIndex) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, emoji, colorIndex) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * Batch action dialog: pick the destination the selected entries move to, optionally leaving a
 * copy behind in a second group (the same word may live in several groups).
 */
@Composable
fun MoveToGroupDialog(
    groups: List<WordGroup>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onPick: (moveTo: String?, copyTo: String?) -> Unit
) {
    var moveTo by remember { mutableStateOf<String?>(null) }
    var copyEnabled by remember { mutableStateOf(false) }
    var copyTo by remember { mutableStateOf<String?>(null) }

    val moveLabel = groups.firstOrNull { it.id == moveTo }?.name
        ?: if (moveTo == null) "未分组" else "未分组"
    val canConfirm = !copyEnabled || copyTo != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动 $selectedCount 个词条") },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(
                        "还没有分组，先到「管理分组」里创建一个。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@Column
                }

                Text("① 移动到", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                GroupPickRow(
                    emoji = "🗂️",
                    name = "未分组",
                    color = MaterialTheme.colorScheme.outline,
                    selected = moveTo == null,
                    onClick = { moveTo = null }
                )
                groups.forEach { group ->
                    GroupPickRow(
                        emoji = group.emoji,
                        name = group.name,
                        color = groupColor(group.colorIndex),
                        selected = moveTo == group.id,
                        onClick = { moveTo = group.id }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("② 同时复制一份到另一个分组", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "开启后原词条会保留一份，适合把同一个词放进两个分组",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = copyEnabled, onCheckedChange = { copyEnabled = it })
                }

                if (copyEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        GroupPickRow(
                            emoji = "🗂️",
                            name = "未分组",
                            color = MaterialTheme.colorScheme.outline,
                            selected = copyTo == null,
                            onClick = { copyTo = null }
                        )
                        groups.forEach { group ->
                            GroupPickRow(
                                emoji = group.emoji,
                                name = group.name,
                                color = groupColor(group.colorIndex),
                                selected = copyTo == group.id,
                                onClick = { copyTo = group.id }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(moveTo, if (copyEnabled) copyTo else null) },
                enabled = canConfirm
            ) {
                Text(
                    when {
                        copyEnabled -> "移动并复制"
                        moveTo == null -> "移出分组"
                        else -> "移动到「$moveLabel」"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** One selectable group row used by [MoveToGroupDialog]. */
@Composable
private fun GroupPickRow(
    emoji: String,
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) color.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
/** Generic group chooser used by the review flow and bulk moves. */
@Composable
fun GroupPickerDialog(
    groups: List<WordGroup>,
    counts: Map<String?, Int>,
    title: String,
    includeUngrouped: Boolean = true,
    /** Empty groups are selectable — needed when importing new words into a fresh group. */
    allowEmptyGroups: Boolean = false,
    /** Optional "keep the current choice" action rendered above the list. */
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (resetLabel != null && onReset != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onReset() }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(resetLabel, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (groups.isEmpty()) {
                    Text(
                        "还没有分组。到生词本的「管理分组」里创建一个，就能按分组随机抽背了。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groups.forEach { group ->
                        val count = counts[group.id] ?: 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = allowEmptyGroups || count > 0) { onPick(group.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(groupColor(group.colorIndex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(group.emoji)
                            Spacer(Modifier.width(6.dp))
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (includeUngrouped && (counts[null] ?: 0) > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(null) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("🗂️")
                            Spacer(Modifier.width(6.dp))
                            Text("未分组", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${counts[null] ?: 0}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Small coloured dot + group name, shown on a word row. */
@Composable
fun GroupTag(name: String, colorIndex: Int, modifier: Modifier = Modifier) {
    Surface(
        color = groupColor(colorIndex).copy(alpha = 0.15f),
        contentColor = groupColor(colorIndex),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}