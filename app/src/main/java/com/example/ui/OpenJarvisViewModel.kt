package com.example.ui

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppDestination
import com.example.core.AppUiCommand
import com.example.core.AppUpdateState
import com.example.core.GuidedTourState
import com.example.data.ChatMessageEntity
import com.example.data.ConversationSessionEntity
import com.example.data.DEFAULT_SESSION_ID
import com.example.data.JarvisSettings
import com.example.data.MemoryEntity
import com.example.data.RoutingMode
import com.example.data.TraceEntity
import com.example.data.VoiceCommandEntity
import com.example.engine.EngineType
import com.example.engine.InferenceStreamEvent
import com.example.engine.LiveVoiceState
import com.example.engine.NetworkEngineState
import com.example.engine.OllamaHealthStatus
import com.example.state.AssistantPhase
import com.example.state.AssistantState
import com.example.state.CommandSource
import com.example.voice.JarvisRuntimeCoordinator
import com.example.voice.LanServerState
import com.example.voice.TtsStatus
import com.example.voice.VoiceRuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StreamingUiState {
    data object Idle : StreamingUiState
    data class Streaming(
        val text: String,
        val engineType: EngineType,
        val modelName: String
    ) : StreamingUiState

    data class Error(val message: String) : StreamingUiState
}

class OpenJarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = JarvisRuntimeCoordinator.getInstance(application.applicationContext)
    private val database = coordinator.database
    private val settingsDataStore = coordinator.settingsDataStore
    private val orchestrator = coordinator.orchestrator
    private val networkEngineManager = coordinator.networkEngineManager
    private val conversationStateManager = coordinator.conversationStateManager
    private val appSelfController = coordinator.appSelfController
    private val speechController = coordinator.speechController
    private val appUpdateManager = coordinator.appUpdateManager

    init {
        viewModelScope.launch {
            conversationStateManager.ensureDefaultSessionExists()
        }
    }

    val settings: StateFlow<JarvisSettings> = settingsDataStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = JarvisSettings()
    )

    val sessions: StateFlow<List<ConversationSessionEntity>> = conversationStateManager.sessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val activeSessionId: StateFlow<String> = conversationStateManager.activeSessionId

    val messages: StateFlow<List<ChatMessageEntity>> = conversationStateManager.activeMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val memories: StateFlow<List<MemoryEntity>> = database.memoryDao().observeMemories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val voiceCommands: StateFlow<List<VoiceCommandEntity>> = database.voiceCommandDao().observeCommands().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val traces: StateFlow<List<TraceEntity>> = database.traceDao().observeTraces().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val assistantState: StateFlow<AssistantState> = conversationStateManager.assistantState
    val networkState: StateFlow<NetworkEngineState> = networkEngineManager.state
    val lanServerState: StateFlow<LanServerState> = coordinator.lanServerState
    val voiceState: StateFlow<VoiceRuntimeState> = coordinator.voiceState
    val liveVoiceState: StateFlow<LiveVoiceState> = coordinator.geminiLiveManager.state
    val ttsStatus: StateFlow<TtsStatus> = speechController.ttsStatus
    val isTtsSpeaking: StateFlow<Boolean> = speechController.isSpeaking
    val guidedTour: StateFlow<GuidedTourState> = appSelfController.guidedTour
    val uiCommands: SharedFlow<AppUiCommand> = appSelfController.uiCommands
    val updateState: StateFlow<AppUpdateState> = appUpdateManager.state
    val currentVersionLabel: String =
        "v${appUpdateManager.currentVersionName} (build ${appUpdateManager.currentVersionCode})"

    val ollamaHealth: StateFlow<OllamaHealthStatus?> = networkState
        .map { it.ollamaHealth }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _streamingState = MutableStateFlow<StreamingUiState>(StreamingUiState.Idle)
    val streamingState: StateFlow<StreamingUiState> = _streamingState.asStateFlow()

    private val _isProbingOllama = MutableStateFlow(false)
    val isProbingOllama: StateFlow<Boolean> = _isProbingOllama.asStateFlow()

    private var promptJob: Job? = null

    fun sendPrompt(
        prompt: String,
        sourceTag: String = "TEXT",
        speakReply: Boolean? = null
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val commandSource = when (sourceTag) {
            CommandSource.VOICE_STT.tag -> CommandSource.VOICE_STT
            CommandSource.VOICE_BG.tag -> CommandSource.VOICE_BG
            CommandSource.LAN_TRIGGER.tag -> CommandSource.LAN_TRIGGER
            CommandSource.TOOL_TEST.tag -> CommandSource.TOOL_TEST
            else -> CommandSource.TEXT
        }

        // Stop any currently playing TTS before processing a new turn
        speechController.stopSpeaking()

        promptJob?.cancel()
        promptJob = viewModelScope.launch {
            val currentSettings = settings.value
            val shouldSpeak = speakReply ?: currentSettings.autoSpeakResponses
            val initialEngine = when (currentSettings.routingMode) {
                RoutingMode.OLLAMA -> EngineType.OLLAMA
                RoutingMode.OFFLINE -> EngineType.OFFLINE
                else -> EngineType.GEMINI
            }
            _streamingState.value = StreamingUiState.Streaming(
                text = "",
                engineType = initialEngine,
                modelName = if (initialEngine == EngineType.OLLAMA) currentSettings.ollamaModel else currentSettings.geminiModel
            )

            try {
                conversationStateManager.executeCommand(
                    prompt = clean,
                    source = commandSource
                ).collect { event ->
                    when (event) {
                        is InferenceStreamEvent.Chunk -> {
                            _streamingState.value = StreamingUiState.Streaming(
                                text = event.accumulatedText,
                                engineType = event.engineType,
                                modelName = event.modelName
                            )
                        }

                        is InferenceStreamEvent.Completed -> {
                            _streamingState.value = StreamingUiState.Idle
                            if (shouldSpeak && event.fullText.isNotBlank()) {
                                speakText(event.fullText)
                            }
                        }

                        is InferenceStreamEvent.Error -> {
                            _streamingState.value = StreamingUiState.Error(event.message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                _streamingState.value = StreamingUiState.Idle
                throw e
            } catch (e: Exception) {
                _streamingState.value = StreamingUiState.Error(
                    e.localizedMessage ?: "Erro inesperado ao processar solicitação."
                )
            } finally {
                if (_streamingState.value is StreamingUiState.Streaming) {
                    _streamingState.value = StreamingUiState.Idle
                }
            }
        }
    }

    fun speakText(text: String) {
        if (text.isBlank()) return
        conversationStateManager.updatePhase(AssistantPhase.Speaking(text.take(60)))
        coordinator.updateVoiceState {
            it.copy(isSpeaking = true, statusText = "OpenJarvis falando…")
        }
        speechController.speak(text) {
            conversationStateManager.updatePhase(AssistantPhase.Idle)
            coordinator.updateVoiceState {
                it.copy(isSpeaking = false, statusText = "Pronto para escutar")
            }
        }
    }

    fun stopSpeaking() {
        speechController.stopSpeaking()
        conversationStateManager.updatePhase(AssistantPhase.Idle)
        coordinator.updateVoiceState {
            it.copy(isSpeaking = false, statusText = "Pronto para escutar")
        }
    }

    fun testTtsVoice() {
        speakText("Olá! Aqui é o OpenJarvis. A síntese de voz está funcionando perfeitamente.")
    }

    fun reinitializeTts() {
        speechController.reinitializeTts()
    }

    // Self-navigation & Guided Tour
    fun reportCurrentDestination(destination: AppDestination) {
        appSelfController.reportCurrentDestination(destination)
    }

    fun startGuidedTour() {
        val step = appSelfController.startGuidedTour()
        if (settings.value.autoSpeakResponses) {
            speakText(step.narration)
        }
    }

    fun nextTourStep() {
        val step = appSelfController.nextTourStep()
        if (step != null && settings.value.autoSpeakResponses) {
            speakText(step.narration)
        } else if (step == null) {
            stopSpeaking()
        }
    }

    fun stopGuidedTour() {
        appSelfController.stopGuidedTour()
        stopSpeaking()
    }

    fun onMicButtonClicked() {
        val vState = voiceState.value
        if (vState.isBackgroundRunning) {
            coordinator.togglePauseBackgroundService(getApplication())
            return
        }

        if (vState.isListening) {
            speechController.stopListening()
            conversationStateManager.updatePhase(AssistantPhase.Idle)
            coordinator.updateVoiceState {
                it.copy(isListening = false, rmsDb = 0f, partialTranscript = "", statusText = "Escuta parada")
            }
            return
        }

        stopLiveSession()
        speechController.stopSpeaking()
        val currentSettings = settings.value
        conversationStateManager.updatePhase(AssistantPhase.ListeningCommand(isFollowUp = false))
        coordinator.updateVoiceState {
            it.copy(
                isListening = true,
                isSpeaking = false,
                errorMessage = null,
                partialTranscript = "",
                statusText = if (currentSettings.preferOfflineStt) "Ouvindo (STT Offline pref.)…" else "Ouvindo (STT Nativo)…"
            )
        }

        speechController.startListening(
            preferOffline = currentSettings.preferOfflineStt,
            onReady = {
                coordinator.updateVoiceState { it.copy(isListening = true) }
            },
            onPartial = { partial ->
                coordinator.updateVoiceState { it.copy(partialTranscript = partial) }
            },
            onFinal = { transcript ->
                coordinator.updateVoiceState {
                    it.copy(isListening = false, rmsDb = 0f, partialTranscript = "", statusText = "Processando comando…")
                }
                sendPrompt(transcript, sourceTag = CommandSource.VOICE_STT.tag, speakReply = currentSettings.autoSpeakResponses)
            },
            onRms = { rms ->
                coordinator.updateVoiceState { it.copy(rmsDb = rms) }
            },
            onError = { code, msg ->
                val displayError = if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    null
                } else {
                    msg
                }
                conversationStateManager.updatePhase(
                    if (displayError != null) AssistantPhase.Error(displayError) else AssistantPhase.Idle
                )
                coordinator.updateVoiceState {
                    it.copy(
                        isListening = false,
                        rmsDb = 0f,
                        statusText = "Pronto para escutar",
                        errorMessage = displayError
                    )
                }
            }
        )
    }

    fun toggleBackgroundService() {
        if (voiceState.value.isBackgroundRunning) {
            coordinator.stopBackgroundService(getApplication())
        } else {
            stopLiveSession()
            speechController.stopListening()
            speechController.stopSpeaking()
            coordinator.startBackgroundService(getApplication())
        }
    }

    fun startBackgroundServiceExplicitly() {
        if (!voiceState.value.isBackgroundRunning) {
            stopLiveSession()
            speechController.stopListening()
            speechController.stopSpeaking()
            coordinator.startBackgroundService(getApplication())
        }
    }

    fun stopBackgroundServiceExplicitly() {
        if (voiceState.value.isBackgroundRunning) {
            coordinator.stopBackgroundService(getApplication())
        }
    }

    fun startLiveSession() {
        if (voiceState.value.isBackgroundRunning) {
            coordinator.stopBackgroundService(getApplication())
        }
        speechController.stopListening()
        speechController.stopSpeaking()

        val currentSettings = settings.value
        coordinator.geminiLiveManager.startSession(
            settings = currentSettings,
            memories = memories.value,
            onSessionEnded = { durationMs, error ->
                viewModelScope.launch {
                    orchestrator.recordLiveTrace(durationMs, currentSettings.liveVoiceName, error)
                }
            }
        )
    }

    fun stopLiveSession() {
        val currentVoice = settings.value.liveVoiceName
        coordinator.geminiLiveManager.stopSession { durationMs ->
            viewModelScope.launch {
                orchestrator.recordLiveTrace(durationMs, currentVoice, null)
            }
        }
    }

    fun probeOllama(urlOverride: String? = null) {
        viewModelScope.launch {
            _isProbingOllama.value = true
            val targetUrl = urlOverride ?: settings.value.ollamaBaseUrl
            networkEngineManager.probeOllama(targetUrl)
            _isProbingOllama.value = false
        }
    }

    fun refreshNetworkState() {
        networkEngineManager.refreshNetworkState()
    }

    // Session & History management
    fun createNewSession() = viewModelScope.launch {
        conversationStateManager.createNewSession()
    }

    fun switchSession(sessionId: String) = viewModelScope.launch {
        conversationStateManager.switchSession(sessionId)
    }

    fun deleteSession(sessionId: String) = viewModelScope.launch {
        if (sessionId == DEFAULT_SESSION_ID) {
            conversationStateManager.clearActiveSession()
        } else {
            conversationStateManager.deleteSession(sessionId)
        }
    }

    fun clearChat() = viewModelScope.launch {
        conversationStateManager.clearActiveSession()
    }

    fun dismissStreamError() {
        if (_streamingState.value is StreamingUiState.Error) {
            _streamingState.value = StreamingUiState.Idle
        }
    }

    fun clearVoiceError() {
        coordinator.updateVoiceState { it.copy(errorMessage = null) }
    }

    fun clearLiveError() {
        coordinator.geminiLiveManager.clearError()
    }

    fun clearTraces() = viewModelScope.launch { database.traceDao().clearAll() }

    fun addVoiceCommand(trigger: String, actionType: String, payload: String) = viewModelScope.launch {
        database.voiceCommandDao().insertCommand(
            VoiceCommandEntity(
                triggerPhrase = trigger.trim(),
                actionType = actionType,
                actionPayload = payload.trim(),
                enabled = true
            )
        )
    }

    fun toggleVoiceCommand(command: VoiceCommandEntity) = viewModelScope.launch {
        database.voiceCommandDao().updateCommand(command.copy(enabled = !command.enabled))
    }

    fun deleteVoiceCommand(id: Long) = viewModelScope.launch {
        database.voiceCommandDao().deleteCommand(id)
    }

    fun addMemory(key: String, content: String, category: String) = viewModelScope.launch {
        database.memoryDao().insertMemory(
            MemoryEntity(
                key = key.trim(),
                content = content.trim(),
                category = category.trim().ifBlank { "Geral" }
            )
        )
    }

    fun deleteMemory(id: Long) = viewModelScope.launch {
        database.memoryDao().deleteMemory(id)
    }

    // Settings updates
    fun updateRoutingMode(mode: RoutingMode) = viewModelScope.launch {
        settingsDataStore.updateRoutingMode(mode)
    }

    fun updateGeminiModel(model: String) = viewModelScope.launch {
        settingsDataStore.updateGeminiModel(model)
    }

    fun updateLiveVoice(voice: String) = viewModelScope.launch {
        settingsDataStore.updateLiveVoice(voice)
    }

    fun updateOllamaConfig(baseUrl: String, model: String) = viewModelScope.launch {
        settingsDataStore.updateOllamaConfig(baseUrl, model)
    }

    fun updateTemperature(temp: Float) = viewModelScope.launch {
        settingsDataStore.updateTemperature(temp)
    }

    fun updateSystemPrompt(prompt: String) = viewModelScope.launch {
        settingsDataStore.updateSystemPrompt(prompt)
    }

    fun updateVoicePreferences(
        wakeWordEnabled: Boolean,
        wakeWord: String,
        preferOfflineStt: Boolean,
        autoSpeakResponses: Boolean
    ) = viewModelScope.launch {
        settingsDataStore.updateVoicePreferences(
            wakeWordEnabled = wakeWordEnabled,
            wakeWord = wakeWord,
            preferOfflineStt = preferOfflineStt,
            autoSpeakResponses = autoSpeakResponses
        )
    }

    fun updateBackgroundModePreferences(
        voiceListeningInBgEnabled: Boolean,
        continuousConversationMode: Boolean
    ) = viewModelScope.launch {
        settingsDataStore.updateBackgroundModePreferences(
            voiceListeningInBgEnabled = voiceListeningInBgEnabled,
            continuousConversationMode = continuousConversationMode
        )
    }

    fun updateLanTriggerConfig(enabled: Boolean, port: Int) = viewModelScope.launch {
        settingsDataStore.updateLanTriggerConfig(enabled = enabled, port = port)
    }

    fun regenerateLanTriggerToken() = viewModelScope.launch {
        settingsDataStore.regenerateLanTriggerToken()
    }

    fun updateDarkTheme(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updateDarkTheme(enabled)
    }

    // Remote Updates (GitHub Releases & F-Droid)
    fun updateRemoteUpdateConfig(
        githubRepo: String,
        fdroidRepoUrl: String,
        autoCheckUpdates: Boolean
    ) = viewModelScope.launch {
        val normalizedRepo = appUpdateManager.normalizeGitHubRepo(githubRepo)
        settingsDataStore.updateRemoteUpdateConfig(
            githubRepo = normalizedRepo,
            fdroidRepoUrl = fdroidRepoUrl.trim(),
            autoCheckUpdates = autoCheckUpdates
        )
    }

    fun checkForUpdates(repoOverride: String? = null) {
        viewModelScope.launch {
            val targetRepo = repoOverride ?: settings.value.githubRepo
            appUpdateManager.checkForUpdates(targetRepo)
        }
    }

    fun buildDefaultFdroidUrl(githubRepo: String): String {
        return appUpdateManager.buildDefaultFdroidPagesUrl(githubRepo)
    }

    fun openGitHubRelease(url: String): Result<String> {
        return appUpdateManager.openGitHubRelease(url)
    }

    fun openInFdroid(url: String): Result<String> {
        return appUpdateManager.openInFdroid(url)
    }

    override fun onCleared() {
        super.onCleared()
        if (!voiceState.value.isBackgroundRunning) {
            speechController.stopListening()
        }
        speechController.stopSpeaking()
        coordinator.geminiLiveManager.stopSession()
    }
}
