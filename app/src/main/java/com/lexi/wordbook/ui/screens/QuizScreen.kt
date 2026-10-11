package com.lexi.wordbook.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.data.WordGroup
import com.lexi.wordbook.ui.QuizAnswer
import com.lexi.wordbook.ui.QuizPool
import com.lexi.wordbook.ui.QuizResult
import com.lexi.wordbook.ui.QuizSelection
import com.lexi.wordbook.ui.QuizType
import com.lexi.wordbook.ui.components.EmptyState
import com.lexi.wordbook.ui.components.PosBadge
import com.lexi.wordbook.ui.components.ProgressRing
import com.lexi.wordbook.ui.components.SectionHeader

/**
 * Quiz setup: choose where the words come from, which question style, and how many.
 * Shows a live availability count so the start button is never a dead end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSetupScreen(
    selection: QuizSelection,
    groups: List<WordGroup>,
    availableCount: Int,
    todayCount: Int,
    onSelectionChange: (QuizSelection) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                },
                title = { Text("测验设置", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionHeader("出题范围")
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuizPool.entries.forEach { pool ->
                    FilterChip(
                        selected = selection.pool == pool,
                        onClick = { onSelectionChange(selection.copy(pool = pool)) },
                        label = {
                            Text(
                                when (pool) {
                                    QuizPool.TODAY -> "${pool.labelZh}（$todayCount）"
                                    else -> pool.labelZh
                                }
                            )
                        }
                    )
                }
            }

            if (selection.pool == QuizPool.GROUP) {
                Spacer(Modifier.height(10.dp))
                if (groups.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "还没有分组，先到生词本的「管理分组」里创建一个。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = selection.groupId == group.id,
                                onClick = { onSelectionChange(selection.copy(groupId = group.id)) },
                                label = { Text("${group.emoji} ${group.name}") }
                            )
                        }
                    }
                }
            }

            SectionHeader("题型")
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuizType.entries.forEach { type ->
                    FilterChip(
                        selected = selection.type == type,
                        onClick = { onSelectionChange(selection.copy(type = type)) },
                        label = { Text(type.labelZh) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (selection.type) {
                    QuizType.CHOICE_ZH_TO_EN -> "看中文释义，从四个英文单词里选出正确的一个。"
                    QuizType.CHOICE_EN_TO_ZH -> "看英文单词，从四个中文释义里选出正确的一个。"
                    QuizType.SPELLING -> "看中文释义，手写拼出英文单词（会给出首尾字母提示）。"
                    QuizType.MIXED -> "选择题与拼写题各占一半，随机顺序。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader("题目数量：${selection.questionCount} 题")
            Slider(
                value = selection.questionCount.toFloat(),
                onValueChange = { value ->
                    onSelectionChange(selection.copy(questionCount = value.toInt().coerceIn(4, 50)))
                },
                valueRange = 4f..50f,
                steps = 45
            )

            Spacer(Modifier.height(8.dp))
            Surface(
                color = if (availableCount >= 4) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (availableCount >= 4) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (availableCount >= 4) {
                            "可用词条 $availableCount 个"
                        } else {
                            "可用词条只有 $availableCount 个"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (availableCount >= 4) {
                            "将从这些词条中随机抽题，实际题数取「题目数量」与词条数中较小的一个。"
                        } else {
                            "选择题至少需要 4 个带中文释义的词条；不足时只能做拼写题，或者先补充词条。"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                enabled = availableCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("开始测验", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** The question runner. Records every answer for the result screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizRunScreen(
    items: List<QuizItem>,
    title: String,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onFinish: (List<QuizAnswer>) -> Unit,
    onQuit: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateOf(listOf<QuizAnswer>()) }
    val current = items.getOrNull(index)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onQuit) {
                        Icon(Icons.Rounded.Close, contentDescription = "退出测验")
                    }
                },
                title = {
                    Text(
                        "第 ${index + 1} / ${items.size} 题 · $title",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .imePadding()
        ) {
            LinearProgressIndicator(
                progress = { if (items.isEmpty()) 0f else index.toFloat() / items.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(16.dp))

            if (current == null) {
                EmptyState(
                    glyph = "🧩",
                    title = "没有可用的题目",
                    subtitle = "所选范围内没有带中文释义的词条。"
                )
                return@Column
            }

            val advance: (String) -> Unit = { given ->
                answers.value = answers.value + current.toAnswer(given)
                if (index + 1 >= items.size) {
                    onFinish(answers.value)
                } else {
                    index++
                    answers.value = answers.value
                }
            }

            when (current.mode) {
                QuizMode.CHOICE -> ChoiceQuestion(
                    item = current,
                    onNext = advance
                )

                QuizMode.SPELLING -> SpellingQuestion(
                    item = current,
                    isSpeaking = isSpeaking,
                    onSpeak = onSpeak,
                    onNext = advance
                )
            }
        }
    }
}

@Composable
private fun ChoiceQuestion(item: QuizItem, onNext: (String) -> Unit) {
    var picked by remember(item.answer) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (item.promptIsChinese) "下列哪个单词的意思是：" else "这个单词的意思是：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    item.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!item.promptIsChinese && item.phonetic.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.phonetic, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            item.options.forEach { option ->
                val isCorrect = option.equals(item.answer, ignoreCase = true)
                val chosen = picked == option
                val revealed = picked != null
                val container by animateColorAsState(
                    targetValue = when {
                        revealed && isCorrect -> MaterialTheme.colorScheme.secondaryContainer
                        revealed && chosen -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    label = "option"
                )
                Surface(
                    color = container,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = !revealed) { picked = option }
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (revealed && isCorrect) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (picked != null && item.example.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("例句", style = MaterialTheme.typography.labelSmall)
                        Text(item.example, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        if (picked != null) {
            Button(
                onClick = { onNext(picked.orEmpty()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("下一题", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SpellingQuestion(
    item: QuizItem,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onNext: (String) -> Unit
) {
    var input by remember(item.answer) { mutableStateOf("") }
    var checked by remember(item.answer) { mutableStateOf<Boolean?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(item.answer) {
        runCatching { focusRequester.requestFocus() }
    }

    val hint = remember(item.answer) {
        item.answer.mapIndexed { i, c ->
            if (i == 0 || i == item.answer.lastIndex) c.toString() else "_"
        }.joinToString(" ")
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "请根据中文释义拼写英文：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    item.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "提示：$hint（${item.answer.length} 个字母）",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("英文拼写") },
            singleLine = true,
            enabled = checked == null,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            isError = checked == false,
            supportingText = {
                when (checked) {
                    true -> Text("✓ 拼写正确", color = MaterialTheme.colorScheme.secondary)
                    false -> Text("✗ 正确答案：${item.answer}", color = MaterialTheme.colorScheme.error)
                    null -> Text("不区分大小写")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSpeak(item.answer) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "听发音",
                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("听发音", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                keyboard?.hide()
                if (checked == null) {
                    checked = item.evaluate(input)
                } else {
                    onNext(input)
                }
            },
            enabled = checked != null || input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (checked == null) "检查" else "下一题",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Result screen: score, per-question review, and a "retry the mistakes" action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizResultScreen(
    result: QuizResult,
    onRetryWrong: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("测验结果", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressRing(
                            progress = result.accuracy,
                            innerProgress = 0f,
                            modifier = Modifier.size(104.dp)
                        )
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text(
                                "${(result.accuracy * 100).toInt()}%",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "答对 ${result.correctCount} / ${result.total}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "${result.type.labelZh} · ${result.poolLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onRetryWrong,
                        enabled = result.wrong.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重练错题(${result.wrong.size})")
                    }
                    OutlinedButton(
                        onClick = onRestart,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("再来一轮")
                    }
                }
            }

            item { SectionHeader("答题明细") }

            items(result.answers) { answer ->
                Surface(
                    color = if (answer.correct) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (answer.correct) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (answer.correct) "✓" else "✗",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                answer.answer,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (answer.phonetic.isNotBlank()) {
                                Text(
                                    answer.phonetic,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                answer.meaningZh.ifBlank { answer.prompt },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!answer.correct && answer.given.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "你答的是：${answer.given}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
