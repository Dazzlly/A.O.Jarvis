package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow

/**
 * Repository Pattern sobre a tabela Room local `voice_command_logs`
 * para registrar e consultar todo o histórico de interações por voz no dispositivo.
 */
class VoiceCommandHistoryRepository(
    private val dao: VoiceCommandLogDao
) {
    fun observeRecentLogs(limit: Int = 200): Flow<List<VoiceCommandLogEntity>> =
        dao.observeRecentLogs(limit)

    fun searchLogs(query: String): Flow<List<VoiceCommandLogEntity>> =
        dao.searchLogs(query.trim())

    suspend fun recordInteraction(
        transcript: String,
        responsePreview: String,
        source: String,
        engineUsed: String,
        modelName: String,
        confidence: Float = -1f,
        latencyMs: Long = 0L,
        success: Boolean = true,
        errorMessage: String? = null
    ): Long {
        val cleanTranscript = transcript.trim()
        if (cleanTranscript.isEmpty()) return -1L
        return dao.insertLog(
            VoiceCommandLogEntity(
                transcript = cleanTranscript,
                responsePreview = responsePreview.trim().take(360),
                source = source,
                engineUsed = engineUsed,
                modelName = modelName,
                confidence = confidence,
                latencyMs = latencyMs,
                success = success,
                errorMessage = errorMessage?.take(240),
                appVersion = "${BuildConfig.VERSION_NAME} (b${BuildConfig.VERSION_CODE})",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteLog(id: Long) {
        dao.deleteLogById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
