package com.lexi.wordbook.ai

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire format used by the configured endpoint. */
enum class AiApiFormat(val labelZh: String, val exampleEndpoint: String, val defaultModel: String) {
    OPENAI(
        "OpenAI 兼容（OpenAI / DeepSeek / 通义 / Kimi / Ollama…）",
        "https://api.openai.com/v1/chat/completions",
        "gpt-4o-mini"
    ),
    ANTHROPIC(
        "Anthropic Claude",
        "https://api.anthropic.com/v1/messages",
        "claude-3-5-haiku-latest"
    ),
    GEMINI(
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        "gemini-2.0-flash"
    );

    companion object {
        fun fromName(name: String?): AiApiFormat =
            entries.firstOrNull { it.name == name } ?: OPENAI
    }
}

data class ChatMessage(val role: String, val content: String) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

/** Result of one round trip. */
data class AiResponse(
    val text: String,
    val error: String? = null,
    val httpCode: Int = 0
) {
    val isSuccess: Boolean get() = error == null && text.isNotBlank()
}

/** Result of a model-list query. */
data class ModelListResult(
    val models: List<String> = emptyList(),
    val error: String? = null,
    /** How many entries were dropped because they are not chat models. */
    val hiddenCount: Int = 0
) {
    val isSuccess: Boolean get() = error == null && models.isNotEmpty()
}

/**
 * One saved AI endpoint. Users can keep several (e.g. a cheap model for lookup and a
 * stronger one for batch import) and switch between them.
 */
@Serializable
data class AiProfile(
    val id: String,
    val name: String = "",
    val apiFormat: AiApiFormat = AiApiFormat.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.3f,
    val maxTokens: Int = AiConfig.DEFAULT_MAX_TOKENS,
    /** Input lines per 「AI 批量导入」 request; 0 = derive it from [maxTokens]. */
    val batchSize: Int = 0,
    val timeoutSeconds: Int = 60,
    val lookupPrompt: String = AiConfig.DEFAULT_LOOKUP_PROMPT,
    val chatPrompt: String = AiConfig.DEFAULT_CHAT_PROMPT,
    val batchPrompt: String = AiConfig.DEFAULT_BATCH_PROMPT
) {
    val displayName: String
        get() = name.ifBlank { model.ifBlank { "未命名配置" } }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() &&
            (apiFormat == AiApiFormat.OPENAI || apiKey.isNotBlank())

    /** Short "(provider · model)" caption for lists. */
    val summary: String
        get() = "${apiFormat.name} · ${model.ifBlank { apiFormat.defaultModel }}"

    fun toConfig(): AiConfig = AiConfig(
        apiFormat = apiFormat,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        batchSize = batchSize,
        timeoutSeconds = timeoutSeconds,
        lookupPrompt = lookupPrompt,
        chatPrompt = chatPrompt,
        batchPrompt = batchPrompt
    )

    companion object {
        fun from(config: AiConfig, id: String, name: String): AiProfile = AiProfile(
            id = id,
            name = name,
            apiFormat = config.apiFormat,
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            temperature = config.temperature,
            maxTokens = config.maxTokens,
        batchSize = config.batchSize,
            timeoutSeconds = config.timeoutSeconds,
            lookupPrompt = config.lookupPrompt,
            chatPrompt = config.chatPrompt,
            batchPrompt = config.batchPrompt
        )
    }
}

