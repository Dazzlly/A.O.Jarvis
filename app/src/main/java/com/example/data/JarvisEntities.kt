package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String = DEFAULT_SESSION_ID,
    val role: String, // "USER", "ASSISTANT", "SYSTEM"
    val content: String,
    val engineType: String, // "GEMINI", "OLLAMA", "OFFLINE", "GEMINI_LIVE", "TEXT", "VOICE_STT", "VOICE_BG", "LAN_TRIGGER"
    val modelName: String = "",
    val latencyMs: Long = 0L,
    val tokensPerSec: Double = 0.0,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

const val DEFAULT_SESSION_ID = "default"

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val key: String,
    val content: String,
    val category: String = "Geral",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_commands")
data class VoiceCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val triggerPhrase: String,
    val actionType: String, // "OFFLINE_RESPONSE", "LOCAL_WEBHOOK", "AGENT_PROMPT"
    val actionPayload: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_voice_commands")
data class OfflineVoiceCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val triggerPhrase: String,
    val aliases: String = "",
    val actionKey: String, // "TORCH_ON", "TORCH_OFF", "POWER_SAVE", "BATTERY_STATUS", "DEVICE_SETTING"
    val actionArgument: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "traces")
data class TraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val engineType: String, // "GEMINI", "OLLAMA", "OFFLINE", "GEMINI_LIVE"
    val modelName: String,
    val promptPreview: String,
    val latencyMs: Long,
    val tokensPerSec: Double = 0.0,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_command_logs")
data class VoiceCommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transcript: String,
    val responsePreview: String,
    val source: String, // "VOICE_STT", "VOICE_BG", "GEMINI_LIVE"
    val engineUsed: String, // "GEMINI", "OLLAMA", "OFFLINE", "GEMINI_LIVE"
    val modelName: String = "",
    val confidence: Float = -1f,
    val latencyMs: Long = 0L,
    val success: Boolean = true,
    val errorMessage: String? = null,
    val appVersion: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
