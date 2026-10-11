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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordGroup
import com.lexi.wordbook.data.WordRepository

import com.lexi.wordbook.ui.MainViewModel

/**
 * 「清理重复词条」, reached from the library tab.
 *
 * Three steps: pick what to scan, review the duplicates, then either let the app merge them
 * automatically (content unioned, oldest entry wins) or pick by hand which entry survives in each
 * group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanDuplicatesScreen(
    state: MainViewModel.CleanState,
    groups: List<WordGroup>,
    onScan: (String?) -> Unit,
    onCleanAll: () -> Unit,
    onStartManual: () -> Unit,
    onPickKeeper: (String, String) -> Unit,
    onResetKeepers: () -> Unit,
    onCleanKeeping: () -> Unit,
    onClose: () -> Unit
) {
    val groupName: (String?) -> String = { id ->
        if (id == null) "未分组" else groups.firstOrNull { it.id == id }?.name ?: "未知分组"
    }
    val scopeLabel = if (state.scope == WordRepository.NO_GROUP) {
        "全部词条"
    } else {
        groupName(state.scope)
    }
    val totalExtra = state.groups.sumOf { it.extraCount }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (state.stage == MainViewModel.CleanStage.MANUAL) "选择保留哪一条" else "清理重复词条",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when (state.stage) {
                                MainViewModel.CleanStage.MANUAL -> "每个词选一条保留，其余会被删除"
                                else -> "$scopeLabel · 发现 $totalExtra 个多余词条"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (state.stage) {
                MainViewModel.CleanStage.SCAN -> ScanStage(
                    state = state,
                    groups = groups,
                    groupName = groupName,
                    totalExtra = totalExtra,
                    onScan = onScan,
                    onCleanAll = onCleanAll,
                    onStartManual = onStartManual
                )

                MainViewModel.CleanStage.MANUAL -> ManualStage(
                    state = state,
                    groupName = groupName,
                    onPickKeeper = onPickKeeper,
                    onResetKeepers = onResetKeepers,
                    onCleanKeeping = onCleanKeeping
                )

                MainViewModel.CleanStage.DONE -> DoneStage(removed = state.removed, onClose = onClose)
            }
        }
    }
}

@Composable
private fun ScanStage(
    state: MainViewModel.CleanState,
    groups: List<WordGroup>,
    groupName: (String?) -> String,
    totalExtra: Int,
    onScan: (String?) -> Unit,
    onCleanAll: () -> Unit,
    onStartManual: () -> Unit
) {
    val scopeChips = buildList {
        add(WordRepository.NO_GROUP to "全部词条")
        add(null as String? to "未分组")
        groups.forEach { add(it.id to "${it.emoji} ${it.name}") }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "intro") {
            Text(
                "扫描范围",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item(key = "scopes") {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(scopeChips) { (id, label) ->
                    val selected = state.scope == id
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { onScan(id) }
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (state.groups.isEmpty()) {
            item(key = "clean") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("没有重复词条 🎉", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "「${groupName(state.scope)}」里每个词都只有一条，不需要清理。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "actions") {
            Column {
                Text(
                    "怎么清理？",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onCleanAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("直接清理（自动合并）")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onStartManual,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("我自己选保留哪一条")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "直接清理会把重复条目的释义、标签合并，保留创建时间最早的那条。" +
                        "自己选则可以指定每一个词留下哪一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "list-title") {
            Text(
                "重复详情（${state.groups.size} 个词）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(state.groups, key = { it.key }) { group ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            group.headword,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "${group.entries.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        group.groupName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    group.entries.forEachIndexed { index, entry ->
                        Column(Modifier.padding(top = 6.dp)) {
                            Text(
                                summarise(entry),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (index == 0) {
                                Text(
                                    "↑ 自动清理会保留这一条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualStage(
    state: MainViewModel.CleanState,
    groupName: (String?) -> String,
    onPickKeeper: (String, String) -> Unit,
    onResetKeepers: () -> Unit,
    onCleanKeeping: () -> Unit
) {
    val unresolved = state.groups.count { group ->
        state.keepIds[group.key]?.let { id -> group.entries.none { it.id == id } } != false
    }
    val wouldRemove = state.groups.sumOf { it.extraCount }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.groups, key = { it.key }) { group ->
                val keeper = state.keepIds[group.key] ?: group.entries.first().id
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                group.headword,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                group.groupName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "保留 1 条，删除 ${group.extraCount} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        group.entries.forEach { entry ->
                            val checked = entry.id == keeper
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickKeeper(group.key, entry.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                RadioButton(
                                    selected = checked,
                                    onClick = { onPickKeeper(group.key, entry.id) }
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        summarise(entry),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.tags.isNotEmpty()) {
                                        Text(
                                            entry.tags.joinToString(" ") { "#$it" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        "加入时间 ${com.lexi.wordbook.util.DayUtils.addedLabel(entry.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "将删除 $wouldRemove 条，保留 ${state.groups.size} 条",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onResetKeepers) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("重置")
                    }
                }
                if (unresolved > 0) {
                    Text(
                        "还有 $unresolved 个词没选保留哪一条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Button(
                    onClick = onCleanKeeping,
                    enabled = unresolved == 0 && wouldRemove > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("按我的选择清理")
                }
            }
        }
    }
}

@Composable
private fun DoneStage(removed: Int, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (removed == 0) "没有需要清理的词条" else "已清理 $removed 个重复词条",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "保留下来的条目已经合并了重复条目的释义和标签。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("回到生词本")
        }
    }
}

/** One-line description of an entry, used in both the preview and the picker. */
private fun summarise(word: Word): String {
    val meanings = word.senses.mapNotNull { it.meaningZh.ifBlank { it.meaningEn }.ifBlank { null } }
    val meaning = meanings.take(2).joinToString("；").ifBlank { "（没有释义）" }
    val phonetic = word.phonetic.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    val status = when (word.status) {
        com.lexi.wordbook.data.LearningStatus.NEW -> "新词"
        com.lexi.wordbook.data.LearningStatus.LEARNING -> "学习中"
        com.lexi.wordbook.data.LearningStatus.FAMILIAR -> "熟悉"
        com.lexi.wordbook.data.LearningStatus.MASTERED -> "已掌握"
    }
    val star = if (word.starred) " ⭐" else ""
    return "$meaning$phonetic · $status$star"
}
