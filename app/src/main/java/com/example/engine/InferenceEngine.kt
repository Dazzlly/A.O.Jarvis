package com.example.engine

enum class EngineType(val label: String) {
    GEMINI("Gemini Cloud"),
    OLLAMA("Ollama Local"),
    OFFLINE("Motor Offline"),
    GEMINI_LIVE("Gemini Live")
}

data class ConversationTurn(
    val role: String, // "user" or "assistant"
    val content: String
)

data class OllamaModelInfo(
    val name: String,
    val sizeBytes: Long = 0L,
    val parameterSize: String = "",
    val quantization: String = ""
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0L) return ""
            val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) String.format("%.1f GB", gb)
            else String.format("%.0f MB", sizeBytes / (1024.0 * 1024.0))
        }
}

data class OllamaHealthStatus(
    val isReachable: Boolean = false,
    val latencyMs: Long = 0L,
    val models: List<OllamaModelInfo> = emptyList(),
    val errorMessage: String? = null,
    val checkedAt: Long = 0L
)

sealed interface InferenceStreamEvent {
    data class Chunk(
        val accumulatedText: String,
        val engineType: EngineType,
        val modelName: String
    ) : InferenceStreamEvent

    data class Completed(
        val fullText: String,
        val engineType: EngineType,
        val modelName: String,
        val latencyMs: Long,
        val tokensPerSec: Double = 0.0
    ) : InferenceStreamEvent

    data class Error(
        val message: String,
        val engineType: EngineType,
        val modelName: String,
        val latencyMs: Long
    ) : InferenceStreamEvent
}
