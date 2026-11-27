package com.manhwatranslate.translate

import android.os.Build
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.translate.BatchFormatter
import com.manhwatranslate.core.translate.TranslationProvider
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a quantized GGUF model directly on the device through llama.cpp, so a page is translated
 * without any network or API key.
 *
 * The model is "uncensored" in the sense that no refusal / safety filter is layered on top of
 * the prompt: the instruction only asks for a natural translation and never tells the model to
 * decline a topic, so the model's own alignment (a tuned uncensored checkpoint, e.g. a Dolphin
 * or WizardLM variant) is the only thing that shapes the answer.
 */
class LocalLlmTranslationProvider(
    private val modelPath: String,
) : TranslationProvider {

    override val id: String = "local-llm"
    override val displayName: String = "LLM trên máy (GGUF)"
    override val recommendedBatchSize: Int = 8

    private val model: LlamaModel by lazy { loadModel() }

    private fun loadModel(): LlamaModel {
        require(modelPath.isNotBlank()) { "Chưa chọn mô hình GGUF. Vào Cài đặt → Bộ dịch để nạp mô hình." }
        require(Build.SUPPORTED_ABIS.any { it.startsWith("arm64") }) {
            "LLM trên máy chỉ hỗ trợ thiết bị ARM64 (arm64-v8a). Thiết bị của bạn dùng ${Build.SUPPORTED_ABIS.firstOrNull() ?: "không rõ"}."
        }
        val params = ModelParameters()
            .setModel(modelPath)
            .setCtxSize(4096)
            .setThreads(4)
            .setThreadsBatch(2)
            .setGpuLayers(0) // CPU-only keeps the app small and avoids driver problems on phones.
        return LlamaModel(params)
    }

    override suspend fun translate(
        texts: List<String>,
        sourceLanguage: SourceScript,
        target: TranslationLanguage,
    ): List<String> = withContext(Dispatchers.Default) {
        val prompt = systemPrompt(target) + "\n\n" + BatchFormatter.format(texts) + "\n\n"
        val params = InferenceParameters(prompt)
            .setTemperature(0.2f)
            .setTopP(0.9f)
            .setNPredict(1024)
            .setStopStrings("</s>", "\n\n[[", "[[")
        val raw = model.complete(params)
        parse(raw, texts.size)
    }

    /**
     * No refusal instruction: the model is told to translate whatever the dialogue says, keeping
     * the tone, without being asked to refuse any topic. This is the "uncensored" contract — the
     * app only provides a task, never a filter.
     */
    private fun systemPrompt(target: TranslationLanguage): String = """
        Bạn là dịch giả truyện tranh chuyên nghiệp.
        Nhiệm vụ: dịch mỗi dòng được đánh số [[1]], [[2]], ... sang ${target.label}.
        Quy tắc:
        - Giữ nguyên số thứ tự [[n]] ở đầu mỗi dòng, đủ số dòng và đúng thứ tự.
        - Dịch tự nhiên, ngắn gọn, đúng giọng điệu lời thoại của nhân vật.
        - Không thêm chú thích, không giải thích, không giữ nguyên văn bản gốc.
        - Chỉ xuất ra các dòng đã dịch, không thêm lời dẫn.
    """.trimIndent()

    private fun parse(raw: String, expected: Int): List<String> {
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
        if (lines.size >= expected) return lines.take(expected)
        throw TranslationException("Mô hình trả về ${lines.size}/$expected dòng đã dịch")
    }

    fun close() {
        runCatching { model.close() }
    }
}
