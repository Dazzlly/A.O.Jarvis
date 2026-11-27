package com.example.engine

import android.graphics.Bitmap
import com.example.core.AppIntegrationManager
import com.example.core.AppSelfController
import com.example.core.BatteryStatusSnapshot
import com.example.core.DeviceSettingsController
import com.example.data.ChatMessageEntity
import com.example.data.DEFAULT_SESSION_ID
import com.example.data.JarvisDatabase
import com.example.data.JarvisSettings
import com.example.data.RoutingMode
import com.example.data.SettingsDataStore
import com.example.data.TraceEntity
import com.example.data.VoiceCommandEntity
import com.example.vision.ScreenVisionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class OpenJarvisOrchestrator(
    private val database: JarvisDatabase,
    private val settingsDataStore: SettingsDataStore,
    val networkEngineManager: NetworkEngineManager,
    val offlineEngine: OfflineCommandEngine,
    val appSelfController: AppSelfController,
    val integrationManager: AppIntegrationManager,
    val deviceSettingsController: DeviceSettingsController,
    val screenVisionManager: ScreenVisionManager,
    private val isBackgroundRunningProvider: () -> Boolean = { false },
    private val batteryStatusProvider: () -> BatteryStatusSnapshot? = { null }
) {
    val geminiEngine: GeminiInferenceEngine get() = networkEngineManager.geminiEngine
    val ollamaEngine: OllamaInferenceEngine get() = networkEngineManager.ollamaEngine

    fun dispatchPrompt(
        rawPrompt: String,
        sourceTag: String = "TEXT",
        sessionId: String = DEFAULT_SESSION_ID,
        routingOverride: RoutingMode? = null,
        attachedBitmap: Bitmap? = null
    ): Flow<InferenceStreamEvent> = flow {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return@flow

        val startMs = System.currentTimeMillis()
        val settings: JarvisSettings = settingsDataStore.settingsFlow.first()
        val effectiveRoutingMode = routingOverride ?: settings.routingMode

        // Persist user message immediately scoped to active conversation session
        val userMessageId = database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "USER",
                content = trimmed,
                engineType = sourceTag
            )
        )

        // 1. Always evaluate Offline Commands, App Integrations & Self-Navigation first
        val offlineMatch = offlineEngine.tryHandle(trimmed)
        val effectivePrompt = when (offlineMatch) {
            is OfflineMatchResult.Handled -> {
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                recordAssistantAndTrace(
                    sessionId = sessionId,
                    text = offlineMatch.responseText,
                    engineType = EngineType.OFFLINE,
                    modelName = offlineMatch.toolName,
                    promptPreview = trimmed,
                    latencyMs = elapsed,
                    tokensPerSec = 0.0,
                    success = true
                )
                emit(
                    InferenceStreamEvent.Completed(
                        fullText = offlineMatch.responseText,
                        engineType = EngineType.OFFLINE,
                        modelName = offlineMatch.toolName,
                        latencyMs = elapsed
                    )
                )
                return@flow
            }

            is OfflineMatchResult.RedirectToAgentPrompt -> offlineMatch.expandedPrompt
            OfflineMatchResult.NotMatched -> trimmed
        }

        // 2. If forced OFFLINE mode and no command matched
        if (effectiveRoutingMode == RoutingMode.OFFLINE) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val fallbackMsg = "Modo Somente Offline ativo. Nenhum comando local correspondeu a \"$trimmed\". Diga \"status do sistema\", \"abrir WhatsApp\", \"ir para Motores\", \"me guie pelo app\" ou crie um comando personalizado na aba Comandos."
            recordAssistantAndTrace(
                sessionId = sessionId,
                text = fallbackMsg,
                engineType = EngineType.OFFLINE,
                modelName = "offline:router",
                promptPreview = trimmed,
                latencyMs = elapsed,
                tokensPerSec = 0.0,
                success = true
            )
            emit(
                InferenceStreamEvent.Completed(
                    fullText = fallbackMsg,
                    engineType = EngineType.OFFLINE,
                    modelName = "offline:router",
                    latencyMs = elapsed
                )
            )
            return@flow
        }

        // Gather session-scoped conversation history (excluding exact inserted user message) & memories
        val history = database.chatMessageDao().getRecentMessagesForSession(sessionId, 16)
            .filter { it.id != userMessageId && !it.isError }
            .reversed()
            .map {
                ConversationTurn(
                    role = if (it.role == "USER") "user" else "assistant",
                    content = it.content
                )
            }
        val memories = database.memoryDao().getAllMemories()
        val screenState = screenVisionManager.state.value
        val isScreenSharing = screenState.isSharing
        val effectiveBitmap = attachedBitmap ?: if (isScreenSharing || screenState.hasFrame) {
            screenVisionManager.getLatestFrameCopy()
        } else {
            null
        }
        if (effectiveBitmap != null) {
            screenVisionManager.markAnalyzing(true)
        }

        val selfAwarenessContext = appSelfController.buildSelfAwarenessPrompt(
            settings = settings,
            isBackgroundRunning = isBackgroundRunningProvider(),
            isScreenSharing = isScreenSharing,
            lastVisualSummary = screenState.lastVisualSummary,
            batteryStatus = batteryStatusProvider(),
            deviceSettings = deviceSettingsController.readSnapshot()
        )

        // 3. Delegate routing between Local Ollama (Wi-Fi) and Gemini API (Firebase AI Logic + Vision)
        networkEngineManager.streamInference(
            prompt = effectivePrompt,
            history = history,
            memories = memories,
            settings = settings,
            routingOverride = routingOverride,
            selfAwarenessContext = selfAwarenessContext,
            attachedBitmap = effectiveBitmap
        ).collect { event ->
            when (event) {
                is InferenceStreamEvent.Chunk -> emit(event)
                is InferenceStreamEvent.Completed -> {
                    // Intercept & execute any [[ACTION:...]] self-navigation, device-settings or app-integration tags
                    val parsed = appSelfController.parseActions(event.fullText)
                    val executedNotes = if (parsed.actions.isNotEmpty()) {
                        appSelfController.executeParsedActions(
                            actions = parsed.actions,
                            integrationManager = integrationManager,
                            deviceSettingsController = deviceSettingsController,
                            onLearnCommand = { trigger, reply ->
                                database.voiceCommandDao().insertCommand(
                                    VoiceCommandEntity(
                                        triggerPhrase = trigger,
                                        actionType = "OFFLINE_RESPONSE",
                                        actionPayload = reply,
                                        enabled = true
                                    )
                                )
                            }
                        )
                    } else {
                        emptyList()
                    }
                    val finalReplyText = parsed.cleanText.ifBlank {
                        executedNotes.joinToString(" ").ifBlank { "Ação executada." }
                    }
                    if (effectiveBitmap != null) {
                        screenVisionManager.recordVisualInterpretation(
                            summary = finalReplyText,
                            engineLabel = event.modelName
                        )
                    }

                    recordAssistantAndTrace(
                        sessionId = sessionId,
                        text = finalReplyText,
                        engineType = event.engineType,
                        modelName = event.modelName,
                        promptPreview = trimmed,
                        latencyMs = event.latencyMs,
                        tokensPerSec = event.tokensPerSec,
                        success = true
                    )
                    emit(event.copy(fullText = finalReplyText))
                }

                is InferenceStreamEvent.Error -> {
                    if (effectiveBitmap != null) {
                        screenVisionManager.markAnalyzing(false)
                    }
                    recordAssistantAndTrace(
                        sessionId = sessionId,
                        text = event.message,
                        engineType = event.engineType,
                        modelName = event.modelName,
                        promptPreview = trimmed,
                        latencyMs = event.latencyMs,
                        tokensPerSec = 0.0,
                        success = false,
                        errorMessage = event.message
                    )
                    emit(event)
                }
            }
        }
    }

    suspend fun recordLiveTrace(durationMs: Long, voiceName: String, error: String?) {
        database.traceDao().insertTrace(
            TraceEntity(
                engineType = EngineType.GEMINI_LIVE.name,
                modelName = "gemini-3.8-live ($voiceName)",
                promptPreview = "Sessão de voz em tempo real (Live API)",
                latencyMs = durationMs,
                tokensPerSec = 0.0,
                success = error == null,
                errorMessage = error
            )
        )
    }

    private suspend fun recordAssistantAndTrace(
        sessionId: String,
        text: String,
        engineType: EngineType,
        modelName: String,
        promptPreview: String,
        latencyMs: Long,
        tokensPerSec: Double,
        success: Boolean,
        errorMessage: String? = null
    ) {
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "ASSISTANT",
                content = text,
                engineType = engineType.name,
                modelName = modelName,
                latencyMs = latencyMs,
                tokensPerSec = tokensPerSec,
                isError = !success
            )
        )
        database.traceDao().insertTrace(
            TraceEntity(
                engineType = engineType.name,
                modelName = modelName,
                promptPreview = promptPreview.take(90),
                latencyMs = latencyMs,
                tokensPerSec = tokensPerSec,
                success = success,
                errorMessage = errorMessage
            )
        )
    }
}
