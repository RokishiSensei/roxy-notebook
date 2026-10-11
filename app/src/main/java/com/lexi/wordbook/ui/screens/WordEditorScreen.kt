package com.lexi.wordbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.data.LearningStatus
import com.lexi.wordbook.data.PartOfSpeech
import com.lexi.wordbook.data.Sense
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordDraft
import com.lexi.wordbook.data.WordGroup
import com.lexi.wordbook.ui.ai.AiAssistant
import com.lexi.wordbook.ui.ai.AiLookupState
import com.lexi.wordbook.ui.components.LoadingIndicator
import com.lexi.wordbook.ui.components.SectionHeader
import com.lexi.wordbook.ui.components.labelZh

/**
 * Full-screen editor for adding or editing a word. Multiple senses (词性 + 释义) are
 * supported, each with its own example sentence. 「AI 查询」 asks the configured endpoint
 * to fill the form, including phonetics and every common sense of a polysemous word.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordEditorScreen(
    initial: WordDraft,
    isNew: Boolean,
    speaking: String?,
    groups: List<WordGroup>,
    aiConfigured: Boolean,
    aiLookup: AiLookupState,
    onSpeak: (String) -> Unit,
    onAskAi: (String) -> Unit,
    onClearAiResult: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onSave: (WordDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var showRawResponse by remember { mutableStateOf(false) }
    val canSave = draft.isValid

    // Merge an AI answer into the form as soon as it arrives.
    LaunchedEffect(aiLookup) {
        if (aiLookup is AiLookupState.Success) {
            val structured = aiLookup.structured
            if (structured != null) {
                draft = AiAssistant.merge(structured, draft)
            }
            showRawResponse = structured == null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.settings_close)
                        )
                    }
                },
                title = {
                    Text(
                        if (isNew) {
                            stringResource(R.string.editor_new_title)
                        } else {
                            stringResource(R.string.editor_edit_title)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = { if (canSave) onSave(draft) },
                        enabled = canSave
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.action_save)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.text,
                        onValueChange = { draft = draft.copy(text = it) },
                        label = { Text(stringResource(R.string.editor_word_label)) },
                        placeholder = { Text("serendipity") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { if (draft.text.isNotBlank()) onSpeak(draft.text) },
                        enabled = draft.text.isNotBlank()
                    ) {
                        Icon(
                            if (speaking == draft.text) {
                                Icons.Rounded.GraphicEq
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = stringResource(R.string.speak)
                        )
                    }
                }
            }

            // ---- AI lookup -------------------------------------------------
            item {
                AiLookupCard(
                    state = aiLookup,
                    word = draft.text,
                    aiConfigured = aiConfigured,
                    onAsk = { onAskAi(draft.text) },
                    onOpenSettings = onOpenAiSettings,
                    onDismiss = {
                        onClearAiResult()
                        showRawResponse = false
                    },
                    onShowRaw = { showRawResponse = !showRawResponse }
                )
            }

            if (showRawResponse && aiLookup is AiLookupState.Success) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .height(220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                        ) {
                            Text(
                                "模型原始回复",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                aiLookup.raw,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = draft.phonetic,
                    onValueChange = { draft = draft.copy(phonetic = it) },
                    label = { Text(stringResource(R.string.editor_phonetic_label)) },
                    placeholder = { Text("/ˌserənˈdɪpəti/") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SectionHeader(stringResource(R.string.editor_senses_header))
            }

            itemsIndexed(draft.senses) { index, sense ->
                SenseCard(
                    index = index,
                    sense = sense,
                    canRemove = draft.senses.size > 1,
                    onChange = { updated ->
                        draft = draft.copy(
                            senses = draft.senses.toMutableList().also { it[index] = updated }
                        )
                    },
                    onRemove = {
                        draft = draft.copy(
                            senses = draft.senses.toMutableList().also { it.removeAt(index) }
                        )
                    }
                )
            }

            item {
                TextButton(
                    onClick = { draft = draft.copy(senses = draft.senses + Sense()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.editor_add_sense))
                }
            }

            item { SectionHeader(stringResource(R.string.editor_status_header)) }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LearningStatus.entries.forEach { status ->
                        FilterChip(
                            selected = draft.status == status,
                            onClick = { draft = draft.copy(status = status) },
                            label = { Text(status.labelZh()) }
                        )
                    }
                }
            }

            item { SectionHeader("分组") }

            item {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = draft.groupId == null,
                        onClick = { draft = draft.copy(groupId = null) },
                        label = { Text("未分组") }
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = draft.groupId == group.id,
                            onClick = { draft = draft.copy(groupId = group.id) },
                            label = { Text("${group.emoji} ${group.name}") }
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.editor_meta_header)) }

            item {
                OutlinedTextField(
                    value = draft.source,
                    onValueChange = { draft = draft.copy(source = it) },
                    label = { Text(stringResource(R.string.editor_source)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.tagsText,
                    onValueChange = { draft = draft.copy(tagsText = it) },
                    label = { Text(stringResource(R.string.editor_tags)) },
                    placeholder = { Text("雅思, 写作替换") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    label = { Text(stringResource(R.string.editor_note)) },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.editor_tip_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.editor_tip_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The 「AI 查询」 block at the top of the editor. */
