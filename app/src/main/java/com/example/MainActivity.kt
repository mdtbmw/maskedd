package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.service.CognitiveOrbService
import com.example.service.TtsPlaybackService
import com.example.ui.components.FloatingProgressShowerWidget
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.LyricReaderScreen
import com.example.ui.screens.PasteDocumentModal
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LyricReadTheme
import com.example.ui.viewmodel.LyricReaderViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LyricReaderViewModel by viewModels()
    private var incomingUriState = mutableStateOf<Uri?>(null)
    private var pendingShortcutActionState = mutableStateOf<String?>(null)
    private lateinit var appUpdateManager: com.example.update.AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            appUpdateManager = com.example.update.AppUpdateManager(applicationContext, lifecycleScope)
            appUpdateManager.checkForUpdates()
        } catch (_: Exception) {}

        // Bind SpeechEngine to Services
        try {
            TtsPlaybackService.speechEngine = viewModel.speechEngine
            CognitiveOrbService.speechEngine = viewModel.speechEngine
        } catch (_: Exception) {}

        handleIncomingIntent(intent)

        val prefs = getSharedPreferences("masked_d_app_prefs", MODE_PRIVATE)
        val showOnboardingState = mutableStateOf(!prefs.getBoolean("has_completed_onboarding", false))

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val updateInfo = if (::appUpdateManager.isInitialized) appUpdateManager.updateState.collectAsState().value else com.example.update.AppUpdateInfo()

            LyricReadTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showOnboardingState.value) {
                            com.example.ui.screens.OnboardingScreen(
                                onFinishOnboarding = {
                                    prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                    showOnboardingState.value = false
                                }
                            )
                        } else {
                            LyricReadApp(
                                viewModel = viewModel,
                                incomingUri = incomingUriState.value,
                                shortcutAction = pendingShortcutActionState.value,
                                onClearIncomingUri = { incomingUriState.value = null },
                                onClearShortcutAction = { pendingShortcutActionState.value = null }
                            )
                        }

                        val userDismissedUpdate = rememberSaveable { mutableStateOf(false) }

                        if (updateInfo.hasUpdate && !userDismissedUpdate.value && !showOnboardingState.value) {
                            com.example.ui.screens.AppUpdateModal(
                                updateInfo = updateInfo,
                                onStartUpdate = { if (::appUpdateManager.isInitialized) appUpdateManager.startApkDownload() },
                                onDismiss = { userDismissedUpdate.value = true }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.unregister()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action

        // Module 3: Universal Document Handler ("Open With")
        if (Intent.ACTION_VIEW == action || Intent.ACTION_SEND == action) {
            val uri: Uri? = if (Intent.ACTION_SEND == action) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            } else {
                intent.data
            }
            if (uri != null) {
                incomingUriState.value = uri
            }
        }

        // Module 4: Quick Action App Shortcuts
        if (action != null && action.startsWith("com.example.action.SHORTCUT_")) {
            pendingShortcutActionState.value = action
        }
    }
}

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object LyricReader : Screen("lyric_reader")
    object Bookmarks : Screen("bookmarks")
    object Settings : Screen("settings")
}

