package com.lexi.wordbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexi.wordbook.R
import com.lexi.wordbook.ui.ai.AiAssistant
import com.lexi.wordbook.ai.AiApiFormat
import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ai.AiProfile
import com.lexi.wordbook.ui.components.LoadingIndicator
import com.lexi.wordbook.ui.components.SectionHeader

/**
 * Dedicated screen for the user's own AI endpoint: provider format, URL, key, model,
 * generation options and the two system prompts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    config: AiConfig,
    profiles: List<AiProfile>,
    activeProfileId: String?,
    modelsBusy: Boolean,
    onConfigChange: ((AiConfig) -> AiConfig) -> Unit,
    onFormatChange: (AiApiFormat) -> Unit,
    onTest: () -> Unit,
    onFetchModels: (onResult: (List<String>, String?, Int) -> Unit) -> Unit,
    onResetPrompts: () -> Unit,
    onAddProfile: (name: String) -> Unit,
    onActivateProfile: (String) -> Unit,
    onDuplicateProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onClose: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf<ModelPickerState>(ModelPickerState.Idle) }
    var renaming by remember { mutableStateOf<AiProfile?>(null) }
    var deleting by remember { mutableStateOf<AiProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

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
                    Text("AI 接口设置", style = MaterialTheme.typography.titleLarge)
                }
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
            Spacer(Modifier.height(4.dp))

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("接口由你自行提供", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "App 只把请求发到你填写的地址，不经过任何第三方服务器。密钥保存在本机，" +
                            "导出数据时不会包含密钥。留空则 AI 功能自动隐藏，其余功能照常离线使用。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SectionHeader(
                title = "配置档（${profiles.size}）",
                trailing = {
                    TextButton(onClick = { creating = true }) { Text("＋ 新增") }
                }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    val active = profile.id == activeProfileId
                    Surface(
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { if (!active) onActivateProfile(profile.id) }
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (profile.isConfigured) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        profile.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    if (active) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                "使用中",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    profile.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    profile.baseUrl.ifBlank { "未填写地址" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { renaming = profile }) {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = "重命名",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(onClick = { onDuplicateProfile(profile.id) }) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "复制配置",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { deleting = profile },
                                enabled = profiles.size > 1
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = if (profiles.size > 1) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "可以保存多套接口（例如便宜的模型用来查词、更强的模型用来批量导入），点一下即可切换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeader("接口类型")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiApiFormat.entries.forEach { format ->
                    Surface(
                        color = if (config.apiFormat == format) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFormatChange(format) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = config.apiFormat == format,
                                    onClick = { onFormatChange(format) },
                                    label = { Text(format.name) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    format.defaultModel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(format.labelZh, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SectionHeader("连接信息")
            OutlinedTextField(
                value = config.baseUrl,
                onValueChange = { value -> onConfigChange { it.copy(baseUrl = value) } },
                label = { Text("API 地址") },
                placeholder = { Text(config.apiFormat.exampleEndpoint) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                supportingText = {
                    Text("可以填完整地址，也可以只填到 /v1，App 会自动补全路径。")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { value -> onConfigChange { it.copy(apiKey = value) } },
                label = { Text("API Key") },
                placeholder = { Text("sk-…（本地模型可留空）") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showKey) "隐藏密钥" else "显示密钥",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = config.model,
                onValueChange = { value -> onConfigChange { it.copy(model = value) } },
                label = { Text("模型名称") },
                placeholder = { Text(config.apiFormat.defaultModel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        modelPicker = ModelPickerState.Loading
                        onFetchModels { models, error, hidden ->
                            modelPicker = if (error != null) {
                                ModelPickerState.Error(error)
                            } else {
                                ModelPickerState.Loaded(models, hidden)
                            }
                        }
                    },
                    enabled = !modelsBusy && config.baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (modelsBusy) {
                        LoadingIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (modelsBusy) "获取中…" else "获取在线模型")
                }
            }
            Text(
                "填好地址和 Key 后点这里，App 会请求 /models 接口并列出该 Key 可用的模型，选一个即可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Text("测试连接")
                }
                OutlinedButton(
                    onClick = { onConfigChange { it.copy(apiKey = "") } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空密钥")
                }
            }

            SectionHeader("生成参数")
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "温度：${"%.1f".format(config.temperature)}（越低越稳定，查词建议 0.2–0.4）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = config.temperature,
                        onValueChange = { value -> onConfigChange { it.copy(temperature = value) } },
                        valueRange = 0f..1.5f,
                        steps = 14
                    )

                    Text(
                        "最大回复长度：${config.maxTokens} tokens",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "决定一次能返回多少内容。批量导入时每个词条约占 300 tokens，" +
                            "词多就把它调大（例如 30 个词建议 16384）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = config.maxTokens.toFloat(),
                        onValueChange = { value ->
                            onConfigChange { it.copy(maxTokens = value.toInt()) }
                        },
                        valueRange = 512f..AiConfig.MAX_MAX_TOKENS.toFloat(),
                        steps = 62
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2048, 8192, 16384, 32768).forEach { preset ->
                            FilterChip(
                                selected = config.maxTokens == preset,
                                onClick = { onConfigChange { it.copy(maxTokens = preset) } },
                                label = {
                                    Text("$preset", style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    val effectiveBatch = AiAssistant.chunkSizeFor(config)
                    Text(
                        "批量导入每次处理：$effectiveBatch 行" +
                            if (config.batchSize > 0) "（手动设置）" else "（按上面的长度自动计算）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "想让一长串单词一次全部导进去，就把这个值调到不小于你的行数。" +
                            "设成 0 = 自动。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = config.batchSize.toFloat(),
                        onValueChange = { value ->
                            onConfigChange { it.copy(batchSize = value.toInt()) }
                        },
                        valueRange = 0f..150f,
                        steps = 49
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "超时：${config.timeoutSeconds} 秒",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "一次导入的词越多耗时越长，超时可以设大一些。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = config.timeoutSeconds.toFloat(),
                        onValueChange = { value ->
                            onConfigChange { it.copy(timeoutSeconds = value.toInt()) }
                        },
                        valueRange = 15f..300f,
                        steps = 56
                    )
                }
            }

            SectionHeader(
                title = "提示词",
                trailing = {
                    TextButton(onClick = onResetPrompts) { Text("恢复默认") }
                }
            )
            Text(
                "查词提示词决定「AI 查询」返回的字段格式，默认要求返回 JSON；如果模型总是不按格式返回，" +
                    "可以在这里调整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.lookupPrompt,
                onValueChange = { value -> onConfigChange { it.copy(lookupPrompt = value) } },
                label = { Text("查词提示词（system）") },
                minLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = config.chatPrompt,
                onValueChange = { value -> onConfigChange { it.copy(chatPrompt = value) } },
                label = { Text("聊天提示词（system）") },
                minLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("常见问题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "· HTTP 400/401：多为密钥错误或模型名不存在。\n" +
                            "· 连不上：确认手机能访问该域名；国内直连 OpenAI 通常需要代理。\n" +
                            "· Ollama / LM Studio：地址填 http://<电脑IP>:11434/v1，密钥留空。\n" +
                            "· 只能 https：Android 默认禁止明文 http，除非自己配置网络白名单。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    ModelPickerDialog(
        state = modelPicker,
        currentModel = config.model,
        onDismiss = { modelPicker = ModelPickerState.Idle },
        onPick = { model ->
            onConfigChange { it.copy(model = model) }
            modelPicker = ModelPickerState.Idle
        }
    )

    if (creating) {
        NamePromptDialog(
            title = "新增配置档",
            label = "配置名称",
            placeholder = "例如：DeepSeek 查词 / GPT 批量导入",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                onAddProfile(name)
                creating = false
            }
        )
    }

    renaming?.let { profile ->
        NamePromptDialog(
            title = "重命名配置档",
            label = "配置名称",
            placeholder = profile.displayName,
            initial = profile.displayName,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRenameProfile(profile.id, name)
                renaming = null
            }
        )
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除配置「${profile.displayName}」？") },
            text = { Text("只删除这套接口设置，词库和对话记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteProfile(profile.id)
                    deleting = null
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** Small reusable single-field dialog. */
@Composable
private fun NamePromptDialog(
    title: String,
    label: String,
    placeholder: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** State of the "fetch online models" flow. */
sealed interface ModelPickerState {
    data object Idle : ModelPickerState
    data object Loading : ModelPickerState
    data class Loaded(val models: List<String>, val hiddenCount: Int = 0) : ModelPickerState
    data class Error(val message: String) : ModelPickerState
}

/** Model chooser shown after a successful /models query. */
@Composable
private fun ModelPickerDialog(
    state: ModelPickerState,
    currentModel: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    when (state) {
        ModelPickerState.Idle -> Unit
        ModelPickerState.Loading -> ModelLoadingDialog(onDismiss)
        is ModelPickerState.Error -> ModelErrorDialog(state.message, onDismiss)
        is ModelPickerState.Loaded -> ModelListDialog(
            models = state.models,
            hiddenCount = state.hiddenCount,
            currentModel = currentModel,
            onDismiss = onDismiss,
            onPick = onPick
        )
    }
}

@Composable
private fun ModelLoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("获取在线模型") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("正在请求接口…")
            }
        }
    )
}

@Composable
private fun ModelErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("获取模型失败") },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "可以手动填写模型名称 —— 例如 deepseek-chat、gpt-4o-mini、" +
                        "qwen-plus、glm-4-flash、gemini-2.0-flash。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
private fun ModelListDialog(
    models: List<String>,
    hiddenCount: Int,
    currentModel: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = models.filter { query.isBlank() || it.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模型（${models.size} 个可用）") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索模型") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (hiddenCount > 0) {
                    Text(
                        "已隐藏 $hiddenCount 个非对话模型（向量化 / 语音 / 画图等），选错它们会返回 400。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { model ->
                        Surface(
                            color = if (model == currentModel) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(model) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    model,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (model == currentModel) {
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** Compact summary tile shown on the main settings page. */
@Composable
fun AiStatusCard(
    config: AiConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (config.isConfigured) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (config.isConfigured) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (config.isConfigured) "AI 已连接" else "AI 未配置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (config.isConfigured) {
                        "${config.apiFormat.name} · ${config.model}"
                    } else {
                        "点这里填入你自己的 API 地址、密钥和模型"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box(Modifier.size(8.dp))
        }
    }
}
