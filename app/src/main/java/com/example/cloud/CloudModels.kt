package com.example.cloud

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val key: String = "",
    val content: String = "",
    val category: String = "Geral",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudVoiceCommand(
    @DocumentId val id: String = "",
    val userId: String = "",
    val triggerPhrase: String = "",
    val actionType: String = "AGENT_PROMPT",
    val actionPayload: String = "",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val lastMessagePreview: String? = null,
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

data class CloudSyncSummary(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val memoriesCount: Int = 0,
    val commandsCount: Int = 0,
    val sessionsCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String = "Sincronização em tempo real ativa",
    val errorMessage: String? = null
)
