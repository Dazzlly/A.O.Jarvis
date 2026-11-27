package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.cloud.onGoogleSignInClicked
import com.example.cloud.signOutWithCredentialClear
import com.example.core.AppDestination
import com.example.core.AppUiCommand
import com.example.core.AppUpdateState
import com.example.core.GuidedTourStep
import com.example.core.PermissionsManager
import com.example.data.AppThemeMode
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.EnginesScreen
import com.example.ui.screens.TelemetryScreen
import com.example.ui.screens.ToolsMemoryScreen
import com.example.ui.theme.JarvisThemeBackdrop
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
fun OpenJarvisApp(
    viewModel: OpenJarvisViewModel,
    assistantLaunchSignal: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val commands by viewModel.voiceCommands.collectAsStateWithLifecycle()
    val offlineVoiceCommands by viewModel.offlineVoiceCommands.collectAsStateWithLifecycle()
    val traces by viewModel.traces.collectAsStateWithLifecycle()
    val voiceCommandLogs by viewModel.voiceCommandLogs.collectAsStateWithLifecycle()
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
    val fdroidRepoStatus by viewModel.fdroidRepoStatus.collectAsStateWithLifecycle()
    val screenShareState by viewModel.screenShareState.collectAsStateWithLifecycle()
    val deviceSettingsSnapshot by viewModel.deviceSettingsSnapshot.collectAsStateWithLifecycle()
    val deviceControlServiceState by viewModel.deviceControlServiceState.collectAsStateWithLifecycle()
    val cloudSyncSummary by viewModel.cloudSyncSummary.collectAsStateWithLifecycle()
    val defaultAssistantStatus by viewModel.defaultAssistantStatus.collectAsStateWithLifecycle()
    val selfEvolutionState by viewModel.selfEvolutionState.collectAsStateWithLifecycle()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(JarvisTab.CONSOLE) }
    var selectedConsoleMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Native STT & Chat, 1 = Gemini Live
    var selectedToolsTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Comandos, 1 = Memórias, 2 = Auto-Dev
    var selfEvolutionDraft by rememberSaveable { mutableStateOf("") }
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

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startScreenShare(result.resultCode, data)
            Toast.makeText(
                context,
                "Compartilhamento de tela ativo! Pergunte ao OpenJarvis sobre o que estiver vendo.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, "Compartilhamento de tela cancelado.", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            currentTab = JarvisTab.CONSOLE
            selectedConsoleMode = 0
            viewModel.analyzePickedImageUri(uri)
        }
    }

    val defaultAssistantRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshDefaultAssistantStatus()
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(
                context,
                "A.O.Jarvis definido como Assistente Digital Padrão!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Auto-refresh Default Assistant & Device Settings when returning from Android system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDefaultAssistantStatus()
                viewModel.refreshDeviceSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    fun launchScreenShareFlow() {
        val startProjection = {
            mediaProjectionLauncher.launch(viewModel.createScreenCaptureIntent())
        }
        requestPermissionsAndRun(
            requireMic = false,
            includeNotifications = true,
            onGranted = startProjection
        )
    }

    fun launchDefaultAssistantConfigFlow() {
        val roleIntent = viewModel.createDefaultAssistantRoleIntent()
        if (roleIntent != null) {
            runCatching {
                defaultAssistantRoleLauncher.launch(roleIntent)
            }.onFailure {
                val fallback = viewModel.openDefaultAssistantSettings()
                Toast.makeText(context, fallback.message, Toast.LENGTH_SHORT).show()
            }
        } else {
            val res = viewModel.openDefaultAssistantSettings()
            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
        }
    }

    // Automatically open Console and start voice listening when triggered via Default Assistant gesture / ACTION_ASSIST
    LaunchedEffect(assistantLaunchSignal) {
        if (assistantLaunchSignal > 0) {
            currentTab = JarvisTab.CONSOLE
            selectedConsoleMode = 0
            requestPermissionsAndRun(requireMic = true, includeNotifications = false) {
                if (!voiceState.isListening) {
                    viewModel.onMicButtonClicked()
                }
            }
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

                is AppUiCommand.SetThemeMode -> {
                    viewModel.updateThemeMode(command.mode)
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

                is AppUiCommand.ToggleDeviceControlService -> {
                    if (command.shouldRun) {
                        requestPermissionsAndRun(requireMic = false, includeNotifications = true) {
                            viewModel.startDeviceControlService()
                        }
                    } else {
                        viewModel.stopDeviceControlService()
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

                AppUiCommand.RequestScreenShare -> {
                    if (!screenShareState.isSharing) {
                        launchScreenShareFlow()
                    }
                }

                AppUiCommand.StopScreenShare -> {
                    viewModel.stopScreenShare()
                }

                is AppUiCommand.StartSelfEvolution -> {
                    currentTab = JarvisTab.TOOLS
                    selectedToolsTab = 2
                    selfEvolutionDraft = command.request
                    if (command.request.isNotBlank() && settings.githubRepo.isNotBlank() && hasGitHubToken) {
                        viewModel.generateSelfEvolutionProposal(command.request)
                    }
                }

                is AppUiCommand.ShowGuidanceBanner -> {
                    Toast.makeText(context, command.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    OpenJarvisTheme(
        themeMode = settings.themeMode,
        customAccentArgb = settings.customAccentColor
    ) {
        JarvisThemeBackdrop(themeMode = settings.themeMode) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        if (!useRail) {
                            Column {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = if (settings.themeMode == AppThemeMode.TRON || settings.themeMode == AppThemeMode.CUSTOM) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
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
                                    viewModel.buildRawFdroidUrl(settings.githubRepo)
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
                                    screenShareState = screenShareState,
                                    batteryStatus = deviceControlServiceState.batteryStatus,
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
                                    onToggleScreenShare = {
                                        if (screenShareState.isSharing) {
                                            viewModel.stopScreenShare()
                                        } else {
                                            launchScreenShareFlow()
                                        }
                                    },
                                    onAnalyzeScreenNow = {
                                        viewModel.analyzeCurrentScreenNow()
                                    },
                                    onPickScreenshot = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    onClearScreenShareError = { viewModel.clearScreenShareError() },
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
                                    fdroidRepoStatus = fdroidRepoStatus,
                                    currentVersionLabel = viewModel.currentVersionLabel,
                                    onUpdateRemoteUpdateConfig = { repo, fdroidUrl, autoCheck ->
                                        viewModel.updateRemoteUpdateConfig(repo, fdroidUrl, autoCheck)
                                    },
                                    onCheckForUpdates = {
                                        updateBannerDismissed = false
                                        viewModel.checkForUpdates(it)
                                    },
                                    onVerifyFdroidRepo = { viewModel.verifyFdroidRepo(it) },
                                    onBuildRawFdroidUrl = { viewModel.buildRawFdroidUrl(it) },
                                    onBuildDefaultFdroidUrl = { viewModel.buildDefaultFdroidUrl(it) },
                                    onOpenGitHubRelease = { viewModel.openGitHubRelease(it) },
                                    onOpenInFdroid = { viewModel.openInFdroid(it) },
                                    deviceSettingsSnapshot = deviceSettingsSnapshot,
                                    deviceControlServiceState = deviceControlServiceState,
                                    onToggleDeviceControlService = {
                                        if (deviceControlServiceState.isRunning) {
                                            viewModel.stopDeviceControlService()
                                        } else {
                                            requestPermissionsAndRun(
                                                requireMic = false,
                                                includeNotifications = true
                                            ) {
                                                viewModel.startDeviceControlService()
                                            }
                                        }
                                    },
                                    onRefreshDeviceSettings = { viewModel.refreshDeviceSettings() },
                                    onApplyPowerSaving = { viewModel.applyPowerSavingProfile() },
                                    onOpenWriteSettingsPermission = { viewModel.openWriteSettingsPermission() },
                                    onOpenDndPermission = { viewModel.openDndAccessPermission() },
                                    onSetDeviceBrightness = { viewModel.setDeviceBrightness(it) },
                                    onSetDeviceVolume = { viewModel.setDeviceVolume(it) },
                                    onToggleDeviceWifi = { viewModel.toggleDeviceWifi(it) },
                                    onToggleDeviceBluetooth = { viewModel.toggleDeviceBluetooth(it) },
                                    onToggleDeviceTorch = { viewModel.toggleDeviceTorch(it) },
                                    onToggleDeviceAutoRotate = { viewModel.toggleDeviceAutoRotate(it) },
                                    onToggleDeviceDnd = { viewModel.toggleDeviceDnd(it) },
                                    onOpenDevicePanel = { viewModel.openDeviceSystemPanel(it) },
                                    cloudSyncSummary = cloudSyncSummary,
                                    onSyncCloudNow = { viewModel.syncAllToCloud() },
                                    onSignInGoogle = {
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onAuthSuccess = {
                                                viewModel.updateSkipAccountSignIn(false)
                                                Toast.makeText(context, "Conta Google conectada!", Toast.LENGTH_SHORT).show()
                                            },
                                            onAuthError = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            },
                                            scope = scope
                                        )
                                    },
                                    onSignOutGoogle = {
                                        viewModel.updateSkipAccountSignIn(false)
                                        signOutWithCredentialClear(
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onSignOutComplete = {
                                                Toast.makeText(context, "Sessão Google encerrada.", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    defaultAssistantStatus = defaultAssistantStatus,
                                    onRefreshDefaultAssistant = { viewModel.refreshDefaultAssistantStatus() },
                                    onRequestSetDefaultAssistant = { launchDefaultAssistantConfigFlow() },
                                    onOpenDefaultAssistantSettings = { viewModel.openDefaultAssistantSettings() },
                                    onTestDefaultAssistantTrigger = {
                                        currentTab = JarvisTab.CONSOLE
                                        selectedConsoleMode = 0
                                        requestPermissionsAndRun(requireMic = true, includeNotifications = false) {
                                            if (!voiceState.isListening) {
                                                viewModel.onMicButtonClicked()
                                            }
                                        }
                                    },
                                    onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                                    onUpdateCustomAccentColor = { viewModel.updateCustomAccentColor(it) }
                                )

                                JarvisTab.TOOLS -> ToolsMemoryScreen(
                                    commands = commands,
                                    offlineVoiceCommands = offlineVoiceCommands,
                                    memories = memories,
                                    selectedTabIndex = selectedToolsTab,
                                    onSelectTabIndex = { selectedToolsTab = it },
                                    githubRepo = settings.githubRepo,
                                    hasGitHubToken = hasGitHubToken,
                                    selfEvolutionState = selfEvolutionState,
                                    selfEvolutionDraft = selfEvolutionDraft,
                                    onSaveGitHubToken = { viewModel.saveGitHubDevToken(it) },
                                    onClearGitHubToken = { viewModel.clearGitHubDevToken() },
                                    onGenerateSelfEvolution = { viewModel.generateSelfEvolutionProposal(it) },
                                    onApproveSelfEvolution = { viewModel.approveSelfEvolutionProposal(it) },
                                    onResetSelfEvolution = { viewModel.resetSelfEvolution() },
                                    onOpenGitHubUrl = { viewModel.openGitHubRelease(it) },
                                    onGoToEnginesRepoConfig = { currentTab = JarvisTab.ENGINES },
                                    onAddCommand = { trigger, type, payload ->
                                        viewModel.addVoiceCommand(trigger, type, payload)
                                    },
                                    onToggleCommand = { viewModel.toggleVoiceCommand(it) },
                                    onDeleteCommand = { viewModel.deleteVoiceCommand(it) },
                                    onAddOfflineCommand = { trigger, aliases, actionKey, actionArg, desc ->
                                        viewModel.addOfflineVoiceCommand(trigger, aliases, actionKey, actionArg, desc)
                                    },
                                    onToggleOfflineCommand = { viewModel.toggleOfflineVoiceCommand(it) },
                                    onDeleteOfflineCommand = { viewModel.deleteOfflineVoiceCommand(it) },
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
                                    voiceLogs = voiceCommandLogs,
                                    traces = traces,
                                    onDeleteVoiceLog = { viewModel.deleteVoiceCommandLog(it) },
                                    onClearVoiceLogs = { viewModel.clearVoiceCommandLogs() },
                                    onReplayVoiceCommand = { transcript ->
                                        viewModel.sendPrompt(transcript, sourceTag = "VOICE_STT")
                                        currentTab = JarvisTab.CONSOLE
                                    },
                                    onSpeakLogResponse = { reply -> viewModel.speakText(reply) },
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