@Composable
private fun AiLookupCard(
    state: AiLookupState,
    word: String,
    aiConfigured: Boolean,
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onShowRaw: () -> Unit
) {
    val loading = state is AiLookupState.Loading
    Surface(
        color = when (state) {
            is AiLookupState.Failure -> MaterialTheme.colorScheme.errorContainer
            is AiLookupState.Success -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when (state) {
            is AiLookupState.Failure -> MaterialTheme.colorScheme.onErrorContainer
            is AiLookupState.Success -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "AI 查询",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !aiConfigured -> "未配置接口，点右侧按钮去设置"
                            loading -> "正在查询「${state.query}」…"
                            state is AiLookupState.Success -> "已填入释义，可以再手动修改"
                            state is AiLookupState.Failure -> state.message
                            else -> "让 AI 补全音标、词性和全部义项"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    IconButton(
                        onClick = { if (aiConfigured) onAsk() else onOpenSettings() },
                        enabled = word.isNotBlank()
                    ) {
                        Icon(
                            if (aiConfigured) Icons.Rounded.AutoAwesome else Icons.Rounded.Refresh,
                            contentDescription = "AI 查询"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state is AiLookupState.Success || state is AiLookupState.Failure) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state is AiLookupState.Success) {
                            TextButton(onClick = onShowRaw) { Text("查看原始回复") }
                        }
                        if (aiConfigured) {
                            TextButton(onClick = onAsk, enabled = word.isNotBlank()) { Text("重新查询") }
                        }
                        TextButton(onClick = onDismiss) { Text("收起") }
                    }
                }
            }

            if (!aiConfigured) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("去配置 AI 接口")
                }
            }
        }
    }
}

@Composable
private fun SenseCard(
    index: Int,
    sense: Sense,
    canRemove: Boolean,
    onChange: (Sense) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.editor_sense_number, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.editor_remove_sense),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PartOfSpeech.entries.forEach { pos ->
                    FilterChip(
                        selected = sense.partOfSpeech == pos,
                        onClick = { onChange(sense.copy(partOfSpeech = pos)) },
                        label = {
                            Text(
                                if (pos == PartOfSpeech.OTHER) {
                                    pos.labelZh
                                } else {
                                    "${pos.label} ${pos.labelZh}"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = sense.meaningZh,
                onValueChange = { onChange(sense.copy(meaningZh = it)) },
                label = { Text(stringResource(R.string.editor_meaning_zh)) },
                placeholder = { Text("意外发现珍奇事物的运气") },
                minLines = 1,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sense.meaningEn,
                onValueChange = { onChange(sense.copy(meaningEn = it)) },
                label = { Text(stringResource(R.string.editor_meaning_en)) },
                minLines = 1,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sense.example,
                onValueChange = { onChange(sense.copy(example = it)) },
                label = { Text(stringResource(R.string.editor_example)) },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Read-only detail card body reused by the review flow and the search results. */
@Composable
fun WordDetailContent(
    word: Word,
    onSpeak: (String) -> Unit,
    showHeadword: Boolean = true,
    groups: List<WordGroup> = emptyList()
) {
    Column(Modifier.fillMaxWidth()) {
        if (showHeadword) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    word.text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                if (word.phonetic.isNotBlank()) {
                    Text(
                        word.phonetic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onSpeak(word.text) }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.speak)
                    )
                }
            }
        }
        word.senses.forEach { sense ->
            Spacer(Modifier.height(10.dp))
            Row {
                com.lexi.wordbook.ui.components.PosBadge(sense.partOfSpeech)
                Spacer(Modifier.width(8.dp))
                Column {
                    if (sense.meaningZh.isNotBlank()) {
                        Text(sense.meaningZh, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (sense.meaningEn.isNotBlank()) {
                        Text(
                            sense.meaningEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (sense.example.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            sense.example,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
        word.groupId?.let { id ->
            groups.firstOrNull { it.id == id }?.let { group ->
                Spacer(Modifier.height(10.dp))
                GroupTag(name = "${group.emoji} ${group.name}", colorIndex = group.colorIndex)
            }
        }
        if (word.note.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    stringResource(R.string.card_note, word.note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
