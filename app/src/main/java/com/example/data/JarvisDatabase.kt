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
        TraceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationSessionDao(): ConversationSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun voiceCommandDao(): VoiceCommandDao
    abstract fun traceDao(): TraceDao

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
                .addMigrations(MIGRATION_1_2)
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
                    actionPayload = "Modo foco ativado localmente. Notificações minimizadas e OpenJarvis pronto em modo silencioso."
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

            val memoryDao = database.memoryDao()
            memoryDao.insertMemory(
                MemoryEntity(
                    key = "Idioma & Estilo",
                    content = "Responder sempre em português do Brasil, de forma técnica, concisa e direta, no estilo do OpenJarvis.",
                    category = "Preferência"
                )
            )
        }
    }
}
