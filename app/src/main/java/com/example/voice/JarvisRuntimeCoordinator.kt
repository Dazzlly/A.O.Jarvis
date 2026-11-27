package com.example.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.core.AppIntegrationManager
import com.example.core.AppSelfController
import com.example.core.AppUpdateManager
import com.example.core.DeviceControlForegroundService
import com.example.core.DeviceControlServiceState
import com.example.core.DeviceSettingsController
import com.example.core.DeviceSettingsSnapshot
import com.example.core.SecureTokenStore
import com.example.core.SelfEvolutionManager
import com.example.data.JarvisDatabase
import com.example.data.SettingsDataStore
import com.example.engine.GeminiInferenceEngine
import com.example.engine.GeminiLiveManager
import com.example.engine.NetworkEngineManager
import com.example.engine.OfflineCommandEngine
import com.example.engine.OllamaInferenceEngine
import com.example.engine.OpenJarvisOrchestrator
import com.example.state.ConversationStateManager
import com.example.vision.ScreenVisionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceRuntimeState(
    val isBackgroundRunning: Boolean = false,
    val isBackgroundPaused: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isProcessingVoice: Boolean = false,
    val partialTranscript: String = "",
    val rmsDb: Float = 0f,
    val statusText: String = "Pronto para escutar",
    val awaitingFollowUpCommand: Boolean = false,
    val errorMessage: String? = null
)

class JarvisRuntimeCoordinator private constructor(appContext: Context) {

    private val _voiceState = MutableStateFlow(VoiceRuntimeState())
    val voiceState: StateFlow<VoiceRuntimeState> = _voiceState.asStateFlow()

    private val _lanServerState = MutableStateFlow(LanServerState())
    val lanServerState: StateFlow<LanServerState> = _lanServerState.asStateFlow()

    val database: JarvisDatabase = JarvisDatabase.getInstance(appContext)
    val settingsDataStore: SettingsDataStore = SettingsDataStore(appContext)
    val speechController: NativeSpeechController = NativeSpeechController(appContext)
    val integrationManager: AppIntegrationManager = AppIntegrationManager(appContext)
    val appSelfController: AppSelfController = AppSelfController(appContext)
    val appUpdateManager: AppUpdateManager = AppUpdateManager(appContext)
    val secureTokenStore: SecureTokenStore = SecureTokenStore(appContext)
    val deviceSettingsController: DeviceSettingsController = DeviceSettingsController(appContext)
    private val _deviceControlServiceState = MutableStateFlow(
        DeviceControlServiceState(
            isRunning = false,
            snapshot = deviceSettingsController.readSnapshot(),
            lastUpdatedAt = System.currentTimeMillis()
        )
    )
    val deviceControlServiceState: StateFlow<DeviceControlServiceState> = _deviceControlServiceState.asStateFlow()
    val screenVisionManager: ScreenVisionManager = ScreenVisionManager()
    val selfEvolutionManager: SelfEvolutionManager = SelfEvolutionManager(
        tokenStore = secureTokenStore,
        appUpdateManager = appUpdateManager
    )

    val geminiEngine: GeminiInferenceEngine = GeminiInferenceEngine()
    val ollamaEngine: OllamaInferenceEngine = OllamaInferenceEngine()
    val networkEngineManager: NetworkEngineManager = NetworkEngineManager(
        context = appContext,
        geminiEngine = geminiEngine,
        ollamaEngine = ollamaEngine
    )
    val offlineEngine: OfflineCommandEngine = OfflineCommandEngine(
        context = appContext,
        memoryDao = database.memoryDao(),
        voiceCommandDao = database.voiceCommandDao(),
        settingsDataStore = settingsDataStore,
        ollamaEngine = ollamaEngine,
        integrationManager = integrationManager,
        appSelfController = appSelfController,
        deviceSettingsController = deviceSettingsController,
        screenVisionManager = screenVisionManager
    )
    val orchestrator: OpenJarvisOrchestrator = OpenJarvisOrchestrator(
        database = database,
        settingsDataStore = settingsDataStore,
        networkEngineManager = networkEngineManager,
        offlineEngine = offlineEngine,
        appSelfController = appSelfController,
        integrationManager = integrationManager,
        deviceSettingsController = deviceSettingsController,
        screenVisionManager = screenVisionManager,
        isBackgroundRunningProvider = { _voiceState.value.isBackgroundRunning }
    )
    val conversationStateManager: ConversationStateManager = ConversationStateManager(
        database = database,
        orchestrator = orchestrator
    )
    val geminiLiveManager: GeminiLiveManager = GeminiLiveManager()

    fun updateVoiceState(transform: (VoiceRuntimeState) -> VoiceRuntimeState) {
        _voiceState.update(transform)
    }

    fun updateLanServerState(state: LanServerState) {
        _lanServerState.value = state
    }

    fun startBackgroundService(context: Context) {
        val intent = Intent(context, JarvisBackgroundService::class.java).apply {
            action = JarvisBackgroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopBackgroundService(context: Context) {
        val intent = Intent(context, JarvisBackgroundService::class.java).apply {
            action = JarvisBackgroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun togglePauseBackgroundService(context: Context) {
        val paused = _voiceState.value.isBackgroundPaused
        val intent = Intent(context, JarvisBackgroundService::class.java).apply {
            action = if (paused) JarvisBackgroundService.ACTION_RESUME else JarvisBackgroundService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun startDeviceControlService(context: Context) {
        val intent = Intent(context, DeviceControlForegroundService::class.java).apply {
            action = DeviceControlForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopDeviceControlService(context: Context) {
        val intent = Intent(context, DeviceControlForegroundService::class.java).apply {
            action = DeviceControlForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun executeDeviceSettingViaService(context: Context, settingSpec: String) {
        val intent = Intent(context, DeviceControlForegroundService::class.java).apply {
            action = DeviceControlForegroundService.ACTION_EXECUTE_SETTING
            putExtra(DeviceControlForegroundService.EXTRA_SETTING_SPEC, settingSpec)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun updateDeviceControlServiceState(
        isRunning: Boolean? = null,
        snapshot: DeviceSettingsSnapshot? = null,
        lastActionMessage: String? = null
    ) {
        _deviceControlServiceState.update { current ->
            current.copy(
                isRunning = isRunning ?: current.isRunning,
                snapshot = snapshot ?: deviceSettingsController.readSnapshot(),
                lastActionMessage = lastActionMessage ?: current.lastActionMessage,
                lastUpdatedAt = System.currentTimeMillis()
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: JarvisRuntimeCoordinator? = null

        fun getInstance(context: Context): JarvisRuntimeCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvisRuntimeCoordinator(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
