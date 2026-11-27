package com.manhwatranslate.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.vision.awaitTask

/**
 * Google's on-device model. Free and offline once the ~30 MB language pack is downloaded,
 * which makes it the default so the app works without any API key.
 */
class MlKitTranslationProvider : TranslationProvider {

    override val id: String = "mlkit"
    override val displayName: String = "ML Kit (trên máy)"
    override val recommendedBatchSize: Int = 4

    private val clients = HashMap<String, Translator>()

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        val sourceCode = mlkitSourceCode(sourceLanguage)
        val targetCode = target.mlkitCode
        if (sourceCode == targetCode) return texts
        if (!target.mlkitSupported) {
            throw TranslationException(
                "ML Kit không hỗ trợ ${target.label}. Hãy chọn nhà cung cấp khác như Google Cloud hoặc OpenAI.",
            )
        }
        val translator = clientFor(sourceCode, targetCode)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
        } catch (error: Exception) {
            throw TranslationException(
                "Không tải được gói ngôn ngữ ${target.label}. Kiểm tra kết nối mạng rồi thử lại.",
                error,
            )
        }
        val results = ArrayList<String>(texts.size)
        for (text in texts) {
            val translated = runCatching { translator.translate(text).awaitTask() }.getOrElse { error ->
                throw TranslationException("ML Kit không dịch được đoạn văn bản này", error)
            }
            results += translated
        }
        return results
    }

    private fun clientFor(sourceCode: String, targetCode: String): Translator =
        clients.getOrPut("$sourceCode->$targetCode") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(sourceCode)
                    .setTargetLanguage(targetCode)
                    .build(),
            )
        }

    private fun mlkitSourceCode(script: SourceScript): String = when (script) {
        SourceScript.KOREAN -> TranslateLanguage.KOREAN
        SourceScript.JAPANESE -> TranslateLanguage.JAPANESE
        SourceScript.CHINESE -> TranslateLanguage.CHINESE
        SourceScript.LATIN -> TranslateLanguage.ENGLISH
        SourceScript.AUTO -> TranslateLanguage.KOREAN
    }

    fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
    }
}
