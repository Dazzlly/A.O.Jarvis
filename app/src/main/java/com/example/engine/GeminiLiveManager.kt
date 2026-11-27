package com.example.engine

import android.annotation.SuppressLint
import android.util.Log
import com.example.data.JarvisSettings
import com.example.data.MemoryEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiLiveManager"
private const val LIVE_MODEL_NAME = "gemini-3.8-live"

sealed interface LiveVoiceState {
    data object Idle : LiveVoiceState
    data object Connecting : LiveVoiceState
    data class Active(val voiceName: String, val startedAt: Long) : LiveVoiceState
    data class Error(val message: String) : LiveVoiceState
}

@OptIn(PublicPreviewAPI::class)
class GeminiLiveManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var liveSession: LiveSession? = null
    private var sessionJob: Job? = null

    private val _state = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
    val state: StateFlow<LiveVoiceState> = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startSession(
        settings: JarvisSettings,
        memories: List<MemoryEntity>,
        onSessionEnded: (durationMs: Long, error: String?) -> Unit = { _, _ -> }
    ) {
        if (_state.value is LiveVoiceState.Connecting || _state.value is LiveVoiceState.Active) {
            return
        }

        val voiceName = settings.liveVoiceName.ifBlank { "CHARON" }
        _state.value = LiveVoiceState.Connecting
        val startMs = System.currentTimeMillis()

        sessionJob = scope.launch {
            try {
                val memoryContext = if (memories.isNotEmpty()) {
                    "\nContexto salvo do usuário:\n" +
                        memories.take(10).joinToString("\n") { "- ${it.key}: ${it.content}" }
                } else ""

                val liveConfig = liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice(voiceName))
                }

                val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                    modelName = LIVE_MODEL_NAME,
                    generationConfig = liveConfig,
                    systemInstruction = content {
                        text(
                            "${settings.systemPrompt}\nResponda de forma natural, conversacional e breve por voz em português do Brasil.$memoryContext"
                        )
                    }
                )

                val session = withTimeout(30_000L) {
                    liveModel.connect()
                }
                liveSession = session
                _state.value = LiveVoiceState.Active(
                    voiceName = voiceName,
                    startedAt = System.currentTimeMillis()
                )

                // Starts duplex microphone streaming and real-time audio playback
                session.startAudioConversation()
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Live connection timed out", e)
                val msg = "Tempo limite excedido ao conectar com o Gemini Live."
                _state.value = LiveVoiceState.Error(msg)
                onSessionEnded(System.currentTimeMillis() - startMs, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Live session error", e)
                val msg = e.localizedMessage
                    ?: "Não foi possível iniciar a conversa ao vivo com Gemini Live."
                _state.value = LiveVoiceState.Error(msg)
                onSessionEnded(System.currentTimeMillis() - startMs, msg)
            }
        }
    }

    fun stopSession(onClosed: (durationMs: Long) -> Unit = {}) {
        val startedAt = (_state.value as? LiveVoiceState.Active)?.startedAt
        sessionJob?.cancel()
        sessionJob = null

        val sessionToClose = liveSession
        liveSession = null
        _state.value = LiveVoiceState.Idle

        if (sessionToClose != null) {
            scope.launch {
                runCatching {
                    sessionToClose.stopAudioConversation()
                    sessionToClose.close()
                }.onFailure {
                    Log.w(TAG, "Error closing LiveSession", it)
                }
            }
        }
        if (startedAt != null) {
            onClosed((System.currentTimeMillis() - startedAt).coerceAtLeast(1L))
        }
    }

    fun clearError() {
        if (_state.value is LiveVoiceState.Error) {
            _state.value = LiveVoiceState.Idle
        }
    }
}
