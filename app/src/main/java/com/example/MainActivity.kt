package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.assistant.EXTRA_TRIGGERED_AS_ASSISTANT
import com.example.cloud.JarvisCloudRepository
import com.example.cloud.authStateFlow
import com.example.data.AppThemeMode
import com.example.data.JarvisSettings
import com.example.ui.OpenJarvisApp
import com.example.ui.OpenJarvisViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.JarvisThemeBackdrop
import com.example.ui.theme.OpenJarvisTheme
import com.example.voice.JarvisRuntimeCoordinator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val _assistantLaunchSignal = MutableStateFlow(0)
    private val assistantLaunchSignal = _assistantLaunchSignal.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Check if launched via Android Default Digital Assistant gesture / ACTION_ASSIST
        handleAssistantLaunchIntent(intent)

        // STEP 3: Enable Edge-to-Edge display
        enableEdgeToEdge()

        // STEP 4: Render auth-gated Compose UI
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
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = androidx.compose.ui.platform.LocalContext.current
    val previewSettings by JarvisRuntimeCoordinator.getInstance(context.applicationContext)
        .settingsDataStore.settingsFlow
        .collectAsStateWithLifecycle(initialValue = JarvisSettings(themeMode = AppThemeMode.TRON))

    val user = currentUser
    if (user == null) {
        // Unauthenticated session: render Google Sign-In screen only (no Firestore listeners/ViewModels instantiated)
        OpenJarvisTheme(
            themeMode = previewSettings.themeMode,
            customAccentArgb = previewSettings.customAccentColor
        ) {
            JarvisThemeBackdrop(themeMode = previewSettings.themeMode) {
                AuthScreen(onAuthSuccess = {})
            }
        }
    } else {
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
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(databaseId)
                        OpenJarvisViewModel(
                            application = app,
                            currentUserId = uid,
                            cloudRepository = JarvisCloudRepository(firestore)
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
}
