package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "openjarvis_settings"
)

enum class RoutingMode(val label: String, val description: String) {
    AUTO(
        label = "Automático",
        description = "Comandos Offline → Ollama LAN (se disponível no Wi-Fi) → Gemini Cloud"
    ),
    GEMINI(
        label = "Gemini Cloud",
        description = "Processamento em nuvem via Firebase AI Logic"
    ),
    OLLAMA(
        label = "Ollama Local",
        description = "Redireciona inferência para o PC na rede Wi-Fi local"
    ),
    OFFLINE(
        label = "Somente Offline",
        description = "Executa apenas ferramentas e comandos locais sem rede"
    )
}

data class JarvisSettings(
    val routingMode: RoutingMode = RoutingMode.AUTO,
    val geminiModel: String = "gemini-3.8-flash",
    val liveVoiceName: String = "CHARON",
    val ollamaBaseUrl: String = "http://192.168.1.100:11434",
    val ollamaModel: String = "llama3.2",
    val temperature: Float = 0.6f,
    val systemPrompt: String = "Você é o OpenJarvis, um assistente pessoal de IA local-first e inteligente rodando no Android. Seja preciso, claro e objetivo em português do Brasil, otimizado tanto para leitura quanto para resposta falada por voz.",
    val wakeWordEnabled: Boolean = true,
    val wakeWord: String = "jarvis",
    val preferOfflineStt: Boolean = false,
    val autoSpeakResponses: Boolean = true,
    val voiceListeningInBgEnabled: Boolean = true,
    val continuousConversationMode: Boolean = true,
    val lanTriggerEnabled: Boolean = false,
    val lanTriggerPort: Int = 8765,
    val lanTriggerToken: String = "",
    val githubRepo: String = "",
    val fdroidRepoUrl: String = "",
    val autoCheckUpdates: Boolean = true,
    val darkTheme: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val LIVE_VOICE_NAME = stringPreferencesKey("live_voice_name")
        val OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")
        val OLLAMA_MODEL = stringPreferencesKey("ollama_model")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val PREFER_OFFLINE_STT = booleanPreferencesKey("prefer_offline_stt")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val VOICE_BG_ENABLED = booleanPreferencesKey("voice_bg_enabled")
        val CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        val LAN_TRIGGER_ENABLED = booleanPreferencesKey("lan_trigger_enabled")
        val LAN_TRIGGER_PORT = intPreferencesKey("lan_trigger_port")
        val LAN_TRIGGER_TOKEN = stringPreferencesKey("lan_trigger_token")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val FDROID_REPO_URL = stringPreferencesKey("fdroid_repo_url")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val settingsFlow: Flow<JarvisSettings> = context.jarvisDataStore.data.map { prefs ->
        val modeName = prefs[Keys.ROUTING_MODE] ?: RoutingMode.AUTO.name
        val routingMode = runCatching { RoutingMode.valueOf(modeName) }.getOrDefault(RoutingMode.AUTO)

        JarvisSettings(
            routingMode = routingMode,
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-3.8-flash",
            liveVoiceName = prefs[Keys.LIVE_VOICE_NAME] ?: "CHARON",
            ollamaBaseUrl = prefs[Keys.OLLAMA_BASE_URL] ?: "http://192.168.1.100:11434",
            ollamaModel = prefs[Keys.OLLAMA_MODEL] ?: "llama3.2",
            temperature = prefs[Keys.TEMPERATURE] ?: 0.6f,
            systemPrompt = prefs[Keys.SYSTEM_PROMPT]
                ?: "Você é o OpenJarvis, um assistente pessoal de IA local-first e inteligente rodando no Android. Seja preciso, claro e objetivo em português do Brasil, otimizado tanto para leitura quanto para resposta falada por voz.",
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            wakeWord = prefs[Keys.WAKE_WORD] ?: "jarvis",
            preferOfflineStt = prefs[Keys.PREFER_OFFLINE_STT] ?: false,
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: true,
            voiceListeningInBgEnabled = prefs[Keys.VOICE_BG_ENABLED] ?: true,
            continuousConversationMode = prefs[Keys.CONTINUOUS_CONVERSATION] ?: true,
            lanTriggerEnabled = prefs[Keys.LAN_TRIGGER_ENABLED] ?: false,
            lanTriggerPort = (prefs[Keys.LAN_TRIGGER_PORT] ?: 8765).coerceIn(1024, 65535),
            lanTriggerToken = prefs[Keys.LAN_TRIGGER_TOKEN] ?: "",
            githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
            fdroidRepoUrl = prefs[Keys.FDROID_REPO_URL] ?: "",
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    suspend fun updateRoutingMode(mode: RoutingMode) {
        context.jarvisDataStore.edit { it[Keys.ROUTING_MODE] = mode.name }
    }

    suspend fun updateGeminiModel(model: String) {
        context.jarvisDataStore.edit { it[Keys.GEMINI_MODEL] = model }
    }

    suspend fun updateLiveVoice(voice: String) {
        context.jarvisDataStore.edit { it[Keys.LIVE_VOICE_NAME] = voice }
    }

    suspend fun updateOllamaConfig(baseUrl: String, model: String) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        context.jarvisDataStore.edit {
            it[Keys.OLLAMA_BASE_URL] = cleanUrl
            it[Keys.OLLAMA_MODEL] = model.trim()
        }
    }

    suspend fun updateTemperature(value: Float) {
        context.jarvisDataStore.edit { it[Keys.TEMPERATURE] = value.coerceIn(0f, 1.5f) }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        context.jarvisDataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    suspend fun updateVoicePreferences(
        wakeWordEnabled: Boolean,
        wakeWord: String,
        preferOfflineStt: Boolean,
        autoSpeakResponses: Boolean
    ) {
        context.jarvisDataStore.edit {
            it[Keys.WAKE_WORD_ENABLED] = wakeWordEnabled
            it[Keys.WAKE_WORD] = wakeWord.trim().lowercase().ifBlank { "jarvis" }
            it[Keys.PREFER_OFFLINE_STT] = preferOfflineStt
            it[Keys.AUTO_SPEAK] = autoSpeakResponses
        }
    }

    suspend fun updateBackgroundModePreferences(
        voiceListeningInBgEnabled: Boolean,
        continuousConversationMode: Boolean
    ) {
        context.jarvisDataStore.edit {
            it[Keys.VOICE_BG_ENABLED] = voiceListeningInBgEnabled
            it[Keys.CONTINUOUS_CONVERSATION] = continuousConversationMode
        }
    }

    suspend fun updateLanTriggerConfig(
        enabled: Boolean,
        port: Int
    ) {
        val safePort = port.coerceIn(1024, 65535)
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.LAN_TRIGGER_ENABLED] = enabled
            prefs[Keys.LAN_TRIGGER_PORT] = safePort
            if (enabled && prefs[Keys.LAN_TRIGGER_TOKEN].isNullOrBlank()) {
                prefs[Keys.LAN_TRIGGER_TOKEN] = generateSecureToken()
            }
        }
    }

    suspend fun regenerateLanTriggerToken(): String {
        val newToken = generateSecureToken()
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.LAN_TRIGGER_TOKEN] = newToken
        }
        return newToken
    }

    suspend fun updateRemoteUpdateConfig(
        githubRepo: String,
        fdroidRepoUrl: String,
        autoCheckUpdates: Boolean
    ) {
        context.jarvisDataStore.edit { prefs ->
            prefs[Keys.GITHUB_REPO] = githubRepo.trim()
            prefs[Keys.FDROID_REPO_URL] = fdroidRepoUrl.trim()
            prefs[Keys.AUTO_CHECK_UPDATES] = autoCheckUpdates
        }
    }

    suspend fun updateDarkTheme(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
