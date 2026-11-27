package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ConversationSessionEntity::class,
        ChatMessageEntity::class,
        MemoryEntity::class,
        VoiceCommandEntity::class,
        OfflineVoiceCommandEntity::class,
        TraceEntity::class,
        VoiceCommandLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationSessionDao(): ConversationSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun voiceCommandDao(): VoiceCommandDao
    abstract fun offlineVoiceCommandDao(): OfflineVoiceCommandDao
    abstract fun traceDao(): TraceDao
    abstract fun voiceCommandLogDao(): VoiceCommandLogDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN sessionId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversation_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO conversation_sessions (id, title, createdAt, updatedAt) VALUES ('default', 'Conversa Principal', ?, ?)",
                    arrayOf<Any>(now, now)
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_voice_commands (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        triggerPhrase TEXT NOT NULL,
                        aliases TEXT NOT NULL,
                        actionKey TEXT NOT NULL,
                        actionArgument TEXT NOT NULL,
                        description TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        usageCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voice_command_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        transcript TEXT NOT NULL,
                        responsePreview TEXT NOT NULL,
                        source TEXT NOT NULL,
                        engineUsed TEXT NOT NULL,
                        modelName TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        latencyMs INTEGER NOT NULL,
                        success INTEGER NOT NULL,
                        errorMessage TEXT,
                        appVersion TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): JarvisDatabase {
            return Room.databaseBuilder(
                context,
                JarvisDatabase::class.java,
                "openjarvis_local.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(getInstance(context))
                        }
                    }
                })
                .build()
        }

        private suspend fun seedInitialData(database: JarvisDatabase) {
            database.conversationSessionDao().upsertSession(
                ConversationSessionEntity(
                    id = DEFAULT_SESSION_ID,
                    title = "Conversa Principal"
                )
            )

            val commandDao = database.voiceCommandDao()
            commandDao.insertCommand(
                VoiceCommandEntity(
                    triggerPhrase = "modo foco",
                    actionType = "OFFLINE_RESPONSE",
                    actionPayload = "Modo foco ativado localmente. Notificações minimizadas e A.O.Jarvis pronto em modo silencioso."
                )
            )
            commandDao.insertCommand(
                VoiceCommandEntity(
                    triggerPhrase = "status servidor",
                    actionType = "LOCAL_WEBHOOK",
                    actionPayload = "http://192.168.1.100:11434/api/version"
                )
            )
            commandDao.insertCommand(
                VoiceCommandEntity(
                    triggerPhrase = "resumo diário",
                    actionType = "AGENT_PROMPT",
                    actionPayload = "Faça um resumo objetivo das minhas memórias salvas e sugira as 3 prioridades técnicas do dia."
                )
            )

            val offlineRepo = OfflineVoiceCommandRepository(database.offlineVoiceCommandDao())
            offlineRepo.ensureDefaultCommandsSeeded()

            val memoryDao = database.memoryDao()
            memoryDao.insertMemory(
                MemoryEntity(
                    key = "Idioma & Estilo",
                    content = "Responder sempre em português do Brasil, de forma técnica, concisa e direta, no estilo do A.O.Jarvis.",
                    category = "Preferência"
                )
            )
        }
    }
}