/** Everything needed to talk to the user's own AI endpoint. */
data class AiConfig(
    val apiFormat: AiApiFormat = AiApiFormat.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.3f,
    val maxTokens: Int = AiConfig.DEFAULT_MAX_TOKENS,
    /** Input lines per 「AI 批量导入」 request; 0 = derive it from [maxTokens]. */
    val batchSize: Int = 0,
    val timeoutSeconds: Int = 60,
    val lookupPrompt: String = DEFAULT_LOOKUP_PROMPT,
    val chatPrompt: String = DEFAULT_CHAT_PROMPT,
    val batchPrompt: String = DEFAULT_BATCH_PROMPT
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() &&
            (apiFormat == AiApiFormat.OPENAI || apiKey.isNotBlank())

    companion object {
        /** Asks for strict JSON so the app can fill the editor form directly. */
        /**
         * Default output budget. 2048 was far too small for 「AI 批量导入」: one finished entry
         * costs roughly 300 tokens, so a long word list was cut off mid-JSON.
         */
        const val DEFAULT_MAX_TOKENS = 8192

        /** Largest output budget the settings slider offers. */
        const val MAX_MAX_TOKENS = 32768

        const val DEFAULT_LOOKUP_PROMPT = """你是一位严谨的英汉词典编辑。用户会给你一个英文单词或词组，请输出它的词典信息。

只输出一个 JSON 对象，不要输出任何解释文字，不要使用 Markdown 代码块。格式如下：
{
  "word": "单词原形",
  "phonetic": "/音标/",
  "senses": [
    {"partOfSpeech": "n.", "meaningZh": "中文释义（简洁，多个义项用；分隔）", "meaningEn": "English definition", "example": "英文例句 + 中文翻译"}
  ],
  "note": "易混淆点或记忆提示，可为空",
  "tags": ["标签1", "标签2"]
}

要求：
1. partOfSpeech 只能取 n. / v. / adj. / adv. / pron. / prep. / conj. / int. / num. / art. / aux. / det. / phr. / idiom / abbr. 之一。
2. 若单词是多义词，senses 数组要包含所有常见义项（最多 6 条），按常用程度排序。
3. 若用户输入的不是有效英文单词，senses 返回空数组，并在 note 里用中文说明。"""

        const val DEFAULT_CHAT_PROMPT = """你是一位耐心的英语老师，帮助学生积累英语词汇。请遵守：
1. 默认用中文讲解，英文例句后附中文翻译。
2. 涉及单词时给出音标、词性、中文释义、常见搭配和 1-2 个例句。
3. 涉及句子时，先翻译，再拆解语法结构和重点短语。
4. 回答简洁有条理，必要时用短列表；不要输出 Markdown 表格。
5. 如果学生写错英文，先温和纠正再继续。"""

        /** Turns an arbitrary word list into the app's import JSON. */
        const val DEFAULT_BATCH_PROMPT = """你是一个数据整理助手。用户会给你一段来自其他软件或文件的英文单词数据，格式可能很乱（一行一个词、带序号、带音标、带词性缩写、中英混排、用空格或制表符或竖线分隔等等）。

请把它整理成 JSON 数组，只输出 JSON，不要输出任何解释文字，不要使用 Markdown 代码块。

每个元素的格式：
{
  "word": "英文单词或词组（原形，去掉序号和多余符号）",
  "phonetic": "/音标/，没有就留空字符串",
  "senses": [
    {"partOfSpeech": "n.", "meaningZh": "中文释义", "meaningEn": "", "example": ""}
  ],
  "note": "",
  "tags": ["可选标签"],
  "group": "可选分组名"
}

严格要求：
1. 顶层必须是 JSON 数组 []，不能是别的结构。
2. partOfSpeech 只能取 n. / v. / adj. / adv. / pron. / prep. / conj. / int. / num. / art. / aux. / det. / phr. / idiom / abbr. 之一；判断不出来就用 "—"。
3. 一个单词有多个词性或义项时，放进同一个 senses 数组，不要拆成多个元素。
4. 中文释义必须保留，不要翻译成英文；能补全的词条可以顺便补上音标和英文释义。
5. 去掉重复的单词，最多输出 60 个元素。
6. **复合词组算一个词条**：像 "thickening agent"、"come away"、"take up"、"in contrast to" 这类多词表达要作为**一个元素**整体处理 —— word 填完整词组，partOfSpeech 用 "phr."（固定习语用 "idiom"），释义给整个词组的意思，并补上例句。**不要**把它拆成两个单词，也不要漏掉。
7. 输入里**每一行都必须产出一个元素**；即使某个词你不确定，也要保留它并尽量给出释义，不要跳过。
8. **词形变化：原词和原形都要保留，各占一个元素。**
   - 输入是过去式、过去分词、现在分词、复数、比较级/最高级、第三人称单数、-ing 形式时，除了这个词本身，**再额外输出一条它的原形**（词典形）。
     例如输入 "ran" → 一条 "ran"（注明是 run 的过去式），再一条 "run"；
     输入 "apples" → 一条 "apples"（apples 是 apple 的复数），再一条 "apple"；
     输入 "studies" → "studies" + "study"；输入 "better" → "better" + "good/well"。
   - 变化形式那条的释义要写明它是什么形式（例如 "run 的过去式"），并给出该形式本身的用法和例句。
   - 原形那条按正常词条补全音标、词性、释义、例句。
   - 输入里本来就有原形时不要重复输出。
9. 只做**词形还原**这一种补充，不要联想同义词、不要补充派生词（如 -ness、-ly、-tion 形式）、不要添加输入里没有的其他单词。
10. **一行里可能有多个单词，要全部拆开识别。** 输入不一定是一行一个词，可能是
    "apple, banana, cherry"、"apple;banana"、"apple	banana"、"apple | banana" 或用顿号 /
    中文逗号分隔的行（"apple，banana"），也可能是一行空格隔开的若干单词
    （"apple banana cherry"）。**每一个单词都要单独产出一个元素**，不要把一整行当成一个词条。
    - 分隔符包括：英文逗号 `,`、中文逗号 `，`、分号 `;`、中文分号 `；`、竖线 `|`、斜杠 `/`、
      顿号 `、`、Tab、以及多个空格。
    - 但如果整行本身就是一个**复合词组或固定搭配**（如 "thickening agent"、"in contrast to"），
      按规则 6 作为一个元素处理，不要拆成两个。
    - 判断依据：拆开之后每一部分是不是各自独立的词；是就拆开，不是就整条保留。"""
    }
}

