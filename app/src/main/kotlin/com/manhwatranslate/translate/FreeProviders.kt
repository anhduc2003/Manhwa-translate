package com.manhwatranslate.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.FallbackTranslationProvider
import com.manhwatranslate.core.translate.TranslationFailure
import com.manhwatranslate.core.translate.TranslationProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Google's public web endpoint, the one the translate widget itself calls. No key, no account,
 * good Korean/Japanese/Chinese → Vietnamese, and it takes a whole page of bubbles in one request
 * by repeating the `q` parameter. Two hosts are tried so a block on one does not lose the source.
 */
class GoogleFreeTranslationProvider : TranslationProvider {

    override val id: String = "google_free"
    override val displayName: String = "Google Dịch (miễn phí)"
    override val recommendedBatchSize: Int = 24

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val source = FreeCodes.source(sourceLanguage, texts)
        val targetCode = FreeCodes.target(target)
        var lastError: TranslationFailure? = null
        for (host in HOSTS) {
            try {
                return request(host, source, targetCode, texts)
            } catch (error: TranslationFailure) {
                lastError = error
            }
        }
        throw lastError ?: TranslationException("Google Dịch không phản hồi", transient = true)
    }

    private suspend fun request(
        host: String,
        source: String,
        target: String,
        texts: List<String>,
    ): List<String> {
        val fields = ArrayList<Pair<String, String>>(texts.size)
        texts.forEach { fields += "q" to it }
        val raw = HttpJson.postForm(
            "$host/translate_a/t?client=gtx&sl=$source&tl=$target&ie=UTF-8&oe=UTF-8",
            fields,
        )
        val element = runCatching { HttpJson.json.parseToJsonElement(raw) }
            .getOrElse { throw TranslationException("Google Dịch trả về dữ liệu lạ", transient = true) }
        val values = when (element) {
            is JsonArray -> element.map { entry ->
                when (entry) {
                    is JsonArray -> entry.firstOrNull()?.jsonPrimitive?.content.orEmpty()
                    is JsonPrimitive -> entry.content
                    else -> ""
                }
            }
            is JsonPrimitive -> listOf(element.content)
            else -> emptyList()
        }
        if (values.size != texts.size) {
            throw TranslationException(
                "Google Dịch trả về ${values.size}/${texts.size} dòng",
                transient = true,
            )
        }
        if (values.any { it.isBlank() }) {
            throw TranslationException("Google Dịch trả về dòng trống", transient = true)
        }
        return values.map { normalizeText(unescapeHtml(it)) }
    }

    private companion object {
        val HOSTS = listOf("https://translate.googleapis.com", "https://clients5.google.com")
    }
}

/**
 * MyMemory, a free translation memory. No key and no account, but a 500-byte query limit and a
 * daily word allowance, so each line goes as its own request and long lines are split.
 */
class MyMemoryTranslationProvider : TranslationProvider {

    override val id: String = "mymemory"
    override val displayName: String = "MyMemory (miễn phí)"
    override val recommendedBatchSize: Int = 1

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        if (texts.isEmpty()) return texts
        val source = FreeCodes.source(sourceLanguage, texts)
        val langpair = "$source|${FreeCodes.target(target)}"
        return texts.map { text -> translateOne(text, langpair) }
    }

    private suspend fun translateOne(text: String, langpair: String): String {
        val pieces = splitToFit(text, MAX_QUERY_BYTES)
        val translated = pieces.map { piece ->
            val url = "https://api.mymemory.translated.net/get?q=${encode(piece)}" +
                "&langpair=${encode(langpair)}"
            val body = HttpJson.json.parseToJsonElement(HttpJson.get(url)).jsonObject
            val status = body["responseStatus"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val value = body["responseData"]
                ?.jsonObject
                ?.get("translatedText")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
            if (status != 200 || value.isBlank()) {
                val detail = body["responseDetails"]?.jsonPrimitive?.content.orEmpty()
                throw TranslationException(
                    "MyMemory không dịch được" + (if (detail.isBlank()) " (HTTP $status)" else ": $detail"),
                    transient = status == 429 || status >= 500,
                )
            }
            normalizeText(unescapeHtml(value))
        }
        return translated.joinToString(" ").trim()
    }

    private companion object {
        const val MAX_QUERY_BYTES = 480
    }
}

/** A LibreTranslate server the user runs themselves: free, no quota, no third party. */
class LibreTranslateProvider(private val baseUrl: String) : TranslationProvider {

