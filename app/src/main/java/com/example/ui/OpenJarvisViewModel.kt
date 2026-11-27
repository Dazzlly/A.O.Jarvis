package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistant.DefaultAssistantManager
import com.example.assistant.DefaultAssistantStatus
import com.example.cloud.CloudMemory
import com.example.cloud.CloudSession
import com.example.cloud.CloudSyncSummary
import com.example.cloud.CloudUiState
import com.example.cloud.CloudVoiceCommand
import com.example.cloud.JarvisCloudRepository
import com.example.core.AppDestination
import com.example.core.AppUiCommand
import com.example.core.AppUpdateState
import com.example.core.BatteryStatusMonitor
import com.example.core.DeviceControlServiceState
import com.example.core.DeviceSettingsSnapshot
import com.example.core.EvolutionProposal
import com.example.core.FdroidRepoCheckState
import com.example.core.GuidedTourState
import com.example.core.IntegrationResult
import com.example.core.SelfEvolutionState
import com.example.vision.ScreenShareState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import com.example.data.AppThemeMode
import com.example.data.ChatMessageEntity
import com.example.data.ConversationSessionEntity
import com.example.data.DEFAULT_SESSION_ID
import com.example.data.JarvisSettings
import com.example.data.MemoryEntity
import com.example.data.OfflineVoiceCommandEntity
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