/**
 * Persists one or more AI endpoint profiles. [state] always exposes the active profile as a
 * plain [AiConfig] so the rest of the app does not care about profiles.
 */
class AiConfigRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lexi_ai", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _profiles = MutableStateFlow(loadProfiles())
    val profiles: StateFlow<List<AiProfile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow(loadActiveId())

    private val _state = MutableStateFlow(activeConfig())
    val state: StateFlow<AiConfig> = _state.asStateFlow()

    private val _activeProfileId = MutableStateFlow(_activeId.value)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    init {
        // Guarantee at least one profile exists so the settings screen always has a target.
        if (_profiles.value.isEmpty()) {
            val profile = AiProfile(id = newId(), name = "默认配置")
            persist(_profiles.value + profile)
            _activeId.value = profile.id
            prefs.edit { putString(KEY_ACTIVE, profile.id) }
        }
        publish()
    }

    // ------------------------------------------------------------------ loading

    private fun loadProfiles(): List<AiProfile> {
        prefs.getString(KEY_PROFILES, null)?.let { raw ->
            runCatching { json.decodeFromString<List<AiProfile>>(raw) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        // Migrate the pre-1.3 single-configuration keys.
        val legacyUrl = prefs.getString(KEY_LEGACY_BASE_URL, null)
        if (!legacyUrl.isNullOrBlank() || !prefs.getString(KEY_LEGACY_API_KEY, null).isNullOrBlank()) {
            val format = AiApiFormat.fromName(prefs.getString(KEY_LEGACY_FORMAT, null))
            val migrated = AiProfile(
                id = newId(),
                name = "默认配置",
                apiFormat = format,
                baseUrl = legacyUrl.orEmpty(),
                apiKey = prefs.getString(KEY_LEGACY_API_KEY, null).orEmpty(),
                model = prefs.getString(KEY_LEGACY_MODEL, null) ?: format.defaultModel,
                temperature = prefs.getFloat(KEY_LEGACY_TEMPERATURE, 0.3f),
                maxTokens = prefs.getInt(KEY_LEGACY_MAX_TOKENS, AiConfig.DEFAULT_MAX_TOKENS),
                timeoutSeconds = prefs.getInt(KEY_LEGACY_TIMEOUT, 60),
                lookupPrompt = prefs.getString(KEY_LEGACY_LOOKUP_PROMPT, null)
                    ?: AiConfig.DEFAULT_LOOKUP_PROMPT,
                chatPrompt = prefs.getString(KEY_LEGACY_CHAT_PROMPT, null)
                    ?: AiConfig.DEFAULT_CHAT_PROMPT
            )
            val list = listOf(migrated)
            persist(list)
            prefs.edit {
                putString(KEY_ACTIVE, migrated.id)
                remove(KEY_LEGACY_FORMAT)
                remove(KEY_LEGACY_BASE_URL)
                remove(KEY_LEGACY_API_KEY)
                remove(KEY_LEGACY_MODEL)
                remove(KEY_LEGACY_TEMPERATURE)
                remove(KEY_LEGACY_MAX_TOKENS)
                remove(KEY_LEGACY_TIMEOUT)
                remove(KEY_LEGACY_LOOKUP_PROMPT)
                remove(KEY_LEGACY_CHAT_PROMPT)
            }
            return list
        }
        return emptyList()
    }

    private fun loadActiveId(): String? {
        val stored = prefs.getString(KEY_ACTIVE, null)
        if (stored != null && _profiles.value.any { it.id == stored }) return stored
        val fallback = _profiles.value.firstOrNull()?.id
        if (fallback != null) prefs.edit { putString(KEY_ACTIVE, fallback) }
        return fallback
    }

    private fun activeConfig(): AiConfig =
        _profiles.value.firstOrNull { it.id == _activeId.value }?.toConfig() ?: AiConfig()

    private fun persist(list: List<AiProfile>) {
        _profiles.value = list
        runCatching { prefs.edit { putString(KEY_PROFILES, json.encodeToString(list)) } }
    }

    private fun publish() {
        _state.value = activeConfig()
        _activeProfileId.value = _activeId.value
    }

    private fun newId(): String = "ai-" + java.util.UUID.randomUUID().toString().take(8)

    // ------------------------------------------------------------------ profiles

    fun addProfile(name: String = "", config: AiConfig = AiConfig()): AiProfile {
        val profile = AiProfile.from(config, newId(), name.ifBlank { "配置 ${_profiles.value.size + 1}" })
        persist(_profiles.value + profile)
        setActiveProfile(profile.id)
        return profile
    }

    /** Duplicates an existing profile so users can tweak a variant without losing the original. */
    fun duplicateProfile(id: String): AiProfile? {
        val source = _profiles.value.firstOrNull { it.id == id } ?: return null
        val copy = source.copy(id = newId(), name = "${source.displayName} 副本")
        persist(_profiles.value + copy)
        return copy
    }

    fun setActiveProfile(id: String) {
        if (_profiles.value.none { it.id == id }) return
        _activeId.value = id
        prefs.edit { putString(KEY_ACTIVE, id) }
        publish()
    }

    fun updateActive(transform: (AiConfig) -> AiConfig) {
        val id = _activeId.value ?: return
        val current = _profiles.value.firstOrNull { it.id == id } ?: return
        val updated = AiProfile.from(transform(current.toConfig()), current.id, current.name)
        persist(_profiles.value.map { if (it.id == id) updated else it })
        publish()
    }

    fun renameProfile(id: String, name: String) {
        persist(_profiles.value.map { if (it.id == id) it.copy(name = name) else it })
        publish()
    }

    fun deleteProfile(id: String) {
        val remaining = _profiles.value.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            // Never leave the app without a target configuration.
            val replacement = AiProfile(id = newId(), name = "默认配置")
            persist(listOf(replacement))
            _activeId.value = replacement.id
        } else {
            persist(remaining)
            if (_activeId.value == id) _activeId.value = remaining.first().id
        }
        prefs.edit { putString(KEY_ACTIVE, _activeId.value) }
        publish()
    }

    /** Applies the selected provider's example endpoint + default model to the active entry. */
    fun applyFormatDefaults(format: AiApiFormat) {
        updateActive {
            it.copy(
                apiFormat = format,
                baseUrl = if (it.baseUrl.isBlank()) format.exampleEndpoint else it.baseUrl,
                model = format.defaultModel
            )
        }
    }

    fun resetPrompts() {
        updateActive {
            it.copy(
                lookupPrompt = AiConfig.DEFAULT_LOOKUP_PROMPT,
                chatPrompt = AiConfig.DEFAULT_CHAT_PROMPT,
                batchPrompt = AiConfig.DEFAULT_BATCH_PROMPT
            )
        }
    }

    fun clear() {
        prefs.edit { clear() }
        val profile = AiProfile(id = newId(), name = "默认配置")
        persist(listOf(profile))
        _activeId.value = profile.id
        prefs.edit { putString(KEY_ACTIVE, profile.id) }
        publish()
    }

    companion object {
        private const val KEY_PROFILES = "profiles_json_v1"
        private const val KEY_ACTIVE = "active_profile_id"

        // Pre-1.3 keys, read once for migration.
        private const val KEY_LEGACY_FORMAT = "format"
        private const val KEY_LEGACY_BASE_URL = "base_url"
        private const val KEY_LEGACY_API_KEY = "api_key"
        private const val KEY_LEGACY_MODEL = "model"
        private const val KEY_LEGACY_TEMPERATURE = "temperature"
        private const val KEY_LEGACY_MAX_TOKENS = "max_tokens"
        private const val KEY_LEGACY_TIMEOUT = "timeout"
        private const val KEY_LEGACY_LOOKUP_PROMPT = "lookup_prompt"
        private const val KEY_LEGACY_CHAT_PROMPT = "chat_prompt"
    }
}
