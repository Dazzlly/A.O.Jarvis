package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AppDestination
import com.example.core.AppUiCommand
import com.example.core.AppUpdateState
import com.example.core.GuidedTourStep
import com.example.core.PermissionsManager
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.EnginesScreen
import com.example.ui.screens.TelemetryScreen
import com.example.ui.screens.ToolsMemoryScreen
import com.example.ui.theme.OpenJarvisTheme

enum class JarvisTab(
    val destination: AppDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CONSOLE(AppDestination.CONSOLE, R.string.nav_console, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_console"),
    ENGINES(AppDestination.ENGINES, R.string.nav_engines, Icons.Filled.Tune, Icons.Outlined.Tune, "nav_engines"),
    TOOLS(AppDestination.TOOLS, R.string.nav_tools, Icons.Filled.Memory, Icons.Outlined.Memory, "nav_tools"),
    TELEMETRY(AppDestination.TELEMETRY, R.string.nav_telemetry, Icons.Filled.Insights, Icons.Outlined.Insights, "nav_telemetry")
}

@Composable
fun OpenJarvisApp(viewModel: OpenJarvisViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val commands by viewModel.voiceCommands.collectAsStateWithLifecycle()
    val traces by viewModel.traces.collectAsStateWithLifecycle()
    val streamingState by viewModel.streamingState.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val lanServerState by viewModel.lanServerState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val liveVoiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
    val ttsStatus by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val guidedTour by viewModel.guidedTour.collectAsStateWithLifecycle()
    val ollamaHealth by viewModel.ollamaHealth.collectAsStateWithLifecycle()
    val isProbingOllama by viewModel.isProbingOllama.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(JarvisTab.CONSOLE) }
    var selectedConsoleMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Native STT & Chat, 1 = Gemini Live
    var permissionRevision by remember { mutableIntStateOf(0) }
    var autoPermissionsPrompted by rememberSaveable { mutableStateOf(false) }
    var autoUpdateChecked by rememberSaveable { mutableStateOf(false) }
    var updateBannerDismissed by rememberSaveable { mutableStateOf(false) }

    val permissionItems = remember(permissionRevision) {
        PermissionsManager.getPermissionSnapshot(context)
    }
    val missingPermissionsCount = remember(permissionItems) {
        permissionItems.count { !it.isGranted }
    }
    val hasMicPermission = remember(permissionRevision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRequiresMic by remember { mutableStateOf(false) }

    // Report active tab to AppSelfController so Jarvis always knows where it is
    LaunchedEffect(currentTab) {
        viewModel.reportCurrentDestination(currentTab.destination)
    }

    BackHandler(enabled = currentTab != JarvisTab.CONSOLE) {
        currentTab = JarvisTab.CONSOLE
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionRevision += 1
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        if (pendingPermissionAction != null) {
            if (micGranted || !pendingRequiresMic) {
                pendingPermissionAction?.invoke()
            } else {
                Toast.makeText(
                    context,
                    "Permissão de microfone necessária para voz nativa, Gemini Live e escuta em background.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            val grantedNow = grants.values.count { it }
            if (grantedNow > 0) {
                Toast.makeText(
                    context,
                    "$grantedNow permissão(ões) de autonomia ativada(s) para o OpenJarvis.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pendingPermissionAction = null
        pendingRequiresMic = false
    }

    fun requestAllAutonomyPermissions() {
        val missing = PermissionsManager.missingPermissions(context)
        if (missing.isEmpty()) {
            Toast.makeText(context, "Todas as permissões de autonomia já estão ativas!", Toast.LENGTH_SHORT).show()
        } else {
            pendingPermissionAction = null
            pendingRequiresMic = false
            permissionsLauncher.launch(missing)
        }
    }

    fun requestPermissionsAndRun(
        requireMic: Boolean = true,
        includeNotifications: Boolean = false,
        onGranted: () -> Unit = {}
    ) {
        val toRequest = mutableListOf<String>()
        if (requireMic &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            toRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (includeNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (toRequest.isEmpty()) {
            onGranted()
        } else {
            pendingRequiresMic = requireMic
            pendingPermissionAction = onGranted
            permissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    // Automatically request all available safe autonomy permissions on first app entry
    LaunchedEffect(Unit) {
        if (!autoPermissionsPrompted) {
            autoPermissionsPrompted = true
            val missing = PermissionsManager.missingPermissions(context)
            if (missing.isNotEmpty()) {
                permissionsLauncher.launch(missing)
            }
        }
    }

    // Automatically check GitHub Releases once on startup when configured
    LaunchedEffect(settings.githubRepo, settings.autoCheckUpdates) {
        if (!autoUpdateChecked && settings.autoCheckUpdates && settings.githubRepo.isNotBlank()) {
            autoUpdateChecked = true
            viewModel.checkForUpdates(settings.githubRepo)
        }
    }

    // Collect Jarvis Self-Navigation & Self-Control UI Commands
    LaunchedEffect(viewModel) {
        viewModel.uiCommands.collect { command ->
            when (command) {
                is AppUiCommand.NavigateTo -> {
                    currentTab = JarvisTab.entries.firstOrNull { it.destination == command.destination }
                        ?: JarvisTab.CONSOLE
                }

                is AppUiCommand.SetConsoleMode -> {
                    currentTab = JarvisTab.CONSOLE
                    selectedConsoleMode = if (command.geminiLive) 1 else 0
                }

                is AppUiCommand.SetDarkTheme -> {
                    viewModel.updateDarkTheme(command.enabled)
                }

                is AppUiCommand.SetRoutingMode -> {
                    viewModel.updateRoutingMode(command.mode)
                }

                is AppUiCommand.ToggleBackgroundService -> {
                    if (command.shouldRun) {
                        val needsMic = settings.voiceListeningInBgEnabled
                        requestPermissionsAndRun(requireMic = needsMic, includeNotifications = true) {
                            viewModel.startBackgroundServiceExplicitly()
                        }
                    } else {
                        viewModel.stopBackgroundServiceExplicitly()
                    }
                }

                AppUiCommand.RequestAllPermissions -> {
                    requestAllAutonomyPermissions()
                }

                AppUiCommand.CreateNewSession -> {
                    viewModel.createNewSession()
                }

                AppUiCommand.ProbeOllama -> {
                    viewModel.probeOllama()
                }

                AppUiCommand.CheckUpdates -> {
                    updateBannerDismissed = false
                    viewModel.checkForUpdates()
                }

                is AppUiCommand.ShowGuidanceBanner -> {
                    Toast.makeText(context, command.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    OpenJarvisTheme(darkTheme = settings.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            JarvisTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            JarvisTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Interactive Jarvis Self-Guided Tour Banner
                        val activeStep = guidedTour.currentStep
                        AnimatedVisibility(visible = activeStep != null) {
                            if (activeStep != null) {
                                GuidedTourBanner(
                                    step = activeStep,
                                    isLastStep = activeStep.index >= guidedTour.steps.lastIndex,
                                    onNext = { viewModel.nextTourStep() },
                                    onStop = { viewModel.stopGuidedTour() }
                                )
                            }
                        }

                        // Remote Update Available Banner (GitHub Releases / F-Droid)
                        val availableUpdate = updateState as? AppUpdateState.UpdateAvailable
                        AnimatedVisibility(visible = availableUpdate != null && !updateBannerDismissed) {
                            if (availableUpdate != null) {
                                val fdroidUrl = settings.fdroidRepoUrl.ifBlank {
                                    viewModel.buildDefaultFdroidUrl(settings.githubRepo)
                                }
                                UpdateAvailableBanner(
                                    tagName = availableUpdate.release.tagName,
                                    hasFdroidUrl = fdroidUrl.isNotBlank(),
                                    onOpenFdroid = {
                                        val res = viewModel.openInFdroid(fdroidUrl)
                                        Toast.makeText(
                                            context,
                                            res.getOrElse { it.message ?: "Erro ao abrir F-Droid" },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onOpenGitHub = {
                                        val res = viewModel.openGitHubRelease(availableUpdate.release.htmlUrl)
                                        Toast.makeText(
                                            context,
                                            res.getOrElse { it.message ?: "Erro ao abrir GitHub" },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDismiss = { updateBannerDismissed = true }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            when (currentTab) {
                                JarvisTab.CONSOLE -> ConsoleScreen(
                                    sessions = sessions,
                                    activeSessionId = activeSessionId,
                                    messages = messages,
                                    streamingState = streamingState,
                                    assistantState = assistantState,
                                    networkState = networkState,
                                    lanServerState = lanServerState,
                                    voiceState = voiceState,
                                    liveVoiceState = liveVoiceState,
                                    isTtsSpeaking = isTtsSpeaking,
                                    selectedMode = selectedConsoleMode,
                                    onSelectMode = { selectedConsoleMode = it },
                                    settings = settings,
                                    hasMicPermission = hasMicPermission,
                                    missingPermissionsCount = missingPermissionsCount,
                                    onRequestAllPermissions = { requestAllAutonomyPermissions() },
                                    onRequestMicPermission = {
                                        requestPermissionsAndRun(requireMic = true, includeNotifications = true)
                                    },
                                    onSelectSession = { viewModel.switchSession(it) },
                                    onCreateNewSession = { viewModel.createNewSession() },
                                    onDeleteSession = { viewModel.deleteSession(it) },
                                    onSendPrompt = { viewModel.sendPrompt(it) },
                                    onSpeakMessage = { viewModel.speakText(it) },
                                    onStopSpeaking = { viewModel.stopSpeaking() },
                                    onStartGuidedTour = { viewModel.startGuidedTour() },
                                    onMicClick = {
                                        requestPermissionsAndRun(requireMic = true, includeNotifications = false) {
                                            viewModel.onMicButtonClicked()
                                        }
                                    },
                                    onToggleBackground = {
                                        val needsMic = !voiceState.isBackgroundRunning && settings.voiceListeningInBgEnabled
                                        requestPermissionsAndRun(
                                            requireMic = needsMic,
                                            includeNotifications = true
                                        ) {
                                            viewModel.toggleBackgroundService()
                                        }
                                    },
                                    onStartLiveSession = {
                                        requestPermissionsAndRun(requireMic = true, includeNotifications = false) {
                                            viewModel.startLiveSession()
                                        }
                                    },
                                    onStopLiveSession = { viewModel.stopLiveSession() },
                                    onClearChat = { viewModel.clearChat() },
                                    onDismissStreamError = { viewModel.dismissStreamError() },
                                    onClearVoiceError = { viewModel.clearVoiceError() },
                                    onClearLiveError = { viewModel.clearLiveError() }
                                )

                                JarvisTab.ENGINES -> EnginesScreen(
                                    settings = settings,
                                    networkState = networkState,
                                    lanServerState = lanServerState,
                                    ttsStatus = ttsStatus,
                                    isTtsSpeaking = isTtsSpeaking,
                                    permissionItems = permissionItems,
                                    isBackgroundRunning = voiceState.isBackgroundRunning,
                                    ollamaHealth = ollamaHealth,
                                    isProbingOllama = isProbingOllama,
                                    onRequestAllPermissions = { requestAllAutonomyPermissions() },
                                    onTestTts = { viewModel.testTtsVoice() },
                                    onStopTts = { viewModel.stopSpeaking() },
                                    onReinitTts = { viewModel.reinitializeTts() },
                                    onUpdateRoutingMode = { viewModel.updateRoutingMode(it) },
                                    onUpdateOllamaConfig = { url, model -> viewModel.updateOllamaConfig(url, model) },
                                    onProbeOllama = { viewModel.probeOllama(it) },
                                    onRefreshNetwork = { viewModel.refreshNetworkState() },
                                    onUpdateGeminiModel = { viewModel.updateGeminiModel(it) },
                                    onUpdateLiveVoice = { viewModel.updateLiveVoice(it) },
                                    onUpdateTemperature = { viewModel.updateTemperature(it) },
                                    onUpdateSystemPrompt = { viewModel.updateSystemPrompt(it) },
                                    onUpdateVoicePreferences = { wakeOn, wake, offlineStt, autoSpeak ->
                                        viewModel.updateVoicePreferences(wakeOn, wake, offlineStt, autoSpeak)
                                    },
                                    onUpdateBackgroundModePreferences = { voiceInBg, continuousMode ->
                                        viewModel.updateBackgroundModePreferences(voiceInBg, continuousMode)
                                    },
                                    onUpdateLanTriggerConfig = { enabled, port ->
                                        viewModel.updateLanTriggerConfig(enabled, port)
                                    },
                                    onRegenerateLanToken = { viewModel.regenerateLanTriggerToken() },
                                    onToggleBackgroundService = {
                                        val needsMic = !voiceState.isBackgroundRunning && settings.voiceListeningInBgEnabled
                                        requestPermissionsAndRun(
                                            requireMic = needsMic,
                                            includeNotifications = true
                                        ) {
                                            viewModel.toggleBackgroundService()
                                        }
                                    },
                                    updateState = updateState,
                                    currentVersionLabel = viewModel.currentVersionLabel,
                                    onUpdateRemoteUpdateConfig = { repo, fdroidUrl, autoCheck ->
                                        viewModel.updateRemoteUpdateConfig(repo, fdroidUrl, autoCheck)
                                    },
                                    onCheckForUpdates = {
                                        updateBannerDismissed = false
                                        viewModel.checkForUpdates(it)
                                    },
                                    onBuildDefaultFdroidUrl = { viewModel.buildDefaultFdroidUrl(it) },
                                    onOpenGitHubRelease = { viewModel.openGitHubRelease(it) },
                                    onOpenInFdroid = { viewModel.openInFdroid(it) },
                                    onUpdateDarkTheme = { viewModel.updateDarkTheme(it) }
                                )

                                JarvisTab.TOOLS -> ToolsMemoryScreen(
                                    commands = commands,
                                    memories = memories,
                                    onAddCommand = { trigger, type, payload ->
                                        viewModel.addVoiceCommand(trigger, type, payload)
                                    },
                                    onToggleCommand = { viewModel.toggleVoiceCommand(it) },
                                    onDeleteCommand = { viewModel.deleteVoiceCommand(it) },
                                    onRunCommandNow = { phrase ->
                                        viewModel.sendPrompt(phrase, sourceTag = "TOOL_TEST")
                                        currentTab = JarvisTab.CONSOLE
                                    },
                                    onAddMemory = { key, content, category ->
                                        viewModel.addMemory(key, content, category)
                                    },
                                    onDeleteMemory = { viewModel.deleteMemory(it) }
                                )

                                JarvisTab.TELEMETRY -> TelemetryScreen(
                                    traces = traces,
                                    onClearTraces = { viewModel.clearTraces() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidedTourBanner(
    step: GuidedTourStep,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("guided_tour_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Auto-Guia Jarvis · ${step.title}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fechar tour guiado",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.narration,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.testTag("guided_tour_next_button")
                ) {
                    Text(if (isLastStep) "Concluir tour" else "Próximo passo")
                    if (!isLastStep) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.NavigateNext,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(
    tagName: String,
    hasFdroidUrl: Boolean,
    onOpenFdroid: () -> Unit,
    onOpenGitHub: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("update_available_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Atualização disponível: $tagName",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_dismiss_update_banner),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasFdroidUrl) {
                    Button(
                        onClick = onOpenFdroid,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("banner_open_fdroid_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Atualizar no F-Droid")
                    }
                }
                OutlinedButton(
                    onClick = onOpenGitHub,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("banner_open_github_button")
                ) {
                    Text("Ver no GitHub")
                }
            }
        }
    }
}
