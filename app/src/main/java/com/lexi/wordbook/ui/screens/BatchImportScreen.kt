package com.lexi.wordbook.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.ai.BatchWord
import com.lexi.wordbook.ui.components.LoadingIndicator
import com.lexi.wordbook.ui.components.SectionHeader

/** State of an import run, owned by the host so it survives recomposition. */
sealed interface BatchImportState {
    data object Input : BatchImportState
    data class Working(val done: Int, val total: Int) : BatchImportState
    data class Preview(
        val words: List<BatchWord>,
        val note: String,
        val usedJson: Boolean,
        /** Rows the model returned twice; already folded into one per group. */
        val mergedDuplicates: Int = 0
    ) : BatchImportState
    data class Error(val message: String) : BatchImportState
}

/**
 * 「AI 批量导入」: paste (or open) a word list in any shape, let the configured model turn it
 * into the app's JSON, then review and confirm before anything is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportScreen(
    state: BatchImportState,
    prompt: String,
    aiConfigured: Boolean,
    aiModel: String,
    groups: List<com.lexi.wordbook.data.WordGroup>,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    onRun: (raw: String) -> Unit,
    onImport: (
        words: List<BatchWord>,
        skipDuplicates: Boolean,
        overrideGroupId: String?,
        forceUngrouped: Boolean
    ) -> Unit,
    onReset: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onClose: () -> Unit
) {
    var raw by remember { mutableStateOf("") }
    var showPrompt by remember { mutableStateOf(false) }
    var skipDuplicates by remember { mutableStateOf(true) }
    // Keep the model inflection suggestions (past tense / plural -> base form) unless opted out.
    var includeNew by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Target group for the whole batch. null = keep whatever the model suggested.
    var targetGroupId by remember { mutableStateOf<String?>(null) }
    var forceUngrouped by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    // The synchronous clipboard read is the simplest fit for a one-shot "paste" action.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Pull the clipboard in once, so "copy a word list → open this screen → 开始整理" is the
    // whole flow. It only fills an empty box, never overwrites what the user typed.
    var autoPasted by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (!autoPasted && state is BatchImportState.Input && raw.isBlank()) {
            autoPasted = true
            val text = clipboard.getText()?.text.orEmpty().trim()
            // A copied API key is not a word list; sending it to the model would leak it.
            if (text.isNotBlank() && !looksLikeSecret(text)) raw = text
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text.isNullOrBlank()) return@rememberLauncherForActivityResult
        raw = if (raw.isBlank()) text else raw.trimEnd() + "\n" + text
    }

    if (showTargetPicker) {
        GroupPickerDialog(
            groups = groups,
            counts = emptyMap(),
            title = "导入到哪个分组",
            includeUngrouped = true,
            allowEmptyGroups = true,
            resetLabel = "按 AI 建议",
            onReset = {
                targetGroupId = null
                forceUngrouped = false
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false },
            onPick = { id ->
                targetGroupId = id
                forceUngrouped = id == null
                showTargetPicker = false
            }
        )
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
                            if (state is BatchImportState.Preview) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = stringResource(R.string.settings_close)
                        )
                    }
                },
                title = {
                    Text(
                        when (state) {
                            is BatchImportState.Preview -> "确认导入（${state.words.size} 个词条）"
                            else -> "AI 批量导入"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (state is BatchImportState.Preview && state.words.isNotEmpty()) {
                TextButton(onClick = onOpenAiSettings) {
                    Text("调大一次处理量", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (state !is BatchImportState.Input && state !is BatchImportState.Working) {
                        IconButton(onClick = {
                            selected = emptySet()
                            onReset()
                        }) {
                            Icon(Icons.Rounded.Restore, contentDescription = "重新开始")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            when (state) {
                is BatchImportState.Input -> InputStage(
                    raw = raw,
                    onRawChange = { raw = it },
                    aiConfigured = aiConfigured,
                    aiModel = aiModel,
                    showPrompt = showPrompt,
                    onTogglePrompt = { showPrompt = !showPrompt },
                    prompt = prompt,
                    onPromptChange = onPromptChange,
                    onResetPrompt = onResetPrompt,
                    onPickFile = {
                        filePicker.launch(
                            arrayOf("text/plain", "text/csv", "text/comma-separated-values", "text/markdown", "application/json", "*/*")
                        )
                    },
                    onPaste = {
                        val text = clipboard.getText()?.text.orEmpty().trim()
                        when {
                            text.isBlank() -> Unit
                            looksLikeSecret(text) -> raw = SECRET_MARKER + raw
                            raw.isBlank() -> raw = text
                            else -> raw = raw.trimEnd() + "\n" + text
                        }
                    },
                    onOpenAiSettings = onOpenAiSettings,
                    onRun = {
                        val text = raw.trim()
                        when {
                            text.isEmpty() -> onRun("")
                            looksLikeSecret(text) -> onRun(SECRET_MARKER)
                            else -> onRun(text)
                        }
                    }
                )

                is BatchImportState.Working -> WorkingStage(state)

                is BatchImportState.Error -> ErrorStage(state.message, onReset)

                is BatchImportState.Preview -> PreviewStage(
                    state = state,
                    groups = groups,
                    selected = selected,
                    onToggle = { key ->
                        selected = if (key in selected) selected - key else selected + key
                    },
                    onSelectAll = {
                        selected = if (selected.size == state.words.size) {
                            emptySet()
                        } else {
                            state.words.mapIndexed { index, _ -> index.toString() }.toSet()
                        }
                    },
                    skipDuplicates = skipDuplicates,
                    onSkipDuplicatesChange = { skipDuplicates = it },
                    includeNew = includeNew,
                    onIncludeNewChange = { includeNew = it },
                    targetGroupId = targetGroupId,
                    forceUngrouped = forceUngrouped,
                    onPickTarget = { showTargetPicker = true },
                    onClearTarget = {
                        targetGroupId = null
                        forceUngrouped = false
                    },
                    onImport = {
                        val picked = if (selected.isEmpty()) {
                            state.words
                        } else {
                            state.words.filterIndexed { index, _ -> index.toString() in selected }
                        }
                        val chosen = if (includeNew) picked else picked.filterNot { it.isNew }
                        onImport(chosen, skipDuplicates, targetGroupId, forceUngrouped)
                    }
                )
            }
        }
    }
}



