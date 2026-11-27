package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TraceEntity
import com.example.data.VoiceCommandLogEntity
import com.example.ui.components.EngineBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TelemetryScreen(
    voiceLogs: List<VoiceCommandLogEntity>,
    traces: List<TraceEntity>,
    onDeleteVoiceLog: (Long) -> Unit,
    onClearVoiceLogs: () -> Unit,
    onReplayVoiceCommand: (String) -> Unit,
    onSpeakLogResponse: (String) -> Unit,
    onClearTraces: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Histórico de Voz (Room), 1 = Traces
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredVoiceLogs = remember(voiceLogs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            voiceLogs
        } else {
            voiceLogs.filter { log ->
                log.transcript.contains(q, ignoreCase = true) ||
                    log.responsePreview.contains(q, ignoreCase = true) ||
                    log.engineUsed.contains(q, ignoreCase = true)
            }
        }
    }

    val totalCount = traces.size
    val localCount = traces.count { it.engineType == "OLLAMA" || it.engineType == "OFFLINE" }
    val localRatio = if (totalCount > 0) (localCount * 100 / totalCount) else 0
    val avgLatency = if (totalCount > 0) traces.map { it.latencyMs }.average().toLong() else 0L
    val successRate = if (totalCount > 0) (traces.count { it.success } * 100 / totalCount) else 100

    val voiceTotal = voiceLogs.size
    val voiceBgCount = voiceLogs.count { it.source == "VOICE_BG" }
    val voiceGeminiCount = voiceLogs.count { it.engineUsed == "GEMINI" || it.engineUsed == "GEMINI_LIVE" }
    val avgVoiceLatency = if (voiceTotal > 0) voiceLogs.map { it.latencyMs }.average().toLong() else 0L

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.telemetry_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (selectedTab == 0) {
                            "Registros locais (SQLite / Room) dos comandos de voz processados pelo assistente."
                        } else {
                            "Ciclo de aprendizado local-first e rastreamento de latência por motor."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val canClear = (selectedTab == 0 && voiceLogs.isNotEmpty()) ||
                    (selectedTab == 1 && traces.isNotEmpty())
                if (canClear) {
                    OutlinedButton(
                        onClick = {
                            if (selectedTab == 0) onClearVoiceLogs() else onClearTraces()
                        },
                        modifier = Modifier.testTag(
                            if (selectedTab == 0) "clear_voice_logs_button" else "clear_traces_button"
                        )
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Limpar")
                    }
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("telemetry_section_switcher")
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("${stringResource(R.string.telemetry_tab_voice_history)} ($voiceTotal)") }
                )
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Insights,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("${stringResource(R.string.telemetry_tab_traces)} ($totalCount)") }
                )
            }
        }

        if (selectedTab == 0) {
            // Voice History KPIs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        label = "COMANDOS DE VOZ",
                        value = "$voiceTotal",
                        subtitle = "$voiceBgCount em segundo plano",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        label = "VIA GEMINI",
                        value = "$voiceGeminiCount",
                        subtitle = if (voiceTotal > 0) "Média ${avgVoiceLatency}ms" else "Banco SQLite pronto",
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (voiceLogs.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.voice_history_search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Limpar busca")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_history_search_input")
                    )
                }
            }

            if (filteredVoiceLogs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (voiceLogs.isEmpty()) {
                                    "Nenhum comando de voz registrado ainda"
                                } else {
                                    "Nenhum comando encontrado para \"$searchQuery\""
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use o microfone no Console ou ative o modo BG Contínuo. Cada comando falado será salvo automaticamente no SQLite local (Room) para consulta e repetição.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredVoiceLogs, key = { it.id }) { log ->
                    VoiceCommandLogCard(
                        log = log,
                        onReplay = { onReplayVoiceCommand(log.transcript) },
                        onSpeakResponse = { onSpeakLogResponse(log.responsePreview) },
                        onDelete = { onDeleteVoiceLog(log.id) }
                    )
                }
            }
        } else {
            // KPI Grid (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiCard(
                            label = "EXECUÇÕES",
                            value = "$totalCount",
                            subtitle = "Traces registrados",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            label = "LOCAL / OFFLINE",
                            value = "$localRatio%",
                            subtitle = "$localCount de $totalCount sem nuvem",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        KpiCard(
                            label = "LATÊNCIA MÉDIA",
                            value = "${avgLatency}ms",
                            subtitle = "Tempo de resposta",
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCard(
                            label = "CONFIABILIDADE",
                            value = "$successRate%",
                            subtitle = "Taxa de sucesso",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Histórico de Traces",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (traces.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Insights,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Nenhum trace registrado ainda",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cada interação via Gemini, Ollama LAN, Live Voice ou Comando Offline aparecerá aqui com latência e throughput.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(traces, key = { it.id }) { trace ->
                    TraceItemCard(trace = trace)
                }
            }
        }
    }
}

@Composable
private fun VoiceCommandLogCard(
    log: VoiceCommandLogEntity,
    onReplay: () -> Unit,
    onSpeakResponse: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(log.timestamp) {
        SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(log.timestamp))
    }
    val isBackground = log.source == "VOICE_BG"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            width = 1.dp,
            color = if (log.success) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_log_card_${log.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isBackground) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isBackground) Icons.Rounded.Hearing else Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isBackground) "BG Contínuo" else "Voz Nativa",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                EngineBadge(engineName = log.engineUsed, modelName = log.modelName)

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = if (log.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (log.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "“${log.transcript}”",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (log.responsePreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.responsePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val metaText = buildString {
                    append(timeStr)
                    if (log.latencyMs > 0) append(" · ${log.latencyMs}ms")
                    if (log.confidence >= 0f) append(" · STT ${(log.confidence * 100f).roundToInt()}%")
                    if (log.appVersion.isNotBlank()) append(" · v${log.appVersion}")
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (log.responsePreview.isNotBlank()) {
                        IconButton(
                            onClick = onSpeakResponse,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("speak_voice_log_${log.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.cd_speak_voice_log),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onReplay,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("replay_voice_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = stringResource(R.string.cd_replay_voice_log),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Repetir", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_voice_log_${log.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_voice_log),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TraceItemCard(trace: TraceEntity) {
    val timeStr = remember(trace.timestamp) {
        SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(trace.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            width = 1.dp,
            color = if (trace.success) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EngineBadge(engineName = trace.engineType, modelName = trace.modelName)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (trace.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = if (trace.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = buildString {
                            append("${trace.latencyMs}ms")
                            if (trace.tokensPerSec > 0.5) {
                                append(String.format(Locale.US, " · %.1f tok/s", trace.tokensPerSec))
                            }
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = trace.promptPreview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!trace.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trace.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
