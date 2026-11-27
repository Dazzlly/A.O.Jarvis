package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.assistant.EXTRA_TRIGGERED_AS_ASSISTANT
import com.example.cloud.JarvisCloudRepository
import com.example.cloud.authStateFlow
import com.example.cloud.getFirebaseAuthOrNull
import com.example.data.AppThemeMode
import com.example.data.JarvisSettings
import com.example.ui.OpenJarvisApp
import com.example.ui.OpenJarvisViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.JarvisThemeBackdrop
import com.example.ui.theme.OpenJarvisTheme
import com.example.voice.JarvisRuntimeCoordinator
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val MAIN_TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val _assistantLaunchSignal = MutableStateFlow(0)
    private val assistantLaunchSignal = _assistantLaunchSignal.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        runCatching { configureAppCheck(this, intent) }
            .onFailure { Log.w(MAIN_TAG, "AppCheck init skipped: ${it.message}") }

        // STEP 2: Check if launched via Android Default Digital Assistant gesture / ACTION_ASSIST
        handleAssistantLaunchIntent(intent)

        // STEP 3: Enable Edge-to-Edge display
        runCatching { enableEdgeToEdge() }

        // STEP 4: Render auth-gated (with optional local Skip Step & safe fallback) Compose UI
        setContent {
            val assistSignal by assistantLaunchSignal.collectAsState()
            AppAuthRoot(assistantLaunchSignal = assistSignal)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAssistantLaunchIntent(intent)
    }

    private fun handleAssistantLaunchIntent(launchIntent: Intent?) {
        val action = launchIntent?.action
        val triggeredByAssistant = launchIntent?.getBooleanExtra(EXTRA_TRIGGERED_AS_ASSISTANT, false) == true ||
            action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_VOICE_COMMAND ||
            action == Intent.ACTION_SEARCH_LONG_PRESS

        if (triggeredByAssistant) {
            _assistantLaunchSignal.value = _assistantLaunchSignal.value + 1
            launchIntent?.removeExtra(EXTRA_TRIGGERED_AS_ASSISTANT)
        }
    }
}

@Composable
private fun AppAuthRoot(assistantLaunchSignal: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) { getFirebaseAuthOrNull(context) }
    val authFlow = remember(auth) { auth?.authStateFlow() ?: flowOf(null) }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth?.currentUser)

    val settingsDataStore = remember(context) {
        JarvisRuntimeCoordinator.getInstance(context.applicationContext).settingsDataStore
    }
    val loadedSettings by settingsDataStore.settingsFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val previewSettings = loadedSettings ?: JarvisSettings(themeMode = AppThemeMode.TRON)

    val user = currentUser
    when {
        user != null -> {
            val uid = user.uid
            // Key composition & ViewModel by UID so sign-out or account switches cleanly dispose Firestore listeners
            key(uid) {
                val viewModel: OpenJarvisViewModel = viewModel(
                    key = uid,
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val cloudRepo = runCatching {
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val firestore = FirebaseFirestore.getInstance(databaseId)
                                JarvisCloudRepository(firestore, auth!!)
                            }.onFailure { err ->
                                Log.w(MAIN_TAG, "Firestore unavailable, running with local persistence: ${err.message}")
                            }.getOrNull()
                            OpenJarvisViewModel(
                                application = app,
                                currentUserId = if (cloudRepo != null) uid else null,
                                cloudRepository = cloudRepo
                            )
                        }
                    }
                )
                OpenJarvisApp(
                    viewModel = viewModel,
                    assistantLaunchSignal = assistantLaunchSignal
                )
            }
        }

        // If Firebase isn't initialized in this APK build (e.g. CI build without google-services.json),
        // seamlessly launch in local/offline mode instead of crashing on startup.
        auth == null -> {
            key("guest_local_mode_no_firebase") {
                val viewModel: OpenJarvisViewModel = viewModel(
                    key = "guest_local_mode_no_firebase",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            OpenJarvisViewModel(
                                application = app,
                                currentUserId = null,
                                cloudRepository = null
                            )
                        }
                    }
                )
                OpenJarvisApp(
                    viewModel = viewModel,
                    assistantLaunchSignal = assistantLaunchSignal
                )
            }
        }

        loadedSettings == null -> {
            // Brief splash while DataStore resolves whether the user previously skipped sign-in
            OpenJarvisTheme(
                themeMode = previewSettings.themeMode,
                customAccentArgb = previewSettings.customAccentColor
            ) {
                JarvisThemeBackdrop(themeMode = previewSettings.themeMode) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        loadedSettings!!.skipAccountSignIn -> {
            // Local/Guest mode: zero Firestore listeners or cloud calls; local Room, voice & offline tools work normally
            key("guest_local_mode") {
                val viewModel: OpenJarvisViewModel = viewModel(
                    key = "guest_local_mode",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            OpenJarvisViewModel(
                                application = app,
                                currentUserId = null,
                                cloudRepository = null
                            )
                        }
                    }
                )
                OpenJarvisApp(
                    viewModel = viewModel,
                    assistantLaunchSignal = assistantLaunchSignal
                )
            }
        }

        else -> {
            // Unauthenticated first-launch screen: Google Sign-In + Skip Step (Continue without account)
            OpenJarvisTheme(
                themeMode = previewSettings.themeMode,
                customAccentArgb = previewSettings.customAccentColor
            ) {
                JarvisThemeBackdrop(themeMode = previewSettings.themeMode) {
                    AuthScreen(
                        onAuthSuccess = {
                            scope.launch { settingsDataStore.updateSkipAccountSignIn(false) }
                        },
                        onSkipSignIn = {
                            scope.launch { settingsDataStore.updateSkipAccountSignIn(true) }
                        }
                    )
                }
            }
        }
    }
}
