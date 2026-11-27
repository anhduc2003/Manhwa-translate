package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import kotlinx.coroutines.CancellationException

/**
 * Runs one batch through several keyless backends in order and returns the first real answer, so a
 * source that is blocked, out of quota or down does not leave the page in the original language.
 *
 * A source that failed is skipped while it rests; the wait doubles with every consecutive failure
 * up to [maxCooldownMillis], and one good answer clears it. When every source is resting the least
 * recently failed one is tried anyway: a page waiting on a cooldown is worse than one more call to
 * a server that may already have recovered.
 */
class FallbackTranslationProvider(
    links: List<TranslationProvider>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cooldownMillis: Long = 60_000,
    private val maxCooldownMillis: Long = 20 * 60_000,
) : TranslationProvider {

    init {
        require(links.isNotEmpty()) { "Cần ít nhất một nguồn dịch" }
    }

    private class Link(val provider: TranslationProvider) {
        var failures: Int = 0
        var restingUntil: Long = 0
    }

    private val chain: List<Link> = links.map(::Link)

    override val id: String = "free_chain"

    override val displayName: String = "Nguồn miễn phí"

    override val recommendedBatchSize: Int = links.maxOf { it.recommendedBatchSize }.coerceAtLeast(1)

    /** The sources in priority order, for the settings screen to report on. */
    val sourceNames: List<String> get() = chain.map { it.provider.displayName }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val now = clock()
        val ready = chain.filter { it.restingUntil <= now }
        val resting = chain.filter { it.restingUntil > now }.sortedBy { it.restingUntil }
        val reasons = ArrayList<String>()
        for (link in ready + resting) {
            val result = try {
                link.provider.translate(texts, sourceLanguage, target)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                rest(link, now)
                reasons += "${link.provider.displayName}: ${error.message ?: error.javaClass.simpleName}"
                continue
            }
            if (result.size != texts.size) {
                rest(link, now)
                reasons += "${link.provider.displayName}: trả về ${result.size}/${texts.size} dòng"
                continue
            }
            if (isEchoResult(texts, result)) {
                rest(link, now)
                reasons += "${link.provider.displayName}: trả lại nguyên văn bản gốc"
                continue
            }
            link.failures = 0
            link.restingUntil = 0
            return result
        }
        throw TranslationFailure(
            "Không nguồn miễn phí nào dịch được (${reasons.joinToString("; ")})",
            transient = true,
        )
    }

    private fun rest(link: Link, now: Long) {
        link.failures++
        val wait = cooldownMillis shl (link.failures - 1).coerceAtMost(8)
        link.restingUntil = now + wait.coerceAtMost(maxCooldownMillis)
    }
}
