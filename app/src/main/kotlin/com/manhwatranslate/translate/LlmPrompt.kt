package com.manhwatranslate.translate

import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.BatchFormatter
import com.manhwatranslate.core.translate.TranslationProvider

/** The instruction shared by every LLM backend, tuned for comic dialogue. */
internal object LlmPrompt {

    fun system(target: TranslationLanguage): String = """
        Bạn là dịch giả truyện tranh (manhwa, webtoon, manga) chuyên nghiệp.
        Nhiệm vụ: dịch từng dòng được đánh số [[1]], [[2]], ... sang ${target.label}.
        Quy tắc bắt buộc:
        - Giữ nguyên số thứ tự [[n]] ở đầu mỗi dòng, đủ số dòng và đúng thứ tự như đầu vào.
        - Dịch tự nhiên, ngắn gọn, đúng giọng điệu lời thoại của nhân vật.
        - Không thêm chú thích, không giải thích, không giữ nguyên văn bản gốc.
        - Từ tượng thanh được dịch sang từ tượng thanh tương đương trong ${target.label}.
        - Chỉ xuất ra các dòng đã dịch, không thêm lời dẫn.
    """.trimIndent()

    fun user(texts: List<String>): String = BatchFormatter.format(texts)

    fun parse(raw: String, expected: Int): List<String> {
        val cleaned = raw.trim()
        BatchFormatter.parse(cleaned, expected)?.let { return it }

        val lines = cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                line.replace(Regex("""^\[\[\d+]]\s*"""), "")
                    .replace(Regex("""^\d+[.)]\s*"""), "")
                    .replace(Regex("""^[-*]\s*"""), "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
        if (lines.size == expected) return lines
        if (lines.size > expected) return lines.take(expected)
        throw TranslationException("Mô hình trả về ${lines.size}/$expected dòng đã dịch")
    }
}
