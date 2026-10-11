package com.lexi.wordbook.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a custom background is made of. */
enum class BackgroundSource { SOLID, IMAGE }

/**
 * One custom background. The main interface and 干净浏览 keep **separate** instances, so a photo
 * can be used for reading while the list stays plain (or the other way round).
 */
data class BackgroundConfig(
    val source: BackgroundSource = BackgroundSource.SOLID,
    /** ARGB value; 0 means "no solid colour chosen". */
    val color: Long = 0L,
    /** Absolute path of the image copied into app storage. */
    val imagePath: String = "",
    /**
     * 背景浓度 (how strongly the background shows), 0–100.
     *
     * 0 = the background is invisible and the plain theme colour is used, 100 = the image is
     * drawn at full strength and the Material surfaces become heavily transparent so it really
     * shows. Higher = **more** background, which is what the slider label promises.
     */
    val opacity: Int = 70
) {
    val isSet: Boolean
        get() = (source == BackgroundSource.IMAGE && imagePath.isNotBlank()) ||
            (source == BackgroundSource.SOLID && color != 0L)

    /** Only used when [source] is IMAGE. */
    val effectiveImagePath: String
        get() = if (source == BackgroundSource.IMAGE) imagePath else ""

    /** Only used when [source] is SOLID. */
    val effectiveColor: Long
        get() = if (source == BackgroundSource.SOLID) color else 0L

    /** 0f..1f alpha for the painted background layer. */
    val layerAlpha: Float
        get() = opacity.coerceIn(0, 100) / 100f

    /**
     * Alpha of the Material surface layers sitting on top of the background.
     *
     * The surface has to get out of the way as the background gets stronger, otherwise the
     * opaque `surface` colour simply covers the picture. The floor is low enough that 100%
     * looks properly transparent while text still has a readable backdrop.
     */
    val surfaceAlpha: Float
        get() = 1f - 0.72f * layerAlpha

    companion object {
        /** Quick presets offered next to the slider. */
        val OPACITY_PRESETS = listOf(0, 25, 50, 75, 100)
    }
}

/** How the 干净浏览 list is ordered. */
enum class CleanSortMode(val labelZh: String) {
    ALPHABET("A–Z"),
    RECENT("最新在前"),
    OLDEST("最早在前")
}

/** App-wide preferences, persisted next to the vocabulary list. */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val autoSpeak: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val dailyGoal: Int = 10,
    val reviewFrontIsChinese: Boolean = false,
    val sortByRecent: Boolean = false,
    val listDensityCompact: Boolean = false,
    val quizQuestionCount: Int = 10,
    /** Background of the main interface (list, review, chat, settings). */
    val mainBackground: BackgroundConfig = BackgroundConfig(),
    /** Background used by 「干净浏览」 only. */
    val cleanBackground: BackgroundConfig = BackgroundConfig(),
    /** Master switch: when off, custom backgrounds are ignored everywhere. */
    val backgroundEnabled: Boolean = true,
    /** 干净浏览 remembers the sort order and text scale across sessions. */
    val cleanSortMode: CleanSortMode = CleanSortMode.ALPHABET,
    val cleanTextScale: Float = 1f,
    /**
     * Last read position of 干净浏览, keyed by group id ("" = 未分组) and valued with the word id
     * that was at the top of the list when the reader left.
     */
    val cleanLastRead: Map<String, String> = emptyMap()
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Palette offered for solid background colours. */
val BACKGROUND_COLORS: List<Long> = listOf(
    0xFFF6F2EA, // 米白
    0xFFEDF3FF, // 淡蓝
    0xFFEAF6EE, // 淡绿
    0xFFFDF0F4, // 淡粉
    0xFF2B2B31, // 深灰
    0xFF14202B  // 深蓝黑
)

class PreferencesRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lexi_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserPreferences> = _state.asStateFlow()

    private fun load(): UserPreferences {
        val defaults = UserPreferences()
        return UserPreferences(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, defaults.themeMode.name)!!)
            }.getOrDefault(defaults.themeMode),
            useDynamicColor = prefs.getBoolean(KEY_DYNAMIC, defaults.useDynamicColor),
            autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, defaults.autoSpeak),
            hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, defaults.hapticsEnabled),
            dailyGoal = prefs.getInt(KEY_GOAL, defaults.dailyGoal).coerceIn(1, 200),
            reviewFrontIsChinese = prefs.getBoolean(KEY_FRONT_ZH, defaults.reviewFrontIsChinese),
            sortByRecent = prefs.getBoolean(KEY_SORT_RECENT, defaults.sortByRecent),
            listDensityCompact = prefs.getBoolean(KEY_COMPACT, defaults.listDensityCompact),
            quizQuestionCount = prefs.getInt(KEY_QUIZ_COUNT, defaults.quizQuestionCount).coerceIn(4, 50),
            mainBackground = loadBackground(MAIN_PREFIX, defaults.mainBackground),
            cleanBackground = loadBackground(CLEAN_PREFIX, defaults.cleanBackground),
            backgroundEnabled = prefs.getBoolean(KEY_BG_ENABLED, defaults.backgroundEnabled),
            cleanSortMode = runCatching {
                CleanSortMode.valueOf(prefs.getString(KEY_CLEAN_SORT, defaults.cleanSortMode.name)!!)
            }.getOrDefault(defaults.cleanSortMode),
            cleanTextScale = prefs.getFloat(KEY_CLEAN_SCALE, defaults.cleanTextScale)
                .coerceIn(0.8f, 1.8f),
            cleanLastRead = decodeLastRead(prefs.getString(KEY_CLEAN_LAST, null))
        )
    }

    // The blocking editor is deliberate: apply() is asynchronous, so a change made right
    // before the process is killed would not be on disk yet.
    @Suppress("ApplySharedPref", "UseKtx")
    fun update(transform: (UserPreferences) -> UserPreferences) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putBoolean(KEY_DYNAMIC, next.useDynamicColor)
            .putBoolean(KEY_AUTO_SPEAK, next.autoSpeak)
            .putBoolean(KEY_HAPTICS, next.hapticsEnabled)
            .putInt(KEY_GOAL, next.dailyGoal)
            .putBoolean(KEY_FRONT_ZH, next.reviewFrontIsChinese)
            .putBoolean(KEY_SORT_RECENT, next.sortByRecent)
            .putBoolean(KEY_COMPACT, next.listDensityCompact)
            .putInt(KEY_QUIZ_COUNT, next.quizQuestionCount)
            .putBoolean(KEY_BG_ENABLED, next.backgroundEnabled)
            .putString(KEY_CLEAN_SORT, next.cleanSortMode.name)
            .putFloat(KEY_CLEAN_SCALE, next.cleanTextScale)
            .putString(KEY_CLEAN_LAST, encodeLastRead(next.cleanLastRead))
            .commit()
        saveBackground(MAIN_PREFIX, next.mainBackground)
        saveBackground(CLEAN_PREFIX, next.cleanBackground)
    }

    private fun loadBackground(prefix: String, defaults: BackgroundConfig): BackgroundConfig =
        BackgroundConfig(
            source = runCatching {
                BackgroundSource.valueOf(
                    prefs.getString(prefix + KEY_SOURCE, defaults.source.name)!!
                )
            }.getOrDefault(defaults.source),
            color = prefs.getLong(prefix + KEY_COLOR, defaults.color),
            imagePath = prefs.getString(prefix + KEY_IMAGE, null).orEmpty(),
            // Migrate the two older keys: "dim" (1.5.x, 0.35 dim ≈ 65%) and "transparency"
            // (1.6.0) both become 背景浓度.
            opacity = when {
                prefs.contains(prefix + KEY_OPACITY) ->
                    prefs.getInt(prefix + KEY_OPACITY, defaults.opacity)
                prefs.contains(prefix + KEY_TRANSPARENCY) ->
                    prefs.getInt(prefix + KEY_TRANSPARENCY, defaults.opacity)
                prefs.contains(prefix + KEY_DIM) ->
                    ((1f - prefs.getFloat(prefix + KEY_DIM, 0.35f)) * 100f).toInt()
                else -> defaults.opacity
            }.coerceIn(0, 100)
        )

    // commit() is intentional (see the KDoc): the blocking writer guarantees the value is on
    // disk before the process can be killed, which apply() does not.
    @Suppress("ApplySharedPref", "UseKtx")
    private fun saveBackground(prefix: String, config: BackgroundConfig) {
        // The blocking editor is used deliberately: background changes must already be on disk
        // when the user swipes the app away right after picking a picture, and apply() would
        // still be flushing in the background.
        prefs.edit()
            .putString(prefix + KEY_SOURCE, config.source.name)
            .putLong(prefix + KEY_COLOR, config.color)
            .putString(prefix + KEY_IMAGE, config.imagePath)
            .putInt(prefix + KEY_OPACITY, config.opacity)
            .commit()
    }

    @Suppress("ApplySharedPref", "UseKtx")
    /** {"groupKey":"wordId"} — stored as compact JSON so it survives process death. */
    private fun encodeLastRead(map: Map<String, String>): String {
        if (map.isEmpty()) return ""
        return map.entries.joinToString(",") { (group, word) ->
            "${escape(group)}=${escape(word)}"
        }
    }

    private fun decodeLastRead(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(',').mapNotNull { entry ->
            val index = entry.indexOf('=')
            // index == 0 is the 未分组 bucket, whose group key is the empty string — a valid
            // entry, not a malformed one.
            if (index < 0) return@mapNotNull null
            unescape(entry.substring(0, index)) to unescape(entry.substring(index + 1))
        }.toMap()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("=", "\\e").replace(",", "\\c")

    private fun unescape(value: String): String = value
        .replace("\\c", ",")
        .replace("\\e", "=")
        .replace("\\\\", "\\")

    @Suppress("ApplySharedPref", "UseKtx")
    fun reset() {
        prefs.edit().clear().commit()
        _state.value = UserPreferences()
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_AUTO_SPEAK = "auto_speak"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_GOAL = "daily_goal"
        private const val KEY_FRONT_ZH = "front_zh"
        private const val KEY_SORT_RECENT = "sort_recent"
        private const val KEY_COMPACT = "compact"
        private const val KEY_QUIZ_COUNT = "quiz_count"
        private const val KEY_BG_ENABLED = "background_enabled"

        // Backgrounds are stored under two independent key prefixes.
        const val MAIN_PREFIX = "bg_main_"
        const val CLEAN_PREFIX = "bg_clean_"
        private const val KEY_SOURCE = "source"
        private const val KEY_COLOR = "color"
        private const val KEY_IMAGE = "image"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_TRANSPARENCY = "transparency"
        private const val KEY_DIM = "dim"

        private const val KEY_CLEAN_SORT = "clean_sort_mode"
        private const val KEY_CLEAN_SCALE = "clean_text_scale"
        private const val KEY_CLEAN_LAST = "clean_last_read"
    }
}