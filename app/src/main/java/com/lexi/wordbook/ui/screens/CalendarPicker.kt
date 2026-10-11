package com.lexi.wordbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.util.Dates
import com.lexi.wordbook.util.DayUtils

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * Month calendar used to browse entries by the day they were added.
 *
 * Days that contain entries are highlighted and show a count; tapping one filters the list.
 * [selectedDay] is the currently active filter (null = no day filter).
 */
@Composable
fun CalendarPickerDialog(
    monthStart: Long,
    counts: Map<Long, Int>,
    selectedDay: Long?,
    onMonthChange: (Long) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit
) {
    val today = remember { Dates.startOfDay(System.currentTimeMillis()) }
    val cells = remember(monthStart) { DayUtils.monthCells(monthStart) }
    val monthTotal = remember(monthStart, counts) {
        cells.filterNotNull().sumOf { counts[it] ?: 0 }
    }
    // Sorted list of days that actually have entries, newest first.
    val recentDays = remember(counts) {
        counts.entries.sortedByDescending { it.key }.map { it.key }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("按日期查看", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (monthTotal == 0) "本月没有添加记录" else "本月添加 $monthTotal 个词条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column {
                // ---- month switcher -------------------------------------------
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onMonthChange(DayUtils.shiftMonth(monthStart, -1)) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "上个月"
                        )
                    }
                    Text(
                        DayUtils.monthTitle(DayUtils.yearOf(monthStart), DayUtils.monthOf(monthStart)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onMonthChange(DayUtils.shiftMonth(monthStart, 1)) },
                        enabled = DayUtils.monthStartOf(monthStart) < DayUtils.monthStartOf(System.currentTimeMillis())
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "下个月",
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(180f)
                        )
                    }
                }

                // ---- weekday header -------------------------------------------
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAY_LABELS.forEach { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ---- day grid --------------------------------------------------
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                DayCell(
                                    dayStart = day,
                                    count = counts[day] ?: 0,
                                    isToday = day == today,
                                    isSelected = day == selectedDay,
                                    isFuture = day > today,
                                    onClick = { onPick(day) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // Pad a short last row so the columns line up.
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ---- quick jump to recent days --------------------------------
                if (recentDays.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "最近有记录的日期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.height(132.dp)) {
                        items(recentDays.take(30)) { day ->
                            val count = counts[day] ?: 0
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(day) }
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    DayUtils.longDate(day),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    DayUtils.dayCaption(day),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.primary,
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
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) { Text("显示全部") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        }
    )
}

@Composable
private fun DayCell(
    dayStart: Long,
    count: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayOfMonth = remember(dayStart) {
        java.text.SimpleDateFormat("d", java.util.Locale.getDefault())
            .format(java.util.Date(dayStart))
    }
    val hasEntries = count > 0
    val container = when {
        isSelected -> MaterialTheme.colorScheme.primary
        hasEntries -> MaterialTheme.colorScheme.primaryContainer
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val content = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        hasEntries -> MaterialTheme.colorScheme.onPrimaryContainer
        isFuture -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayOfMonth,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (hasEntries || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = content
            )
            if (hasEntries) {
                Text(
                    if (isToday && !isSelected) "今天" else "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                    maxLines = 1
                )
            } else if (isToday) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** Banner shown above the list while a day filter is active. */
@Composable
fun DayFilterBanner(
    dayStart: Long,
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    DayUtils.longDate(dayStart),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "当天添加 $count 个词条 · 按 A–Z 排列",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "取消日期筛选",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
