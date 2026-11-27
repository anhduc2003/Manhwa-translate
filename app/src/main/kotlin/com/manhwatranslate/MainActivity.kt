package com.manhwatranslate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.manhwatranslate.ui.theme.ManhwaTranslateTheme

class MainActivity : ComponentActivity() {

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
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

    NavHost(navController = navController, startDestination = "home") {
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

        composable("settings") {
            val factory = remember(container) {
                viewModelFactory { initializer { SettingsViewModel(application, container) } }
            }
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
