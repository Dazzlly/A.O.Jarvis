package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationSessionDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ConversationSessionEntity)

    @Query("UPDATE conversation_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionMeta(sessionId: String, title: String, updatedAt: Long)

    @Query("UPDATE conversation_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long)

    @Query("DELETE FROM conversation_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int = 16): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun countMessagesInSession(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)
}

@Dao
interface VoiceCommandDao {
    @Query("SELECT * FROM voice_commands ORDER BY createdAt DESC")
    fun observeCommands(): Flow<List<VoiceCommandEntity>>

    @Query("SELECT * FROM voice_commands WHERE enabled = 1")
    suspend fun getEnabledCommands(): List<VoiceCommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: VoiceCommandEntity): Long

    @Update
    suspend fun updateCommand(command: VoiceCommandEntity)

    @Query("DELETE FROM voice_commands WHERE id = :id")
    suspend fun deleteCommand(id: Long)
}

@Dao
interface TraceDao {
    @Query("SELECT * FROM traces ORDER BY timestamp DESC LIMIT 100")
    fun observeTraces(): Flow<List<TraceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrace(trace: TraceEntity): Long

    @Query("DELETE FROM traces")
    suspend fun clearAll()
}
