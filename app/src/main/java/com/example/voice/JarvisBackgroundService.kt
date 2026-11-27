package com.example.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.JarvisSettings
import com.example.engine.InferenceStreamEvent
import com.example.state.AssistantPhase
import com.example.state.CommandSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "JarvisBackgroundService"
private const val MAX_FOLLOW_UP_SILENCE_RETRIES = 2
private const val WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L // 30-minute renewable safety cap

/**
 * Foreground Service do OpenJarvis que mantém:
 * 1) Escuta contínua de voz nativa (SpeechRecognizer + wake word + janela de conversa contínua)
 * 2) Servidor local autenticado de gatilhos na rede Wi-Fi/LAN (LocalNetworkTriggerServer)
 */
class JarvisBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var coordinator: JarvisRuntimeCoordinator
    private lateinit var speechController: NativeSpeechController
    private lateinit var lanServer: LocalNetworkTriggerServer

    private var settingsObserverJob: Job? = null
    private var networkObserverJob: Job? = null
    private var lanStateMirrorJob: Job? = null
    private var waveformMirrorJob: Job? = null
    private var restartJob: Job? = null
    private var dispatchJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var isPaused = false

    @Volatile
    private var voiceEnabledInService = true

    private var awaitingDirectCommand = false
    private var followUpSilenceRetries = 0
    private var lastSttConfidence = -1f
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        coordinator = JarvisRuntimeCoordinator.getInstance(applicationContext)
        speechController = coordinator.speechController
        lanServer = LocalNetworkTriggerServer(
            onStatusRequested = { buildLanStatusJson() },
            onTriggerReceived = { req -> handleLanTrigger(req) }
        )
        createNotificationChannel()
        startStateMirrors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfAndCleanup()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                pauseVoiceListening()
                return START_STICKY
            }

            ACTION_RESUME -> {
                resumeVoiceListening()
                return START_STICKY
            }

            ACTION_LISTEN_NOW -> {
                isPaused = false
                awaitingDirectCommand = true
                followUpSilenceRetries = 0
                if (voiceEnabledInService) {
                    acquireWakeLock()
                    speechController.stopSpeaking()
                    scheduleNextListen(delayMs = 150L)
                }
                return START_STICKY
            }

            else -> {
                isPaused = false
                val hasMicPermission = hasMicrophonePermission()
                val initialSettings = runCatching {
                    // Immediate foreground promotion is mandatory on Android 8+
                    promoteToForeground(
                        statusText = if (hasMicPermission) "Iniciando escuta contínua…" else "Aguardando gatilhos LAN…",
                        includeMicrophone = hasMicPermission
                    )
                }
                if (initialSettings.isFailure) {
                    Log.e(TAG, "Failed to promote Foreground Service", initialSettings.exceptionOrNull())
                    stopSelfAndCleanup()
                    return START_NOT_STICKY
                }

                coordinator.updateVoiceState {
                    it.copy(
                        isBackgroundRunning = true,
                        isBackgroundPaused = false,
                        errorMessage = null,
                        statusText = "Serviço A.O.Jarvis ativo"
                    )
                }

                startObserversAndApplySettings()
                return START_STICKY
            }
        }
    }

    private fun startStateMirrors() {
        lanStateMirrorJob?.cancel()
        lanStateMirrorJob = serviceScope.launch {
            lanServer.state.collect { serverState ->
                coordinator.updateLanServerState(serverState)
                if (isForegroundStarted && !voiceEnabledInService && serverState.isRunning) {
                    val label = "Gatilhos LAN ativos (${serverState.endpointUrl})"
                    coordinator.conversationStateManager.updatePhase(
                        AssistantPhase.AwaitingLanTrigger(serverState.endpointUrl)
                    )
                    coordinator.updateVoiceState { it.copy(statusText = label) }
                    updateNotification(label)
                }
            }
        }
        waveformMirrorJob?.cancel()
        waveformMirrorJob = serviceScope.launch {
            speechController.waveformSamples.collect { samples ->
                coordinator.updateVoiceState { it.copy(waveformSamples = samples) }
            }
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val lock = wakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "A.O.Jarvis:PersistentVoiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                wakeLock = this
            }
            if (!lock.isHeld) {
                lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.onFailure {
            Log.w(TAG, "Unable to acquire background wake lock: ${it.message}")
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
    }

    private fun startObserversAndApplySettings() {
        settingsObserverJob?.cancel()
        settingsObserverJob = serviceScope.launch {
            coordinator.settingsDataStore.settingsFlow.collect { settings ->
                applyServiceConfiguration(settings)
            }
        }

        networkObserverJob?.cancel()
        networkObserverJob = serviceScope.launch {
            coordinator.networkEngineManager.state.collect { netState ->
                lanServer.updateLocalIp(netState.localIpAddress)
            }
        }
    }

    private fun applyServiceConfiguration(settings: JarvisSettings) {
        val canUseMic = hasMicrophonePermission() && settings.voiceListeningInBgEnabled
        voiceEnabledInService = canUseMic

        // Re-sync foreground type with active capabilities
        promoteToForeground(
            statusText = buildNotificationSummary(settings, canUseMic),
            includeMicrophone = canUseMic
        )

        // Start or stop local Wi-Fi trigger listener
        if (settings.lanTriggerEnabled) {
            val localIp = coordinator.networkEngineManager.resolveLocalIpv4Address()
            lanServer.start(
                port = settings.lanTriggerPort,
                token = settings.lanTriggerToken,
                localIp = localIp
            )
        } else {
            lanServer.stop()
        }

        // Manage continuous voice listening loop
        if (!canUseMic) {
            restartJob?.cancel()
            speechController.stopListening()
            awaitingDirectCommand = false
            val lanInfo = lanServer.state.value
            val status = if (settings.lanTriggerEnabled) {
                "Aguardando gatilhos LAN em :${settings.lanTriggerPort}"
            } else {
                "Serviço ativo (voz e LAN desativados nas configurações)"
            }
            coordinator.updateVoiceState {
                it.copy(
                    isBackgroundRunning = true,
                    isListening = false,
                    rmsDb = 0f,
                    awaitingFollowUpCommand = false,
                    statusText = status
                )
            }
            if (settings.lanTriggerEnabled) {
                coordinator.conversationStateManager.updatePhase(
                    AssistantPhase.AwaitingLanTrigger(lanInfo.endpointUrl),
                    followUpWindowActive = false
                )
            } else {
                coordinator.conversationStateManager.updatePhase(AssistantPhase.Idle, followUpWindowActive = false)
            }
            if (!settings.lanTriggerEnabled) {
                releaseWakeLock()
            } else {
                acquireWakeLock()
            }
            updateNotification(status)
        } else if (!isPaused && !coordinator.voiceState.value.isListening &&
            !coordinator.voiceState.value.isProcessingVoice &&
            !coordinator.voiceState.value.isSpeaking
        ) {
            acquireWakeLock()
            scheduleNextListen(delayMs = 250L)
        }
    }

    private fun pauseVoiceListening() {
        isPaused = true
        releaseWakeLock()
        restartJob?.cancel()
        speechController.stopListening()
        speechController.stopSpeaking()
        awaitingDirectCommand = false
        followUpSilenceRetries = 0
        coordinator.conversationStateManager.updatePhase(AssistantPhase.Paused, followUpWindowActive = false)
        val lanActive = lanServer.state.value.isRunning
        val status = if (lanActive) {
            "Voz pausada · Gatilhos LAN :${lanServer.state.value.port} ativos"
        } else {
            "Escuta contínua pausada"
        }
        coordinator.updateVoiceState {
            it.copy(
                isBackgroundRunning = true,
                isBackgroundPaused = true,
                isListening = false,
                isSpeaking = false,
                awaitingFollowUpCommand = false,
                statusText = status
            )
        }
        updateNotification(status)
    }

    private fun resumeVoiceListening() {
        isPaused = false
        coordinator.updateVoiceState {
            it.copy(
                isBackgroundRunning = true,
                isBackgroundPaused = false,
                errorMessage = null,
                statusText = "Retomando escuta contínua…"
            )
        }
        updateNotification("Retomando escuta contínua…")
        if (voiceEnabledInService) {
            acquireWakeLock()
            scheduleNextListen(delayMs = 250L)
        }
    }

    private fun scheduleNextListen(delayMs: Long) {
        restartJob?.cancel()
        if (isPaused || !voiceEnabledInService) return
        restartJob = serviceScope.launch {
            delay(delayMs)
            if (!isPaused && voiceEnabledInService) {
                beginListeningCycle()
            }
        }
    }

    private suspend fun beginListeningCycle() {
        if (!hasMicrophonePermission()) {
            voiceEnabledInService = false
            coordinator.updateVoiceState {
                it.copy(
                    isListening = false,
                    errorMessage = "Permissão de microfone necessária para escuta de voz.",
                    statusText = "Sem permissão de microfone"
                )
            }
            return
        }

        val settings = coordinator.settingsDataStore.settingsFlow.first()
        val lanSuffix = if (lanServer.state.value.isRunning) " · LAN :${lanServer.state.value.port}" else ""
        val label = when {
            awaitingDirectCommand -> "Conversa contínua — pode falar$lanSuffix"
            settings.wakeWordEnabled -> "Aguardando \"${settings.wakeWord}\"$lanSuffix"
            else -> "Ouvindo comando de voz$lanSuffix"
        }

        val phase = if (settings.wakeWordEnabled && !awaitingDirectCommand) {
            AssistantPhase.ListeningWakeWord(settings.wakeWord)
        } else {
            AssistantPhase.ListeningCommand(isFollowUp = awaitingDirectCommand)
        }
        coordinator.conversationStateManager.updatePhase(
            phase = phase,
            followUpWindowActive = awaitingDirectCommand
        )

        coordinator.updateVoiceState {
            it.copy(
                isListening = true,
                isSpeaking = false,
                partialTranscript = "",
                statusText = label,
                awaitingFollowUpCommand = awaitingDirectCommand
            )
        }
        updateNotification(label)

        speechController.startListening(
            preferOffline = settings.preferOfflineStt,
            onReady = {
                coordinator.updateVoiceState {
                    it.copy(isListening = true, statusText = label)
                }
            },
            onPartial = { partial ->
                coordinator.updateVoiceState {
                    it.copy(partialTranscript = partial)
                }
                if (partial.isNotBlank()) {
                    updateNotification("Ouvindo: \"$partial\"")
                }
            },
            onFinal = { transcript ->
                followUpSilenceRetries = 0
                coordinator.updateVoiceState {
                    it.copy(
                        isListening = false,
                        rmsDb = 0f,
                        partialTranscript = transcript,
                        lastSttConfidence = lastSttConfidence
                    )
                }
                handleSpokenTranscript(transcript, lastSttConfidence)
            },
            onRms = { rms ->
                coordinator.updateVoiceState { it.copy(rmsDb = rms) }
            },
            onConfidence = { confidence ->
                lastSttConfidence = confidence
            },
            onError = { errorCode, errorMessage ->
                coordinator.updateVoiceState {
                    it.copy(isListening = false, rmsDb = 0f)
                }
                val isRecoverableSilence =
                    errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
                        errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

                if (errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    coordinator.updateVoiceState {
                        it.copy(
                            errorMessage = errorMessage,
                            statusText = "Permissão de microfone necessária"
                        )
                    }
                    voiceEnabledInService = false
                    if (!lanServer.state.value.isRunning) {
                        stopSelfAndCleanup()
                    }
                } else if (!isPaused && voiceEnabledInService) {
                    if (awaitingDirectCommand && isRecoverableSilence) {
                        followUpSilenceRetries += 1
                        if (followUpSilenceRetries >= MAX_FOLLOW_UP_SILENCE_RETRIES) {
                            awaitingDirectCommand = false
                            followUpSilenceRetries = 0
                        }
                    } else if (!isRecoverableSilence) {
                        Log.w(TAG, "Recoverable STT error $errorCode: $errorMessage")
                        coordinator.updateVoiceState {
                            it.copy(statusText = "Reconectando microfone ($errorMessage)")
                        }
                    }
                    scheduleNextListen(delayMs = if (isRecoverableSilence) 450L else 1400L)
                }
            }
        )
    }

    private fun handleSpokenTranscript(rawTranscript: String, sttConfidence: Float = -1f) {
        dispatchJob?.cancel()
        dispatchJob = serviceScope.launch {
            try {
                val settings = coordinator.settingsDataStore.settingsFlow.first()
                val normalized = normalize(rawTranscript)

                // Voice command to stop background service directly
                if (normalized.contains("parar escuta") || normalized.contains("desativar background")) {
                    speakAndAwait("Modo contínuo encerrado.")
                    stopSelfAndCleanup()
                    return@launch
                }

                // Voice command to end follow-up window and return to wake-word standby
                if (awaitingDirectCommand &&
                    (normalized == "obrigado" || normalized.contains("encerrar conversa") || normalized.contains("fim da conversa"))
                ) {
                    awaitingDirectCommand = false
                    followUpSilenceRetries = 0
                    coordinator.conversationStateManager.updatePhase(
                        AssistantPhase.ListeningWakeWord(settings.wakeWord),
                        followUpWindowActive = false
                    )
                    if (settings.autoSpeakResponses) {
                        speakAndAwait("Certo, estou à disposição quando precisar.")
                    }
                    scheduleNextListen(delayMs = 350L)
                    return@launch
                }

                // Voice command to start a fresh persistent conversation session
                if (normalized.contains("nova conversa") || normalized.contains("reiniciar conversa")) {
                    coordinator.conversationStateManager.createNewSession()
                    awaitingDirectCommand = settings.continuousConversationMode
                    if (settings.autoSpeakResponses) {
                        speakAndAwait("Nova conversa iniciada. Como posso ajudar?")
                    }
                    scheduleNextListen(delayMs = 300L)
                    return@launch
                }

                val promptToExecute: String? = if (settings.wakeWordEnabled && !awaitingDirectCommand) {
                    val wake = normalize(settings.wakeWord).ifBlank { "jarvis" }
                    val idx = normalized.indexOf(wake)
                    if (idx < 0) {
                        null
                    } else {
                        val afterWake = rawTranscript
                            .drop((idx + wake.length).coerceAtMost(rawTranscript.length))
                            .trimStart(',', '.', ':', ' ', '-', '!')
                            .trim()
                        if (afterWake.isEmpty()) {
                            awaitingDirectCommand = true
                            followUpSilenceRetries = 0
                            coordinator.updateVoiceState {
                                it.copy(
                                    isSpeaking = true,
                                    awaitingFollowUpCommand = true,
                                    statusText = "Pronto — diga o comando"
                                )
                            }
                            coordinator.conversationStateManager.updatePhase(
                                AssistantPhase.Speaking("Sim, pode falar."),
                                followUpWindowActive = true
                            )
                            updateNotification("Pronto — diga o comando")
                            speakAndAwait("Sim, pode falar.")
                            coordinator.updateVoiceState { it.copy(isSpeaking = false) }
                            scheduleNextListen(delayMs = 200L)
                            return@launch
                        } else {
                            afterWake
                        }
                    }
                } else {
                    rawTranscript
                }

                if (promptToExecute.isNullOrBlank()) {
                    scheduleNextListen(delayMs = 350L)
                    return@launch
                }

                coordinator.updateVoiceState {
                    it.copy(
                        isProcessingVoice = true,
                        partialTranscript = "",
                        statusText = "Processando: \"$promptToExecute\""
                    )
                }
                updateNotification("Processando: $promptToExecute")

                var responseToSpeak: String? = null
                coordinator.conversationStateManager.executeCommand(
                    prompt = promptToExecute,
                    source = CommandSource.VOICE_BG,
                    sttConfidence = sttConfidence
                ).collect { event ->
                    when (event) {
                        is InferenceStreamEvent.Chunk -> {
                            coordinator.updateVoiceState {
                                it.copy(statusText = "Recebendo resposta (${event.engineType.label})…")
                            }
                        }

                        is InferenceStreamEvent.Completed -> {
                            responseToSpeak = event.fullText
                        }

                        is InferenceStreamEvent.Error -> {
                            responseToSpeak = event.message
                        }
                    }
                }

                coordinator.updateVoiceState {
                    it.copy(
                        isProcessingVoice = false,
                        backgroundCommandsHandled = it.backgroundCommandsHandled + 1
                    )
                }

                // Keep continuous follow-up window open if enabled in settings
                awaitingDirectCommand = settings.continuousConversationMode
                followUpSilenceRetries = 0

                val finalReply = responseToSpeak
                if (settings.autoSpeakResponses && !finalReply.isNullOrBlank() && !isPaused) {
                    coordinator.conversationStateManager.updatePhase(
                        AssistantPhase.Speaking(finalReply.take(80)),
                        followUpWindowActive = awaitingDirectCommand
                    )
                    coordinator.updateVoiceState {
                        it.copy(
                            isSpeaking = true,
                            awaitingFollowUpCommand = awaitingDirectCommand,
                            statusText = "Respondendo por voz…"
                        )
                    }
                    updateNotification("Respondendo por voz…")
                    speakAndAwait(finalReply)
                    coordinator.updateVoiceState { it.copy(isSpeaking = false) }
                }

                if (!isPaused && voiceEnabledInService) {
                    scheduleNextListen(delayMs = 350L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error handling background voice transcript", e)
                coordinator.conversationStateManager.updatePhase(
                    AssistantPhase.Error(e.localizedMessage ?: "Erro de processamento")
                )
                coordinator.updateVoiceState {
                    it.copy(
                        isProcessingVoice = false,
                        errorMessage = e.localizedMessage
                    )
                }
                if (!isPaused && voiceEnabledInService) {
                    scheduleNextListen(delayMs = 1200L)
                }
            }
        }
    }

    private suspend fun buildLanStatusJson(): JSONObject {
        val settings = coordinator.settingsDataStore.settingsFlow.first()
        val assistant = coordinator.conversationStateManager.assistantState.value
        val net = coordinator.networkEngineManager.state.value
        return JSONObject()
            .put("status", "ok")
            .put("phase", assistant.phase.toDisplayLabel())
            .put("active_session_id", assistant.activeSessionId)
            .put("turn_count", assistant.turnCountInSession)
            .put("routing_mode", settings.routingMode.name)
            .put("active_engine", net.activeEngine.name)
            .put("wifi_connected", net.isWifiConnected)
            .put("voice_listening_paused", isPaused)
    }

    private suspend fun handleLanTrigger(request: LanTriggerRequest): LanTriggerResponse {
        return when (request.action) {
            "pause" -> {
                serviceScope.launch { pauseVoiceListening() }.join()
                LanTriggerResponse(
                    statusCode = 200,
                    body = JSONObject().put("status", "paused")
                )
            }

            "resume" -> {
                serviceScope.launch { resumeVoiceListening() }.join()
                LanTriggerResponse(
                    statusCode = 200,
                    body = JSONObject().put("status", "resumed")
                )
            }

            "new_session" -> {
                val newId = coordinator.conversationStateManager.createNewSession()
                LanTriggerResponse(
                    statusCode = 200,
                    body = JSONObject()
                        .put("status", "session_created")
                        .put("session_id", newId)
                )
            }

            else -> {
                // Temporarily stop mic capture while executing LAN trigger so audio doesn't collide
                val wasListening = voiceEnabledInService && !isPaused
                serviceScope.launch {
                    restartJob?.cancel()
                    speechController.stopListening()
                    coordinator.updateVoiceState {
                        it.copy(
                            isListening = false,
                            isProcessingVoice = true,
                            statusText = "Gatilho LAN: \"${request.prompt.take(40)}\""
                        )
                    }
                    updateNotification("Gatilho LAN: ${request.prompt.take(48)}")
                }.join()

                var replyText = ""
                var engineUsed = "UNKNOWN"
                var modelUsed = ""
                var latencyMs = 0L
                var isError = false

                coordinator.conversationStateManager.executeCommand(
                    prompt = request.prompt,
                    source = CommandSource.LAN_TRIGGER,
                    routingOverride = request.routingOverride
                ).collect { event ->
                    when (event) {
                        is InferenceStreamEvent.Chunk -> Unit
                        is InferenceStreamEvent.Completed -> {
                            replyText = event.fullText
                            engineUsed = event.engineType.name
                            modelUsed = event.modelName
                            latencyMs = event.latencyMs
                        }

                        is InferenceStreamEvent.Error -> {
                            replyText = event.message
                            engineUsed = event.engineType.name
                            modelUsed = event.modelName
                            latencyMs = event.latencyMs
                            isError = true
                        }
                    }
                }

                val settings = coordinator.settingsDataStore.settingsFlow.first()
                serviceScope.launch {
                    coordinator.updateVoiceState { it.copy(isProcessingVoice = false) }
                    if (request.speak && settings.autoSpeakResponses && replyText.isNotBlank()) {
                        coordinator.conversationStateManager.updatePhase(
                            AssistantPhase.Speaking(replyText.take(80))
                        )
                        coordinator.updateVoiceState {
                            it.copy(isSpeaking = true, statusText = "Respondendo gatilho LAN…")
                        }
                        speakAndAwait(replyText)
                        coordinator.updateVoiceState { it.copy(isSpeaking = false) }
                    }
                    if (wasListening && !isPaused) {
                        scheduleNextListen(delayMs = 350L)
                    } else if (lanServer.state.value.isRunning) {
                        val endpoint = lanServer.state.value.endpointUrl
                        coordinator.conversationStateManager.updatePhase(
                            AssistantPhase.AwaitingLanTrigger(endpoint)
                        )
                        updateNotification("Aguardando gatilhos LAN ($endpoint)")
                    }
                }

                LanTriggerResponse(
                    statusCode = if (isError) 500 else 200,
                    body = JSONObject()
                        .put("status", if (isError) "error" else "ok")
                        .put("session_id", coordinator.conversationStateManager.activeSessionId.value)
                        .put("engine", engineUsed)
                        .put("model", modelUsed)
                        .put("latency_ms", latencyMs)
                        .put("reply", replyText)
                )
            }
        }
    }

    private suspend fun speakAndAwait(text: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            speechController.speak(text) {
                if (cont.isActive) {
                    cont.resume(Unit)
                }
            }
            cont.invokeOnCancellation {
                speechController.stopSpeaking()
            }
        }
    }

    private fun promoteToForeground(statusText: String, includeMicrophone: Boolean) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgType = when {
                includeMicrophone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }

                includeMicrophone -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, notification, fgType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForegroundStarted = true
    }

    private fun buildNotificationSummary(settings: JarvisSettings, micActive: Boolean): String {
        return when {
            micActive && settings.lanTriggerEnabled ->
                "Voz contínua + Gatilhos LAN :${settings.lanTriggerPort}"
            micActive ->
                if (settings.wakeWordEnabled) "Aguardando \"${settings.wakeWord}\"…" else "Ouvindo continuamente…"
            settings.lanTriggerEnabled ->
                "Aguardando gatilhos LAN na porta ${settings.lanTriggerPort}"
            else ->
                "A.O.Jarvis em segundo plano"
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopSelfAndCleanup() {
        isPaused = true
        releaseWakeLock()
        settingsObserverJob?.cancel()
        networkObserverJob?.cancel()
        lanStateMirrorJob?.cancel()
        waveformMirrorJob?.cancel()
        restartJob?.cancel()
        dispatchJob?.cancel()
        lanServer.stop()
        speechController.stopListening()
        speechController.stopSpeaking()
        coordinator.conversationStateManager.updatePhase(AssistantPhase.Idle, followUpWindowActive = false)
        coordinator.updateLanServerState(LanServerState())
        coordinator.updateVoiceState {
            it.copy(
                isBackgroundRunning = false,
                isBackgroundPaused = false,
                isListening = false,
                isSpeaking = false,
                isProcessingVoice = false,
                partialTranscript = "",
                rmsDb = 0f,
                waveformSamples = List(NativeSpeechController.WAVEFORM_BAR_COUNT) { 0.08f },
                awaitingFollowUpCommand = false,
                statusText = "Pronto para escutar"
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bg_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.bg_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(statusText: String) {
        if (!isForegroundStarted) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, JarvisBackgroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePending = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, JarvisBackgroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val listenNowIntent = Intent(this, JarvisBackgroundService::class.java).apply {
            action = ACTION_LISTEN_NOW
        }
        val listenNowPending = PendingIntent.getService(
            this,
            3,
            listenNowIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleLabel = if (isPaused) getString(R.string.bg_action_resume) else getString(R.string.bg_action_pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bg_notif_title))
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, getString(R.string.bg_action_listen_now), listenNowPending)
            .addAction(0, toggleLabel, togglePending)
            .addAction(0, getString(R.string.bg_action_stop), stopPending)
            .build()
    }

    override fun onDestroy() {
        releaseWakeLock()
        settingsObserverJob?.cancel()
        networkObserverJob?.cancel()
        lanStateMirrorJob?.cancel()
        waveformMirrorJob?.cancel()
        restartJob?.cancel()
        dispatchJob?.cancel()
        lanServer.destroy()
        speechController.stopListening()
        speechController.stopSpeaking()
        serviceScope.cancel()
        coordinator.conversationStateManager.updatePhase(AssistantPhase.Idle, followUpWindowActive = false)
        coordinator.updateLanServerState(LanServerState())
        coordinator.updateVoiceState {
            it.copy(
                isBackgroundRunning = false,
                isBackgroundPaused = false,
                isListening = false,
                isSpeaking = false
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "openjarvis_continuous_voice"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_START = "com.example.openjarvis.ACTION_START"
        const val ACTION_PAUSE = "com.example.openjarvis.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.openjarvis.ACTION_RESUME"
        const val ACTION_LISTEN_NOW = "com.example.openjarvis.ACTION_LISTEN_NOW"
        const val ACTION_STOP = "com.example.openjarvis.ACTION_STOP"
    }
}
