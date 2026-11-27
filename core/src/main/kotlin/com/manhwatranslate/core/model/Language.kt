package com.manhwatranslate.core.model

import kotlinx.serialization.Serializable

/** Writing system the detector should look for on the page. */
@Serializable
enum class SourceScript(val bcp47: String, val label: String, val shortLabel: String) {
    AUTO("und", "Tự động nhận diện", "Tự động"),
    LATIN("en", "Latinh (Anh, Tây Ban Nha…)", "Latinh"),
    KOREAN("ko", "Tiếng Hàn", "Hàn"),
    CHINESE("zh", "Tiếng Trung", "Trung"),
    JAPANESE("ja", "Tiếng Nhật", "Nhật"),
    ;

    val isAuto: Boolean get() = this == AUTO
}

/** Languages the app can translate into (and, for cloud providers, from). */
@Serializable
enum class TranslationLanguage(
    val code: String,
    val label: String,
    val englishLabel: String,
    val mlkitSupported: Boolean,
) {
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese", true),
    ENGLISH("en", "Tiếng Anh", "English", true),
    KOREAN("ko", "Tiếng Hàn", "Korean", true),
    JAPANESE("ja", "Tiếng Nhật", "Japanese", true),
    CHINESE_SIMPLIFIED("zh-Hans", "Tiếng Trung (giản thể)", "Chinese (Simplified)", false),
    CHINESE_TRADITIONAL("zh-Hant", "Tiếng Trung (phồn thể)", "Chinese (Traditional)", false),
    THAI("th", "Tiếng Thái", "Thai", true),
    INDONESIAN("id", "Tiếng Indonesia", "Indonesian", true),
    MALAY("ms", "Tiếng Mã Lai", "Malay", true),
    FILIPINO("tl", "Tiếng Philippines", "Filipino", false),
    SPANISH("es", "Tiếng Tây Ban Nha", "Spanish", true),
    FRENCH("fr", "Tiếng Pháp", "French", true),
    GERMAN("de", "Tiếng Đức", "German", true),
    PORTUGUESE("pt", "Tiếng Bồ Đào Nha", "Portuguese", true),
    RUSSIAN("ru", "Tiếng Nga", "Russian", true),
    ARABIC("ar", "Tiếng Ả Rập", "Arabic", true),
    HINDI("hi", "Tiếng Hindi", "Hindi", true),
    TURKISH("tr", "Tiếng Thổ Nhĩ Kỳ", "Turkish", true),
    ITALIAN("it", "Tiếng Ý", "Italian", true),
    ;

    /** Code the on-device ML Kit model expects (it ignores script subtags). */
    val mlkitCode: String get() = when (this) {
        CHINESE_SIMPLIFIED, CHINESE_TRADITIONAL -> "zh"
        FILIPINO -> "tl"
        else -> code
    }

    companion object {
        val supportedByMlKit: List<TranslationLanguage> = entries.filter { it.mlkitSupported }
        fun fromCode(code: String?): TranslationLanguage? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/** Where translations come from. */
@Serializable
enum class TranslationProviderType(
    val label: String,
    val requiresApiKey: Boolean,
    val hint: String,
) {
    ON_DEVICE(
        label = "Trên máy (ML Kit)",
        requiresApiKey = false,
        hint = "Miễn phí, chạy offline sau khi tải gói ngôn ngữ (~30 MB). Chất lượng khá, phù hợp đọc nhanh.",
    ),
    GOOGLE_CLOUD(
        label = "Google Cloud Translation",
        requiresApiKey = true,
        hint = "Cần API key Google Cloud Translation (v2). Chất lượng tốt, tính phí theo ký tự.",
    ),
    DEEPL(
        label = "DeepL",
        requiresApiKey = true,
        hint = "Cần API key DeepL. Chất lượng cao cho châu Âu, hỗ trợ tiếng Việt ở gói Pro.",
    ),
    OPENAI_COMPATIBLE(
        label = "OpenAI / tương thích",
        requiresApiKey = true,
        hint = "Dùng được với OpenAI, DeepSeek, Groq, OpenRouter. Chất lượng cao nhất vì dịch theo ngữ cảnh cả trang.",
    ),
    GEMINI(
        label = "Google Gemini",
        requiresApiKey = true,
        hint = "Cần API key Google AI Studio. Chất lượng cao, miễn phí trong hạn mức.",
    ),
}