@Composable
private fun InputStage(
    raw: String,
    onRawChange: (String) -> Unit,
    aiConfigured: Boolean,
    aiModel: String,
    showPrompt: Boolean,
    onTogglePrompt: () -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onRun: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("把任意格式的单词表交给 AI 整理", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "不用整理格式：txt / csv / md / json、一行一个词、带序号、带音标、" +
                        "中英混排、甚至一整段课文都可以。打开这个页面时已经自动读取了剪贴板，" +
                        "AI 会补全每个单词的音标、词性、中英文释义、例句、标签和分组，" +
                        "再列出来让你勾选导入。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (aiConfigured) "当前模型：$aiModel" else "还没有配置 AI 接口",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("粘贴剪贴板")
            }
            OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("选择文件")
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = raw,
            onValueChange = onRawChange,
            trailingIcon = {
                if (raw.isNotBlank()) {
                    IconButton(onClick = { onRawChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "清空")
                    }
                }
            },
            label = { Text("原始单词数据") },
            placeholder = {
                Text("diet /ˈdaɪət/ n. 日常饮食\n1. resilient 有韧性的\nabandon|v.|放弃")
            },
            minLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        TextButton(onClick = onTogglePrompt) {
            Text(if (showPrompt) "收起整理提示词" else "查看 / 修改整理提示词")
        }
        if (showPrompt) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("批量导入提示词") },
                minLines = 5,
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    IconButton(onClick = onResetPrompt) {
                        Icon(Icons.Rounded.Restore, contentDescription = "恢复默认")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        if (!aiConfigured) {
            Button(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth()) {
                Text("先去配置 AI 接口")
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRun,
                enabled = raw.isNotBlank() && aiConfigured,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("交给 AI 整理")
            }
        }
    }
}

