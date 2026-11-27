package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.model.TranslationStatus

data class TranslationBatchResult(
    val translations: List<RegionTranslation>,
    val warnings: List<String> = emptyList(),
) {
    fun asMap(): Map<Int, RegionTranslation> = translations.associateBy { it.regionId }
}

/**
 * Batches region texts into provider calls, deduplicates repeated lines (webtoons repeat a
 * lot), retries a failed batch once, and degrades to the original text instead of failing the
 * whole page.
 *
 * A cloud backend can refuse a batch outright, whether on quota or because its own moderation
 * dislikes the dialogue, so a [backup] provider (normally the on-device model) picks those
 * batches up instead of leaving the page in the original language.
 */
class TranslationService(
    private val provider: TranslationProvider,
    private val backup: TranslationProvider? = null,
    private val maxRetries: Int = 1,
) {

    suspend fun translate(
        regions: List<TextRegion>,
        script: SourceScript,
        target: TranslationLanguage,
        minLetterCount: Int = 1,
        onProgress: (Float) -> Unit = {},
    ): TranslationBatchResult {
        val warnings = ArrayList<String>()
        val skipped = ArrayList<RegionTranslation>()
        val workable = ArrayList<TextRegion>()
        for (region in regions) {
            if (region.letterCount < minLetterCount) {
                skipped += RegionTranslation(
                    regionId = region.id,
                    sourceText = region.text,
                    translatedText = region.text,
                    status = TranslationStatus.SKIPPED,
                    note = "Không đủ ký tự để dịch",
                )
            } else {
                workable += region
            }
        }

        if (workable.isEmpty()) {
            onProgress(1f)
            return TranslationBatchResult(skipped, warnings)
        }

        val groups = workable.groupBy { region ->
            if (region.script == SourceScript.AUTO) script else region.script
        }
        val batchSize = provider.recommendedBatchSize.coerceAtLeast(1)
        val totalBatches = groups.values.sumOf { group ->
            (group.map { it.text.trim() }.filter { it.isNotEmpty() }.distinct().size + batchSize - 1) / batchSize
        }.coerceAtLeast(1)
        var completedBatches = 0

        val resultMap = HashMap<Pair<SourceScript, String>, String>()
        val failedTexts = HashSet<String>()
        for ((groupScript, group) in groups) {
            val pending = group.map { it.text.trim() }.filter { it.isNotEmpty() }.distinct()
            for (batch in pending.chunked(batchSize)) {
                val attempt = translateBatchWithRetry(batch, groupScript, target, warnings)
                if (!attempt.succeeded) failedTexts += batch
                batch.forEachIndexed { offset, source ->
                    resultMap[groupScript to source] =
                        attempt.texts.getOrNull(offset)?.takeIf { it.isNotBlank() } ?: source
                }
                completedBatches++
                onProgress(completedBatches.toFloat() / totalBatches.toFloat())
            }
        }

        val translations = workable.map { region ->
            val source = region.text.trim()
            val regionScript = if (region.script == SourceScript.AUTO) script else region.script
            val translated = resultMap[regionScript to source] ?: source
            // A short "OH!" that a model leaves alone is still a translation, not a failure;
            // only a batch that never reached the backend leaves the original in place.
            val failed = source in failedTexts
            RegionTranslation(
                regionId = region.id,
                sourceText = region.text,
                translatedText = translated,
                status = if (failed) TranslationStatus.FAILED else TranslationStatus.DONE,
                note = if (failed) "Giữ nguyên văn bản gốc" else null,
            )
        }
        return TranslationBatchResult(skipped + translations, warnings)
    }

    /**
     * A backend that answers with the text it was given has not translated it: a model whose
     * moderation fires often returns the input instead of an error, and treating that as a
     * result is what left pages sitting in the original language.
     */
    private fun isEcho(batch: List<String>, result: List<String>): Boolean {
        if (batch.size != result.size) return true
        val letters = batch.indices.any { index ->
            batch[index].any(Char::isLetter) && batch[index].trim() == result[index].trim()
        }
        return letters && batch.indices.all { batch[it].trim() == result[it].trim() }
    }

    private data class BatchAttempt(val texts: List<String>, val succeeded: Boolean)

    private suspend fun translateBatchWithRetry(
        batch: List<String>,
        script: SourceScript,
        target: TranslationLanguage,
        warnings: MutableList<String>,
    ): BatchAttempt {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= maxRetries) {
            try {
                val result = provider.translate(batch, script, target)
                if (result.size == batch.size && !isEcho(batch, result)) {
                    return BatchAttempt(result, true)
                }
                lastError = if (result.size != batch.size) {
                    IllegalStateException("Provider ${provider.id} trả về ${result.size}/${batch.size} dòng")
                } else {
                    IllegalStateException("${provider.displayName} trả lại nguyên văn bản gốc")
                }
            } catch (error: Throwable) {
                lastError = error
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (error is TranslationFailure && !error.transient) break
            }
            attempt++
        }
        val fallback = backup
        if (fallback != null && fallback !== provider) {
            val reason = lastError?.message ?: "lỗi không xác định"
            val recovered = try {
                fallback.translate(batch, script, target)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                warnings += "${provider.displayName} và ${fallback.displayName} đều không dịch được " +
                    "($reason)"
                return BatchAttempt(batch, false)
            }
            if (recovered.size == batch.size && !isEcho(batch, recovered)) {
                warnings += "${provider.displayName} không dịch được ($reason), " +
                    "đã chuyển sang ${fallback.displayName}"
                return BatchAttempt(recovered, true)
            }
            warnings += "${fallback.displayName} trả về ${recovered.size}/${batch.size} dòng"
            return BatchAttempt(batch, false)
        }
        warnings += "Dịch thất bại (${provider.displayName}): ${lastError?.message ?: "lỗi không xác định"}"
        return BatchAttempt(batch, false)
    }
}
