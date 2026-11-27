package com.manhwatranslate.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.TranslationProvider
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Google Cloud Translation v2, the classic key-in-URL REST API. */
class GoogleCloudTranslationProvider(private val apiKey: String) : TranslationProvider {

    override val id: String = "google_cloud"
    override val displayName: String = "Google Cloud Translation"
    override val recommendedBatchSize: Int = 24

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (apiKey.isBlank()) throw TranslationException("Chưa nhập API key cho Google Cloud")
        val payload = buildJsonObject {
            put("q", buildJsonArray { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("target", target.code.substringBefore("-"))
            if (!sourceLanguage.isAuto) put("source", sourceLanguage.bcp47)
            put("format", "text")
        }
        val body = HttpJson.post(
            url = "https://translation.googleapis.com/language/translate/v2?key=$apiKey",
            body = payload.toString(),
        )
        val translations = body["data"]
            ?.jsonObject
            ?.get("translations")
            ?.jsonArray
            ?: throw TranslationException("Google Cloud không trả về bản dịch")
        val values = translations.map { entry ->
            unescape(entry.jsonObject["translatedText"]?.jsonPrimitive?.content.orEmpty())
        }
        if (values.size != texts.size) {
            throw TranslationException("Google Cloud trả về ${values.size}/${texts.size} dòng")
        }
        return values
    }

    private fun unescape(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

/** DeepL, free or pro endpoint depending on the key. */
class DeepLTranslationProvider(
    private val apiKey: String,
    private val pro: Boolean,
) : TranslationProvider {

    override val id: String = "deepl"
    override val displayName: String = "DeepL"
    override val recommendedBatchSize: Int = 24

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (apiKey.isBlank()) throw TranslationException("Chưa nhập API key cho DeepL")
        val freeKey = apiKey.trim().endsWith(":fx")
        val host = if (pro && !freeKey) "https://api.deepl.com" else "https://api-free.deepl.com"
        val payload = buildJsonObject {
            put("text", buildJsonArray { texts.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("target_lang", deeplCode(target))
            if (!sourceLanguage.isAuto) put("source_lang", deeplCode(sourceLanguage))
            put("preserve_formatting", true)
        }
        val body = HttpJson.post(
            url = "$host/v2/translate",
            body = payload.toString(),
            headers = mapOf("Authorization" to "DeepL-Auth-Key $apiKey"),
        )
        val translations = body["translations"]?.jsonArray
            ?: throw TranslationException("DeepL không trả về bản dịch")
        val values = translations.map { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
        if (values.size != texts.size) {
            throw TranslationException("DeepL trả về ${values.size}/${texts.size} dòng")
        }
        return values
    }

    private fun deeplCode(language: TranslationLanguage): String =
        when (language) {
            TranslationLanguage.CHINESE_SIMPLIFIED -> "ZH"
            TranslationLanguage.CHINESE_TRADITIONAL -> "ZH"
            TranslationLanguage.ENGLISH -> "EN-US"
            else -> language.code.uppercase()
        }

    private fun deeplCode(script: SourceScript): String = when (script) {
        SourceScript.KOREAN -> "KO"
        SourceScript.JAPANESE -> "JA"
        SourceScript.CHINESE -> "ZH"
        SourceScript.LATIN -> "EN"
        SourceScript.AUTO -> "EN"
    }
}