@Composable
fun LyricReadApp(
    viewModel: LyricReaderViewModel,
    incomingUri: Uri? = null,
    shortcutAction: String? = null,
    onClearIncomingUri: () -> Unit = {},
    onClearShortcutAction: () -> Unit = {}
) {
    val navController = rememberNavController()
    var showPasteModal by remember { mutableStateOf(false) }
    var isWidgetDismissed by remember { mutableStateOf(false) }

    val playbackProgress by viewModel.playbackState.collectAsState()
    val activeDocEntity by viewModel.activeDocumentEntity.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Storage Access Framework File Picker Launcher using GetContent()
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileNameFromUri(uri, viewModel.getApplication())
            viewModel.importDocumentFromUri(uri, fileName)
            navController.navigate(Screen.LyricReader.route)
        }
    }

    // Module 3: Ingestion of Universal Document Handler ("Open With")
    androidx.compose.runtime.LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            val fileName = getFileNameFromUri(incomingUri, viewModel.getApplication())
            viewModel.importDocumentFromUri(incomingUri, fileName)
            onClearIncomingUri()
            navController.navigate(Screen.LyricReader.route)
        }
    }

    // Module 4: Handle Quick Action App Shortcuts
    androidx.compose.runtime.LaunchedEffect(shortcutAction, allDocs) {
        if (shortcutAction != null) {
            when (shortcutAction) {
                "com.example.action.SHORTCUT_RESUME_BOOK" -> {
                    val activeOrFirst = activeDocEntity ?: allDocs.firstOrNull()
                    if (activeOrFirst != null) {
                        viewModel.openDocument(activeOrFirst)
                        viewModel.speechEngine.play()
                        navController.navigate(Screen.LyricReader.route)
                    }
                }
                "com.example.action.SHORTCUT_LIBRARY" -> {
                    navController.navigate(Screen.Library.route)
                }
                "com.example.action.SHORTCUT_LOAD_DOC" -> {
                    getContentLauncher.launch("*/*")
                }
            }
            onClearShortcutAction()
        }
    }

    // Update Foreground Notification & Active Document Metadata on state changes
    androidx.compose.runtime.LaunchedEffect(activeDocEntity) {
        if (activeDocEntity != null) {
            TtsPlaybackService.activeDocumentTitle = activeDocEntity!!.title
            TtsPlaybackService.activeAuthor = "Format: ${activeDocEntity!!.format} • ${activeDocEntity!!.wordCount} words"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Library.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenDocument = { doc ->
                        viewModel.openDocument(doc)
                        val serviceIntent = Intent(viewModel.getApplication(), TtsPlaybackService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            viewModel.getApplication<android.app.Application>().startForegroundService(serviceIntent)
                        } else {
                            viewModel.getApplication<android.app.Application>().startService(serviceIntent)
                        }
                        navController.navigate(Screen.LyricReader.route)
                    },
                    onOpenBookmarks = {
                        navController.navigate(Screen.Bookmarks.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onImportFileClicked = {
                        getContentLauncher.launch("*/*")
                    },
                    onPasteTextClicked = {
                        showPasteModal = true
                    }
                )
            }

            composable(Screen.LyricReader.route) {
                LyricReaderScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        navController.popBackStack()
                    },
                    onJumpToBookmark = { bookmark ->
                        val docs = viewModel.allDocuments.value
                        val targetDoc = docs.find { it.id == bookmark.documentId }
                        if (targetDoc != null) {
                            viewModel.openDocument(targetDoc)
                            viewModel.speechEngine.seekToWord(bookmark.wordIndex)
                            navController.navigate(Screen.LyricReader.route)
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        navController.popBackStack()
                    }
                )
            }
        }

        // Module 2: The Cognitive Orb Overlay Widget
        if (activeDocEntity != null && currentRoute != Screen.LyricReader.route && !isWidgetDismissed) {
            FloatingProgressShowerWidget(
                progress = playbackProgress,
                activeDocumentTitle = activeDocEntity?.title ?: "Document",
                onTogglePlayPause = {
                    if (playbackProgress.isPlaying) {
                        viewModel.speechEngine.pause()
                    } else {
                        viewModel.speechEngine.play()
                    }
                },
                onExpandReader = {
                    navController.navigate(Screen.LyricReader.route)
                },
                onDismiss = {
                    isWidgetDismissed = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 20.dp, end = 12.dp)
            )
        }
    }

    if (showPasteModal) {
        PasteDocumentModal(
            onDismiss = { showPasteModal = false },
            onCreateDocument = { title, format, text ->
                viewModel.createDocumentFromText(title, format, text)
                navController.navigate(Screen.LyricReader.route)
            }
        )
    }
}

fun getFileNameFromUri(uri: Uri, context: android.content.Context): String {
    var result = "Document.txt"
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    } else {
        uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) {
                result = path.substring(cut + 1)
            }
        }
    }
    return result
}
