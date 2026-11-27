package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.assistant.DefaultAssistantStatus
import com.example.cloud.CloudSyncSummary
import com.example.core.AppUpdateState
import com.example.core.AutonomyPermissionItem
import com.example.core.BatteryOptimizationStatus
import com.example.core.DeviceControlServiceState
import com.example.core.DeviceSettingsSnapshot
import com.example.core.FdroidRepoCheckState
import com.example.core.IntegrationResult
import com.example.data.AppThemeMode
import com.example.data.JarvisSettings
import com.example.data.RoutingMode
import com.example.engine.NetworkEngineState
import com.example.engine.OllamaHealthStatus
import com.example.ui.theme.LocalAppThemeMode
import com.example.voice.LanServerState
import com.example.voice.TtsStatus
import com.example.voice.WakeWordDetectorState
import java.util.Locale

private data class GeminiModelPreset(
    val id: String,
    val title: String,
    val badge: String,
    val description: String
)

private val GEMINI_MODELS = listOf(
    GeminiModelPreset(
        id = "gemini-3.8-flash",
        title = "Gemini 3.8 Flash",
        badge = "Padrão",
        description = "Equilíbrio ideal entre velocidade, custo e inteligência geral."
    ),
    GeminiModelPreset(
        id = "gemini-3.5-flash-lite",
        title = "Gemini 3.5 Flash-Lite",
        badge = "Ultra Rápido",
        description = "Respostas com latência mínima para comandos curtos e voz."
    ),
    GeminiModelPreset(
        id = "gemini-4-argon",
        title = "Gemini 4 Argon",
        badge = "Raciocínio",
        description = "Máxima profundidade para engenharia, código e raciocínio complexo."
    )
)

private val LIVE_VOICES = listOf(
    "CHARON" to "Charon",
    "FENRIR" to "Fenrir",
    "PUCK" to "Puck",
    "KORE" to "Kore",
    "AOEDE" to "Aoede"
)

