package com.lexi.wordbook.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.ReviewSession
import com.lexi.wordbook.ui.components.EmptyState
import com.lexi.wordbook.ui.components.PosBadge
import com.lexi.wordbook.ui.components.ProgressRing
import com.lexi.wordbook.ui.components.StatTile

/** Entrance screen for review: today's goal, due counts and session modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewHomeScreen(
    totalWords: Int,
    dueWords: List<Word>,
    starredWords: List<Word>,
    todayWords: List<Word>,
    groups: List<com.lexi.wordbook.data.WordGroup>,
    reviewedToday: Int,
    dailyGoal: Int,
    onStartDue: () -> Unit,
    onStartStarred: () -> Unit,
    onStartShuffleAll: () -> Unit,
    onStartToday: () -> Unit,
    onStartGroup: () -> Unit,
    onStartQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goalProgress = if (dailyGoal <= 0) 0f else reviewedToday.toFloat() / dailyGoal
    val mastered = 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            )
        },
        modifier = modifier
    ) { padding ->
        if (totalWords == 0) {
            Box(Modifier.padding(padding)) {
                EmptyState(
                    glyph = "📚",
                    title = stringResource(R.string.review_empty_title),
                    subtitle = stringResource(R.string.review_empty_subtitle)
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressRing(
                        progress = goalProgress.coerceAtMost(1f),
                        innerProgress = if (totalWords == 0) 0f
                        else (totalWords - dueWords.size).toFloat() / totalWords,
                        modifier = Modifier.size(96.dp),
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                        ringColor = MaterialTheme.colorScheme.primary,
                        innerRingColor = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(
                            stringResource(R.string.review_today_goal),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$reviewedToday / $dailyGoal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (goalProgress >= 1f) stringResource(R.string.review_goal_done)
                            else stringResource(R.string.review_goal_left, (dailyGoal - reviewedToday).coerceAtLeast(0)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = "${dueWords.size}",
                    label = stringResource(R.string.review_due),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary
                )
                StatTile(
                    value = "${starredWords.size}",
                    label = stringResource(R.string.review_starred),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary
                )
                StatTile(
                    value = "$mastered",
                    label = stringResource(R.string.review_mastered),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                stringResource(R.string.review_flashcards),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            ModeCard(
                title = stringResource(R.string.review_mode_due),
                subtitle = if (dueWords.isEmpty()) stringResource(R.string.review_mode_due_empty) else stringResource(R.string.review_mode_due_detail, dueWords.size),
                icon = Icons.Rounded.School,
                enabled = dueWords.isNotEmpty(),
                onClick = onStartDue
            )
            ModeCard(
                title = stringResource(R.string.review_mode_starred),
                subtitle = if (starredWords.isEmpty()) stringResource(R.string.review_mode_starred_empty) else stringResource(R.string.review_mode_starred_detail, starredWords.size),
                icon = Icons.Rounded.Star,
                enabled = starredWords.isNotEmpty(),
                onClick = onStartStarred
            )
            ModeCard(
                title = stringResource(R.string.review_mode_shuffle),
                subtitle = stringResource(R.string.review_mode_shuffle_detail, totalWords),
                icon = Icons.Rounded.Refresh,
                enabled = totalWords > 0,
                onClick = onStartShuffleAll
            )
            ModeCard(
                title = "今天新增的词",
                subtitle = if (todayWords.isEmpty()) {
                    "今天还没有添加新词"
                } else {
                    "${todayWords.size} 个今天添加的词条"
                },
                icon = Icons.Rounded.Today,
                enabled = todayWords.isNotEmpty(),
                onClick = onStartToday
            )
            ModeCard(
                title = "按分组随机抽背",
                subtitle = if (groups.isEmpty()) {
                    "还没有分组，先去生词本创建"
                } else {
                    "${groups.size} 个分组可选，只抽该分组的词"
                },
                icon = Icons.Rounded.FolderSpecial,
                enabled = totalWords > 0,
                onClick = onStartGroup
            )

            Text(
                stringResource(R.string.review_quizzes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            ModeCard(
                title = "测验（可自选范围与题型）",
                subtitle = "中译英 / 英译中 / 拼写 / 混合，按分组或全部出题，做完可重练错题",
                icon = Icons.Rounded.CheckCircle,
                enabled = totalWords >= 1,
                onClick = onStartQuiz
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Flashcard session: flip to reveal, then grade with 「忘了 / 记住了」. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    session: ReviewSession,
    word: Word?,
    frontIsChinese: Boolean,
    isSpeaking: Boolean,
    hapticsEnabled: Boolean,
    autoSpeak: Boolean,
    onSpeak: (String, Boolean) -> Unit,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    // Pronounce each new card automatically when the user enabled auto-speak.
    LaunchedEffect(session.currentId, frontIsChinese, autoSpeak) {
        val current = word ?: return@LaunchedEffect
        if (autoSpeak && !frontIsChinese) onSpeak(current.text, false)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Text(
                        if (session.isFinished) stringResource(R.string.session_done_title) else stringResource(R.string.card_counter, session.index + 1, session.queue.size),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onQuit) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.review_exit)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            LinearProgressIndicator(
                progress = { session.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(20.dp))

            if (session.isFinished) {
                SessionSummary(
                    correct = session.correct,
                    total = session.total,
                    onRestart = onQuit
                )
                return@Column
            }

            if (word == null) {
                EmptyState(
                    glyph = "🎉",
                    title = stringResource(R.string.session_empty_title),
                    subtitle = stringResource(R.string.session_empty_subtitle)
                )
                return@Column
            }

            AnimatedContent(
                targetState = session.revealed,
                label = "card",
                modifier = Modifier.weight(1f)
            ) { revealed ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (revealed) Arrangement.Top else Arrangement.Center
                    ) {
                        if (!revealed) {
                            Text(
                                if (frontIsChinese) word.meaningZhJoined.ifBlank { word.text }
                                else word.text,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            if (frontIsChinese) {
                                word.primarySense?.let {
                                    PosBadge(it.partOfSpeech)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = { onSpeak(word.text, false) }) {
                                        Icon(
                                            if (isSpeaking) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.VolumeUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.speak))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { onSpeak(word.text, true) }) {
                                        Text(stringResource(R.string.speak_slow))
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                stringResource(R.string.card_tap_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                word.text,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (word.phonetic.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    word.phonetic,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = { onSpeak(word.text, false) }) {
                                    Icon(
                                        if (isSpeaking) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.speak))
                                }
                                Spacer(Modifier.width(8.dp))
                                com.lexi.wordbook.ui.components.StatusPill(word.status)
                            }
                            Spacer(Modifier.height(18.dp))
                            word.senses.forEach { sense ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    PosBadge(sense.partOfSpeech)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        if (sense.meaningZh.isNotBlank()) {
                                            Text(
                                                sense.meaningZh,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        if (sense.meaningEn.isNotBlank()) {
                                            Text(
                                                sense.meaningEn,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (sense.example.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                sense.example,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                            }
                            if (word.note.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
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
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!session.revealed) {
                    Button(
                        onClick = {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReveal()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(stringResource(R.string.reveal_meaning), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGrade(false)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(stringResource(R.string.grade_forgot), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = {
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGrade(true)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(stringResource(R.string.grade_remembered), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummary(correct: Int, total: Int, onRestart: () -> Unit) {
    val accuracy = if (total == 0) 0f else correct.toFloat() / total
    val animated by animateFloatAsState(targetValue = accuracy, label = "accuracy")
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ProgressRing(
            progress = animated,
            innerProgress = 0f,
            modifier = Modifier.size(140.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "${(animated * 100).toInt()}%",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.session_remembered, correct, total),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRestart, modifier = Modifier.height(52.dp)) {
            Text(stringResource(R.string.session_back))
        }
    }
}
