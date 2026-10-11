package com.lexi.wordbook.data

import kotlinx.serialization.Serializable

/** A user-defined bucket for vocabulary, e.g. 「雅思核心」「阅读生词」. */
@Serializable
data class WordGroup(
    val id: String,
    val name: String,
    /** Emoji shown on the group chip; falls back to 📁. */
    val emoji: String = "📁",
    /** Index into [WordGroup.COLORS]; kept as an int so the palette stays in the UI layer. */
    val colorIndex: Int = 0,
    val createdAt: Long = 0L
) {
    companion object {
        val EMOJIS = listOf("📁", "📚", "🎯", "🔥", "⭐", "🌱", "✈️", "💼", "🧪", "🎬", "🎧", "📝")
        const val COLOR_COUNT = 6
    }
}

/** AI-generated draft for one word, used by the 「AI 查询」 flow. */
@Serializable
data class AiWordResult(
    val word: String = "",
    val phonetic: String = "",
    val senses: List<AiSense> = emptyList(),
    val note: String = "",
    val tags: List<String> = emptyList()
) {
    val hasContent: Boolean
        get() = senses.isNotEmpty() || phonetic.isNotBlank()
}

@Serializable
data class AiSense(
    val partOfSpeech: String = "",
    val meaningZh: String = "",
    val meaningEn: String = "",
    val example: String = ""
) {
    fun toSense(): Sense = Sense(
        partOfSpeech = PartOfSpeech.fromAi(partOfSpeech),
        meaningZh = meaningZh.trim(),
        meaningEn = meaningEn.trim(),
        example = example.trim()
    )
}
