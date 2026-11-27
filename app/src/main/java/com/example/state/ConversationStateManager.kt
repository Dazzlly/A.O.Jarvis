package com.example.state

import android.graphics.Bitmap
import com.example.data.ChatMessageEntity
import com.example.data.ConversationSessionEntity
import com.example.data.DEFAULT_SESSION_ID
import com.example.data.JarvisDatabase
import com.example.data.RoutingMode
import com.example.engine.EngineType
import com.example.engine.InferenceStreamEvent
import com.example.engine.OpenJarvisOrchestrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class CommandSource(val tag: String, val label: String) {
    TEXT("TEXT", "Texto"),
    VOICE_STT("VOICE_STT", "Voz Nativa"),
    VOICE_BG("VOICE_BG", "Voz Contínua"),
    LAN_TRIGGER("LAN_TRIGGER", "Gatilho LAN"),
    SCREEN_VISION("SCREEN_VISION", "Visão de Tela"),
    TOOL_TEST("TOOL_TEST", "Comando")
}

sealed interface AssistantPhase {
    data object Idle : AssistantPhase
    data class ListeningWakeWord(val wakeWord: String) : AssistantPhase
    data class ListeningCommand(val isFollowUp: Boolean) : AssistantPhase
    data class Processing(
        val source: CommandSource,
        val engine: EngineType,
        val promptPreview: String
    ) : AssistantPhase

    data class Speaking(val preview: String) : AssistantPhase
    data class AwaitingLanTrigger(val endpoint: String) : AssistantPhase
    data object Paused : AssistantPhase
    data class Error(val message: String) : AssistantPhase

    fun toDisplayLabel(): String = when (this) {
        Idle -> "Pronto"
        is ListeningWakeWord -> "Aguardando \"$wakeWord\""
        is ListeningCommand -> if (isFollowUp) "Conversa contínua (pode falar)" else "Ouvindo comando…"
        is Processing -> "Processando (${source.label} · ${engine.label})"
        is Speaking -> "Respondendo por voz"
        is AwaitingLanTrigger -> "Aguardando gatilho LAN ($endpoint)"
        Paused -> "Pausado"
        is Error -> "Atenção: $message"
    }
}

data class AssistantState(
    val phase: AssistantPhase = AssistantPhase.Idle,
    val activeSessionId: String = DEFAULT_SESSION_ID,
    val turnCountInSession: Int = 0,
    val followUpWindowActive: Boolean = false,
    val lastSource: CommandSource? = null,
    val lastEngineUsed: EngineType? = null,
    val lastCommandTimestamp: Long = 0L
)

/**
 * Gerenciador central de estado para comandos contínuos e histórico persistente
 * de sessões de conversa entre interações (UI, voz em background e gatilhos LAN).
 */