class OpenJarvisViewModel(
    application: Application,
    private val currentUserId: String,
    private val cloudRepository: JarvisCloudRepository
) : AndroidViewModel(application) {

    private val coordinator = JarvisRuntimeCoordinator.getInstance(application.applicationContext)
    private val database = coordinator.database
    private val offlineVoiceCommandRepository = coordinator.offlineVoiceCommandRepository
    private val settingsDataStore = coordinator.settingsDataStore
    private val orchestrator = coordinator.orchestrator
    private val networkEngineManager = coordinator.networkEngineManager
    private val conversationStateManager = coordinator.conversationStateManager
    private val appSelfController = coordinator.appSelfController
    private val speechController = coordinator.speechController
    private val appUpdateManager = coordinator.appUpdateManager
    private val deviceSettingsController = coordinator.deviceSettingsController
    private val screenVisionManager = coordinator.screenVisionManager
    private val selfEvolutionManager = coordinator.selfEvolutionManager
    private val defaultAssistantManager = DefaultAssistantManager(application.applicationContext)

    private val _defaultAssistantStatus = MutableStateFlow(defaultAssistantManager.readStatus())
    val defaultAssistantStatus: StateFlow<DefaultAssistantStatus> = _defaultAssistantStatus.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    private val _cloudStatusMessage = MutableStateFlow("Sincronização Firestore ativa em tempo real")

    // Two-tier Flow error unwrapping: CancellationException -> FirebaseFirestoreException -> CloudUiState.Error
    val cloudMemoriesState: StateFlow<CloudUiState<List<CloudMemory>>> = cloudRepository
        .observeMemories(currentUserId)
        .map<List<CloudMemory>, CloudUiState<List<CloudMemory>>> { CloudUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w("OpenJarvisVM", "Error observing cloud memories", rootError)
            emit(CloudUiState.Error(rootError.message ?: "Falha ao sincronizar memórias na nuvem."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CloudUiState.Loading
        )

    val cloudCommandsState: StateFlow<CloudUiState<List<CloudVoiceCommand>>> = cloudRepository
        .observeVoiceCommands(currentUserId)
        .map<List<CloudVoiceCommand>, CloudUiState<List<CloudVoiceCommand>>> { CloudUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w("OpenJarvisVM", "Error observing cloud voice commands", rootError)
            emit(CloudUiState.Error(rootError.message ?: "Falha ao sincronizar comandos na nuvem."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CloudUiState.Loading
        )

    val cloudSessionsState: StateFlow<CloudUiState<List<CloudSession>>> = cloudRepository
        .observeSessions(currentUserId)
        .map<List<CloudSession>, CloudUiState<List<CloudSession>>> { CloudUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w("OpenJarvisVM", "Error observing cloud sessions", rootError)
            emit(CloudUiState.Error(rootError.message ?: "Falha ao sincronizar sessões na nuvem."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CloudUiState.Loading
        )

    val cloudSyncSummary: StateFlow<CloudSyncSummary> = combine(
        cloudMemoriesState,
        cloudCommandsState,
        cloudSessionsState,
        _isCloudSyncing,
        _cloudStatusMessage
    ) { memState, cmdState, sessState, syncing, statusMsg ->
        val user = Firebase.auth.currentUser
        val memCount = (memState as? CloudUiState.Success)?.data?.size ?: 0
        val cmdCount = (cmdState as? CloudUiState.Success)?.data?.size ?: 0
        val sessCount = (sessState as? CloudUiState.Success)?.data?.size ?: 0
        val firstError = (memState as? CloudUiState.Error)?.message
            ?: (cmdState as? CloudUiState.Error)?.message
            ?: (sessState as? CloudUiState.Error)?.message
        CloudSyncSummary(
            userId = currentUserId,
            displayName = user?.displayName.orEmpty().ifBlank { "Usuário A.O.Jarvis" },
            email = user?.email.orEmpty(),
            memoriesCount = memCount,
            commandsCount = cmdCount,
            sessionsCount = sessCount,
            isSyncing = syncing,
            lastSyncMessage = statusMsg,
            errorMessage = firstError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CloudSyncSummary(
            userId = currentUserId,
            displayName = Firebase.auth.currentUser?.displayName.orEmpty(),
            email = Firebase.auth.currentUser?.email.orEmpty()
        )
    )

    init {
        viewModelScope.launch {
            conversationStateManager.ensureDefaultSessionExists()
            offlineVoiceCommandRepository.ensureDefaultCommandsSeeded()
            Firebase.auth.currentUser?.let { user ->
                cloudRepository.upsertUserProfile(user)
            }
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

    val offlineVoiceCommands: StateFlow<List<OfflineVoiceCommandEntity>> =
        offlineVoiceCommandRepository.observeCommands().stateIn(
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
    val fdroidRepoStatus: StateFlow<FdroidRepoCheckState> = appUpdateManager.fdroidStatus
    val currentVersionLabel: String =
        "v${appUpdateManager.currentVersionName} (build ${appUpdateManager.currentVersionCode})"
    val screenShareState: StateFlow<ScreenShareState> = screenVisionManager.state
    val deviceControlServiceState: StateFlow<DeviceControlServiceState> = coordinator.deviceControlServiceState
    val selfEvolutionState: StateFlow<SelfEvolutionState> = selfEvolutionManager.state
    val hasGitHubToken: StateFlow<Boolean> = selfEvolutionManager.hasToken

    val deviceSettingsSnapshot: StateFlow<DeviceSettingsSnapshot> = deviceControlServiceState
        .map { it.snapshot }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = deviceSettingsController.readSnapshot()
        )

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
            CommandSource.SCREEN_VISION.tag -> CommandSource.SCREEN_VISION
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
            it.copy(isSpeaking = true, statusText = "A.O.Jarvis falando…")
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
        speakText("Olá! Aqui é o A.O.Jarvis. A síntese de voz está funcionando perfeitamente.")
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

    // Session & History management (Local Room + Firestore Cloud Sync)
    fun createNewSession() = viewModelScope.launch {
        val newId = conversationStateManager.createNewSession()
        cloudRepository.upsertSession(sessionId = newId, title = "Nova Conversa", messageCount = 0L)
    }

    fun switchSession(sessionId: String) = viewModelScope.launch {
        conversationStateManager.switchSession(sessionId)
    }

    fun deleteSession(sessionId: String) = viewModelScope.launch {
        if (sessionId == DEFAULT_SESSION_ID) {
            conversationStateManager.clearActiveSession()
        } else {
            conversationStateManager.deleteSession(sessionId)
            cloudRepository.deleteSession(sessionId)
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
        val cleanTrigger = trigger.trim()
        val cleanPayload = payload.trim()
        if (cleanTrigger.isEmpty() || cleanPayload.isEmpty()) return@launch
        val localId = database.voiceCommandDao().insertCommand(
            VoiceCommandEntity(
                triggerPhrase = cleanTrigger,
                actionType = actionType,
                actionPayload = cleanPayload,
                enabled = true
            )
        )
        cloudRepository.addVoiceCommand(
            triggerPhrase = cleanTrigger,
            actionType = actionType,
            actionPayload = cleanPayload,
            enabled = true,
            docIdOverride = "cmd_$localId"
        )
    }

    fun toggleVoiceCommand(command: VoiceCommandEntity) = viewModelScope.launch {
        val nextEnabled = !command.enabled
        database.voiceCommandDao().updateCommand(command.copy(enabled = nextEnabled))
        cloudRepository.addVoiceCommand(
            triggerPhrase = command.triggerPhrase,
            actionType = command.actionType,
            actionPayload = command.actionPayload,
            enabled = nextEnabled,
            docIdOverride = "cmd_${command.id}"
        )
    }

    fun deleteVoiceCommand(id: Long) = viewModelScope.launch {
        database.voiceCommandDao().deleteCommand(id)
        cloudRepository.deleteVoiceCommand("cmd_$id")
    }

    // Essential Offline Voice Commands (Local Room Table)
    fun addOfflineVoiceCommand(
        triggerPhrase: String,
        aliases: String,
        actionKey: String,
        actionArgument: String,
        description: String
    ) = viewModelScope.launch {
        if (triggerPhrase.isBlank() || actionKey.isBlank()) return@launch
        offlineVoiceCommandRepository.addCommand(
            triggerPhrase = triggerPhrase,
            aliases = aliases,
            actionKey = actionKey,
            actionArgument = actionArgument,
            description = description
        )
    }

    fun toggleOfflineVoiceCommand(command: OfflineVoiceCommandEntity) = viewModelScope.launch {
        offlineVoiceCommandRepository.toggleCommand(command)
    }

    fun deleteOfflineVoiceCommand(id: Long) = viewModelScope.launch {
        offlineVoiceCommandRepository.deleteCommand(id)
    }

    fun addMemory(key: String, content: String, category: String) = viewModelScope.launch {
        val cleanKey = key.trim()
        val cleanContent = content.trim()
        val cleanCategory = category.trim().ifBlank { "Geral" }
        if (cleanKey.isEmpty() || cleanContent.isEmpty()) return@launch
        val localId = database.memoryDao().insertMemory(
            MemoryEntity(
                key = cleanKey,
                content = cleanContent,
                category = cleanCategory
            )
        )
        cloudRepository.addMemory(
            key = cleanKey,
            content = cleanContent,
            category = cleanCategory,
            docIdOverride = "mem_$localId"
        )
    }

    fun deleteMemory(id: Long) = viewModelScope.launch {
        database.memoryDao().deleteMemory(id)
        cloudRepository.deleteMemory("mem_$id")
    }

    fun syncAllToCloud() = viewModelScope.launch {
        _isCloudSyncing.value = true
        _cloudStatusMessage.value = "Sincronizando dados com o Firebase Firestore…"
        var syncedCount = 0
        var hadError = false
        try {
            Firebase.auth.currentUser?.let { user ->
                if (cloudRepository.upsertUserProfile(user).isFailure) hadError = true
            }
            val localMemories = database.memoryDao().getAllMemories()
            localMemories.forEach { mem ->
                val res = cloudRepository.addMemory(
                    key = mem.key,
                    content = mem.content,
                    category = mem.category,
                    docIdOverride = "mem_${mem.id}"
                )
                if (res.isSuccess) syncedCount++ else hadError = true
            }
            val localCommands = voiceCommands.value
            localCommands.forEach { cmd ->
                val res = cloudRepository.addVoiceCommand(
                    triggerPhrase = cmd.triggerPhrase,
                    actionType = cmd.actionType,
                    actionPayload = cmd.actionPayload,
                    enabled = cmd.enabled,
                    docIdOverride = "cmd_${cmd.id}"
                )
                if (res.isSuccess) syncedCount++ else hadError = true
            }
            val localSessions = sessions.value
            localSessions.forEach { sess ->
                val count = database.chatMessageDao().countMessagesInSession(sess.id).toLong()
                val res = cloudRepository.upsertSession(
                    sessionId = sess.id,
                    title = sess.title,
                    messageCount = count
                )
                if (res.isSuccess) syncedCount++ else hadError = true
            }
            _cloudStatusMessage.value = if (hadError) {
                "Sincronização parcial ($syncedCount itens enviados)."
            } else {
                "Tudo sincronizado no Firestore ($syncedCount itens)."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _cloudStatusMessage.value = e.localizedMessage ?: "Falha ao sincronizar com o Firestore."
        } finally {
            _isCloudSyncing.value = false
        }
    }

    // Default Digital Assistant Configuration
    fun refreshDefaultAssistantStatus() {
        _defaultAssistantStatus.value = defaultAssistantManager.readStatus()
    }

    fun createDefaultAssistantRoleIntent(): Intent? {
        return defaultAssistantManager.createRequestDefaultAssistantRoleIntent()
    }

    fun openDefaultAssistantSettings(): IntegrationResult {
        return defaultAssistantManager.openSystemAssistantSettings().also {
            refreshDefaultAssistantStatus()
        }
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

    fun updateThemeMode(mode: AppThemeMode) = viewModelScope.launch {
        settingsDataStore.updateThemeMode(mode)
    }

    fun updateCustomAccentColor(argb: Long) = viewModelScope.launch {
        settingsDataStore.updateCustomAccentColor(argb)
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

    fun buildRawFdroidUrl(githubRepo: String): String {
        return appUpdateManager.buildRawGitHubFdroidUrl(githubRepo)
    }

    fun buildDefaultFdroidUrl(githubRepo: String): String {
        return appUpdateManager.buildDefaultFdroidPagesUrl(githubRepo)
    }

    fun verifyFdroidRepo(url: String) {
        viewModelScope.launch {
            appUpdateManager.verifyFdroidRepo(url)
        }
    }

    fun openGitHubRelease(url: String): Result<String> {
        return appUpdateManager.openGitHubRelease(url)
    }

    fun openInFdroid(url: String): Result<String> {
        return appUpdateManager.openInFdroid(url)
    }

    // Real-Time Screen Sharing & Visual Identification
    fun createScreenCaptureIntent(): Intent {
        return screenVisionManager.createScreenCaptureIntent(getApplication())
    }

    fun startScreenShare(resultCode: Int, data: Intent) {
        screenVisionManager.startScreenShareService(getApplication(), resultCode, data)
    }

    fun stopScreenShare() {
        screenVisionManager.stopScreenShareService(getApplication())
    }

    fun clearScreenShareError() {
        screenVisionManager.clearError()
    }

    fun analyzeCurrentScreenNow(
        question: String = "Identifique e descreva de forma clara o que estou visualizando na tela agora."
    ) {
        if (screenShareState.value.isSharing) {
            screenVisionManager.requestOnDemandScreenAnalysis(getApplication(), question)
        } else {
            sendPrompt(question, sourceTag = CommandSource.SCREEN_VISION.tag)
        }
    }

    fun analyzePickedImageUri(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(resolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = false
                        }
                    } else {
                        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
            }
            if (bitmap != null) {
                screenVisionManager.attachManualScreenshot(bitmap)
                sendPrompt(
                    prompt = "Identifique e explique o que aparece nesta imagem/captura de tela.",
                    sourceTag = CommandSource.SCREEN_VISION.tag
                )
            }
        }
    }

    // Device Settings Control & Dedicated Foreground Service
    fun refreshDeviceSettings() {
        coordinator.updateDeviceControlServiceState(
            snapshot = deviceSettingsController.readSnapshot(),
            batteryStatus = BatteryStatusMonitor.readSnapshot(getApplication())
        )
    }

    fun applyPowerSavingProfile(): IntegrationResult =
        deviceSettingsController.applyPowerSavingActions(openBatteryPanelIfNeeded = true).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                batteryStatus = BatteryStatusMonitor.readSnapshot(getApplication()),
                lastActionMessage = it.message
            )
        }

    fun startDeviceControlService() {
        coordinator.startDeviceControlService(getApplication())
    }

    fun stopDeviceControlService() {
        coordinator.stopDeviceControlService(getApplication())
    }

    fun toggleDeviceControlService() {
        if (deviceControlServiceState.value.isRunning) {
            stopDeviceControlService()
        } else {
            startDeviceControlService()
        }
    }

    fun openWriteSettingsPermission(): IntegrationResult =
        deviceSettingsController.openWriteSettingsPermission().also { refreshDeviceSettings() }

    fun openDndAccessPermission(): IntegrationResult =
        deviceSettingsController.openDndAccessPermission().also { refreshDeviceSettings() }

    fun setDeviceBrightness(percent: Int): IntegrationResult =
        deviceSettingsController.setBrightnessPercent(percent).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun setDeviceVolume(percent: Int): IntegrationResult =
        deviceSettingsController.setMediaVolumePercent(percent).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun toggleDeviceWifi(enabled: Boolean): IntegrationResult =
        deviceSettingsController.toggleWifi(enabled).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun toggleDeviceBluetooth(enabled: Boolean): IntegrationResult =
        deviceSettingsController.toggleBluetooth(enabled).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun toggleDeviceTorch(enabled: Boolean): IntegrationResult =
        deviceSettingsController.setTorchEnabled(enabled).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun toggleDeviceAutoRotate(enabled: Boolean): IntegrationResult =
        deviceSettingsController.setAutoRotate(enabled).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun toggleDeviceDnd(enabled: Boolean): IntegrationResult =
        deviceSettingsController.setDoNotDisturb(enabled).also {
            coordinator.updateDeviceControlServiceState(
                snapshot = deviceSettingsController.readSnapshot(),
                lastActionMessage = it.message
            )
        }

    fun openDeviceSystemPanel(target: String): IntegrationResult =
        deviceSettingsController.openSystemPanelOrSettings(target)

    // Safe GitHub Self-Evolution (PR Auto-Development)
    fun saveGitHubDevToken(token: String): Boolean = selfEvolutionManager.saveGitHubToken(token)

    fun clearGitHubDevToken() = selfEvolutionManager.clearGitHubToken()

    fun generateSelfEvolutionProposal(request: String) {
        viewModelScope.launch {
            selfEvolutionManager.generateEvolutionProposal(
                rawGitHubRepo = settings.value.githubRepo,
                userRequest = request
            )
        }
    }

    fun approveSelfEvolutionProposal(proposal: EvolutionProposal) {
        viewModelScope.launch {
            selfEvolutionManager.approveAndCreatePullRequest(proposal)
        }
    }

    fun resetSelfEvolution() {
        selfEvolutionManager.resetState()
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