@Composable
private fun WorkingStage(state: BatchImportState.Working) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text("AI 正在整理…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.total > 1) {
                "第 ${(state.done + 1).coerceAtMost(state.total)} / ${state.total} 段"
            } else {
                "内容较多时会分段发送，请稍候"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.total > 1) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun ErrorStage(message: String, onReset: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("😕", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text("导入失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onReset) { Text("返回重试") }
    }
}

@Composable
private fun PreviewStage(
    state: BatchImportState.Preview,
    groups: List<com.lexi.wordbook.data.WordGroup>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    skipDuplicates: Boolean,
    onSkipDuplicatesChange: (Boolean) -> Unit,
    includeNew: Boolean,
    onIncludeNewChange: (Boolean) -> Unit,
    targetGroupId: String?,
    forceUngrouped: Boolean,
    onPickTarget: () -> Unit,
    onClearTarget: () -> Unit,
    onImport: () -> Unit
) {
    val groupName = groups.firstOrNull { it.id == targetGroupId }?.let { "${it.emoji} ${it.name}" }
        ?: "已选择的分组"
    val duplicates = state.words.count { it.isDuplicate }
    val newCount = state.words.count { it.isNew }
    val effective = if (selected.isEmpty()) state.words.size else selected.size
    val willImport = if (skipDuplicates) {
        state.words.withIndex().count { (index, word) ->
            !word.isDuplicate && (selected.isEmpty() || index.toString() in selected)
        }
    } else {
        effective
    }

    Column(Modifier.fillMaxSize()) {
        Surface(
            color = if (state.usedJson) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
            contentColor = if (state.usedJson) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "AI 整理了 ${state.words.size} 个词条" +
                        if (duplicates > 0) "，其中 $duplicates 个词库里已经有了" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (state.mergedDuplicates > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "有 ${state.mergedDuplicates} 个词被模型重复返回，已自动合并为一条（同一个词在同一个分组里只保留一次）。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Where the batch lands. Defaults to whatever the model suggested.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onPickTarget)
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("导入到分组", style = MaterialTheme.typography.labelSmall)
                    Text(
                        when {
                            forceUngrouped -> "未分组"
                            targetGroupId != null -> groupName
                            else -> "按 AI 建议（每行自带的分组）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (targetGroupId != null || forceUngrouped) {
                    TextButton(onClick = onClearTarget) { Text("恢复") }
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSelectAll) {
                Text(if (selected.size == state.words.size) "取消全选" else "全选")
            }
            Spacer(Modifier.weight(1f))
            Text("跳过重复", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Switch(checked = skipDuplicates, onCheckedChange = onSkipDuplicatesChange)
        }

        // The model also supplies dictionary forms for past tense / plural input; those are
        // highlighted below and can be dropped here in one tap.
        if (newCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI 另外补了 $newCount 个原形词条（过去式 / 复数等）",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text("保存新增", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = includeNew, onCheckedChange = onIncludeNewChange)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Index-based keys: duplicate headwords used to collide and crash the list.
            itemsIndexed(items = state.words, key = { index, _ -> index }) { index, word ->
                val checked = selected.isEmpty() || index.toString() in selected
                Surface(
                    color = when {
                        word.isDuplicate && skipDuplicates ->
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                        checked -> MaterialTheme.colorScheme.surfaceContainerLow
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onToggle(index.toString()) }
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            if (checked) "☑" else "☐",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    word.draft.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (word.draft.phonetic.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        word.draft.phonetic,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (word.isNew) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "✨ 新增",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 5.dp)
                                        )
                                    }
                                }
                                if (word.isDuplicate) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "已存在",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 5.dp)
                                        )
                                    }
                                }
                            }
                            word.draft.senses.take(3).forEach { sense ->
                                Row(Modifier.padding(top = 4.dp)) {
                                    com.lexi.wordbook.ui.components.PosBadge(sense.partOfSpeech, compact = true)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        sense.meaningZh.ifBlank { sense.meaningEn },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (word.draft.senses.size > 3) {
                                Text(
                                    "还有 ${word.draft.senses.size - 3} 个义项…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val extras = buildList {
                                if (word.groupName.isNotBlank()) add("分组：${word.groupName}")
                                if (word.draft.tagsText.isNotBlank()) add("标签：${word.draft.tagsText}")
                            }
                            if (extras.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    extras.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
            Column(Modifier.padding(16.dp)) {
                SectionHeader("将导入 $willImport 个词条")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImport,
                        enabled = willImport > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("导入生词本")
                    }
                }
            }
        }
    }
}
/** Marker passed instead of the text when the input looks like an API key. */
const val SECRET_MARKER = "__SECRET__"

/**
 * True when [text] looks like a key/token rather than a word list.
 *
 * Auto-pasting a copied `sk-…` key happened in practice and is both useless and a privacy leak,
 * because the text is sent to a third-party model.
 *
 * The check must stay conservative: a comma-separated word list
 * (`average，regardless，miles，thinner`) is long, and with full-width punctuation it contains no
 * ASCII whitespace — so it used to be classified as a secret, which replaced the whole list with
 * [SECRET_MARKER] and made the AI answer with a single nonsense entry. A text that splits into
 * two or more word-shaped pieces is a list, and a lone token only counts as a secret when it is
 * long *and* mostly non-letters (real keys are base64-ish; English words are letters).
 */
fun looksLikeSecret(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    // Unambiguous key prefixes win over everything, including a trailing newline.
    if (lower.startsWith("sk-") || lower.startsWith("sk_")) return true
    if (lower.startsWith("bearer ")) return true
    if (lower.startsWith("aiza") || lower.startsWith("gsk_") || lower.startsWith("xai-")) return true

    if (trimmed.any { it == '\n' }) return false

    // Several word-shaped pieces separated by list punctuation: a word list, never a key.
    val pieces = trimmed.split(',', '，', ';', '；', '\t', '|', '、', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (pieces.size >= 2) return false

    // One long unbroken token with little structure is a key. Plain English words are long runs of
    // letters only, so requiring a second character class (a digit or fancy punctuation) is what
    // keeps a single pasted word safe.
    if (trimmed.length < 28) return false
    val classes = listOf(
        trimmed.any { it.isLowerCase() },
        trimmed.any { it.isUpperCase() },
        trimmed.any { it.isDigit() },
        trimmed.any { !it.isLetterOrDigit() }
    ).count { it }
    if (classes < 2) return false
    if (trimmed.none { !it.isLetter() }) return false
    return true
}