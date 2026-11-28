package com.manhwatranslate.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import com.manhwatranslate.translate.FreeProviders
import com.manhwatranslate.translate.GeminiTranslationProvider
import com.manhwatranslate.translate.GoogleCloudTranslationProvider
import com.manhwatranslate.translate.GoogleFreeTranslationProvider
import com.manhwatranslate.translate.LibreTranslateProvider
import com.manhwatranslate.translate.LocalLlmTranslationProvider
import com.manhwatranslate.translate.MlKitTranslationProvider
import com.manhwatranslate.translate.ModelDownloader
import com.manhwatranslate.translate.ModelDownloadManager
import com.manhwatranslate.translate.MyMemoryTranslationProvider
import com.manhwatranslate.translate.OpenAiTranslationProvider
import com.manhwatranslate.core.model.OcrProviderType
import com.manhwatranslate.core.pipeline.TileRecognizer
import com.manhwatranslate.vision.MlKitTextRecognizer
import com.manhwatranslate.vision.OnnxTextDetector
import com.manhwatranslate.web.AdBlocker
import com.manhwatranslate.vision.PaddleOcrRecognizer

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** App-wide scope so a model download keeps going if the user leaves the settings screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val modelDownloader = ModelDownloader(appContext, ModelDownloader.client())

    val settingsRepository = SettingsRepository(appContext)

    /** One download for the whole app, so leaving the settings screen does not lose it. */
    val modelDownloadManager = ModelDownloadManager(
        scope = appScope,
        download = { url, onProgress -> modelDownloader.download(url, onProgress) },
        persist = { model ->
            settingsRepository.update {
                it.copy(localLlmModelPath = model.path, localLlmModelName = model.name)
            }
        },
        discard = { url -> modelDownloader.discardPartial(url) },
    )
    val secretsStore = SecretsStore(appContext)
    val imageStore = ImageStore(appContext)
    val projectStore = ProjectStore(appContext, imageStore)
    val fontStore = FontStore(appContext)
    val measurer = AndroidTextMeasurer().apply { setCustomTypeface(fontStore.loadTypeface()) }
    val compositor = CanvasPageCompositor(measurer)
    val adBlocker = AdBlocker.fromAssets(appContext)
    val mlKitRecognizer = MlKitTextRecognizer()

    /** Loaded on first use, so a reader that never enables it pays nothing for the model. */
    private val textDetector by lazy { OnnxTextDetector(appContext) }

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
        backupRecognizer = mlKitRecognizer,
        textDetector = textDetector,
        providerResolver = { settings -> providerFor(settings) },
        backupsResolver = { settings -> backupsFor(settings) },
    )

    fun providerFor(settings: AppSettings): TranslationProvider = when (settings.provider) {
        TranslationProviderType.FREE_AUTO -> FreeProviders.chain(settings.libreTranslateUrl)
        TranslationProviderType.ON_DEVICE -> onDeviceTranslator
        TranslationProviderType.LOCAL_LLM -> LocalLlmTranslationProvider(
            modelPath = settings.localLlmModelPath,
        )
        TranslationProviderType.GOOGLE_FREE -> GoogleFreeTranslationProvider()
        TranslationProviderType.MYMEMORY -> MyMemoryTranslationProvider()
        TranslationProviderType.LIBRE_TRANSLATE -> LibreTranslateProvider(settings.libreTranslateUrl)
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

    /**
     * What picks up a batch the chosen backend refused: the keyless sources first, because they
     * read better than the on-device model, and the on-device model last because it always
     * answers, offline included. Automatic mode already runs the free sources, so only the
     * on-device model is left behind it.
     */
    private fun backupsFor(settings: AppSettings): List<TranslationProvider> = when {
        settings.provider == TranslationProviderType.ON_DEVICE -> emptyList()
        settings.provider == TranslationProviderType.LOCAL_LLM -> emptyList()
        settings.provider == TranslationProviderType.FREE_AUTO -> listOf(onDeviceTranslator)
        else -> listOf(FreeProviders.chain(settings.libreTranslateUrl), onDeviceTranslator)
    }

    private fun requireKey(provider: TranslationProviderType): String =
        secretsStore.apiKey(provider)
}
