package com.example.core

import android.content.Context
import com.example.data.JarvisSettings
import com.example.data.RoutingMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppDestination(val key: String, val displayName: String, val summary: String) {
    CONSOLE(
        key = "CONSOLE",
        displayName = "Console",
        summary = "Chat com streaming, múltiplas sessões de conversa, microfone nativo (STT/TTS) e Gemini Live"
    ),
    ENGINES(
        key = "ENGINES",
        displayName = "Motores",
        summary = "Alternância entre Gemini Cloud, Ollama local no Wi-Fi, voz/TTS, serviço em background, gatilhos LAN e permissões"
    ),
    TOOLS(
        key = "TOOLS",
        displayName = "Comandos",
        summary = "Comandos de voz personalizados, webhooks LAN, integrações com aplicativos e memórias locais"
    ),
    TELEMETRY(
        key = "TELEMETRY",
        displayName = "Telemetria",
        summary = "Histórico de execuções, latência por motor e velocidade de tokens por segundo"
    )
}

data class GuidedTourStep(
    val index: Int,
    val destination: AppDestination,
    val title: String,
    val narration: String
)

data class GuidedTourState(
    val isActive: Boolean = false,
    val currentStepIndex: Int = 0,
    val steps: List<GuidedTourStep> = DEFAULT_TOUR_STEPS
) {
    val currentStep: GuidedTourStep?
        get() = if (isActive) steps.getOrNull(currentStepIndex) else null
}

sealed interface AppUiCommand {
    data class NavigateTo(val destination: AppDestination, val reason: String? = null) : AppUiCommand
    data class SetConsoleMode(val geminiLive: Boolean) : AppUiCommand
    data class SetDarkTheme(val enabled: Boolean) : AppUiCommand
    data class SetRoutingMode(val mode: RoutingMode) : AppUiCommand
    data class ToggleBackgroundService(val shouldRun: Boolean) : AppUiCommand
    data object RequestAllPermissions : AppUiCommand
    data object CreateNewSession : AppUiCommand
    data object ProbeOllama : AppUiCommand
    data object CheckUpdates : AppUiCommand
    data class ShowGuidanceBanner(val message: String) : AppUiCommand
}

data class ParsedActionOutput(
    val cleanText: String,
    val actions: List<String>
)

private val DEFAULT_TOUR_STEPS = listOf(
    GuidedTourStep(
        index = 0,
        destination = AppDestination.CONSOLE,
        title = "1/4 · Console de Voz & Chat",
        narration = "Estamos no Console. Aqui você conversa comigo por texto, microfone nativo ou Gemini Live, alterna sessões salvas no topo e ativa a escuta contínua em segundo plano."
    ),
    GuidedTourStep(
        index = 1,
        destination = AppDestination.ENGINES,
        title = "2/4 · Motores, Ollama & Permissões",
        narration = "Na aba Motores você alterna entre Gemini na nuvem e seu servidor Ollama no Wi-Fi, testa a síntese de voz, configura gatilhos de rede local e concede todas as permissões de autonomia."
    ),
    GuidedTourStep(
        index = 2,
        destination = AppDestination.TOOLS,
        title = "3/4 · Comandos, Integrações & Memórias",
        narration = "Em Comandos você cria atalhos de voz offline, webhooks na rede local, dispara integrações com WhatsApp, Maps, Spotify e Agenda, e gerencia minhas memórias persistentes."
    ),
    GuidedTourStep(
        index = 3,
        destination = AppDestination.TELEMETRY,
        title = "4/4 · Telemetria de Execução",
        narration = "Por fim, na aba Telemetria você acompanha a latência, o motor usado em cada resposta e os tokens por segundo. Tour concluído!"
    )
)

/**
 * Controlador de autoconsciência e autonavegação do OpenJarvis.
 * Permite que tanto comandos offline quanto respostas do Gemini/Ollama
 * entendam a estrutura interna do app e guiem ou naveguem a interface automaticamente.
 */
class AppSelfController(private val appContext: Context) {

    private val _currentDestination = MutableStateFlow(AppDestination.CONSOLE)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _guidedTour = MutableStateFlow(GuidedTourState())
    val guidedTour: StateFlow<GuidedTourState> = _guidedTour.asStateFlow()

    private val _uiCommands = MutableSharedFlow<AppUiCommand>(extraBufferCapacity = 16)
    val uiCommands: SharedFlow<AppUiCommand> = _uiCommands.asSharedFlow()

