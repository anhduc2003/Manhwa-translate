package com.manhwatranslate.di

import android.content.Context
import com.manhwatranslate.core.model.TranslationProviderType
import com.manhwatranslate.core.translate.TranslationProvider
import com.manhwatranslate.data.AppSettings
import com.manhwatranslate.data.FontStore
import com.manhwatranslate.data.ImageStore
import com.manhwatranslate.data.ProjectStore
import com.manhwatranslate.data.SecretsStore
import com.manhwatranslate.data.SettingsRepository
import com.manhwatranslate.pipeline.TranslationEngine
import com.manhwatranslate.render.AndroidTextMeasurer
import com.manhwatranslate.render.CanvasPageCompositor
import com.manhwatranslate.translate.DeepLTranslationProvider
import com.manhwatranslate.translate.GeminiTranslationProvider
import com.manhwatranslate.translate.GoogleCloudTranslationProvider
import com.manhwatranslate.translate.MlKitTranslationProvider
import com.manhwatranslate.translate.OpenAiTranslationProvider
import com.manhwatranslate.core.model.OcrProviderType
import com.manhwatranslate.core.pipeline.TileRecognizer
import com.manhwatranslate.vision.MlKitTextRecognizer
import com.manhwatranslate.vision.PaddleOcrRecognizer

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val secretsStore = SecretsStore(appContext)
    val imageStore = ImageStore(appContext)
    val projectStore = ProjectStore(appContext, imageStore)
    val fontStore = FontStore(appContext)
    val measurer = AndroidTextMeasurer().apply { setCustomTypeface(fontStore.loadTypeface()) }
    val compositor = CanvasPageCompositor(measurer)
    val mlKitRecognizer = MlKitTextRecognizer()

    fun recognizerFor(settings: AppSettings): TileRecognizer = when (settings.ocrProvider) {
        OcrProviderType.ML_KIT -> mlKitRecognizer
        OcrProviderType.PADDLE_OCR -> PaddleOcrRecognizer(
            serverUrl = settings.paddleOcrUrl,
            token = settings.paddleOcrToken,
        )
    }
    val onDeviceTranslator = MlKitTranslationProvider()

    val engine = TranslationEngine(
        recognizerResolver = { settings -> recognizerFor(settings) },
        measurer = measurer,
        compositor = compositor,
        providerResolver = { settings -> providerFor(settings) },
    )

    fun providerFor(settings: AppSettings): TranslationProvider = when (settings.provider) {
        TranslationProviderType.ON_DEVICE -> onDeviceTranslator
        TranslationProviderType.GOOGLE_CLOUD -> GoogleCloudTranslationProvider(
            requireKey(settings.provider),
        )
        TranslationProviderType.DEEPL -> DeepLTranslationProvider(
            apiKey = requireKey(settings.provider),
            pro = settings.deeplPro,
        )
        TranslationProviderType.OPENAI_COMPATIBLE -> OpenAiTranslationProvider(
            baseUrl = settings.openAiBaseUrl,
            apiKey = requireKey(settings.provider),
            model = settings.openAiModel,
        )
        TranslationProviderType.GEMINI -> GeminiTranslationProvider(
            apiKey = requireKey(settings.provider),
            model = settings.geminiModel,
        )
    }

    private fun requireKey(provider: TranslationProviderType): String =
        secretsStore.apiKey(provider)
}