@Composable
fun EnginesScreen(
    settings: JarvisSettings,
    networkState: NetworkEngineState,
    lanServerState: LanServerState,
    wakeWordState: WakeWordDetectorState,
    ttsStatus: TtsStatus,
    isTtsSpeaking: Boolean,
    permissionItems: List<AutonomyPermissionItem>,
    isBackgroundRunning: Boolean,
    ollamaHealth: OllamaHealthStatus?,
    isProbingOllama: Boolean,
    onRequestAllPermissions: () -> Unit,
    onTestTts: () -> Unit,
    onStopTts: () -> Unit,
    onReinitTts: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    batteryOptimizationStatus: BatteryOptimizationStatus,
    onRefreshBatteryOptimization: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> IntegrationResult,
    onUpdateRoutingMode: (RoutingMode) -> Unit,
    onUpdateOllamaConfig: (String, String) -> Unit,
    onProbeOllama: (String) -> Unit,
    onRefreshNetwork: () -> Unit,
    onUpdateGeminiModel: (String) -> Unit,
    onUpdateLiveVoice: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateSystemPrompt: (String) -> Unit,
    onUpdateVoicePreferences: (Boolean, String, Boolean, Boolean) -> Unit,
    onUpdateAudioRecordWakeWordConfig: (Boolean, Float) -> Unit,
    onUpdateBackgroundModePreferences: (Boolean, Boolean) -> Unit,
    onUpdateLanTriggerConfig: (Boolean, Int) -> Unit,
    onRegenerateLanToken: () -> Unit,
    onToggleBackgroundService: () -> Unit,
    updateState: AppUpdateState,
    fdroidRepoStatus: FdroidRepoCheckState,
    currentVersionLabel: String,
    onUpdateRemoteUpdateConfig: (String, String, Boolean) -> Unit,
    onCheckForUpdates: (String?) -> Unit,
    onVerifyFdroidRepo: (String) -> Unit,
    onBuildRawFdroidUrl: (String) -> String,
    onBuildDefaultFdroidUrl: (String) -> String,
    onOpenGitHubRelease: (String) -> Result<String>,
    onOpenInFdroid: (String) -> Result<String>,
    deviceSettingsSnapshot: DeviceSettingsSnapshot,
    deviceControlServiceState: DeviceControlServiceState,
    onToggleDeviceControlService: () -> Unit,
    onRefreshDeviceSettings: () -> Unit,
    onApplyPowerSaving: () -> IntegrationResult,
    onOpenWriteSettingsPermission: () -> IntegrationResult,
    onOpenDndPermission: () -> IntegrationResult,
    onSetDeviceBrightness: (Int) -> IntegrationResult,
    onSetDeviceVolume: (Int) -> IntegrationResult,
    onToggleDeviceWifi: (Boolean) -> IntegrationResult,
    onToggleDeviceBluetooth: (Boolean) -> IntegrationResult,
    onToggleDeviceTorch: (Boolean) -> IntegrationResult,
    onToggleDeviceAutoRotate: (Boolean) -> IntegrationResult,
    onToggleDeviceDnd: (Boolean) -> IntegrationResult,
    onOpenDevicePanel: (String) -> IntegrationResult,
    cloudSyncSummary: CloudSyncSummary,
    onSyncCloudNow: () -> Unit,
    onSignInGoogle: () -> Unit,
    onSignOutGoogle: () -> Unit,
    defaultAssistantStatus: DefaultAssistantStatus,
    onRefreshDefaultAssistant: () -> Unit,
    onRequestSetDefaultAssistant: () -> Unit,
    onOpenDefaultAssistantSettings: () -> IntegrationResult,
    onTestDefaultAssistantTrigger: () -> Unit,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onUpdateCustomAccentColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var ollamaUrlDraft by remember { mutableStateOf(settings.ollamaBaseUrl) }
    var ollamaModelDraft by remember { mutableStateOf(settings.ollamaModel) }
    var wakeWordDraft by remember { mutableStateOf(settings.wakeWord) }
    var systemPromptDraft by remember { mutableStateOf(settings.systemPrompt) }
    var lanPortDraft by remember { mutableStateOf(settings.lanTriggerPort.toString()) }

    LaunchedEffect(settings.ollamaBaseUrl, settings.ollamaModel) {
        ollamaUrlDraft = settings.ollamaBaseUrl
        ollamaModelDraft = settings.ollamaModel
    }
    LaunchedEffect(settings.wakeWord) {
        wakeWordDraft = settings.wakeWord
    }
    LaunchedEffect(settings.systemPrompt) {
        systemPromptDraft = settings.systemPrompt
    }
    LaunchedEffect(settings.lanTriggerPort) {
        lanPortDraft = settings.lanTriggerPort.toString()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Motores & Inteligência",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Configure conta Firebase, Assistente Padrão, roteamento entre Ollama LAN, Gemini Cloud e comandos offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 0A. Firebase Account & Cloud Firestore Sync
        item {
            CloudAccountCard(
                cloudSyncSummary = cloudSyncSummary,
                onSyncNow = onSyncCloudNow,
                onSignInGoogle = onSignInGoogle,
                onSignOut = onSignOutGoogle
            )
        }

        // 0B. Android Default Digital Assistant Configuration
        item {
            DefaultAssistantCard(
                status = defaultAssistantStatus,
                onRefreshStatus = onRefreshDefaultAssistant,
                onRequestSetDefaultAssistant = onRequestSetDefaultAssistant,
                onOpenAssistantSettings = onOpenDefaultAssistantSettings,
                onTestAssistantTrigger = onTestDefaultAssistantTrigger
            )
        }

        // 1. Routing Policy
        item {
            SectionCard(
                title = "Política de Roteamento",
                subtitle = "Define qual motor responde primeiro às interações",
                icon = Icons.Rounded.Hub
            ) {
                RoutingMode.entries.forEach { mode ->
                    val selected = settings.routingMode == mode
                    Surface(
                        onClick = { onUpdateRoutingMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("routing_mode_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { onUpdateRoutingMode(mode) })
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                // Live NetworkEngineManager status pill
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (networkState.isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = if (networkState.isWifiConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rede: ${networkState.transportLabel} · IP Local: ${networkState.localIpAddress ?: "Indisponível"}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = networkState.lastRoutingReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onRefreshNetwork,
                            modifier = Modifier.testTag("refresh_network_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar status da rede")
                        }
                    }
                }
            }
        }

        // 2. Local PC Ollama LAN Configuration
        item {
            SectionCard(
                title = "Ollama Local (PC na Rede LAN)",
                subtitle = "Redirecione o processamento para o seu computador rodando Ollama",
                icon = Icons.Rounded.Computer
            ) {
                OutlinedTextField(
                    value = ollamaUrlDraft,
                    onValueChange = { ollamaUrlDraft = it },
                    label = { Text("Endereço do servidor Ollama") },
                    placeholder = { Text("http://192.168.1.100:11434") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ollama_url_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "LAN Padrão" to "http://192.168.1.100:11434",
                        "Emulador Host" to "http://10.0.2.2:11434",
                        "Localhost" to "http://127.0.0.1:11434"
                    ).forEach { (label, url) ->
                        AssistChip(
                            onClick = { ollamaUrlDraft = url },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ollamaModelDraft,
                    onValueChange = { ollamaModelDraft = it },
                    label = { Text("Modelo Ollama (ex: llama3.2, qwen2.5, gemma3)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ollama_model_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onUpdateOllamaConfig(ollamaUrlDraft, ollamaModelDraft)
                            Toast.makeText(context, "Configuração Ollama salva", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_ollama_button")
                    ) {
                        Text("Salvar")
                    }
                    Button(
                        onClick = {
                            onUpdateOllamaConfig(ollamaUrlDraft, ollamaModelDraft)
                            onProbeOllama(ollamaUrlDraft)
                        },
                        enabled = !isProbingOllama,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("probe_ollama_button")
                    ) {
                        if (isProbingOllama) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testar & Descobrir")
                    }
                }

                if (ollamaHealth != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (ollamaHealth.isReachable) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (ollamaHealth.isReachable) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (ollamaHealth.isReachable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (ollamaHealth.isReachable) {
                                        "Ollama Online · ${ollamaHealth.latencyMs}ms · ${ollamaHealth.models.size} modelo(s)"
                                    } else {
                                        ollamaHealth.errorMessage ?: "Servidor Ollama inacessível"
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (ollamaHealth.models.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ollamaHealth.models.forEach { model ->
                                        FilterChip(
                                            selected = settings.ollamaModel == model.name,
                                            onClick = {
                                                ollamaModelDraft = model.name
                                                onUpdateOllamaConfig(ollamaUrlDraft, model.name)
                                            },
                                            label = {
                                                val sizeLabel = if (model.formattedSize.isNotBlank()) " (${model.formattedSize})" else ""
                                                Text("${model.name}$sizeLabel")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Comando no PC para aceitar conexões na rede Wi-Fi:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "OLLAMA_HOST=0.0.0.0 ollama serve",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Ollama Serve", "OLLAMA_HOST=0.0.0.0 ollama serve"))
                                Toast.makeText(context, "Comando copiado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar comando Ollama", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 3. Gemini Cloud & Gemini Live
        item {
            SectionCard(
                title = "Gemini Cloud & Live API",
                subtitle = "Via Firebase AI Logic (App Check) — sem chave de API manual",
                icon = Icons.Rounded.AutoAwesome
            ) {
                GEMINI_MODELS.forEach { preset ->
                    val selected = settings.geminiModel == preset.id
                    Surface(
                        onClick = { onUpdateGeminiModel(preset.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("gemini_model_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { onUpdateGeminiModel(preset.id) })
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(preset.title, style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = preset.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voz do Gemini 3.8 Live", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LIVE_VOICES.forEach { (voiceId, label) ->
                        FilterChip(
                            selected = settings.liveVoiceName == voiceId,
                            onClick = { onUpdateLiveVoice(voiceId) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Temperatura", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = String.format(Locale.US, "%.2f", settings.temperature),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.temperature,
                    onValueChange = onUpdateTemperature,
                    valueRange = 0f..1.3f
                )

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = systemPromptDraft,
                    onValueChange = { systemPromptDraft = it },
                    label = { Text("Instrução de sistema do agente A.O.Jarvis") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        onUpdateSystemPrompt(systemPromptDraft)
                        Toast.makeText(context, "Instrução atualizada", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Salvar instrução")
                }
            }
        }

        // 4. Native Android Speech-to-Text & Continuous Background Mode
        item {
            SectionCard(
                title = "Voz Nativa (Android STT) & Background",
                subtitle = "SpeechRecognizer nativo do sistema, palavra de ativação e resposta falada",
                icon = Icons.Rounded.Mic
            ) {
                TogglePreferenceRow(
                    title = "Priorizar Speech-to-Text Offline",
                    subtitle = "Ativa EXTRA_PREFER_OFFLINE para reconhecimento de voz sem internet",
                    checked = settings.preferOfflineStt,
                    onCheckedChange = { preferOffline ->
                        onUpdateVoicePreferences(
                            settings.wakeWordEnabled,
                            wakeWordDraft,
                            preferOffline,
                            settings.autoSpeakResponses
                        )
                    },
                    testTag = "switch_offline_stt"
                )
                TogglePreferenceRow(
                    title = "Responder em voz alta (TTS)",
                    subtitle = "Lê respostas automaticamente após comandos por voz e no modo background",
                    checked = settings.autoSpeakResponses,
                    onCheckedChange = { autoSpeak ->
                        onUpdateVoicePreferences(
                            settings.wakeWordEnabled,
                            wakeWordDraft,
                            settings.preferOfflineStt,
                            autoSpeak
                        )
                    },
                    testTag = "switch_auto_speak"
                )
                TogglePreferenceRow(
                    title = "Palavra de ativação no modo Background",
                    subtitle = "Filtra ruídos aguardando a palavra-chave configurada",
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { wakeEnabled ->
                        onUpdateVoicePreferences(
                            wakeEnabled,
                            wakeWordDraft,
                            settings.preferOfflineStt,
                            settings.autoSpeakResponses
                        )
                    },
                    testTag = "switch_wake_word"
                )

                if (settings.wakeWordEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = wakeWordDraft,
                            onValueChange = { wakeWordDraft = it },
                            label = { Text("Palavra de ativação (ex: hey jarvis)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpdateVoicePreferences(
                                    true,
                                    wakeWordDraft,
                                    settings.preferOfflineStt,
                                    settings.autoSpeakResponses
                                )
                                Toast.makeText(context, "Palavra de ativação salva", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Salvar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TogglePreferenceRow(
                    title = stringResource(R.string.wake_word_audiorecord_title),
                    subtitle = stringResource(R.string.wake_word_audiorecord_subtitle),
                    checked = settings.audioRecordWakeWordEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateAudioRecordWakeWordConfig(enabled, settings.wakeWordSensitivity)
                    },
                    testTag = "switch_audiorecord_wake_word"
                )

                if (settings.audioRecordWakeWordEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val sensLabel = when {
                        settings.wakeWordSensitivity >= 0.85f -> "Máxima"
                        settings.wakeWordSensitivity >= 0.6f -> "Equilibrada"
                        settings.wakeWordSensitivity >= 0.4f -> "Moderada"
                        else -> "Rigorosa"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sensibilidade AudioRecord (${(settings.wakeWordSensitivity * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = sensLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.wakeWordSensitivity,
                        onValueChange = { onUpdateAudioRecordWakeWordConfig(true, it) },
                        valueRange = 0.2f..1.0f,
                        modifier = Modifier.testTag("wake_word_sensitivity_slider")
                    )
                    Text(
                        text = "${wakeWordState.statusText} · Ruído ambiente: ${wakeWordState.noiseFloorDb.toInt()} dBFS · Ativações: ${wakeWordState.triggerCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                TogglePreferenceRow(
                    title = "Manter microfone ativo no Foreground Service",
                    subtitle = "Desative se quiser rodar o serviço em background apenas para gatilhos LAN",
                    checked = settings.voiceListeningInBgEnabled,
                    onCheckedChange = { voiceInBg ->
                        onUpdateBackgroundModePreferences(
                            voiceInBg,
                            settings.continuousConversationMode
                        )
                    },
                    testTag = "switch_voice_bg_enabled"
                )
                TogglePreferenceRow(
                    title = "Modo conversa contínua (follow-up)",
                    subtitle = "Mantém a escuta aberta após cada resposta sem precisar repetir a palavra de ativação",
                    checked = settings.continuousConversationMode,
                    onCheckedChange = { continuous ->
                        onUpdateBackgroundModePreferences(
                            settings.voiceListeningInBgEnabled,
                            continuous
                        )
                    },
                    testTag = "switch_continuous_conversation"
                )

                Spacer(modifier = Modifier.height(8.dp))
                // TTS Health & Test Controls
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val statusText = when (ttsStatus) {
                            TtsStatus.Initializing -> "Inicializando motor de voz TTS…"
                            is TtsStatus.Ready -> "TTS Pronto (${ttsStatus.localeLabel})"
                            is TtsStatus.Error -> ttsStatus.reason
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ttsStatus is TtsStatus.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Velocidade ${String.format(Locale.US, "%.2fx", settings.ttsSpeechRate)} · Tom ${String.format(Locale.US, "%.2fx", settings.ttsPitch)}" +
                                if (settings.ttsVoiceName.isNotBlank()) " · Voz personalizada" else " · Voz padrão",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (isTtsSpeaking) onStopTts() else onTestTts() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_tts_button")
                            ) {
                                Icon(
                                    imageVector = if (isTtsSpeaking) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTtsSpeaking) "Parar Voz" else "Testar Voz (TTS)")
                            }
                            OutlinedButton(
                                onClick = {
                                    onReinitTts()
                                    Toast.makeText(context, "Motor TTS reinicializado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("reinit_tts_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Reiniciar TTS", modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenTtsSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_tts_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.open_tts_settings_cta))
                        }
                    }
                }
            }
        }

        // 4-0. Battery Optimization Check & Exemption for Foreground Service
        item {
            BatteryOptimizationCard(
                status = batteryOptimizationStatus,
                isBackgroundRunning = isBackgroundRunning,
                onRefreshStatus = onRefreshBatteryOptimization,
                onRequestIgnoreBatteryOptimizations = onRequestIgnoreBatteryOptimizations,
                onOpenBatterySettings = onOpenBatteryOptimizationSettings
            )
        }

        // 4A. Autonomy Permissions Hub (all Play-compliant Android permissions)
        item {
            SectionCard(
                title = "Permissões & Autonomia do Jarvis",
                subtitle = "Autorize recursos do Android para contatos, agenda, localização, câmera, voz e notificações",
                icon = Icons.Rounded.Security
            ) {
                val grantedCount = permissionItems.count { it.isGranted }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$grantedCount de ${permissionItems.size} permissões ativas",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = onRequestAllPermissions,
                        modifier = Modifier.testTag("request_all_autonomy_permissions_button")
                    ) {
                        Text(if (grantedCount == permissionItems.size) "Tudo Ativo" else "Conceder Todas")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                permissionItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (item.isGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4A-2. Real Device Settings Control (Brightness, Volume, Torch, Rotation, DND, Panels)
        item {
            DeviceControlCard(
                snapshot = deviceSettingsSnapshot,
                serviceState = deviceControlServiceState,
                onToggleService = onToggleDeviceControlService,
                onRefreshSnapshot = onRefreshDeviceSettings,
                onApplyPowerSaving = onApplyPowerSaving,
                onOpenWriteSettingsPermission = onOpenWriteSettingsPermission,
                onOpenDndPermission = onOpenDndPermission,
                onSetBrightness = onSetDeviceBrightness,
                onSetVolume = onSetDeviceVolume,
                onToggleWifi = onToggleDeviceWifi,
                onToggleBluetooth = onToggleDeviceBluetooth,
                onToggleTorch = onToggleDeviceTorch,
                onToggleAutoRotate = onToggleDeviceAutoRotate,
                onToggleDnd = onToggleDeviceDnd,
                onOpenSystemPanel = onOpenDevicePanel
            )
        }

        // 4B. Local Network (LAN / Wi-Fi) Trigger Server for Foreground Service
        item {
            SectionCard(
                title = stringResource(R.string.lan_server_title),
                subtitle = stringResource(R.string.lan_server_subtitle),
                icon = Icons.Rounded.Lan
            ) {
                TogglePreferenceRow(
                    title = "Aguardar gatilhos via rede local (Wi-Fi)",
                    subtitle = "Abre um servidor HTTP autenticado no Foreground Service para receber comandos do PC ou automações",
                    checked = settings.lanTriggerEnabled,
                    onCheckedChange = { enabled ->
                        val parsedPort = lanPortDraft.toIntOrNull() ?: settings.lanTriggerPort
                        onUpdateLanTriggerConfig(enabled, parsedPort)
                    },
                    testTag = "switch_lan_trigger_enabled"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = lanPortDraft,
                        onValueChange = { input ->
                            lanPortDraft = input.filter { it.isDigit() }.take(5)
                        },
                        label = { Text("Porta TCP local (1024-65535)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lan_port_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val parsed = lanPortDraft.toIntOrNull()
                            if (parsed == null || parsed !in 1024..65535) {
                                Toast.makeText(context, "Informe uma porta entre 1024 e 65535", Toast.LENGTH_SHORT).show()
                            } else {
                                onUpdateLanTriggerConfig(settings.lanTriggerEnabled, parsed)
                                Toast.makeText(context, "Porta LAN salva ($parsed)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("save_lan_port_button")
                    ) {
                        Text("Salvar")
                    }
                }

                if (settings.lanTriggerEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))

                    val displayIp = lanServerState.localIp ?: networkState.localIpAddress ?: "<ip-do-android>"
                    val endpoint = "http://$displayIp:${settings.lanTriggerPort}/trigger"

                    // Server status badge + quick start/stop Foreground Service button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (lanServerState.isRunning) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lanServerState.isRunning) {
                                        "Servidor LAN Ativo · ${lanServerState.triggerCount} gatilho(s)"
                                    } else {
                                        "Pronto — inicie o modo Background para abrir a porta"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = lanServerState.errorMessage ?: endpoint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onToggleBackgroundService,
                                modifier = Modifier.testTag("lan_toggle_service_button")
                            ) {
                                Icon(
                                    imageVector = if (isBackgroundRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBackgroundRunning) "Parar" else "Iniciar")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Auth token management (masked display, copy & regenerate)
                    val token = settings.lanTriggerToken
                    val maskedToken = if (token.length >= 12) {
                        "${token.take(6)}••••••••${token.takeLast(6)}"
                    } else if (token.isNotBlank()) {
                        "••••••••"
                    } else {
                        "Gerando token…"
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Token de autenticação (X-Jarvis-Token)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = maskedToken,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (token.isNotBlank()) {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("A.O.Jarvis LAN Token", token))
                                        Toast.makeText(context, "Token LAN copiado", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("copy_lan_token_button")
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_lan_token),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    onRegenerateLanToken()
                                    Toast.makeText(context, "Novo token LAN gerado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("regenerate_lan_token_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.cd_regenerate_lan_token),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val curlPreview = "curl -X POST $endpoint -H \"X-Jarvis-Token: <SEU_TOKEN>\" -H \"Content-Type: application/json\" -d '{\"prompt\":\"Status do sistema\",\"speak\":true}'"
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Exemplo de disparo via rede Wi-Fi (PC / Home Assistant):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = curlPreview,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            IconButton(
                                onClick = {
                                    val actualCurl = "curl -X POST $endpoint -H \"X-Jarvis-Token: $token\" -H \"Content-Type: application/json\" -d '{\"prompt\":\"Status do sistema\",\"speak\":true}'"
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("A.O.Jarvis LAN Curl", actualCurl))
                                    Toast.makeText(context, "Comando curl copiado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_lan_curl_button")
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.cd_copy_curl),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4C. Remote Updates via GitHub Releases & F-Droid
        item {
            RemoteUpdateCard(
                settings = settings,
                currentVersionLabel = currentVersionLabel,
                updateState = updateState,
                fdroidRepoStatus = fdroidRepoStatus,
                onUpdateRemoteConfig = onUpdateRemoteUpdateConfig,
                onCheckForUpdates = onCheckForUpdates,
                onVerifyFdroidRepo = onVerifyFdroidRepo,
                onBuildRawFdroidUrl = onBuildRawFdroidUrl,
                onBuildDefaultFdroidUrl = onBuildDefaultFdroidUrl,
                onOpenGitHubRelease = onOpenGitHubRelease,
                onOpenInFdroid = onOpenInFdroid
            )
        }

        // 5. Appearance & Themes (Tron Legacy, Obsidian Pro, Daylight, Custom)
        item {
            ThemeSelectorCard(
                selectedTheme = settings.themeMode,
                customAccentColor = settings.customAccentColor,
                onSelectTheme = onUpdateThemeMode,
                onSelectCustomAccentColor = onUpdateCustomAccentColor
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val activeTheme = LocalAppThemeMode.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (activeTheme == AppThemeMode.TRON) {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TogglePreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