    private val actionRegex = Regex("\\[\\[ACTION:([A-Z0-9_]+)(?::([^\\]]+))?\\]\\]")

    fun reportCurrentDestination(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun emitCommand(command: AppUiCommand) {
        _uiCommands.tryEmit(command)
    }

    fun startGuidedTour(): GuidedTourStep {
        val firstStep = DEFAULT_TOUR_STEPS.first()
        _guidedTour.value = GuidedTourState(isActive = true, currentStepIndex = 0)
        emitCommand(AppUiCommand.NavigateTo(firstStep.destination, firstStep.title))
        return firstStep
    }

    fun nextTourStep(): GuidedTourStep? {
        val state = _guidedTour.value
        if (!state.isActive) return null
        val nextIdx = state.currentStepIndex + 1
        if (nextIdx >= state.steps.size) {
            stopGuidedTour()
            return null
        }
        val step = state.steps[nextIdx]
        _guidedTour.value = state.copy(currentStepIndex = nextIdx)
        emitCommand(AppUiCommand.NavigateTo(step.destination, step.title))
        return step
    }

    fun stopGuidedTour() {
        _guidedTour.value = GuidedTourState(isActive = false, currentStepIndex = 0)
    }

    /**
     * Gera o bloco de autoconsciência injetado no prompt de sistema do Gemini e do Ollama,
     * permitindo que o assistente entenda a si mesmo e execute ações reais no app.
     */
    fun buildSelfAwarenessPrompt(
        settings: JarvisSettings,
        isBackgroundRunning: Boolean
    ): String {
        val currentTab = _currentDestination.value
        val permsSummary = PermissionsManager.summarizePermissionsForPrompt(appContext)
        return buildString {
            append("\n\n[AUTOCONSCIÊNCIA E AUTONAVEGAÇÃO DO APP OPENJARVIS]\n")
            append("Você é o próprio aplicativo Android OpenJarvis. Conheça suas telas:\n")
            AppDestination.entries.forEach { dest ->
                append("- Aba ${dest.key} (${dest.displayName}): ${dest.summary}.\n")
            }
            append("Estado atual do app: aba ativa=${currentTab.displayName}, modo de roteamento=${settings.routingMode.label}, ")
            append("TTS automático=${if (settings.autoSpeakResponses) "ativado" else "desativado"}, ")
            append("tema=${if (settings.darkTheme) "escuro" else "claro"}, ")
            append("serviço background=${if (isBackgroundRunning) "ativo" else "inativo"}, ")
            append("servidor LAN=${if (settings.lanTriggerEnabled) "habilitado na porta ${settings.lanTriggerPort}" else "desativado"}.\n")
            append("$permsSummary.\n")
            append("Quando o usuário pedir para navegar no app, mudar configurações, iniciar tour guiado, abrir aplicativos externos, ")
            append("traçar rota ou conceder permissões, responda de forma natural em português e adicione ao final a tag de ação correspondente (o sistema executa e oculta a tag):\n")
            append("- [[ACTION:NAVIGATE:CONSOLE|ENGINES|TOOLS|TELEMETRY]] (navega para uma aba do app)\n")
            append("- [[ACTION:OPEN_MODE:GEMINI_LIVE|NATIVE]] (alterna entre Voz Nativa e Gemini Live)\n")
            append("- [[ACTION:SET_THEME:DARK|LIGHT]] (troca o tema do app)\n")
            append("- [[ACTION:SET_ROUTING:AUTO|GEMINI|OLLAMA|OFFLINE]] (troca o motor de IA)\n")
            append("- [[ACTION:TOGGLE_BG:START|STOP]] (liga/desliga o serviço em segundo plano)\n")
            append("- [[ACTION:NEW_SESSION]] (cria uma nova conversa)\n")
            append("- [[ACTION:PROBE_OLLAMA]] (testa conexão com o servidor Ollama local)\n")
            append("- [[ACTION:CHECK_UPDATES]] (verifica atualizações do app via GitHub Releases / F-Droid)\n")
            append("- [[ACTION:REQUEST_PERMISSIONS]] (abre pedido de todas as permissões de autonomia)\n")
            append("- [[ACTION:TOUR:START]] (inicia o tour guiado interativo pelo próprio app)\n")
            append("- [[ACTION:OPEN_APP:<nome>]] (abre WhatsApp, YouTube, Spotify, Maps, Waze, Chrome, Gmail, Telegram, Instagram, Agenda, Câmera, Configurações)\n")
            append("- [[ACTION:NAVIGATE_MAPS:<destino>]] (abre rota no Google Maps)\n")
            append("- [[ACTION:WEB_SEARCH:<consulta>]] (pesquisa no navegador)\n")
            append("Use tags [[ACTION:...]] apenas quando o usuário solicitar uma ação ou navegação.")
        }
    }

    fun parseActions(rawText: String): ParsedActionOutput {
        val matchedActions = mutableListOf<String>()
        val cleaned = actionRegex.replace(rawText) { match ->
            val verb = match.groupValues[1]
            val arg = match.groupValues.getOrNull(2).orEmpty().trim()
            matchedActions.add(if (arg.isNotEmpty()) "$verb:$arg" else verb)
            ""
        }.replace(Regex("\\n{3,}"), "\n\n").trim()

        return ParsedActionOutput(
            cleanText = cleaned,
            actions = matchedActions
        )
    }

    suspend fun executeParsedActions(
        actions: List<String>,
        integrationManager: AppIntegrationManager
    ): List<String> {
        val executedNotes = mutableListOf<String>()
        for (rawAction in actions) {
            val verb = rawAction.substringBefore(":")
            val arg = rawAction.substringAfter(":", "").trim()
            when (verb) {
                "NAVIGATE" -> {
                    val dest = AppDestination.entries.firstOrNull { it.key.equals(arg, ignoreCase = true) }
                    if (dest != null) {
                        emitCommand(AppUiCommand.NavigateTo(dest, "Navegando para ${dest.displayName}"))
                        executedNotes.add("Aba ${dest.displayName} aberta")
                    }
                }

                "OPEN_MODE" -> {
                    val isLive = arg.equals("GEMINI_LIVE", ignoreCase = true) || arg.equals("LIVE", ignoreCase = true)
                    emitCommand(AppUiCommand.NavigateTo(AppDestination.CONSOLE))
                    emitCommand(AppUiCommand.SetConsoleMode(geminiLive = isLive))
                    executedNotes.add(if (isLive) "Modo Gemini Live aberto" else "Modo Voz Nativa aberto")
                }

                "SET_THEME" -> {
                    val dark = !arg.equals("LIGHT", ignoreCase = true)
                    emitCommand(AppUiCommand.SetDarkTheme(dark))
                    executedNotes.add(if (dark) "Tema escuro ativado" else "Tema claro ativado")
                }

                "SET_ROUTING" -> {
                    val mode = runCatching { RoutingMode.valueOf(arg.uppercase()) }.getOrNull()
                    if (mode != null) {
                        emitCommand(AppUiCommand.SetRoutingMode(mode))
                        executedNotes.add("Motor alterado para ${mode.label}")
                    }
                }

                "TOGGLE_BG" -> {
                    val start = !arg.equals("STOP", ignoreCase = true)
                    emitCommand(AppUiCommand.ToggleBackgroundService(start))
                }

                "NEW_SESSION" -> {
                    emitCommand(AppUiCommand.CreateNewSession)
                }

                "PROBE_OLLAMA" -> {
                    emitCommand(AppUiCommand.NavigateTo(AppDestination.ENGINES))
                    emitCommand(AppUiCommand.ProbeOllama)
                }

                "CHECK_UPDATES" -> {
                    emitCommand(AppUiCommand.NavigateTo(AppDestination.ENGINES))
                    emitCommand(AppUiCommand.CheckUpdates)
                }

                "REQUEST_PERMISSIONS" -> {
                    emitCommand(AppUiCommand.RequestAllPermissions)
                }

                "TOUR" -> {
                    if (arg.equals("STOP", ignoreCase = true)) {
                        stopGuidedTour()
                    } else {
                        startGuidedTour()
                    }
                }

                "OPEN_APP" -> {
                    if (arg.isNotBlank()) {
                        val res = integrationManager.openAppByName(arg)
                        executedNotes.add(res.message)
                    }
                }

                "NAVIGATE_MAPS" -> {
                    if (arg.isNotBlank()) {
                        val res = integrationManager.navigateWithMaps(arg)
                        executedNotes.add(res.message)
                    }
                }

                "WEB_SEARCH" -> {
                    if (arg.isNotBlank()) {
                        val res = integrationManager.webSearch(arg)
                        executedNotes.add(res.message)
                    }
                }
            }
        }
        return executedNotes
    }
}
