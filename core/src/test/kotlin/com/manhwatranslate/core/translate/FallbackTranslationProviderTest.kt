package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FallbackTranslationProviderTest {

    private class Source(
        override val id: String,
        private val answer: (List<String>) -> List<String>,
    ) : TranslationProvider {
        var calls = 0
        override val displayName: String = id
        override val recommendedBatchSize: Int = 8

        override suspend fun translate(
            texts: List<String>,
            sourceLanguage: SourceScript,
            target: TranslationLanguage,
        ): List<String> {
            calls++
            return answer(texts)
        }
    }

    private val ok = { texts: List<String> -> texts.map { "vi:$it" } }
    private val failing = { _: List<String> -> throw TranslationFailure("HTTP 429") }
    private val echo = { texts: List<String> -> texts }

    private fun translate(chain: FallbackTranslationProvider): List<String> = runBlocking {
        chain.translate(listOf("안녕하세요"), SourceScript.KOREAN, TranslationLanguage.VIETNAMESE)
    }

    @Test
    fun `the first source that answers wins`() {
        val chain = FallbackTranslationProvider(listOf(Source("a", ok), Source("b", ok)))
        assertEquals(listOf("vi:안녕하세요"), translate(chain))
    }

    @Test
    fun `a failing source hands the batch to the next one`() {
        val dead = Source("a", failing)
        val live = Source("b", ok)
        val chain = FallbackTranslationProvider(listOf(dead, live))
        assertEquals(listOf("vi:안녕하세요"), translate(chain))
        assertEquals(1, dead.calls)
        assertEquals(1, live.calls)
    }

    @Test
    fun `a source that echoes its input is not a translation`() {
        val chain = FallbackTranslationProvider(listOf(Source("a", echo), Source("b", ok)))
        assertEquals(listOf("vi:안녕하세요"), translate(chain))
    }

    @Test
    fun `a failed source rests while another one answers`() {
        var now = 0L
        val dead = Source("a", failing)
        val live = Source("b", ok)
        val chain = FallbackTranslationProvider(
            links = listOf(dead, live),
            clock = { now },
            cooldownMillis = 60_000,
        )
        translate(chain)
        now = 30_000
        translate(chain)
        assertEquals(1, dead.calls, "a source inside its cooldown is skipped")
        assertEquals(2, live.calls)
    }

    @Test
    fun `a source is tried again once its rest is over`() {
        var now = 0L
        val flaky = Source("a") { texts -> if (now < 61_000) throw TranslationFailure("HTTP 503") else texts.map { "vi:$it" } }
        val live = Source("b", ok)
        val chain = FallbackTranslationProvider(
            links = listOf(flaky, live),
            clock = { now },
            cooldownMillis = 60_000,
        )
        translate(chain)
        now = 61_000
        assertEquals(listOf("vi:안녕하세요"), translate(chain))
        assertEquals(2, flaky.calls)
        assertEquals(1, live.calls)
    }

    @Test
    fun `a success clears the cooldown of a source`() {
        var now = 0L
        var broken = true
        val flaky = Source("a") { texts ->
            if (broken) throw TranslationFailure("HTTP 503") else texts.map { "vi:$it" }
        }
        val chain = FallbackTranslationProvider(
            links = listOf(flaky, Source("b", ok)),
            clock = { now },
            cooldownMillis = 60_000,
        )
        translate(chain)
        broken = false
        now = 61_000
        translate(chain)
        now = 70_000
        translate(chain)
        assertEquals(3, flaky.calls, "the source that recovered is back in front")
    }

    @Test
    fun `every source resting still gets one more try`() {
        var now = 0L
        val chain = FallbackTranslationProvider(
            links = listOf(Source("a", failing), Source("b", failing)),
            clock = { now },
            cooldownMillis = 60_000,
        )
        val first = runCatching { translate(chain) }
        now = 1_000
        val second = runCatching { translate(chain) }
        assertTrue(first.isFailure)
        assertTrue(second.isFailure)
        assertTrue(
            second.exceptionOrNull()?.message.orEmpty().contains("a: HTTP 429"),
            "the error names every source it tried",
        )
    }

    @Test
    fun `a short answer counts as a failure`() {
        val chain = FallbackTranslationProvider(listOf(Source("a") { emptyList() }, Source("b", ok)))
        assertEquals(listOf("vi:안녕하세요"), translate(chain))
    }
}
