package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage

/**
 * A translation backend. Implementations must return exactly one entry per input text, in
 * the same order; the on-device ML Kit model, Google Cloud, DeepL and any OpenAI-compatible
 * chat model all plug in here.
 */
interface TranslationProvider {
    val id: String

    val displayName: String

    /** LLM providers translate the whole page in one call to keep the context. */
    val recommendedBatchSize: Int get() = 8

    suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String>
}

/** Numbers the pages' texts so a batch call can be matched back to its region reliably. */
object BatchFormatter {
    private const val SEPARATOR = "\n"
    const val MARKER_PREFIX = "[["
    const val MARKER_SUFFIX = "]]"

    fun format(texts: List<String>): String =
        texts.mapIndexed { index, text -> "$MARKER_PREFIX${index + 1}$MARKER_SUFFIX $text" }.joinToString(SEPARATOR)

    fun parse(raw: String, expected: Int): List<String>? {
        val pattern = Regex("""\[\[(\d+)]]""")
        val matches = pattern.findAll(raw).toList()
        if (matches.isEmpty()) return null
        val collected = arrayOfNulls<String>(expected)
        matches.forEachIndexed { index, match ->
            val number = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEachIndexed
            if (number < 0 || number >= expected) return@forEachIndexed
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else raw.length
            val value = raw.substring(start, end).trim()
            if (value.isNotEmpty()) collected[number] = value
        }
        if (collected.any { it == null }) return null
        return collected.map { it!! }
    }
}
