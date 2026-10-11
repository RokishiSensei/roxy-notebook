package com.lexi.wordbook.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search

import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexi.wordbook.R
import com.lexi.wordbook.data.BackgroundConfig
import com.lexi.wordbook.data.CleanSortMode
import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.components.BackgroundLayer
import com.lexi.wordbook.util.DayUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「干净浏览」: one group, nothing but the words.
 *
 * Every control lives in a **bottom** toolbar that slides away while reading — search, sort
 * order, text size, background shortcut and exit. The list itself stays typography-only.
 */
@Composable
fun CleanBrowseScreen(
    title: String,
    emoji: String,
    words: List<Word>,
    /** Dedicated 干净浏览 background (independent from the main interface one). */
    background: BackgroundConfig,
    query: String,
    /** Letters that actually have entries, for the right-hand A–Z rail. */
    activeLetters: List<String>,
    /** Flat list index of the first entry for each letter. */
    headerIndex: Map<String, Int>,
    sortMode: CleanSortMode,
    textScale: Float,
    isSpeaking: Boolean,
    speakingText: String?,
    /** Word id to scroll back to (the position recorded when the reader last left). */
    resumeWordId: String?,
    onQueryChange: (String) -> Unit,
    onSortModeChange: (CleanSortMode) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onSpeak: (String) -> Unit,
    /** Called with the word currently at the top, whenever the reader scrolls. */
    onTopWordChange: (String?) -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var toolbarVisible by remember { mutableStateOf(true) }
    var searchOpen by remember { mutableStateOf(query.isNotBlank()) }
    var showSizePanel by remember { mutableStateOf(false) }
    var showSortPanel by remember { mutableStateOf(false) }

    val keyboard = LocalSoftwareKeyboardController.current
    val noRipple = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    // Auto-hide the toolbar while reading; tapping anywhere brings it back.
    LaunchedEffect(toolbarVisible, searchOpen, showSizePanel, showSortPanel) {
        if (toolbarVisible && !searchOpen && !showSizePanel && !showSortPanel) {
            delay(4000)
            toolbarVisible = false
        }
    }

    // A new search term or sort order sends the reader back to the top.
    LaunchedEffect(query, sortMode) {
        runCatching { listState.scrollToItem(0) }
    }

    // Report the top entry so the caller can remember the reading position.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> onTopWordChange(words.getOrNull(index)?.id) }
    }

    val contentColor = MaterialTheme.colorScheme.onSurface

    BackgroundLayer(config = background, modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = noRipple) {
                    if (!toolbarVisible) toolbarVisible = true
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 26.dp,
                    end = 26.dp,
                    top = 44.dp,
                    bottom = if (toolbarVisible) 190.dp else 70.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (textScale > 1.2f) 22.dp else 18.dp)
            ) {
                item(key = "clean-header") {
                    Column(Modifier.padding(bottom = 6.dp)) {
                        Text(
                            "$emoji $title",
                            fontSize = (24 * textScale).sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            buildString {
                                append("${words.size} 个词条 · ${sortMode.labelZh}")

                                if (query.isNotBlank()) append(" · 搜索「$query」")
                            },
                            fontSize = (12 * textScale).sp,
                            color = contentColor.copy(alpha = 0.55f)
                        )
                    }
                }

                if (words.isEmpty()) {
                    item(key = "clean-empty") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔍", fontSize = 34.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (query.isBlank()) {
                                    "这个分组还没有词条"
                                } else {
                                    "没有匹配「$query」的词条"
                                },
                                fontSize = (16 * textScale).sp,
                                color = contentColor.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(items = words, key = { it.id }) { word ->
                        CleanWordEntry(
                            word = word,
                            textScale = textScale,
                            color = contentColor,
                            isSpeaking = isSpeaking && speakingText == word.text,
                            onSpeak = { onSpeak(word.text) }
                        )
                    }
                }

                item(key = "clean-footer") {
                    Text(
                        "— 共 ${words.size} 个词条 —",
                        fontSize = (11 * textScale).sp,
                        color = contentColor.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }

            // ---- right-hand A–Z rail -------------------------------------------
            // Same interaction as the main list: tapping a letter jumps to that group.
            AnimatedVisibility(
                visible = activeLetters.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            ) {
                LetterIndexRail(
                    activeKeys = activeLetters,
                    onLetterSelected = { letter ->
                        headerIndex[letter]?.let { target ->
                            scope.launch { listState.animateScrollToItem(target) }
                        }
                    }
                )
            }
            // ---- bottom toolbar -------------------------------------------------
            AnimatedVisibility(
                visible = toolbarVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        if (searchOpen) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                placeholder = {
                                    Text(
                                        "在这个分组里搜索",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                        }

                        if (showSortPanel) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CleanSortMode.entries.forEach { mode ->
                                    val selected = mode == sortMode
                                    Surface(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        contentColor = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onSortModeChange(mode) }
                                    ) {
                                        Text(
                                            mode.labelZh,
                                            style = MaterialTheme.typography.labelMedium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }

                        if (showSizePanel) {
                            Text(
                                "字号 ${(textScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = textScale,
                                onValueChange = onTextScaleChange,
                                valueRange = 0.8f..1.8f
                            )
                        }

                        // Scrollable so seven controls still fit on a narrow phone.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CleanTool(
                                icon = Icons.Rounded.Search,
                                label = if (searchOpen) "收起" else "搜索",
                                active = searchOpen,
                                onClick = {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) {
                                        onQueryChange("")
                                        keyboard?.hide()
                                    }
                                }
                            )

                            CleanTool(
                                icon = Icons.AutoMirrored.Rounded.Undo,
                                label = "续读",
                                active = resumeWordId != null,
                                enabled = resumeWordId != null,
                                onClick = {
                                    val target = resumeWordId ?: return@CleanTool
                                    val index = words.indexOfFirst { it.id == target }
                                    if (index >= 0) {
                                        scope.launch { listState.animateScrollToItem(index) }
                                    }
                                }
                            )
                            CleanTool(
                                icon = Icons.AutoMirrored.Rounded.Sort,
                                label = sortMode.labelZh,
                                active = showSortPanel,
                                onClick = {
                                    showSortPanel = !showSortPanel
                                    showSizePanel = false
                                }
                            )
                            CleanTool(
                                icon = Icons.Rounded.TextFields,
                                label = "${(textScale * 100).toInt()}%",
                                active = showSizePanel,
                                onClick = {
                                    showSizePanel = !showSizePanel
                                    showSortPanel = false
                                }
                            )
                            CleanTool(
                                icon = Icons.Rounded.Palette,
                                label = "背景",
                                active = false,
                                onClick = onOpenBackgroundSettings
                            )
                            CleanTool(
                                icon = Icons.Rounded.Close,
                                label = "退出",
                                active = false,
                                danger = true,
                                onClick = {
                                    keyboard?.hide()
                                    onExit()
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** One item of the bottom toolbar. */
@Composable
private fun CleanTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger -> MaterialTheme.colorScheme.error
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** A single word rendered as reading material rather than a card. */
@Composable
private fun CleanWordEntry(
    word: Word,
    textScale: Float,
    color: Color,
    isSpeaking: Boolean,
    onSpeak: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSpeak)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                word.text,
                fontSize = (26 * textScale).sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            if (word.phonetic.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    word.phonetic,
                    fontSize = (13 * textScale).sp,
                    color = color.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = stringResource(R.string.speak),
                tint = if (isSpeaking) {
                    MaterialTheme.colorScheme.primary
                } else {
                    color.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(4.dp))
        word.senses.forEach { sense ->
            Row(Modifier.padding(top = 2.dp)) {
                if (sense.partOfSpeech.label != "—") {
                    Text(
                        sense.partOfSpeech.label,
                        fontSize = (14 * textScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.width((38 * textScale).dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    if (sense.meaningZh.isNotBlank()) {
                        Text(
                            sense.meaningZh,
                            fontSize = (17 * textScale).sp,
                            color = color,
                            lineHeight = (24 * textScale).sp
                        )
                    }
                    if (sense.meaningEn.isNotBlank()) {
                        Text(
                            sense.meaningEn,
                            fontSize = (13 * textScale).sp,
                            color = color.copy(alpha = 0.6f)
                        )
                    }
                    if (sense.example.isNotBlank()) {
                        Text(
                            sense.example,
                            fontSize = (13 * textScale).sp,
                            color = color.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        if (word.note.isNotBlank()) {
            Text(
                word.note,
                fontSize = (12 * textScale).sp,
                color = color.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )
        }

        Text(
            DayUtils.addedLabel(word.createdAt),
            fontSize = (11 * textScale).sp,
            color = color.copy(alpha = 0.42f),
            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
        )
    }
}

/** Thin divider reused between entries when the list is ordered by date. */
@Composable
internal fun CleanDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}
