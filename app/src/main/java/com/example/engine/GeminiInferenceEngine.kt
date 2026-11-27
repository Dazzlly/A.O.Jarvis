package com.example.engine

import android.graphics.Bitmap
import android.util.Log
import com.example.data.JarvisSettings
import com.example.data.MemoryEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiInferenceEngine"

class GeminiInferenceEngine {

    private fun buildModel(
        settings: JarvisSettings,
        memories: List<MemoryEntity>,
        selfAwarenessContext: String
    ) = run {
        val memoryBlock = if (memories.isNotEmpty()) {
            buildString {
                append("\n\nMemórias locais do usuário (use como contexto persistente):\n")
                memories.take(15).forEach { mem ->
                    append("- [${mem.category}] ${mem.key}: ${mem.content}\n")
                }
            }
        } else ""

        val baseInstruction = settings.systemPrompt.ifBlank {
            "Você é o A.O.Jarvis, um assistente pessoal inteligente em português do Brasil."
        }
        val fullInstruction = baseInstruction + selfAwarenessContext + memoryBlock

        Firebase.ai.generativeModel(
            modelName = settings.geminiModel.ifBlank { "gemini-3.8-flash" },
            generationConfig = generationConfig {
                temperature = settings.temperature
            },
            systemInstruction = content { text(fullInstruction) }
        )
    }

    /**
     * Sanitiza o histórico para a API do Gemini:
     * - Sempre inicia com turno "user"
     * - Alterna estritamente "user" -> "model" -> "user" -> "model"
     * - Nunca termina em "user" (pois o prompt atual será enviado logo em seguida)
     */
    private fun sanitizeChatHistory(history: List<ConversationTurn>): List<Content> {
        val merged = mutableListOf<Pair<String, String>>()
        for (turn in history) {
            val text = turn.content.trim()
            if (text.isEmpty()) continue
            val role = if (turn.role == "user") "user" else "model"
            if (merged.isEmpty()) {
                if (role == "user") {
                    merged.add(role to text)
                }
            } else {
                val last = merged.last()
                if (last.first == role) {
                    merged[merged.lastIndex] = role to "${last.second}\n$text"
                } else {
                    merged.add(role to text)
                }
            }
        }

        // History sent to startChat must end with a "model" turn so sendMessageStream(prompt) alternates cleanly
        while (merged.isNotEmpty() && merged.last().first != "model") {
            merged.removeAt(merged.lastIndex)
        }

        // Limit to 12 most recent turns while keeping leading role == "user"
        val trimmed = if (merged.size > 12) merged.takeLast(12).toMutableList() else merged
        while (trimmed.isNotEmpty() && trimmed.first().first != "user") {
            trimmed.removeAt(0)
        }

        return trimmed.map { (role, text) ->
            content(role = role) { text(text) }
        }
    }

    fun streamResponse(
        prompt: String,
        history: List<ConversationTurn>,
        memories: List<MemoryEntity>,
        settings: JarvisSettings,
        selfAwarenessContext: String = "",
        attachedBitmap: Bitmap? = null
    ): Flow<InferenceStreamEvent> = flow {
        val startMs = System.currentTimeMillis()
        val modelName = settings.geminiModel.ifBlank { "gemini-3.8-flash" }
        val accumulated = StringBuilder()

        try {
            val model = buildModel(settings, memories, selfAwarenessContext)
            val chatHistory = sanitizeChatHistory(history)

            withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                val streamFlow = if (attachedBitmap != null && !attachedBitmap.isRecycled) {
                    val visionPrompt = buildString {
                        append("[IMAGEM DA TELA DO USUÁRIO COMPARTILHADA EM TEMPO REAL]\n")
                        append("Analise atentamente o conteúdo visível na tela anexada (textos, apps, botões, imagens, erros ou contexto) para responder ao pedido do usuário.\n\n")
                        append(prompt)
                    }
                    chat.sendMessageStream(
                        content(role = "user") {
                            image(attachedBitmap)
                            text(visionPrompt)
                        }
                    )
                } else {
                    chat.sendMessageStream(prompt)
                }

                streamFlow.collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        val visiblePreview = accumulated.toString()
                            .substringBefore("[[ACTION")
                            .trimEnd()
                        emit(
                            InferenceStreamEvent.Chunk(
                                accumulatedText = visiblePreview,
                                engineType = EngineType.GEMINI,
                                modelName = modelName
                            )
                        )
                    }
                }
            }

            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val finalText = accumulated.toString().trim()
            if (finalText.isNotEmpty()) {
                val approxTokens = (finalText.length / 4.0).coerceAtLeast(1.0)
                val tokPerSec = approxTokens / (elapsedMs / 1000.0)
                emit(
                    InferenceStreamEvent.Completed(
                        fullText = finalText,
                        engineType = EngineType.GEMINI,
                        modelName = modelName,
                        latencyMs = elapsedMs,
                        tokensPerSec = tokPerSec
                    )
                )
            } else {
                emit(
                    InferenceStreamEvent.Error(
                        message = "O modelo Gemini retornou uma resposta vazia.",
                        engineType = EngineType.GEMINI,
                        modelName = modelName,
                        latencyMs = elapsedMs
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            val elapsedMs = System.currentTimeMillis() - startMs
            Log.e(TAG, "Gemini streaming timed out", e)
            emit(
                InferenceStreamEvent.Error(
                    message = "Tempo limite excedido (60s) ao consultar o Gemini. Tente novamente.",
                    engineType = EngineType.GEMINI,
                    modelName = modelName,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsedMs = System.currentTimeMillis() - startMs
            Log.e(TAG, "Gemini streaming failed", e)
            emit(
                InferenceStreamEvent.Error(
                    message = e.localizedMessage ?: "Falha na chamada ao Gemini via Firebase AI Logic.",
                    engineType = EngineType.GEMINI,
                    modelName = modelName,
                    latencyMs = elapsedMs
                )
            )
        }
    }
}
