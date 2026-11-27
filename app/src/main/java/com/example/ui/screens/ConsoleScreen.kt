package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.BatteryStatusSnapshot
import com.example.data.ChatMessageEntity
import com.example.data.ConversationSessionEntity
import com.example.data.JarvisSettings
import com.example.data.RoutingMode
import com.example.engine.LiveVoiceState
import com.example.engine.NetworkEngineState
import com.example.state.AssistantState
import com.example.ui.StreamingUiState
import com.example.ui.components.EngineBadge
import com.example.ui.components.TronPulseHaloBox
import com.example.ui.components.VoiceOrbVisualizer
import com.example.ui.components.rememberTronPulseState
import com.example.vision.ScreenShareState
import com.example.voice.LanServerState
import com.example.voice.VoiceRuntimeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleScreen(
    sessions: List<ConversationSessionEntity>,
    activeSessionId: String,
    messages: List<ChatMessageEntity>,
    streamingState: StreamingUiState,
    assistantState: AssistantState,
    networkState: NetworkEngineState,
    lanServerState: LanServerState,
    voiceState: VoiceRuntimeState,
    liveVoiceState: LiveVoiceState,
    screenShareState: ScreenShareState,
    batteryStatus: BatteryStatusSnapshot,
    isTtsSpeaking: Boolean,
    selectedMode: Int,
    onSelectMode: (Int) -> Unit,
    settings: JarvisSettings,
    hasMicPermission: Boolean,
    missingPermissionsCount: Int,
    onRequestAllPermissions: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCreateNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartGuidedTour: () -> Unit,
    onMicClick: () -> Unit,
    onToggleBackground: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onAnalyzeScreenNow: () -> Unit,
    onPickScreenshot: () -> Unit,
    onClearScreenShareError: () -> Unit,
    onStartLiveSession: () -> Unit,
    onStopLiveSession: () -> Unit,
    onClearChat: () -> Unit,
    onDismissStreamError: () -> Unit,
    onClearVoiceError: () -> Unit,
    onClearLiveError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isActivelyListening = voiceState.isListening ||
        liveVoiceState is LiveVoiceState.Active ||
        liveVoiceState is LiveVoiceState.Connecting ||
        (voiceState.isBackgroundRunning && !voiceState.isBackgroundPaused)

    LaunchedEffect(messages.size, streamingState, activeSessionId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Hero Header Card with Tron Data Disc & Subtle Listening Pulse Animation
        JarvisHeroHeader(
            settings = settings,
            voiceState = voiceState,
            isActivelyListening = isActivelyListening,
            onToggleBackground = onToggleBackground,
            onClearChat = onClearChat,
            hasMessages = messages.isNotEmpty()
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Live Network, Battery Monitor, Assistant State Machine & LAN Trigger Status Bar
        RuntimeTelemetryRibbon(
            networkState = networkState,
            assistantState = assistantState,
            lanServerState = lanServerState,
            batteryStatus = batteryStatus,
            isActivelyListening = isActivelyListening
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Real-Time Screen Sharing & Visual Analysis Strip
        ScreenVisionControlStrip(
            screenShareState = screenShareState,
            onToggleScreenShare = onToggleScreenShare,
            onAnalyzeScreenNow = onAnalyzeScreenNow,
            onPickScreenshot = onPickScreenshot,
            onClearError = onClearScreenShareError
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Interaction Mode Switcher: Native STT + Multi-Engine vs Gemini Live Real-time Audio
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("console_mode_switcher")
        ) {
            SegmentedButton(
                selected = selectedMode == 0,
                onClick = { onSelectMode(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = { Text(stringResource(R.string.mode_native_voice)) }
            )
            SegmentedButton(
                selected = selectedMode == 1,
                onClick = { onSelectMode(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = { Text(stringResource(R.string.mode_gemini_live)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Autonomy Permissions Banner when any runtime permission is still pending
        AnimatedVisibility(visible = missingPermissionsCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Text(
                        text = if (!hasMicPermission) {
                            stringResource(R.string.permission_mic_required)
                        } else {
                            "$missingPermissionsCount permissão(ões) de autonomia pendente(s) (contatos, agenda, localização, câmera)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = onRequestAllPermissions,
                        modifier = Modifier.testTag("grant_all_permissions_button")
                    ) {
                        Text(stringResource(R.string.permission_grant))
                    }
                }
            }
        }

        if (selectedMode == 1) {
            // Gemini Live Real-Time Bidirectional Voice Mode
            GeminiLiveVoicePanel(
                liveState = liveVoiceState,
                settings = settings,
                hasMicPermission = hasMicPermission,
                onRequestMicPermission = onRequestMicPermission,
                onStart = onStartLiveSession,
                onStop = onStopLiveSession,
                onDismissError = onClearLiveError,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Persistent Multi-Session Conversation Switcher
            ConversationSessionBar(
                sessions = sessions,
                activeSessionId = activeSessionId,
                onSelectSession = onSelectSession,
                onCreateNewSession = onCreateNewSession,
                onDeleteSession = onDeleteSession
            )

            // Native Voice STT + Multi-Engine Streaming Console
            AnimatedVisibility(
                visible = voiceState.isListening || voiceState.isBackgroundRunning || voiceState.isSpeaking || isTtsSpeaking || voiceState.isProcessingVoice
            ) {
                LiveNativeVoiceStrip(
                    voiceState = voiceState,
                    isTtsSpeaking = isTtsSpeaking,
                    settings = settings,
                    onToggleAction = onMicClick,
                    onStopSpeaking = onStopSpeaking
                )
            }

            // Voice or Streaming Errors (transparent propagation)
            val activeError = voiceState.errorMessage ?: (streamingState as? StreamingUiState.Error)?.message
            AnimatedVisibility(visible = activeError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onClearVoiceError()
                                onDismissStreamError()
                            }
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Fechar erro")
                        }
                    }
                }
            }

            // Chat Feed
            if (messages.isEmpty() && streamingState !is StreamingUiState.Streaming) {
                EmptyConsolePlaceholder(
                    settings = settings,
                    isListening = isActivelyListening,
                    rmsDb = voiceState.rmsDb,
                    onQuickPrompt = onSendPrompt,
                    onStartGuidedTour = onStartGuidedTour,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            isTtsSpeaking = isTtsSpeaking,
                            onSpeak = { onSpeakMessage(msg.content) },
                            onStopSpeaking = onStopSpeaking
                        )
                    }

                    if (streamingState is StreamingUiState.Streaming) {
                        item(key = "streaming_active") {
                            StreamingBubbleItem(state = streamingState)
                        }
                    }
                }
            }

            // Quick Offline, Vision, Device Settings & Self-Navigation Chips
            val quickPrompts = remember {
                listOf(
                    "O que estou vendo na tela?",
                    "Aumentar brilho",
                    "Ligar lanterna",
                    "Me guie pelo app",
                    "Status do sistema",
                    "Abrir WhatsApp"
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp)
            ) {
                items(quickPrompts) { chipText ->
                    AssistChip(
                        onClick = { onSendPrompt(chipText) },
                        label = { Text(chipText, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            // Bottom Composer with Native Speech-to-Text Mic + Send Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.input_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_prompt_input"),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onSendPrompt(inputText)
                                inputText = ""
                            }
                        }
                    )
                )

                val isMicHot = voiceState.isListening && !voiceState.isBackgroundRunning
                val isMicPulsing = isMicHot || (voiceState.isBackgroundRunning && !voiceState.isBackgroundPaused)
                TronPulseHaloBox(
                    isListening = isMicPulsing,
                    haloSize = 58.dp,
                    pulseColor = if (isMicHot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (!hasMicPermission) onRequestMicPermission() else onMicClick()
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("native_mic_button"),
                        colors = if (isMicHot) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    ) {
                        Icon(
                            imageVector = when {
                                voiceState.isBackgroundRunning && voiceState.isBackgroundPaused -> Icons.Rounded.PlayArrow
                                voiceState.isBackgroundRunning -> Icons.Rounded.Pause
                                isMicHot -> Icons.Rounded.Stop
                                else -> Icons.Rounded.Mic
                            },
                            contentDescription = if (isMicHot) {
                                stringResource(R.string.cd_stop_listening)
                            } else {
                                stringResource(R.string.cd_mic_listen)
                            }
                        )
                    }
                }

                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendPrompt(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && streamingState !is StreamingUiState.Streaming,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("send_prompt_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.cd_send_message)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenVisionControlStrip(
    screenShareState: ScreenShareState,
    onToggleScreenShare: () -> Unit,
    onAnalyzeScreenNow: () -> Unit,
    onPickScreenshot: () -> Unit,
    onClearError: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (screenShareState.isSharing) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (screenShareState.isSharing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("screen_vision_strip")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (screenShareState.isSharing) Icons.Rounded.Visibility else Icons.Rounded.ScreenShare,
                    contentDescription = null,
                    tint = if (screenShareState.isSharing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = when {
                        screenShareState.isAnalyzing -> "Interpretando tela com IA…"
                        screenShareState.isSharing && screenShareState.hasFrame ->
                            "Tela ao vivo (${screenShareState.captureCount} quadros)"
                        screenShareState.isSharing -> "Tela ao vivo · Aguardando quadro"
                        screenShareState.hasFrame -> "Captura pronta para análise"
                        else -> "Visão de Tela"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (screenShareState.hasFrame) {
                    FilledTonalButton(
                        onClick = onAnalyzeScreenNow,
                        enabled = !screenShareState.isAnalyzing,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("analyze_screen_now_button")
                    ) {
                        Text(
                            text = if (screenShareState.isAnalyzing) "Lendo…" else "Analisar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                IconButton(
                    onClick = onPickScreenshot,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("pick_screenshot_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.cd_pick_screenshot),
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalButton(
                    onClick = onToggleScreenShare,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = if (screenShareState.isSharing) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("toggle_screen_share_button")
                ) {
                    Icon(
                        imageVector = if (screenShareState.isSharing) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                        contentDescription = stringResource(R.string.cd_share_screen),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (screenShareState.isSharing) "Parar" else "Compartilhar",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (screenShareState.lastVisualSummary.isNotBlank()) {
                val enginePrefix = if (screenShareState.lastAnalyzedEngine.isNotBlank()) {
                    "Contexto (${screenShareState.lastAnalyzedEngine}): "
                } else {
                    "Contexto: "
                }
                Text(
                    text = "$enginePrefix${screenShareState.lastVisualSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (screenShareState.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = screenShareState.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClearError,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar aviso", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun JarvisHeroHeader(
    settings: JarvisSettings,
    voiceState: VoiceRuntimeState,
    isActivelyListening: Boolean,
    onToggleBackground: () -> Unit,
    onClearChat: () -> Unit,
    hasMessages: Boolean
) {
    val pulse = rememberTronPulseState(isListening = isActivelyListening)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = if (isActivelyListening) 1.5.dp else 1.dp,
            color = if (isActivelyListening) {
                MaterialTheme.colorScheme.primary.copy(alpha = pulse.borderAlpha)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_core_banner_1795731283687),
                contentDescription = stringResource(R.string.cd_jarvis_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xEB0A0D12),
                                Color(0xB80A0D12),
                                Color(0x990A0D12)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TronPulseHaloBox(
                            isListening = isActivelyListening,
                            haloSize = 46.dp,
                            pulseColor = MaterialTheme.colorScheme.primary
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.tron_data_disc_transparent),
                                contentDescription = stringResource(R.string.app_name),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            text = "A.O.Jarvis",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFECF2F8)
                        )
                        Surface(
                            color = when (settings.routingMode) {
                                RoutingMode.OLLAMA -> MaterialTheme.colorScheme.secondaryContainer
                                RoutingMode.OFFLINE -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = settings.routingMode.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (settings.routingMode) {
                                    RoutingMode.OLLAMA -> MaterialTheme.colorScheme.onSecondaryContainer
                                    RoutingMode.OFFLINE -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (settings.routingMode) {
                            RoutingMode.OLLAMA -> "LAN: ${settings.ollamaModel} @ ${settings.ollamaBaseUrl.removePrefix("http://")}"
                            RoutingMode.OFFLINE -> "Ferramentas locais & STT nativo"
                            RoutingMode.GEMINI -> "Cloud: ${settings.geminiModel}"
                            RoutingMode.AUTO -> "Auto: Offline → Ollama → ${settings.geminiModel}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB8C7D9),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = voiceState.isBackgroundRunning,
                        onClick = onToggleBackground,
                        modifier = Modifier.testTag("background_mode_toggle"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Hearing,
                                contentDescription = stringResource(R.string.cd_toggle_background),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (voiceState.isBackgroundRunning) {
                                    stringResource(R.string.bg_mode_on)
                                } else {
                                    stringResource(R.string.bg_mode_off)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    if (hasMessages) {
                        IconButton(
                            onClick = onClearChat,
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.cd_clear_chat),
                                tint = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveNativeVoiceStrip(
    voiceState: VoiceRuntimeState,
    isTtsSpeaking: Boolean,
    settings: JarvisSettings,
    onToggleAction: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val isListeningPulse = voiceState.isListening || (voiceState.isBackgroundRunning && !voiceState.isBackgroundPaused)
    val pulse = rememberTronPulseState(isListening = isListeningPulse)
    Surface(
        color = if (voiceState.isBackgroundRunning) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = if (isListeningPulse) 1.5.dp else 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulse.borderAlpha)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (voiceState.isSpeaking || isTtsSpeaking) Icons.Rounded.RecordVoiceOver else Icons.Rounded.Hearing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTtsSpeaking) "A.O.Jarvis falando (TTS)…" else voiceState.statusText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        voiceState.partialTranscript.isNotBlank() -> "\"${voiceState.partialTranscript}\""
                        isTtsSpeaking -> "Síntese de voz nativa ativa"
                        voiceState.isBackgroundRunning && settings.wakeWordEnabled ->
                            "Diga \"${settings.wakeWord}\" seguido do comando · STT nativo"
                        else -> "Reconhecimento de fala nativo do Android ativo"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isTtsSpeaking || voiceState.isSpeaking) {
                    FilledTonalButton(
                        onClick = onStopSpeaking,
                        modifier = Modifier.testTag("stop_tts_strip_button")
                    ) {
                        Icon(Icons.Rounded.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Silenciar")
                    }
                } else if (voiceState.isBackgroundRunning) {
                    FilledTonalButton(onClick = onToggleAction) {
                        Text(if (voiceState.isBackgroundPaused) "Retomar" else "Pausar")
                    }
                }
            }
            if (voiceState.isListening || voiceState.isProcessingVoice) {
                Spacer(modifier = Modifier.height(6.dp))
                val level = ((voiceState.rmsDb + 2f) / 12f).coerceIn(0.08f, 1f)
                if (voiceState.isProcessingVoice) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { level },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyConsolePlaceholder(
    settings: JarvisSettings,
    isListening: Boolean,
    rmsDb: Float,
    onQuickPrompt: (String) -> Unit,
    onStartGuidedTour: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VoiceOrbVisualizer(
            isActive = isListening,
            rmsDb = if (isListening) rmsDb else 0f,
            accentColor = MaterialTheme.colorScheme.primary,
            secondaryColor = MaterialTheme.colorScheme.secondary,
            size = 136.dp
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isListening) "A.O.Jarvis Ouvindo…" else "A.O.Jarvis Pronto",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Converse via Gemini ou Ollama local, controle aplicativos (WhatsApp, Maps, Spotify, Agenda) ou peça para o Jarvis guiar você pelo próprio app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onQuickPrompt("Status do sistema") },
                modifier = Modifier.testTag("quick_diagnostics_button")
            ) {
                Text("Diagnóstico (${settings.routingMode.label})")
            }
            Button(
                onClick = onStartGuidedTour,
                modifier = Modifier.testTag("start_guided_tour_button")
            ) {
                Text("Auto-Guia")
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessageEntity,
    isTtsSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val isUser = message.role == "USER"
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                message.isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = when {
                isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                message.isError -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        EngineBadge(
                            engineName = message.engineType,
                            modelName = message.modelName
                        )
                        if (message.latencyMs > 0) {
                            Text(
                                text = buildString {
                                    append("${message.latencyMs}ms")
                                    if (message.tokensPerSec > 0.5) {
                                        append(String.format(Locale.US, " · %.1f tok/s", message.tokensPerSec))
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        IconButton(
                            onClick = { if (isTtsSpeaking) onStopSpeaking() else onSpeak() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("speak_message_${message.id}")
                        ) {
                            Icon(
                                imageVector = if (isTtsSpeaking) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = if (isTtsSpeaking) "Parar leitura TTS" else "Ouvir resposta em voz alta",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(4.dp))
                val sourceLabel = when {
                    !isUser -> timeStr
                    message.engineType == "LAN_TRIGGER" -> "$timeStr · Gatilho LAN"
                    message.engineType == "VOICE_BG" -> "$timeStr · Voz Contínua"
                    message.engineType.startsWith("VOICE") -> "$timeStr · Voz Nativa"
                    else -> timeStr
                }
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun RuntimeTelemetryRibbon(
    networkState: NetworkEngineState,
    assistantState: AssistantState,
    lanServerState: LanServerState,
    batteryStatus: BatteryStatusSnapshot,
    isActivelyListening: Boolean
) {
    val pulse = rememberTronPulseState(isListening = isActivelyListening)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("runtime_telemetry_ribbon")
    ) {
        item {
            Surface(
                color = if (networkState.isWifiConnected) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                } else if (networkState.isOnline) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (networkState.isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${networkState.transportLabel} · ${networkState.activeEngine.label}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (batteryStatus.isKnown) {
            item {
                Surface(
                    color = when {
                        batteryStatus.needsPowerSaving -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.78f)
                        batteryStatus.isCharging -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("ribbon_battery_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                batteryStatus.isCharging -> Icons.Rounded.BatteryChargingFull
                                batteryStatus.needsPowerSaving -> Icons.Rounded.BatteryAlert
                                else -> Icons.Rounded.BatteryStd
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${batteryStatus.levelPercent}%" +
                                if (batteryStatus.needsPowerSaving) " · Economizar" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        item {
            Surface(
                color = if (isActivelyListening) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                shape = RoundedCornerShape(8.dp),
                border = if (isActivelyListening) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulse.borderAlpha))
                } else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isActivelyListening) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulse.borderAlpha))
                        )
                    }
                    Text(
                        text = if (isActivelyListening) "Ouvindo · ${assistantState.phase.toDisplayLabel()}" else assistantState.phase.toDisplayLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (lanServerState.isRunning) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lan,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "LAN :${lanServerState.port}" +
                                if (lanServerState.triggerCount > 0) " (${lanServerState.triggerCount})" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationSessionBar(
    sessions: List<ConversationSessionEntity>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onCreateNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag("conversation_session_bar")
    ) {
        item(key = "new_session_action") {
            AssistChip(
                onClick = onCreateNewSession,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = stringResource(R.string.cd_new_session),
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.session_new),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("new_session_button")
            )
        }

        items(sessions, key = { it.id }) { session ->
            FilterChip(
                selected = session.id == activeSessionId,
                onClick = { onSelectSession(session.id) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                trailingIcon = if (session.id != "default") {
                    {
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remover conversa ${session.title}",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else null,
                label = {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("session_chip_${session.id}")
            )
        }
    }
}

@Composable
private fun StreamingBubbleItem(state: StreamingUiState.Streaming) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(0.92f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EngineBadge(engineName = state.engineType.name, modelName = state.modelName)
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Gerando…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.text.ifBlank { "Roteando solicitação no A.O.Jarvis…" },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun GeminiLiveVoicePanel(
    liveState: LiveVoiceState,
    settings: JarvisSettings,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnecting = liveState is LiveVoiceState.Connecting
    val isActive = liveState is LiveVoiceState.Active
    val errorMsg = (liveState as? LiveVoiceState.Error)?.message

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EngineBadge(
            engineName = "GEMINI_LIVE",
            modelName = "gemini-3.8-live · ${settings.liveVoiceName}"
        )

        Spacer(modifier = Modifier.height(20.dp))

        VoiceOrbVisualizer(
            isActive = isActive || isConnecting,
            rmsDb = if (isActive) 7.5f else 0f,
            accentColor = MaterialTheme.colorScheme.primary,
            secondaryColor = MaterialTheme.colorScheme.secondary,
            size = 210.dp
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when (liveState) {
                LiveVoiceState.Idle -> stringResource(R.string.live_title)
                LiveVoiceState.Connecting -> stringResource(R.string.live_connecting)
                is LiveVoiceState.Active -> stringResource(R.string.live_active)
                is LiveVoiceState.Error -> "Sessão interrompida"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.live_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissError) {
                        Icon(Icons.Rounded.Close, contentDescription = "Fechar")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isActive || isConnecting) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("stop_live_session_button")
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.live_stop))
            }
        } else {
            Button(
                onClick = {
                    if (!hasMicPermission) onRequestMicPermission() else onStart()
                },
                modifier = Modifier
                    .height(52.dp)
                    .testTag("start_live_session_button")
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.live_start))
            }
        }
    }
}