    override val id: String = "libretranslate"
    override val displayName: String = "LibreTranslate"
    override val recommendedBatchSize: Int = 8

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> {
        val server = baseUrl.trim().trimEnd('/')
        if (server.isBlank()) {
            throw TranslationException("Chưa nhập địa chỉ máy chủ LibreTranslate")
        }
        val payload = buildJsonObject {
            put("q", buildJsonArray { texts.forEach { add(JsonPrimitive(it)) } })
            put("source", libreCode(FreeCodes.source(sourceLanguage, texts)))
            put("target", libreCode(FreeCodes.target(target)))
            put("format", "text")
        }
        val body = HttpJson.post("$server/translate", payload.toString())
        val answer = body["translatedText"]
            ?: throw TranslationException("LibreTranslate không trả về bản dịch")
        val values = when (answer) {
            is JsonArray -> answer.map { it.jsonPrimitive.content }
            is JsonPrimitive -> listOf(answer.content)
            else -> emptyList()
        }
        if (values.size != texts.size) {
            throw TranslationException("LibreTranslate trả về ${values.size}/${texts.size} dòng")
        }
        return values
    }

    /** LibreTranslate names Chinese by script, not by variant. */
    private fun libreCode(code: String): String = if (code.startsWith("zh")) "zh" else code
}

/** The keyless backends the app ships, in the order the automatic mode tries them. */
object FreeProviders {

    /** Every source, for the settings screen's own check. */
    fun all(libreTranslateUrl: String): List<TranslationProvider> = buildList {
        add(GoogleFreeTranslationProvider())
        add(MyMemoryTranslationProvider())
        if (libreTranslateUrl.isNotBlank()) add(LibreTranslateProvider(libreTranslateUrl))
    }

    /**
     * One instance per LibreTranslate address, kept for the whole session: the chain remembers
     * which sources are resting, and a fresh chain per page would forget that and hammer them.
     */
    private val chains = HashMap<String, TranslationProvider>()

    fun chain(libreTranslateUrl: String): TranslationProvider {
        val key = libreTranslateUrl.trim()
        return synchronized(chains) {
            chains.getOrPut(key) { FallbackTranslationProvider(all(key)) }
        }
    }
}

/** Language codes the free endpoints understand, and the script sniffing automatic mode needs. */
internal object FreeCodes {

    fun target(language: TranslationLanguage): String = when (language) {
        TranslationLanguage.CHINESE_SIMPLIFIED -> "zh-CN"
        TranslationLanguage.CHINESE_TRADITIONAL -> "zh-TW"
        else -> language.code
    }

    fun source(script: SourceScript, texts: List<String>): String = when (script) {
        SourceScript.KOREAN -> "ko"
        SourceScript.JAPANESE -> "ja"
        SourceScript.CHINESE -> "zh-CN"
        SourceScript.LATIN -> "en"
        SourceScript.AUTO -> sniff(texts)
    }

    /** Automatic mode reads the script off the page: a Latin page sent as Korean comes back garbled. */
    private fun sniff(texts: List<String>): String {
        val sample = texts.joinToString(" ")
        return when {
            sample.any { it in '\uAC00'..'\uD7A3' || it in '\u1100'..'\u11FF' } -> "ko"
            sample.any { it in '\u3040'..'\u30FF' } -> "ja"
            sample.any { it in '\u4E00'..'\u9FFF' } -> "zh-CN"
            else -> "en"
        }
    }
}

private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

/** Splits a line so no piece goes over [maxBytes] of UTF-8, the free endpoint's query limit. */
internal fun splitToFit(text: String, maxBytes: Int): List<String> {
    if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return listOf(text)
    val pieces = ArrayList<String>()
    val current = StringBuilder()
    var bytes = 0
    for (word in text.split(' ')) {
        val wordBytes = word.toByteArray(Charsets.UTF_8).size
        if (current.isNotEmpty() && bytes + 1 + wordBytes > maxBytes) {
            pieces += current.toString()
            current.clear()
            bytes = 0
        }
        if (wordBytes > maxBytes) {
            if (current.isNotEmpty()) {
                pieces += current.toString()
                current.clear()
                bytes = 0
            }
            pieces += splitWord(word, maxBytes)
            continue
        }
        if (current.isNotEmpty()) {
            current.append(' ')
            bytes++
        }
        current.append(word)
        bytes += wordBytes
    }
    if (current.isNotEmpty()) pieces += current.toString()
    return pieces.ifEmpty { listOf(text) }
}

private fun splitWord(word: String, maxBytes: Int): List<String> {
    val pieces = ArrayList<String>()
    val current = StringBuilder()
    var bytes = 0
    for (char in word) {
        val size = char.toString().toByteArray(Charsets.UTF_8).size
        if (current.isNotEmpty() && bytes + size > maxBytes) {
            pieces += current.toString()
            current.clear()
            bytes = 0
        }
        current.append(char)
        bytes += size
    }
    if (current.isNotEmpty()) pieces += current.toString()
    return pieces
}

/**
 * Vietnamese comes back decomposed from the free endpoints ("Chào"), which draws as separate
 * marks and never matches the composed text the rest of the app compares against.
 */
internal fun normalizeText(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)

internal fun unescapeHtml(value: String): String = value
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
