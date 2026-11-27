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
