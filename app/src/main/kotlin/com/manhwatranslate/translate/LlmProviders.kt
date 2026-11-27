package com.manhwatranslate.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.TranslationProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Any chat-completions endpoint: OpenAI, DeepSeek, Groq, OpenRouter, local servers. */
class OpenAiTranslationProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : TranslationProvider {

    override val id: String = "openai"
    override val displayName: String = "OpenAI ($model)"
    override val recommendedBatchSize: Int = 24

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (apiKey.isBlank()) throw TranslationException("Chưa nhập API key cho OpenAI")
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            put("messages", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", LlmPrompt.system(target))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", LlmPrompt.user(texts))
                    },
                )
            })
        }
        val response = HttpJson.post(
            url = endpoint,
            body = payload.toString(),
            headers = mapOf("Authorization" to "Bearer $apiKey"),
        )
        val content = response["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?: throw TranslationException("Phản hồi của mô hình không có nội dung")
        return LlmPrompt.parse(content, texts.size)
    }
}

/** Google AI Studio's Gemini generateContent endpoint. */
class GeminiTranslationProvider(
    private val apiKey: String,
    private val model: String,
) : TranslationProvider {

    override val id: String = "gemini"
    override val displayName: String = "Gemini ($model)"
    override val recommendedBatchSize: Int = 24

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (apiKey.isBlank()) throw TranslationException("Chưa nhập API key cho Gemini")
        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent?key=$apiKey"
        val prompt = LlmPrompt.system(target) + "\n\n" + LlmPrompt.user(texts)
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(
                    buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt) }) })
                    },
                )
            })
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 4096)
                },
            )
        }
        val response = HttpJson.post(endpoint, payload.toString())
        val content = response["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("content")
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.joinToString("") { part -> (part as? JsonObject)?.get("text")?.jsonPrimitive?.content.orEmpty() }
        if (content.isNullOrBlank()) throw TranslationException("Gemini không trả về nội dung")
        return LlmPrompt.parse(content, texts.size)
    }
}

internal fun JsonArray.texts(): List<String> = map { it.jsonPrimitive.content }
