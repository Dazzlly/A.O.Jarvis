package com.example.cloud

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private val VALID_ACTION_TYPES = setOf("OFFLINE_RESPONSE", "LOCAL_WEBHOOK", "AGENT_PROMPT")
private val DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

class JarvisCloudRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("Usuário precisa estar autenticado com Google antes de acessar o Firestore.")
        }
        return uid
    }

    private fun sanitizeDocId(rawId: String, fallbackPrefix: String): String {
        val cleaned = rawId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return if (cleaned.isNotEmpty() && DOC_ID_REGEX.matches(cleaned)) {
            cleaned
        } else {
            "${fallbackPrefix}_${System.currentTimeMillis()}"
        }
    }

    suspend fun upsertUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val docPath = "users/$uid"
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "Sessão alterada; gravação cancelada." }
            val docRef = db.collection("users").document(uid)
            val displayName = (user.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "Usuário A.O.Jarvis").take(100)
            val email = user.email?.trim()?.take(200)?.takeIf { it.isNotBlank() }
            val photoUrl = user.photoUrl?.toString()?.trim()?.take(500)?.takeIf { it.isNotBlank() }

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = buildMap<String, Any> {
                    put("displayName", displayName)
                    if (email != null) put("email", email)
                    if (photoUrl != null) put("photoUrl", photoUrl)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            } else {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", displayName)
                    if (email != null) put("email", email)
                    if (photoUrl != null) put("photoUrl", photoUrl)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // --- MEMORIES (/users/{userId}/memories/{memoryId}) ---

    fun observeMemories(userId: String): Flow<List<CloudMemory>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/memories"
        emitAll(
            db.collection("users").document(userId).collection("memories")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudMemory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addMemory(
        key: String,
        content: String,
        category: String,
        docIdOverride: String? = null
    ): Result<String> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}/memories"
        return try {
            val uid = requireUserId()
            val cleanKey = key.trim().take(120)
            val cleanContent = content.trim().take(2000)
            val cleanCategory = category.trim().ifBlank { "Geral" }.take(60)
            require(cleanKey.isNotEmpty()) { "O título da memória não pode estar vazio." }
            require(cleanContent.isNotEmpty()) { "O conteúdo da memória não pode estar vazio." }

            val colRef = db.collection("users").document(uid).collection("memories")
            val docRef = if (!docIdOverride.isNullOrBlank()) {
                colRef.document(sanitizeDocId(docIdOverride, "mem"))
            } else {
                colRef.document()
            }
            docPath = docRef.path

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "key" to cleanKey,
                        "content" to cleanContent,
                        "category" to cleanCategory,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "key" to cleanKey,
                        "content" to cleanContent,
                        "category" to cleanCategory,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank() && DOC_ID_REGEX.matches(memoryId)) { "ID de memória inválido." }
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- VOICE COMMANDS (/users/{userId}/voice_commands/{commandId}) ---

    fun observeVoiceCommands(userId: String): Flow<List<CloudVoiceCommand>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/voice_commands"
        emitAll(
            db.collection("users").document(userId).collection("voice_commands")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudVoiceCommand::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addVoiceCommand(
        triggerPhrase: String,
        actionType: String,
        actionPayload: String,
        enabled: Boolean = true,
        docIdOverride: String? = null
    ): Result<String> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}/voice_commands"
        return try {
            val uid = requireUserId()
            val cleanTrigger = triggerPhrase.trim().take(120)
            val cleanType = actionType.trim().takeIf { it in VALID_ACTION_TYPES } ?: "AGENT_PROMPT"
            val cleanPayload = actionPayload.trim().take(2000)
            require(cleanTrigger.isNotEmpty()) { "A frase gatilho não pode estar vazia." }
            require(cleanPayload.isNotEmpty()) { "O conteúdo da ação não pode estar vazio." }

            val colRef = db.collection("users").document(uid).collection("voice_commands")
            val docRef = if (!docIdOverride.isNullOrBlank()) {
                colRef.document(sanitizeDocId(docIdOverride, "cmd"))
            } else {
                colRef.document()
            }
            docPath = docRef.path

            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "triggerPhrase" to cleanTrigger,
                        "actionType" to cleanType,
                        "actionPayload" to cleanPayload,
                        "enabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "triggerPhrase" to cleanTrigger,
                        "actionType" to cleanType,
                        "actionPayload" to cleanPayload,
                        "enabled" to enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleVoiceCommand(commandId: String, enabled: Boolean): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}/voice_commands/$commandId"
        return try {
            val uid = requireUserId()
            require(commandId.isNotBlank() && DOC_ID_REGEX.matches(commandId)) { "ID de comando inválido." }
            val docRef = db.collection("users").document(uid).collection("voice_commands").document(commandId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "enabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVoiceCommand(commandId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        var docPath = "users/${currentUid ?: "unauthenticated"}/voice_commands/$commandId"
        return try {
            val uid = requireUserId()
            require(commandId.isNotBlank() && DOC_ID_REGEX.matches(commandId)) { "ID de comando inválido." }
            val docRef = db.collection("users").document(uid).collection("voice_commands").document(commandId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- CONVERSATION SESSIONS (/users/{userId}/sessions/{sessionId}) ---

    fun observeSessions(userId: String): Flow<List<CloudSession>> = flow {
        require(userId.isNotBlank()) { "userId must not be blank" }
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CloudSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSession(
        sessionId: String,
        title: String,
        messageCount: Long,
        lastMessagePreview: String? = null
    ): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        val safeId = sanitizeDocId(sessionId, "sess")
        var docPath = "users/${currentUid ?: "unauthenticated"}/sessions/$safeId"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().ifBlank { "Conversa" }.take(120)
            val safeCount = messageCount.coerceIn(0L, 100_000L)
            val cleanPreview = lastMessagePreview?.trim()?.take(300)?.takeIf { it.isNotBlank() }

            val docRef = db.collection("users").document(uid).collection("sessions").document(safeId)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val updatePayload = buildMap<String, Any> {
                    put("title", cleanTitle)
                    put("messageCount", safeCount)
                    if (cleanPreview != null) put("lastMessagePreview", cleanPreview)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            } else {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("title", cleanTitle)
                    put("messageCount", safeCount)
                    if (cleanPreview != null) put("lastMessagePreview", cleanPreview)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        val currentUid = auth.currentUser?.uid
        val safeId = sanitizeDocId(sessionId, "sess")
        var docPath = "users/${currentUid ?: "unauthenticated"}/sessions/$safeId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("sessions").document(safeId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
