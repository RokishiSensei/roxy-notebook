package com.lexi.wordbook.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lexi.wordbook.R
import com.lexi.wordbook.data.ThemeMode
import com.lexi.wordbook.data.UserPreferences
import com.lexi.wordbook.data.WordRepository
import com.lexi.wordbook.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings: appearance, learning behaviour, data portability and about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    totalWords: Int,
    aiConfig: com.lexi.wordbook.ai.AiConfig,
    chatMessageCount: Int,
    cet4Count: Int,
    onImportCet4: () -> Unit,
    onPickBackground: (android.net.Uri, Boolean) -> Unit,
    onClearBackground: (Boolean) -> Unit,
    onSetBackgroundColor: (Long, Boolean) -> Unit,
    onSetBackgroundOpacity: (Int, Boolean) -> Unit,
    onSaveBackground: (Boolean) -> Unit,
    onSetBackgroundEnabled: (Boolean) -> Unit,
    onCopyBackground: (Boolean) -> Unit,
    onOpenAiSettings: () -> Unit,
    onExportChat: () -> String,
    onClearChat: () -> Unit,
    onPreferencesChange: ((UserPreferences) -> UserPreferences) -> Unit,
    onExportJson: () -> String,
    onExportCsv: () -> String,
    onImportPayload: (String, Boolean) -> Pair<Int, String?>,
    /** Duplicate entries currently stored, for the 「清理重复词条」 row. */
    duplicateCount: Int,
    /** Scans [groupId] (WordRepository.NO_GROUP = whole library); returns removed count. */
    /** Opens the cleanup page for a range (WordRepository.NO_GROUP = whole library). */
    onOpenCleanDuplicates: (String?) -> Unit,
    /** Duplicate count for one group id (WordRepository.NO_GROUP = whole library). */
    onDuplicateCountFor: (String) -> Int,
    groups: List<com.lexi.wordbook.data.WordGroup>,
    onClearAll: () -> Unit,
    onCopyText: (String) -> Unit,
    onImportSamples: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    var showCleanDialog by remember { mutableStateOf(false) }
    var confirmCet4 by remember { mutableStateOf(false) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickBackground(uri, false)
    }

    val cleanBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickBackground(uri, true)
    }

    // Resolved up front: the import callback and the export helper run outside composition,
    // where stringResource() cannot be called.
    val importFailText = resources.getString(R.string.settings_import_fail)
    val exportOkText = resources.getString(R.string.settings_export_ok)
    val exportFailText = resources.getString(R.string.settings_export_fail)
    val exportSubject = resources.getString(R.string.settings_export_subject)
    val exportChooser = resources.getString(R.string.settings_export_chooser)

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (payload.isNullOrBlank()) {
                importResult = importFailText
                return@launch
            }
            // Decide from the content, not the URI: a SAF document URI rarely ends in ".csv"
            // (content://…/document/1234), which used to make a CSV backup fall through to the
            // JSON reader and import zero words — the groups then showed "0 个词条".
            val isCsv = !looksLikeJson(payload)
            val (count, error) = onImportPayload(payload, isCsv)
            importResult = if (error != null) {
                resources.getString(R.string.settings_import_error, error)
            } else {
                resources.getString(R.string.settings_import_ok, count)
            }
        }
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
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            SectionHeader("AI 学习助手")
            AiStatusCard(config = aiConfig, onClick = onOpenAiSettings)
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Text(
                    "AI 相关操作",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "导出 AI 对话记录",
                    subtitle = if (chatMessageCount == 0) {
                        "还没有对话记录"
                    } else {
                        "共 $chatMessageCount 条消息，导出为纯文本"
                    }
                ) {
                    if (chatMessageCount == 0) return@ActionRow
                    scope.launch {
                        val ok = exportAndShare(
                            context = context,
                            content = onExportChat(),
                            fileName = "lexi-ai-chat.txt",
                            subject = exportSubject,
                            chooserTitle = exportChooser
                        )
                        importResult = if (ok) exportOkText else exportFailText
                    }
                }
                ActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "清空 AI 对话记录",
                    subtitle = "只删除聊天，不影响词库",
                    destructive = true
                ) {
                    onClearChat()
                }
            }

            SectionHeader(stringResource(R.string.settings_appearance))
            SettingsCard {
                Text("自定义背景", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "主页背景和「干净浏览」背景是两套独立设置：可以给背诵页面放一张照片，" +
                        "列表保持干净。图片会复制进应用内部，原图删掉也不影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "启用自定义背景",
                    subtitle = "总开关；关闭后所有页面用主题纯色",
                    checked = preferences.backgroundEnabled,
                    onCheckedChange = onSetBackgroundEnabled
                )
                Spacer(Modifier.height(8.dp))

                BackgroundTargetPicker(
                    title = "🏠 主页背景",
                    subtitle = "生词本 / 复习 / AI 问答 / 统计 / 设置",
                    config = preferences.mainBackground,
                    enabled = preferences.backgroundEnabled,
                    otherConfig = preferences.cleanBackground,
                    onPickImage = { backgroundPicker.launch(arrayOf("image/*")) },
                    onPickColor = { onSetBackgroundColor(it, false) },
                    onOpacity = { onSetBackgroundOpacity(it, false) },
                    onClear = { onClearBackground(false) },
                    onCopyOther = { onCopyBackground(true) },
                    onSave = { onSaveBackground(false) }
                )
                Spacer(Modifier.height(12.dp))

                BackgroundTargetPicker(
                    title = "📖 干净浏览背景",
                    subtitle = "分组纯净阅读页面（可单独用一张更素/更暗的图）",
                    config = preferences.cleanBackground,
                    enabled = preferences.backgroundEnabled,
                    otherConfig = preferences.mainBackground,
                    onPickImage = { cleanBackgroundPicker.launch(arrayOf("image/*")) },
                    onPickColor = { onSetBackgroundColor(it, true) },
                    onOpacity = { onSetBackgroundOpacity(it, true) },
                    onClear = { onClearBackground(true) },
                    onCopyOther = { onCopyBackground(false) },
                    onSave = { onSaveBackground(true) }
                )
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onPreferencesChange { it.copy(themeMode = mode) } },
                            label = { Text(stringResource(mode.labelRes())) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_detail),
                    checked = preferences.useDynamicColor,
                    onCheckedChange = { value ->
                        onPreferencesChange { it.copy(useDynamicColor = value) }
                    }
                )
                SwitchRow(
                    title = stringResource(R.string.settings_compact),
                    subtitle = stringResource(R.string.settings_compact_detail),
                    checked = preferences.listDensityCompact,
                    onCheckedChange = { value ->
                        onPreferencesChange { it.copy(listDensityCompact = value) }
                    }
                )
            }

            SectionHeader(stringResource(R.string.settings_learning))
            SettingsCard {
                Text(
                    stringResource(R.string.settings_daily_goal, preferences.dailyGoal),
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = preferences.dailyGoal.toFloat(),
                    onValueChange = { value ->
                        onPreferencesChange { it.copy(dailyGoal = value.toInt().coerceIn(1, 100)) }
                    },
                    valueRange = 5f..100f,
                    steps = 18
                )
                SwitchRow(
                    title = stringResource(R.string.settings_front_chinese),
                    subtitle = stringResource(R.string.settings_front_chinese_detail),
                    checked = preferences.reviewFrontIsChinese,
                    onCheckedChange = { value ->
                        onPreferencesChange { it.copy(reviewFrontIsChinese = value) }
                    }
                )
                SwitchRow(
                    title = stringResource(R.string.auto_speak),
                    subtitle = stringResource(R.string.settings_auto_speak_detail),
                    checked = preferences.autoSpeak,
                    onCheckedChange = { value ->
                        onPreferencesChange { it.copy(autoSpeak = value) }
                    }
                )
                SwitchRow(
                    title = stringResource(R.string.settings_haptics),
                    subtitle = stringResource(R.string.settings_haptics_detail),
                    checked = preferences.hapticsEnabled,
                    onCheckedChange = { value ->
                        onPreferencesChange { it.copy(hapticsEnabled = value) }
                    }
                )
                SwitchRow(
                    title = stringResource(R.string.settings_sort_recent),
                    subtitle = stringResource(R.string.settings_sort_recent_detail),
                    checked = preferences.sortByRecent,
                    onCheckedChange = { value ->
                        onPreferencesChange { it.copy(sortByRecent = value) }
                    }
                )
            }

            SectionHeader(stringResource(R.string.settings_data, totalWords))
            SettingsCard {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("导入格式（一行一个词，Tab 分隔）", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "word\tphonetic\tsenses\ttags\tgroup\tnote\tstatus\n" +
                                "diet\t/ˈdaɪət/\tn. 日常饮食；v. 节食\t健康\t\t\tNEW",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "· senses 用「；」分隔多个义项，每项写成「词性 释义」，英文释义放括号里，例句写「例: …」\n" +
                                "· 分隔符用 Tab 最省心；也认 | 和逗号（逗号会按 CSV 引号规则解析）\n" +
                                "· 没有表头也行，就按上面的列顺序；分组名不存在会自动创建\n" +
                                "· 只想快速记几个词？一行写「word|释义」就够",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(R.string.settings_export_json),
                    subtitle = stringResource(R.string.settings_export_json_detail)
                ) {
                    scope.launch {
                        val ok = exportAndShare(
                            context = context,
                            content = onExportJson(),
                            fileName = "lexi-wordbook.json",
                            subject = exportSubject,
                            chooserTitle = exportChooser
                        )
                        importResult = if (ok) exportOkText else exportFailText
                    }
                }
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(R.string.settings_export_csv),
                    subtitle = stringResource(R.string.settings_export_csv_detail)
                ) {
                    scope.launch {
                        val ok = exportAndShare(
                            context = context,
                            content = onExportCsv(),
                            fileName = "lexi-wordbook.csv",
                            subject = exportSubject,
                            chooserTitle = exportChooser
                        )
                        importResult = if (ok) exportOkText else exportFailText
                    }
                }
                ActionRow(
                    icon = Icons.Rounded.Upload,
                    title = stringResource(R.string.settings_import),
                    subtitle = "支持本 App 的表格格式，也支持 txt / 一次一行；分隔符可用 Tab、| 或逗号"
                ) {
                    importLauncher.launch(arrayOf("text/plain", "text/csv", "text/comma-separated-values", "application/json", "*/*"))
                }
                ActionRow(
                    icon = Icons.Rounded.FileDownload,
                    title = "导出导入模板",
                    subtitle = "拿到标准表头，照着填就能直接导入"
                ) {
                    scope.launch {
                        val ok = exportAndShare(
                            context = context,
                            content = WordRepository.IMPORT_TEMPLATE,
                            fileName = "lexi-import-template.txt",
                            subject = exportSubject,
                            chooserTitle = exportChooser
                        )
                        importResult = if (ok) "已生成导入模板" else exportFailText
                    }
                }
                ActionRow(
                    icon = Icons.Rounded.School,
                    title = "添加四级英语单词",
                    subtitle = if (cet4Count > 0) {
                        "「四级核心」分组里已有 $cet4Count 个词条，点这里继续补齐"
                    } else {
                        "把内置的四级核心词汇（331 个）加到「四级核心」分组"
                    }
                ) {
                    confirmCet4 = true
                }
                ActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    title = "清理重复词条",
                    subtitle = if (duplicateCount > 0) {
                        "发现 $duplicateCount 个重复词条，可以按分组或全库一次性合并"
                    } else {
                        "扫描全库或指定分组，把重复的词条合并成一条"
                    }
                ) {
                    showCleanDialog = true
                }
                ActionRow(
                    icon = Icons.Rounded.Download,
                    title = stringResource(R.string.settings_import_samples),
                    subtitle = stringResource(R.string.settings_import_samples_detail)
                ) {
                    onImportSamples()
                }
                ActionRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = stringResource(R.string.settings_clear),
                    subtitle = stringResource(R.string.settings_clear_detail),
                    destructive = true
                ) {
                    confirmClear = true
                }
            }

            SectionHeader(stringResource(R.string.settings_about))
            var crashLog by remember { mutableStateOf(com.lexi.wordbook.WordBookApp.readLastCrash(app)) }
            if (!crashLog.isNullOrBlank()) {
                SettingsCard {
                    Text("上次崩溃日志", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "App 上次异常退出的堆栈已保存，复制给开发者可以定位问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onCopyText(crashLog.orEmpty()) }) { Text("复制日志") }
                        TextButton(onClick = {
                            com.lexi.wordbook.WordBookApp.clearLastCrash(app)
                            crashLog = null
                        }) { Text("清除") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Column {
                        Text(
                            "Roxy notebook ${com.lexi.wordbook.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.settings_about_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_about_sdk),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showCleanDialog) {
        CleanDuplicatesDialog(
            groups = groups,
            duplicateCount = duplicateCount,
            onCountFor = onDuplicateCountFor,
            // Hands the chosen range to the cleanup page, which owns the merge logic — one
            // implementation instead of two.
            onClean = { groupId ->
                showCleanDialog = false
                onOpenCleanDuplicates(groupId)
            },
            onDismiss = { showCleanDialog = false }
        )
    }
    if (confirmCet4) {
        AlertDialog(
            onDismissRequest = { confirmCet4 = false },
            title = { Text("添加四级英语单词？") },
            text = {
                Text(
                    "会把内置的四级核心词库加入「四级核心」分组（已存在的词会自动跳过）。" +
                        "不同分组之间允许出现同一个单词，所以不影响你现有的分组。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onImportCet4()
                    confirmCet4 = false
                }) { Text("开始添加") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCet4 = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body, totalWords)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    confirmClear = false
                }) { Text(stringResource(R.string.settings_clear_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            confirmButton = {
                TextButton(onClick = { importResult = null }) { Text(stringResource(R.string.action_ok)) }
            },
            title = { Text(stringResource(R.string.settings_import_dialog_title)) },
            text = { Text(result) }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/** Writes [content] to a cache file and opens the system share sheet. */
private suspend fun exportAndShare(
    context: Context,
    content: String,
    fileName: String,
    subject: String,
    chooserTitle: String
): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (fileName.endsWith(".csv")) "text/csv" else "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }.isSuccess
    }
/** Small square used by the background picker. */
@Composable
private fun BackgroundSwatch(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    isImage: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = color,
            shape = MaterialTheme.shapes.medium,
            border = if (selected) {
                androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary
                )
            } else {
                null
            },
            modifier = Modifier
                .size(58.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isImage) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
/** Background controls for one target (main interface or clean browse). */
@Composable
private fun BackgroundTargetPicker(
    title: String,
    subtitle: String,
    config: com.lexi.wordbook.data.BackgroundConfig,
    enabled: Boolean,
    otherConfig: com.lexi.wordbook.data.BackgroundConfig,
    onPickImage: () -> Unit,
    onPickColor: (Long) -> Unit,
    onOpacity: (Int) -> Unit,
    onClear: () -> Unit,
    onCopyOther: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    BackgroundSwatch(
                        label = "默认",
                        color = MaterialTheme.colorScheme.surface,
                        selected = !config.isSet,
                        onClick = onClear
                    )
                }
                item {
                    BackgroundSwatch(
                        label = "选图片",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        selected = config.source == com.lexi.wordbook.data.BackgroundSource.IMAGE &&
                            config.imagePath.isNotBlank(),
                        isImage = true,
                        onClick = onPickImage
                    )
                }
                items(com.lexi.wordbook.data.BACKGROUND_COLORS.size) { index ->
                    val value = com.lexi.wordbook.data.BACKGROUND_COLORS[index]
                    BackgroundSwatch(
                        label = "色 ${index + 1}",
                        color = com.lexi.wordbook.ui.components.argbColor(value),
                        selected = config.source == com.lexi.wordbook.data.BackgroundSource.SOLID &&
                            config.color == value,
                        onClick = { onPickColor(value) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "背景浓度：${config.opacity}%（0 = 完全看不到背景，100 = 背景最强）",
                style = MaterialTheme.typography.labelMedium
            )
            androidx.compose.material3.Slider(
                value = config.opacity.toFloat(),
                onValueChange = { onOpacity(it.toInt()) },
                valueRange = 0f..100f,
                steps = 19,
                enabled = config.isSet
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.lexi.wordbook.data.BackgroundConfig.OPACITY_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = config.opacity == preset,
                        onClick = { onOpacity(preset) },
                        enabled = config.isSet,
                        label = { Text("$preset%", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (!config.isSet) {
                Text(
                    "先选一个底色或图片，浓度滑杆才会生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (config.isSet) "已设置" else "未设置，使用默认外观",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (config.isSet) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                if (config.isSet) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
                if (otherConfig.isSet) {
                    TextButton(onClick = onCopyOther) { Text("复制另一套") }
                }
                // Explicit save: everything is also written on change, but this guarantees the
                // value is on disk and gives the user visible confirmation.
                Button(
                    onClick = onSave,
                    enabled = config.isSet,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 6.dp
                    )
                ) {
                    Icon(
                        Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("保存", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * True when [payload] is a JSON document (a `{…}` backup or a bare `[…]` word array).
 *
 * Format detection must come from the bytes rather than the file name: the storage access
 * framework hands out `content://…/document/1234` URIs with no extension, so a CSV backup used to
 * be parsed as JSON and import nothing.
 */
fun looksLikeJson(payload: String): Boolean {
    val trimmed = payload.trimStart('\uFEFF', ' ', '\t', '\n', '\r')
    return trimmed.startsWith("{") || trimmed.startsWith("[")
}

/**
 * 「清理重复词条」: pick the whole library or one group, see how many duplicates it holds, then
 * merge them. A duplicate is two entries with the same headword in the same bucket; the survivor
 * keeps the union of their senses and tags plus the most advanced review progress.
 */
@Composable
private fun CleanDuplicatesDialog(
    groups: List<com.lexi.wordbook.data.WordGroup>,
    duplicateCount: Int,
    onCountFor: (String) -> Int,
    onClean: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // null = whole library, otherwise a group id.
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    // null = nothing picked yet; the counts come from the repository on demand.
    var scope by remember { mutableStateOf<String?>(null) }

    val currentCount = remember(duplicateCount) {
        onCountFor(com.lexi.wordbook.data.WordRepository.NO_GROUP)
    }
    val pickedCount = scope?.let { onCountFor(it) } ?: currentCount

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
        title = { Text("清理重复词条") },
        text = {
            Column {
                Text(
                    "同一个词在同一个分组里只应保留一条。清理时会把重复条目的释义、标签合并，" +
                        "并保留复习进度最靠前的那一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (pickedCount == 0) "当前范围没有重复词条" else "当前范围有 $pickedCount 个重复词条",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroupId = null
                                    scope = com.lexi.wordbook.data.WordRepository.NO_GROUP
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGroupId == null,
                                onClick = {
                                    selectedGroupId = null
                                    scope = com.lexi.wordbook.data.WordRepository.NO_GROUP
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("全部词条", modifier = Modifier.weight(1f))
                            Text(
                                "${onCountFor(com.lexi.wordbook.data.WordRepository.NO_GROUP)} 个",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(groups) { group ->
                        val count = onCountFor(group.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroupId = group.id
                                    scope = group.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGroupId == group.id,
                                onClick = {
                                    selectedGroupId = group.id
                                    scope = group.id
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${group.emoji} ${group.name}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "$count 个",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (count > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pickedCount > 0,
                onClick = {
                    onClean(scope ?: com.lexi.wordbook.data.WordRepository.NO_GROUP)
                }
            ) {
                Text("开始清理")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}