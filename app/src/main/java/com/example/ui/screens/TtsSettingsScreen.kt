package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.IntegrationResult
import com.example.data.AppThemeMode
import com.example.data.JarvisSettings
import com.example.ui.theme.LocalAppThemeMode
import com.example.voice.TtsStatus
import com.example.voice.TtsVoiceOption
import java.util.Locale

private enum class VoiceFilter(val label: String) {
    ALL("Todas"),
    PORTUGUESE("Português"),
    ENGLISH("Inglês"),
    OFFLINE("Offline")
}

private val RATE_PRESETS = listOf(
    0.75f to "0.75x Lento",
    1.0f to "1.0x Normal",
    1.25f to "1.25x Ágil",
    1.5f to "1.5x Rápido"
)

private val PITCH_PRESETS = listOf(
    0.75f to "0.75x Grave",
    1.0f to "1.0x Natural",
    1.25f to "1.25x Agudo",
    1.5f to "1.5x Alto"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    settings: JarvisSettings,
    ttsStatus: TtsStatus,
    availableVoices: List<TtsVoiceOption>,
    activeVoiceName: String,
    isTtsSpeaking: Boolean,
    onBack: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onUpdateSpeechRate: (Float) -> Unit,
    onUpdatePitch: (Float) -> Unit,
    onResetDefaults: () -> Unit,
    onPreviewSpeech: (sampleText: String, voiceOverride: String?) -> Unit,
    onStopSpeaking: () -> Unit,
    onRefreshVoices: () -> Unit,
    onOpenSystemTtsSettings: () -> IntegrationResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode = LocalAppThemeMode.current
    val isAccentMode = themeMode == AppThemeMode.TRON || themeMode == AppThemeMode.CUSTOM

    var sampleText by rememberSaveable {
        mutableStateOf("Olá! Aqui é o A.O.Jarvis. Ajuste minha voz, velocidade e tom conforme preferir.")
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(VoiceFilter.ALL) }

    val filteredVoices = remember(availableVoices, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        availableVoices.filter { voice ->
            val matchesFilter = when (activeFilter) {
                VoiceFilter.ALL -> true
                VoiceFilter.PORTUGUESE -> voice.localeTag.lowercase(Locale.ROOT).startsWith("pt")
                VoiceFilter.ENGLISH -> voice.localeTag.lowercase(Locale.ROOT).startsWith("en")
                VoiceFilter.OFFLINE -> !voice.isNetworkRequired
            }
            val matchesQuery = q.isEmpty() ||
                voice.displayName.lowercase(Locale.ROOT).contains(q) ||
                voice.localeDisplayName.lowercase(Locale.ROOT).contains(q) ||
                voice.localeTag.lowercase(Locale.ROOT).contains(q) ||
                voice.name.lowercase(Locale.ROOT).contains(q)
            matchesFilter && matchesQuery
        }
    }

    val selectedVoiceOption = remember(settings.ttsVoiceName, availableVoices) {
        availableVoices.firstOrNull { it.name == settings.ttsVoiceName }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .testTag("tts_settings_screen")
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground
            ),
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("tts_settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = stringResource(R.string.tts_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.tts_settings_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        onRefreshVoices()
                        Toast.makeText(context, "Lista de vozes TTS atualizada.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("tts_refresh_voices_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh_tts_voices)
                    )
                }
                TextButton(
                    onClick = {
                        onResetDefaults()
                        Toast.makeText(context, "Voz, velocidade e tom restaurados ao padrão.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("tts_reset_defaults_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Padrão")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Live Preview & Active Status Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (isAccentMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Prévia em Tempo Real",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val statusLine = when (ttsStatus) {
                                    TtsStatus.Initializing -> "Inicializando motor TTS do Android…"
                                    is TtsStatus.Ready -> "Ativo: ${selectedVoiceOption?.displayName ?: ttsStatus.localeLabel}"
                                    is TtsStatus.Error -> ttsStatus.reason
                                }
                                Text(
                                    text = statusLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ttsStatus is TtsStatus.Error) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Summary chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryPill(
                                label = "Velocidade ${String.format(Locale.US, "%.2fx", settings.ttsSpeechRate)}"
                            )
                            SummaryPill(
                                label = "Tom ${String.format(Locale.US, "%.2fx", settings.ttsPitch)}"
                            )
                            SummaryPill(
                                label = "${availableVoices.size} voz(es) disponível(is)"
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = sampleText,
                            onValueChange = { sampleText = it },
                            label = { Text("Frase de teste de voz") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_sample_text_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isTtsSpeaking) {
                                        onStopSpeaking()
                                    } else {
                                        onPreviewSpeech(sampleText, null)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("tts_preview_speak_button")
                            ) {
                                Icon(
                                    imageVector = if (isTtsSpeaking) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTtsSpeaking) "Parar Prévia" else "Ouvir Prévia")
                            }

                            OutlinedButton(
                                onClick = {
                                    val res = onOpenSystemTtsSettings()
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("tts_open_system_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TTS Android")
                            }
                        }
                    }
                }
            }

            // 2. Speech Rate & Pitch Calibration Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Speech Rate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Velocidade da Fala",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.2fx", settings.ttsSpeechRate),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Slider(
                            value = settings.ttsSpeechRate,
                            onValueChange = { onUpdateSpeechRate(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 29,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_speech_rate_slider")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RATE_PRESETS.forEach { (rateVal, label) ->
                                AssistChip(
                                    onClick = { onUpdateSpeechRate(rateVal) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pitch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tom de Voz (Pitch)",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.2fx", settings.ttsPitch),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Slider(
                            value = settings.ttsPitch,
                            onValueChange = { onUpdatePitch(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 29,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_pitch_slider")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PITCH_PRESETS.forEach { (pitchVal, label) ->
                                AssistChip(
                                    onClick = { onUpdatePitch(pitchVal) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. System Voice Selector Header & Filters
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voz do Sistema",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Escolha entre as vozes instaladas no motor TTS do Android",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar por idioma ou nome da voz…") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_voice_search_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VoiceFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = activeFilter == filter,
                                    onClick = { activeFilter = filter },
                                    label = { Text(filter.label) },
                                    modifier = Modifier.testTag("tts_filter_${filter.name.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // System Default Option pinned at top
                        val isDefaultSelected = settings.ttsVoiceName.isBlank()
                        Surface(
                            onClick = { onSelectVoice("") },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDefaultSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isDefaultSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_voice_option_default")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isDefaultSelected,
                                    onClick = { onSelectVoice("") }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Padrão do Sistema (Automático)",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (activeVoiceName.isNotBlank() && isDefaultSelected) {
                                            "Atualmente usando: $activeVoiceName"
                                        } else {
                                            "Usa a voz padrão pt-BR definida pelo Android"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onPreviewSpeech(sampleText, "") },
                                    modifier = Modifier.testTag("tts_audition_voice_default")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_preview_tts_voice)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Filtered System Voices List
            if (filteredVoices.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (availableVoices.isEmpty()) {
                                    "Nenhuma voz adicional listada pelo motor TTS do dispositivo."
                                } else {
                                    "Nenhuma voz corresponde ao filtro atual."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Você pode baixar pacotes de voz em Português nas configurações TTS do Android.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredVoices,
                    key = { it.name }
                ) { voice ->
                    val selected = settings.ttsVoiceName == voice.name
                    Surface(
                        onClick = { onSelectVoice(voice.name) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tts_voice_option_${voice.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectVoice(voice.name) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VoiceBadge(voice.localeTag)
                                    VoiceBadge(voice.qualityLabel)
                                    VoiceBadge(if (voice.isNetworkRequired) "Rede" else "Offline")
                                }
                            }
                            IconButton(
                                onClick = { onPreviewSpeech(sampleText, voice.name) },
                                modifier = Modifier.testTag("tts_audition_${voice.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_preview_tts_voice),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SummaryPill(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VoiceBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
