package com.manhwatranslate.data

import com.manhwatranslate.core.erase.EraseMode
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.scan.SfxSensitivity
import com.manhwatranslate.core.model.SourceScript
import com.manhwatranslate.core.model.TranslationLanguage
import com.manhwatranslate.core.model.OcrProviderType
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.core.pipeline.PipelineOptions
import com.manhwatranslate.core.render.RenderStyle
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val sourceScript: SourceScript = SourceScript.AUTO,
    val target: TranslationLanguage = TranslationLanguage.VIETNAMESE,
    val provider: TranslationProviderType = TranslationProviderType.ON_DEVICE,
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiModel: String = "gpt-4o-mini",
    val geminiModel: String = "gemini-2.0-flash",
    val deeplPro: Boolean = false,
    val eraseMode: EraseMode = EraseMode.AUTO,
    val fontMode: FontMode = FontMode.AUTO,
    val fontWeight: FontWeightLevel? = null,
    val autoSizeFromOriginal: Boolean = true,
    val customFontName: String = "",
    val sfxSensitivity: SfxSensitivity = SfxSensitivity.LOW,
    val matchBackgroundColor: Boolean = true,
    val autoContrast: Boolean = true,
    val textColor: Int = 0xFF101010.toInt(),
    val textSizeScale: Float = 1f,
    val minTextSizePx: Float = 11f,
    val maxTextSizePx: Float = 96f,
    val strokeWidthFactor: Float = 0.09f,
    val lineSpacing: Float = 1.08f,
    val maxLines: Int = 8,
    val boxExpansion: Float = 1.15f,
    val maxPagePixels: Int = 24_000_000,
    val ocrProvider: OcrProviderType = OcrProviderType.ML_KIT,
    val paddleOcrUrl: String = "http://192.168.1.10:8866",
    val paddleOcrToken: String = "",
    val keepOriginalOnFailure: Boolean = true,
) {
    fun toRenderStyle(): RenderStyle = RenderStyle(
        fontMode = fontMode,
        fontWeightOverride = fontWeight,
        autoSizeFromOriginal = autoSizeFromOriginal,
        autoContrast = autoContrast,
        textColor = textColor,
        strokeColor = if (autoContrast) 0xFFFFFFFF.toInt() else 0xFF0A0A0A.toInt(),
        strokeWidthFactor = strokeWidthFactor,
        shadowEnabled = true,
        minTextSizePx = minTextSizePx,
        maxTextSizePx = maxTextSizePx,
        lineSpacing = lineSpacing,
        maxLines = maxLines,
        textSizeScale = textSizeScale,
    )

    fun toPipelineOptions(): PipelineOptions = PipelineOptions(
        script = sourceScript,
        target = target,
        eraseMode = eraseMode,
        render = toRenderStyle(),
        minLetterCount = 1,
        keepOriginalWhenFailed = keepOriginalOnFailure,
        boxExpansion = boxExpansion,
        sfxSensitivity = sfxSensitivity,
        matchBackgroundColor = matchBackgroundColor,
    )
}