class ConversationStateManager(
    private val database: JarvisDatabase,
    private val orchestrator: OpenJarvisOrchestrator
) {
    private val executionMutex = Mutex()

    private val _activeSessionId = MutableStateFlow(DEFAULT_SESSION_ID)
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _assistantState = MutableStateFlow(AssistantState())
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    val sessions: Flow<List<ConversationSessionEntity>> =
        database.conversationSessionDao().observeSessions()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: Flow<List<ChatMessageEntity>> =
        _activeSessionId.flatMapLatest { sessionId ->
            database.chatMessageDao().observeMessagesForSession(sessionId)
        }

    fun updatePhase(phase: AssistantPhase, followUpWindowActive: Boolean? = null) {
        _assistantState.update { current ->
            current.copy(
                phase = phase,
                followUpWindowActive = followUpWindowActive ?: current.followUpWindowActive
            )
        }
    }

    suspend fun ensureDefaultSessionExists() {
        val dao = database.conversationSessionDao()
        if (dao.getSession(DEFAULT_SESSION_ID) == null) {
            dao.upsertSession(
                ConversationSessionEntity(
                    id = DEFAULT_SESSION_ID,
                    title = "Conversa Principal"
                )
            )
        }
    }

    suspend fun createNewSession(customTitle: String? = null): String {
        val sessionId = "sess_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        val title = customTitle?.trim()?.take(36)?.ifBlank { null } ?: "Nova Conversa"
        database.conversationSessionDao().upsertSession(
            ConversationSessionEntity(
                id = sessionId,
                title = title,
                createdAt = now,
                updatedAt = now
            )
        )
        _activeSessionId.value = sessionId
        _assistantState.update {
            it.copy(
                activeSessionId = sessionId,
                turnCountInSession = 0,
                followUpWindowActive = false
            )
        }
        return sessionId
    }

    suspend fun switchSession(sessionId: String) {
        val existing = database.conversationSessionDao().getSession(sessionId) ?: return
        val count = database.chatMessageDao().countMessagesInSession(existing.id)
        _activeSessionId.value = existing.id
        _assistantState.update {
            it.copy(
                activeSessionId = existing.id,
                turnCountInSession = count / 2
            )
        }
    }

    suspend fun deleteSession(sessionId: String) {
        if (sessionId == DEFAULT_SESSION_ID) {
            clearActiveSession()
            return
        }
        database.chatMessageDao().clearSession(sessionId)
        database.conversationSessionDao().deleteSession(sessionId)
        if (_activeSessionId.value == sessionId) {
            ensureDefaultSessionExists()
            switchSession(DEFAULT_SESSION_ID)
        }
    }

    suspend fun clearActiveSession() {
        val currentId = _activeSessionId.value
        database.chatMessageDao().clearSession(currentId)
        val now = System.currentTimeMillis()
        if (currentId == DEFAULT_SESSION_ID) {
            database.conversationSessionDao().updateSessionMeta(
                sessionId = currentId,
                title = "Conversa Principal",
                updatedAt = now
            )
        } else {
            database.conversationSessionDao().touchSession(currentId, now)
        }
        _assistantState.update {
            it.copy(turnCountInSession = 0, followUpWindowActive = false)
        }
    }

    /**
     * Executa um comando de forma serializada (Mutex) mantendo o histórico da sessão
     * ativa atualizado entre interações contínuas por texto, voz ou rede local.
     */
    fun executeCommand(
        prompt: String,
        source: CommandSource,
        routingOverride: RoutingMode? = null,
        sessionIdOverride: String? = null,
        attachedBitmap: Bitmap? = null
    ): Flow<InferenceStreamEvent> = flow {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return@flow

        executionMutex.withLock {
            ensureDefaultSessionExists()
            val targetSessionId = sessionIdOverride ?: _activeSessionId.value
            val sessionDao = database.conversationSessionDao()
            val existingSession = sessionDao.getSession(targetSessionId)
            val now = System.currentTimeMillis()

            if (existingSession == null) {
                sessionDao.upsertSession(
                    ConversationSessionEntity(
                        id = targetSessionId,
                        title = cleanPrompt.take(36),
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else if (
                existingSession.title == "Nova Conversa" ||
                (existingSession.id == DEFAULT_SESSION_ID &&
                    database.chatMessageDao().countMessagesInSession(targetSessionId) == 0)
            ) {
                val autoTitle = cleanPrompt.replace(Regex("\\s+"), " ").take(34)
                sessionDao.updateSessionMeta(
                    sessionId = targetSessionId,
                    title = if (existingSession.id == DEFAULT_SESSION_ID) "Conversa Principal" else autoTitle,
                    updatedAt = now
                )
            } else {
                sessionDao.touchSession(targetSessionId, now)
            }

            _assistantState.update {
                it.copy(
                    phase = AssistantPhase.Processing(
                        source = source,
                        engine = EngineType.GEMINI,
                        promptPreview = cleanPrompt.take(60)
                    ),
                    lastSource = source,
                    lastCommandTimestamp = now
                )
            }

            orchestrator.dispatchPrompt(
                rawPrompt = cleanPrompt,
                sourceTag = source.tag,
                sessionId = targetSessionId,
                routingOverride = routingOverride,
                attachedBitmap = attachedBitmap
            ).collect { event ->
                when (event) {
                    is InferenceStreamEvent.Chunk -> {
                        _assistantState.update {
                            it.copy(
                                phase = AssistantPhase.Processing(
                                    source = source,
                                    engine = event.engineType,
                                    promptPreview = cleanPrompt.take(60)
                                ),
                                lastEngineUsed = event.engineType
                            )
                        }
                        emit(event)
                    }

                    is InferenceStreamEvent.Completed -> {
                        val updatedCount = database.chatMessageDao().countMessagesInSession(targetSessionId)
                        sessionDao.touchSession(targetSessionId, System.currentTimeMillis())
                        _assistantState.update {
                            it.copy(
                                phase = AssistantPhase.Idle,
                                turnCountInSession = updatedCount / 2,
                                lastEngineUsed = event.engineType
                            )
                        }
                        emit(event)
                    }

                    is InferenceStreamEvent.Error -> {
                        _assistantState.update {
                            it.copy(
                                phase = AssistantPhase.Error(event.message),
                                lastEngineUsed = event.engineType
                            )
                        }
                        emit(event)
                    }
                }
            }
        }
    }
}
