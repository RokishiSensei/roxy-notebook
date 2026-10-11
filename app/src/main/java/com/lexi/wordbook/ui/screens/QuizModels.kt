package com.lexi.wordbook.ui.screens

import com.lexi.wordbook.data.Word
import com.lexi.wordbook.ui.QuizAnswer
import com.lexi.wordbook.ui.QuizType

/** What the user has to do for one question. */
enum class QuizMode { CHOICE, SPELLING }

/**
 * One generated question.
 *
 * @param prompt what the learner sees first
 * @param answer the expected text (headword for spelling, headword for choice)
 * @param options for [QuizMode.CHOICE]: four headwords, one correct
 * @param promptIsChinese true when [prompt] is a Chinese meaning
 */
data class QuizItem(
    val mode: QuizMode,
    val wordId: String,
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val promptIsChinese: Boolean = true,
    val meaningZh: String = "",
    val phonetic: String = "",
    val example: String = ""
) {
    fun evaluate(input: String): Boolean =
        input.trim().equals(answer.trim(), ignoreCase = true)

    fun toAnswer(given: String): QuizAnswer = QuizAnswer(
        prompt = prompt,
        answer = answer,
        given = given,
        correct = evaluate(given),
        meaningZh = meaningZh,
        phonetic = phonetic
    )
}

/** Generates the question list for a quiz session. */
object QuizBuilder {

    private const val MIN_OPTIONS = 4

    fun build(pool: List<Word>, type: QuizType, questionCount: Int): List<QuizItem> {
        val usable = pool.filter { it.primarySense?.meaningZh?.isNotBlank() == true }
        if (usable.isEmpty()) return emptyList()
        val count = questionCount.coerceAtLeast(1)

        return when (type) {
            QuizType.CHOICE_ZH_TO_EN -> choice(usable, count, promptIsChinese = true)
            QuizType.CHOICE_EN_TO_ZH -> choice(usable, count, promptIsChinese = false)
            QuizType.SPELLING -> spelling(usable, count)
            QuizType.MIXED -> mixed(usable, count)
        }
    }

    /**
     * Builds a quiz containing only the words that were answered wrongly.
     *
     * [pool] is the full word set (used as the distractor source so the options stay
     * plausible); [wrong] decides which words become questions.
     */
    fun retryFrom(pool: List<Word>, wrong: List<QuizAnswer>, type: QuizType): List<QuizItem> {
        val byText = pool.associateBy { it.text.lowercase() }
        val targets = wrong
            .mapNotNull { byText[it.answer.lowercase()] }
            .distinctBy { it.id }
        if (targets.isEmpty()) return emptyList()

        return when (type) {
            QuizType.SPELLING -> spelling(targets, targets.size)
            QuizType.CHOICE_EN_TO_ZH -> choice(pool, targets.size, promptIsChinese = false, only = targets)
            else -> choice(pool, targets.size, promptIsChinese = true, only = targets)
        }
    }

    // ------------------------------------------------------------------ generators

    private fun choice(
        pool: List<Word>,
        count: Int,
        promptIsChinese: Boolean,
        only: List<Word>? = null
    ): List<QuizItem> {
        val unique = pool.distinctBy { it.text.lowercase() }
        if (unique.size < 2) return emptyList()
        val shuffled = unique.shuffled()
        val asked = (only ?: shuffled).take(count)
        val isEnToZh = !promptIsChinese

        return asked.mapNotNull { word ->
            val sense = word.primarySense ?: return@mapNotNull null
            val correct = if (isEnToZh) sense.meaningZh else word.text
            val distractorPool = unique
                .filter { it.id != word.id }
                .mapNotNull { other ->
                    val otherSense = other.primarySense ?: return@mapNotNull null
                    if (isEnToZh) otherSense.meaningZh else other.text
                }
                .filter { !it.equals(correct, ignoreCase = true) }
                .distinct()
                .shuffled()

            // Fall back to fewer options rather than dropping the question entirely.
            val others = distractorPool.take(MIN_OPTIONS - 1)
            if (others.isEmpty()) return@mapNotNull null

            QuizItem(
                mode = QuizMode.CHOICE,
                wordId = word.id,
                prompt = if (isEnToZh) word.text else sense.meaningZh,
                answer = correct,
                options = (others + correct).shuffled(),
                promptIsChinese = promptIsChinese,
                meaningZh = sense.meaningZh,
                phonetic = word.phonetic,
                example = sense.example
            )
        }
    }

    private fun spelling(pool: List<Word>, count: Int): List<QuizItem> =
        pool.distinctBy { it.text.lowercase() }
            .shuffled()
            .take(count)
            .mapNotNull { word ->
                val sense = word.primarySense ?: return@mapNotNull null
                QuizItem(
                    mode = QuizMode.SPELLING,
                    wordId = word.id,
                    prompt = sense.meaningZh,
                    answer = word.text,
                    promptIsChinese = true,
                    meaningZh = sense.meaningZh,
                    phonetic = word.phonetic,
                    example = sense.example
                )
            }

    private fun mixed(pool: List<Word>, count: Int): List<QuizItem> {
        val choiceCount = (count + 1) / 2
        val spellingCount = count - choiceCount
        var items = choice(pool, choiceCount, promptIsChinese = true)
        if (items.size < choiceCount) {
            // Not enough words for four options: make the rest spelling questions.
            return spelling(pool, count)
        }
        items = items + spelling(pool, spellingCount + (choiceCount - items.size))
        return items.shuffled().take(count)
    }
}

/**
 * How many questions the given pool can actually produce for [type].
 * Multiple choice needs at least four words with a Chinese meaning; spelling needs one.
 */
fun possibleQuestionCount(pool: List<Word>, type: QuizType): Int {
    val usable = pool.count { it.primarySense?.meaningZh?.isNotBlank() == true }
    return when (type) {
        QuizType.SPELLING -> usable
        QuizType.MIXED -> if (usable >= 4) usable else 0
        else -> if (usable >= 2) usable else 0
    }
}
