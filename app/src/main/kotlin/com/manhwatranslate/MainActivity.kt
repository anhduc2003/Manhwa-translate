package com.manhwatranslate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manhwatranslate.di.AppContainer
import com.manhwatranslate.ui.EditorScreen
import com.manhwatranslate.ui.EditorViewModel
import com.manhwatranslate.ui.HomeScreen
import com.manhwatranslate.ui.HomeViewModel
import com.manhwatranslate.ui.SettingsScreen
import com.manhwatranslate.ui.SettingsViewModel
import com.manhwatranslate.ui.SplashScreen
import com.manhwatranslate.ui.WebReaderScreen
import com.manhwatranslate.ui.WebReaderViewModel
import com.manhwatranslate.ui.theme.ManhwaTranslateTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sharedUris = extractUris(intent)
        val container = (application as ManhwaTranslateApp).container
        setContent {
            ManhwaTranslateTheme {
                AppRoot(
                    container = container,
                    incomingUris = sharedUris,
                    onIncomingConsumed = { sharedUris = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uris = extractUris(intent)
        if (uris.isNotEmpty()) sharedUris = uris
    }

    private fun extractUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.filterNotNull().orEmpty()
            else -> emptyList()
        }
    }
}

@Composable
private fun AppRoot(
    container: AppContainer,
    incomingUris: List<Uri>,
    onIncomingConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext
        as android.app.Application
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1_600)
        showSplash = false
    }
    BackHandler(enabled = showSplash) { }

    Box {
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 14 }
            },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutHorizontally(tween(280)) { it / 14 }
            },
        ) {
            composable("home") {
                val factory = remember(container) {
                    viewModelFactory { initializer { HomeViewModel(application, container) } }
                }
                val homeViewModel: HomeViewModel = viewModel(factory = factory)
                LaunchedEffect(incomingUris) {
                    if (incomingUris.isNotEmpty()) {
                        homeViewModel.translate(incomingUris)
                        onIncomingConsumed()
                    }
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenPage = { pageId -> navController.navigate("editor/$pageId") },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenWebReader = { navController.navigate("web") },
                )
            }

            composable(
                route = "editor/{pageId}",
                arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
            ) { entry ->
                val pageId = entry.arguments?.getString("pageId").orEmpty()
                val factory = remember(pageId) {
                    viewModelFactory { initializer { EditorViewModel(application, container, pageId) } }
                }
                val editorViewModel: EditorViewModel = viewModel(key = pageId, factory = factory)
                EditorScreen(
                    viewModel = editorViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPage = { nextId ->
                        navController.navigate("editor/$nextId") {
                            popUpTo("editor/{pageId}") { inclusive = true }
                        }
                    },
                )
            }

            composable("web") {
                val factory = remember(container) {
                    viewModelFactory { initializer { WebReaderViewModel(application, container) } }
                }
                val webViewModel: WebReaderViewModel = viewModel(factory = factory)
                WebReaderScreen(
                    viewModel = webViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("settings") {
                val factory = remember(container) {
                    viewModelFactory { initializer { SettingsViewModel(application, container) } }
                }
                val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                val fontPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let(settingsViewModel::importFont) }
                val modelPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let(settingsViewModel::importLlmModel) }
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() },
                    onPickFont = {
                        fontPicker.launch(
                            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"),
                        )
                    },
                    onPickLlmModel = {
                        modelPicker.launch(
                            arrayOf("application/octet-stream", "application/x-gguf", "*/*"),
                        )
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(350)),
        ) {
            SplashScreen()
        }
    }
}
