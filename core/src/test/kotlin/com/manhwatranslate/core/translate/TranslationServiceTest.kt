package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TextRegion
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.model.TranslationStatus
import com.manhwatranslate.core.geometry.RectI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationServiceTest {

    private fun region(id: Int, text: String) = TextRegion(
        id = id,
        box = RectI(0, id * 100, 300, id * 100 + 60),
        text = text,
        script = SourceScript.KOREAN,
    )

    private class FakeProvider(
        private val failOn: String? = null,
        private var calls: Int = 0,
    ) : TranslationProvider {
        val batches = ArrayList<List<String>>()
        override val id = "fake"
        override val displayName = "Fake"
        override val recommendedBatchSize = 2

        override suspend fun translate(
            texts: List<String>,
            sourceLanguage: SourceScript,
            target: TranslationLanguage,
        ): List<String> {
            calls++
            batches += texts
            if (failOn != null && texts.any { it.contains(failOn) }) error("boom")
            return texts.map { "vi:$it" }
        }
    }

    @Test
    fun `duplicate texts are translated once`() = runBlocking {
        val provider = FakeProvider()
        val service = TranslationService(provider, maxRetries = 0)
        val regions = listOf(
            region(0, "Hello"),
            region(1, "Hello"),
            region(2, "World"),
        )
        val result = service.translate(regions, SourceScript.KOREAN, TranslationLanguage.VIETNAMESE)
        val translated = result.translations.associateBy { it.regionId }
        assertEquals(3, translated.size)
        assertEquals("vi:Hello", translated.getValue(0).translatedText)
        assertEquals("vi:Hello", translated.getValue(1).translatedText)
        assertEquals("vi:World", translated.getValue(2).translatedText)
        assertEquals(listOf("Hello", "World"), provider.batches.flatten())
    }

    @Test
    fun `batches honour the provider batch size`() = runBlocking {
        val provider = FakeProvider()
        val service = TranslationService(provider, maxRetries = 0)
        val regions = (0 until 5).map { region(it, "text $it") }
        service.translate(regions, SourceScript.KOREAN, TranslationLanguage.VIETNAMESE)
        assertEquals(3, provider.batches.size)
        assertTrue(provider.batches.all { it.size <= 2 })
    }

    @Test
    fun `a failed batch keeps the original text and reports a warning`() = runBlocking {
        val provider = FakeProvider(failOn = "boom")
        val service = TranslationService(provider, maxRetries = 0)
        val regions = listOf(region(0, "fine"), region(1, "boom here"))
        val result = service.translate(regions, SourceScript.KOREAN, TranslationLanguage.VIETNAMESE)
        val translated = result.translations.associateBy { it.regionId }
        assertEquals("boom here", translated.getValue(1).translatedText)
        assertEquals(TranslationStatus.FAILED, translated.getValue(1).status)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `a rejected batch falls back to the backup provider`() = runBlocking {
        val primary = FakeProvider(failOn = "boom")
        val backup = FakeProvider()
        val service = TranslationService(primary, backup = backup, maxRetries = 0)
        val result = service.translate(
            regions = listOf(region(0, "boom here")),
            script = SourceScript.KOREAN,
            target = TranslationLanguage.VIETNAMESE,
        )
        val translated = result.translations.first()
        assertEquals("vi:boom here", translated.translatedText)
        assertEquals(TranslationStatus.DONE, translated.status)
        assertTrue(result.warnings.any { it.contains("chuyển sang") })
    }

    @Test
    fun `an echoed batch falls back to the backup provider`() = runBlocking {
        val echo = object : TranslationProvider {
            override val id = "echo"
            override val displayName = "Echo"
            override val recommendedBatchSize = 2
            override suspend fun translate(
                texts: List<String>,
                sourceLanguage: SourceScript,
                target: TranslationLanguage,
            ): List<String> = texts
        }
        val service = TranslationService(echo, backup = FakeProvider(), maxRetries = 0)
        val result = service.translate(
            regions = listOf(region(0, "안녕하세요")),
            script = SourceScript.KOREAN,
            target = TranslationLanguage.VIETNAMESE,
        )
        assertEquals("vi:안녕하세요", result.translations.first().translatedText)
    }

    @Test
    fun `a permanent failure is not retried`() = runBlocking {
        var calls = 0
        val provider = object : TranslationProvider {
            override val id = "auth"
            override val displayName = "Auth"
            override suspend fun translate(
                texts: List<String>,
                sourceLanguage: SourceScript,
                target: TranslationLanguage,
            ): List<String> {
                calls++
                throw TranslationFailure("API key không hợp lệ", transient = false)
            }
        }
        val service = TranslationService(provider, maxRetries = 3)
        service.translate(
            regions = listOf(region(0, "hello there")),
            script = SourceScript.KOREAN,
            target = TranslationLanguage.VIETNAMESE,
        )
        assertEquals(1, calls, "a rejected key will fail the same way every time")
    }

    @Test
    fun `a transient failure is retried`() = runBlocking {
        var calls = 0
        val provider = object : TranslationProvider {
            override val id = "flaky"
            override val displayName = "Flaky"
            override suspend fun translate(
                texts: List<String>,
                sourceLanguage: SourceScript,
                target: TranslationLanguage,
            ): List<String> {
                calls++
                throw TranslationFailure("timeout", transient = true)
            }
        }
        val service = TranslationService(provider, maxRetries = 2)
        service.translate(
            regions = listOf(region(0, "hello there")),
            script = SourceScript.KOREAN,
            target = TranslationLanguage.VIETNAMESE,
        )
        assertEquals(3, calls, "a timeout is worth retrying")
    }

    @Test
    fun `regions with too few letters are skipped`() = runBlocking {
        val service = TranslationService(FakeProvider(), maxRetries = 0)
        val result = service.translate(
            regions = listOf(region(0, "!!"), region(1, "Real text")),
            script = SourceScript.KOREAN,
            target = TranslationLanguage.VIETNAMESE,
            minLetterCount = 3,
        )
        val skipped = result.translations.first { it.regionId == 0 }
        assertEquals(TranslationStatus.SKIPPED, skipped.status)
    }

    @Test
    fun `progress reaches one hundred percent`() = runBlocking {
        val progress = ArrayList<Float>()
        val service = TranslationService(FakeProvider(), maxRetries = 0)
        service.translate(
            regions = listOf(region(0, "a"), region(1, "b"), region(2, "c")),
            script = SourceScript.KOREAN,
            target = TranslationLanguage.VIETNAMESE,
            onProgress = { progress += it },
        )
        assertTrue(progress.isNotEmpty())
        assertEquals(1f, progress.last(), 0.001f)
    }
}

class BatchFormatterTest {

    @Test
    fun `format and parse round trip`() {
        val texts = listOf("첫 번째", "second line", "third")
        val formatted = BatchFormatter.format(texts)
        assertEquals(
            listOf("첫 번째", "second line", "third"),
            BatchFormatter.parse(formatted, texts.size),
        )
    }

    @Test
    fun `parse tolerates reordered and multi-line answers`() {
        val raw = """
            [[2]] Hai
            dòng
            [[1]] Một
        """.trimIndent()
        val parsed = BatchFormatter.parse(raw, 2)
        assertEquals(listOf("Một", "Hai\ndòng"), parsed)
    }

    @Test
    fun `parse returns null when a line is missing`() {
        assertEquals(null, BatchFormatter.parse("[[1]] only", 2))
    }

    @Test
    fun `parse returns null without markers`() {
        assertEquals(null, BatchFormatter.parse("không có số", 2))
    }
}
