package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.AlarmClock
import com.example.core.AppDestination
import com.example.core.AppIntegrationManager
import com.example.core.AppSelfController
import com.example.core.AppUiCommand
import com.example.core.PermissionsManager
import com.example.data.MemoryDao
import com.example.data.MemoryEntity
import com.example.data.RoutingMode
import com.example.data.SettingsDataStore
import com.example.data.VoiceCommandDao
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface OfflineMatchResult {
    data object NotMatched : OfflineMatchResult
    data class Handled(
        val responseText: String,
        val toolName: String
    ) : OfflineMatchResult

    data class RedirectToAgentPrompt(
        val expandedPrompt: String
    ) : OfflineMatchResult
}

class OfflineCommandEngine(
    private val context: Context,
    private val memoryDao: MemoryDao,
    private val voiceCommandDao: VoiceCommandDao,
    private val settingsDataStore: SettingsDataStore,
    private val ollamaEngine: OllamaInferenceEngine,
    private val integrationManager: AppIntegrationManager,
    private val appSelfController: AppSelfController
) {

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    suspend fun tryHandle(rawInput: String): OfflineMatchResult {
        val clean = normalize(rawInput)
        if (clean.isBlank()) return OfflineMatchResult.NotMatched

        // 1. Check user custom commands stored in Room first
        val customCommands = voiceCommandDao.getEnabledCommands()
        for (cmd in customCommands) {
            val trigger = normalize(cmd.triggerPhrase)
            if (trigger.isNotBlank() && (clean == trigger || clean.contains(trigger))) {
                return when (cmd.actionType) {
                    "OFFLINE_RESPONSE" -> OfflineMatchResult.Handled(
                        responseText = cmd.actionPayload,
                        toolName = "comando:${cmd.triggerPhrase}"
                    )

                    "LOCAL_WEBHOOK" -> {
                        val result = ollamaEngine.executeLocalWebhook(cmd.actionPayload)
                        val text = result.fold(
                            onSuccess = { "Webhook local executado (${cmd.triggerPhrase}): $it" },
                            onFailure = { "Falha no webhook local (${cmd.triggerPhrase}): ${it.localizedMessage}" }
                        )
                        OfflineMatchResult.Handled(
                            responseText = text,
                            toolName = "webhook-lan:${cmd.triggerPhrase}"
                        )
                    }

                    "AGENT_PROMPT" -> OfflineMatchResult.RedirectToAgentPrompt(cmd.actionPayload)
                    else -> OfflineMatchResult.Handled(cmd.actionPayload, "comando:${cmd.triggerPhrase}")
                }
            }
        }

        // 2. Self-Navigation & Guided Self-Tour inside OpenJarvis
        val selfNavResult = tryHandleSelfNavigation(clean)
        if (selfNavResult != null) return selfNavResult

        // 3. External App Integrations (WhatsApp, Dialer, SMS, Maps, Spotify, YouTube, Calendar, Location, Launch App)
        val integrationResult = tryHandleAppIntegrations(rawInput, clean)
        if (integrationResult != null) return integrationResult

        // 4. Built-in Offline Time & Date
        if (clean.contains("que horas") || clean == "horas" || clean == "hora atual" || clean.contains("what time")) {
            val timeStr = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())
            return OfflineMatchResult.Handled(
                responseText = "Agora são $timeStr.",
                toolName = "offline:relogio"
            )
        }
        if (clean.contains("que dia e hoje") || clean.contains("qual a data") || clean == "data" || clean.contains("data de hoje")) {
            val dateStr = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date())
            return OfflineMatchResult.Handled(
                responseText = "Hoje é $dateStr.",
                toolName = "offline:calendario"
            )
        }

        // 5. Built-in Offline Alarm & Timer (real Android AlarmClock Intents)
        if (clean.contains("alarme") || clean.contains("despertador")) {
            val match = Regex("(\\d{1,2})[:h ](\\d{2})").find(clean)
            val hourOnly = Regex("(\\d{1,2})\\s*(horas|h)\\b").find(clean)
            val hour = match?.groupValues?.get(1)?.toIntOrNull() ?: hourOnly?.groupValues?.get(1)?.toIntOrNull()
            val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            if (hour != null && hour in 0..23 && minute in 0..59) {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "OpenJarvis")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = runCatching { context.startActivity(intent); true }.getOrDefault(false)
                val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
                return OfflineMatchResult.Handled(
                    responseText = if (launched) "Alarme configurado no sistema para $formatted."
                    else "Horário reconhecido ($formatted), mas nenhum aplicativo de relógio padrão respondeu.",
                    toolName = "offline:alarme"
                )
            }
        }

        if (clean.contains("timer") || clean.contains("temporizador") || clean.contains("cronometro")) {
            val minMatch = Regex("(\\d+)\\s*(minuto|minutos|min)\\b").find(clean)
            val secMatch = Regex("(\\d+)\\s*(segundo|segundos|seg)\\b").find(clean)
            val totalSeconds = (minMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0) * 60 +
                (secMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0)
            if (totalSeconds > 0) {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "OpenJarvis Timer")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val launched = runCatching { context.startActivity(intent); true }.getOrDefault(false)
                return OfflineMatchResult.Handled(
                    responseText = if (launched) "Temporizador de $totalSeconds segundos iniciado no sistema."
                    else "Não foi possível abrir o temporizador nativo do Android.",
                    toolName = "offline:timer"
                )
            }
        }

        // 6. Built-in Offline Memory Save & Query (Room local persistence)
        val rememberRegex = Regex("^(?:lembre que|lembra que|salvar memoria|salve que|anote que)\\s+(.+)$")
        val rememberMatch = rememberRegex.find(clean)
        if (rememberMatch != null) {
            val fact = rawInput.substringAfter("que ", rawInput.substringAfter("memoria ", rawInput)).trim()
            if (fact.isNotBlank()) {
                val title = fact.split(" ").take(4).joinToString(" ").replaceFirstChar { it.uppercase() }
                memoryDao.insertMemory(
                    MemoryEntity(
                        key = title,
                        content = fact,
                        category = "Voz Local"
                    )
                )
                return OfflineMatchResult.Handled(
                    responseText = "Memória local salva no OpenJarvis: \"$fact\".",
                    toolName = "offline:memoria-salvar"
                )
            }
        }

        if (clean.contains("minhas memorias") || clean.contains("listar memorias") || clean.contains("o que voce lembra")) {
            val memories = memoryDao.getAllMemories()
            val text = if (memories.isEmpty()) {
                "Ainda não há memórias salvas localmente."
            } else {
                "Você tem ${memories.size} memória(s) salva(s):\n" +
                    memories.take(6).joinToString("\n") { "• ${it.key}: ${it.content}" }
            }
            return OfflineMatchResult.Handled(
                responseText = text,
                toolName = "offline:memoria-listar"
            )
        }

        // 7. Built-in Engine Switching via Voice
        when {
            clean.contains("usar ollama") || clean.contains("trocar para ollama") || clean.contains("modo ollama") -> {
                settingsDataStore.updateRoutingMode(RoutingMode.OLLAMA)
                return OfflineMatchResult.Handled(
                    responseText = "Motor de inferência alterado para Ollama Local na rede Wi-Fi.",
                    toolName = "offline:engine-switch"
                )
            }
            clean.contains("usar gemini") || clean.contains("trocar para gemini") || clean.contains("modo gemini") -> {
                settingsDataStore.updateRoutingMode(RoutingMode.GEMINI)
                return OfflineMatchResult.Handled(
                    responseText = "Motor de inferência alterado para Gemini Cloud.",
                    toolName = "offline:engine-switch"
                )
            }
            clean.contains("modo offline") || clean.contains("somente offline") -> {
                settingsDataStore.updateRoutingMode(RoutingMode.OFFLINE)
                return OfflineMatchResult.Handled(
                    responseText = "Modo Somente Offline ativado. Nenhuma consulta externa será enviada.",
                    toolName = "offline:engine-switch"
                )
            }
            clean.contains("modo automatico") -> {
                settingsDataStore.updateRoutingMode(RoutingMode.AUTO)
                return OfflineMatchResult.Handled(
                    responseText = "Roteamento Automático reativado (Offline → Ollama LAN → Gemini).",
                    toolName = "offline:engine-switch"
                )
            }
        }

        // 8. Built-in Real System & Network Telemetry Diagnostics
        if (clean.contains("status do sistema") || clean.contains("diagnostico") || clean.contains("bateria") || clean.contains("status jarvis")) {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val batteryPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = batteryManager?.isCharging == true
            val netStatus = describeNetwork()
            val memoryCount = memoryDao.getAllMemories().size
            val commandCount = voiceCommandDao.getEnabledCommands().size
            val permsInfo = PermissionsManager.summarizePermissionsForPrompt(context)
            val batteryText = if (batteryPct in 0..100) "$batteryPct% (${if (isCharging) "carregando" else "bateria"})" else "indisponível"

            return OfflineMatchResult.Handled(
                responseText = "Diagnóstico OpenJarvis:\n• Energia: $batteryText\n• Rede: $netStatus\n• Memórias locais: $memoryCount\n• Comandos ativos: $commandCount\n• $permsInfo",
                toolName = "offline:telemetria-sistema"
            )
        }

        // 9. Built-in Offline Calculator
        val mathResult = tryEvaluateOfflineMath(clean)
        if (mathResult != null) {
            return OfflineMatchResult.Handled(
                responseText = "Resultado offline: $mathResult.",
                toolName = "offline:calculadora"
            )
        }

        return OfflineMatchResult.NotMatched
    }

    private suspend fun tryHandleSelfNavigation(clean: String): OfflineMatchResult? {
        // Guided self-tour through OpenJarvis
        if (clean.contains("me guie") || clean.contains("guiar pelo app") || clean.contains("tour guiado") ||
            clean.contains("mostre o app") || clean.contains("apresente o app") || clean.contains("como funciona o app")
        ) {
            val step = appSelfController.startGuidedTour()
            return OfflineMatchResult.Handled(
                responseText = "${step.title}: ${step.narration} Toque em 'Próximo' no topo ou diga 'próximo passo' para continuarmos.",
                toolName = "jarvis:auto-guia"
            )
        }

        if (clean.contains("proximo passo") || clean == "proximo" || clean.contains("avancar tour")) {
            val next = appSelfController.nextTourStep()
            return if (next != null) {
                OfflineMatchResult.Handled(
                    responseText = "${next.title}: ${next.narration}",
                    toolName = "jarvis:auto-guia"
                )
            } else {
                OfflineMatchResult.Handled(
                    responseText = "Tour guiado finalizado. Estou pronto para qualquer comando!",
                    toolName = "jarvis:auto-guia"
                )
            }
        }

        if (clean.contains("parar tour") || clean.contains("encerrar tour") || clean.contains("fechar guia")) {
            appSelfController.stopGuidedTour()
            return OfflineMatchResult.Handled(
                responseText = "Guia interativo encerrado.",
                toolName = "jarvis:auto-guia"
            )
        }

        // Tab self-navigation
        val navPrefixes = listOf("ir para", "abrir aba", "abra a aba", "va para", "mostrar", "navegar para", "abrir tela")
        val wantsNav = navPrefixes.any { clean.contains(it) } ||
            clean in setOf("motores", "telemetria", "comandos", "memorias", "console", "configuracoes do jarvis")
        if (wantsNav) {
            when {
                clean.contains("motor") || clean.contains("ollama") && clean.contains("config") || clean.contains("configuracoes do jarvis") -> {
                    appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.ENGINES, "Indo para Motores"))
                    return OfflineMatchResult.Handled(
                        responseText = "Naveguei para a aba Motores. Aqui você pode configurar Ollama, Gemini, TTS, gatilhos LAN e permissões.",
                        toolName = "jarvis:auto-navegacao"
                    )
                }

                clean.contains("telemetria") || clean.contains("latencia") || clean.contains("traces") -> {
                    appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.TELEMETRY, "Indo para Telemetria"))
                    return OfflineMatchResult.Handled(
                        responseText = "Naveguei para a aba Telemetria para exibir latência e desempenho dos motores.",
                        toolName = "jarvis:auto-navegacao"
                    )
                }

                clean.contains("comando") || clean.contains("integrac") || clean.contains("ferramenta") || clean.contains("aba memoria") -> {
                    appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.TOOLS, "Indo para Comandos"))
                    return OfflineMatchResult.Handled(
                        responseText = "Naveguei para a aba Comandos, onde ficam suas integrações com apps, gatilhos offline e memórias.",
                        toolName = "jarvis:auto-navegacao"
                    )
                }

                clean.contains("console") || clean.contains("tela inicial") || clean.contains("chat") -> {
                    appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.CONSOLE, "Indo para Console"))
                    return OfflineMatchResult.Handled(
                        responseText = "De volta ao Console principal do OpenJarvis.",
                        toolName = "jarvis:auto-navegacao"
                    )
                }
            }
        }

        // Direct self-controls inside OpenJarvis
        if (clean.contains("gemini live") || clean.contains("modo live")) {
            appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.CONSOLE))
            appSelfController.emitCommand(AppUiCommand.SetConsoleMode(geminiLive = true))
            return OfflineMatchResult.Handled(
                responseText = "Abri o painel Gemini Live no Console para conversa por voz em tempo real.",
                toolName = "jarvis:auto-controle"
            )
        }

        if (clean.contains("tema claro") || clean.contains("modo claro")) {
            settingsDataStore.updateDarkTheme(false)
            return OfflineMatchResult.Handled(
                responseText = "Tema claro ativado no OpenJarvis.",
                toolName = "jarvis:auto-controle"
            )
        }

        if (clean.contains("tema escuro") || clean.contains("modo escuro")) {
            settingsDataStore.updateDarkTheme(true)
            return OfflineMatchResult.Handled(
                responseText = "Tema escuro Obsidian ativado no OpenJarvis.",
                toolName = "jarvis:auto-controle"
            )
        }

        if (clean.contains("pedir permissoes") || clean.contains("ativar permissoes") || clean.contains("autonomia total") || clean.contains("conceder permissoes")) {
            appSelfController.emitCommand(AppUiCommand.RequestAllPermissions)
            return OfflineMatchResult.Handled(
                responseText = "Solicitando todas as permissões de autonomia disponíveis no Android (microfone, contatos, agenda, localização, câmera, notificações e Bluetooth).",
                toolName = "jarvis:permissoes"
            )
        }

        if (clean.contains("testar ollama") || clean.contains("descobrir modelos")) {
            appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.ENGINES))
            appSelfController.emitCommand(AppUiCommand.ProbeOllama)
            return OfflineMatchResult.Handled(
                responseText = "Abri Motores e iniciei o teste de conexão com seu servidor Ollama na rede Wi-Fi.",
                toolName = "jarvis:auto-controle"
            )
        }

        if (clean.contains("ativar segundo plano") || clean.contains("ativar background") || clean.contains("ligar escuta continua")) {
            appSelfController.emitCommand(AppUiCommand.ToggleBackgroundService(shouldRun = true))
            return OfflineMatchResult.Handled(
                responseText = "Iniciando o serviço contínuo do OpenJarvis em segundo plano.",
                toolName = "jarvis:auto-controle"
            )
        }

        if (clean.contains("desativar segundo plano") || clean.contains("parar background")) {
            appSelfController.emitCommand(AppUiCommand.ToggleBackgroundService(shouldRun = false))
            return OfflineMatchResult.Handled(
                responseText = "Encerrando o serviço em segundo plano do OpenJarvis.",
                toolName = "jarvis:auto-controle"
            )
        }

        if (clean.contains("verificar atualizac") || clean.contains("checar atualizac") || clean.contains("atualizar app") || clean.contains("nova versao")) {
            appSelfController.emitCommand(AppUiCommand.NavigateTo(AppDestination.ENGINES))
            appSelfController.emitCommand(AppUiCommand.CheckUpdates)
            return OfflineMatchResult.Handled(
                responseText = "Abri a aba Motores e iniciei a verificação de atualizações no seu repositório GitHub / F-Droid.",
                toolName = "jarvis:atualizacao"
            )
        }

        if (clean.contains("nova conversa") || clean.contains("nova sessao")) {
            appSelfController.emitCommand(AppUiCommand.CreateNewSession)
            return OfflineMatchResult.Handled(
                responseText = "Nova sessão de conversa criada mantendo o histórico anterior salvo.",
                toolName = "jarvis:sessoes"
            )
        }

        return null
    }

    private suspend fun tryHandleAppIntegrations(rawInput: String, clean: String): OfflineMatchResult? {
        // 1. WhatsApp message: "mandar whatsapp para <nome> dizendo <msg>"
        val waRegex = Regex("^(?:mandar|enviar|mande|envie)\\s+(?:um\\s+)?(?:whatsapp|zap|mensagem no whatsapp)\\s+(?:para|pro|pra)\\s+(.+?)(?:\\s+(?:dizendo|falando|com a mensagem|mensagem)\\s+(.+))?$")
        waRegex.find(clean)?.let {
            val targetRaw = extractSegmentAfter(rawInput, listOf("para ", "pro ", "pra "))
            val splitParts = targetRaw.split(Regex("\\s+(?:dizendo|falando|com a mensagem|mensagem)\\s+", RegexOption.IGNORE_CASE), limit = 2)
            val contact = splitParts.getOrNull(0).orEmpty().trim()
            val message = splitParts.getOrNull(1).orEmpty().trim().ifBlank { "Olá!" }
            if (contact.isNotBlank()) {
                val res = integrationManager.sendWhatsAppMessage(contact, message)
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        // 2. SMS message: "enviar sms para <nome> dizendo <msg>"
        val smsRegex = Regex("^(?:mandar|enviar|mande|envie)\\s+(?:um\\s+)?sms\\s+(?:para|pro|pra)\\s+(.+)$")
        if (smsRegex.containsMatchIn(clean)) {
            val targetRaw = extractSegmentAfter(rawInput, listOf("para ", "pro ", "pra "))
            val splitParts = targetRaw.split(Regex("\\s+(?:dizendo|falando|mensagem)\\s+", RegexOption.IGNORE_CASE), limit = 2)
            val contact = splitParts.getOrNull(0).orEmpty().trim()
            val message = splitParts.getOrNull(1).orEmpty().trim()
            if (contact.isNotBlank()) {
                val res = integrationManager.sendSmsMessage(contact, message)
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        // 3. Phone dialer: "ligar para <nome/numero>"
        val callRegex = Regex("^(?:ligar|ligue|telefonar|discar|chamar)\\s+(?:para|pro|pra)\\s+(.+)$")
        if (callRegex.containsMatchIn(clean)) {
            val target = extractSegmentAfter(rawInput, listOf("para ", "pro ", "pra "))
            if (target.isNotBlank()) {
                val res = integrationManager.dialContactOrNumber(target)
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        // 4. Maps navigation: "navegar para <destino>" / "rota para <destino>"
        val mapsRegex = Regex("^(?:navegar|rota|como chegar|ir de carro|tracar rota)\\s+(?:para|ate|pro|pra)\\s+(.+)$")
        if (mapsRegex.containsMatchIn(clean)) {
            val destination = extractSegmentAfter(rawInput, listOf("para ", "até ", "ate ", "pro ", "pra "))
            if (destination.isNotBlank()) {
                val res = integrationManager.navigateWithMaps(destination)
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        // 5. Music / Spotify / YouTube
        if (clean.startsWith("tocar ") || clean.startsWith("toque ") || clean.startsWith("reproduzir ")) {
            val afterVerb = rawInput.substringAfter(" ").trim()
            val isSpotify = clean.contains("spotify")
            val isYouTube = clean.contains("youtube")
            val query = afterVerb
                .replace(Regex("\\s+no\\s+(?:spotify|youtube)$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (query.isNotBlank()) {
                val res = if (isYouTube) {
                    integrationManager.searchYouTube(query)
                } else {
                    integrationManager.playMediaSearch(query, preferSpotify = isSpotify)
                }
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        if ((clean.startsWith("buscar ") || clean.startsWith("pesquisar ") || clean.startsWith("procurar ")) &&
            clean.contains("youtube")
        ) {
            val query = rawInput.substringAfter(" ")
                .replace(Regex("\\s+no\\s+youtube$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (query.isNotBlank()) {
                val res = integrationManager.searchYouTube(query)
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        if (clean.startsWith("pesquisar no google ") || clean.startsWith("buscar no google ") || clean.startsWith("pesquisar na web ")) {
            val query = extractSegmentAfter(rawInput, listOf("google ", "web "))
            if (query.isNotBlank()) {
                val res = integrationManager.webSearch(query)
                return OfflineMatchResult.Handled(res.message, res.toolName)
            }
        }

        // 6. Calendar read & create
        if (clean.contains("minha agenda") || clean.contains("meus compromissos") || clean.contains("eventos de hoje")) {
            val res = integrationManager.readTodayCalendarEvents()
            return OfflineMatchResult.Handled(res.message, res.toolName)
        }

        if (clean.startsWith("agendar ") || clean.startsWith("marcar evento ") || clean.startsWith("criar evento ")) {
            val body = rawInput.substringAfter(" ").removePrefix("evento ").trim()
            val hourMatch = Regex("(?:as|às)\\s*(\\d{1,2})(?::(\\d{2}))?").find(clean)
            val startMillis = if (hourMatch != null) {
                val h = hourMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 9
                val m = hourMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                Calendar.getInstance().apply {
                    if (clean.contains("amanha")) add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, 0)
                }.timeInMillis
            } else {
                null
            }
            val res = integrationManager.createCalendarEvent(body, startMillis)
            return OfflineMatchResult.Handled(res.message, res.toolName)
        }

        // 7. Device Location
        if (clean.contains("onde estou") || clean.contains("minha localizacao") || clean.contains("qual meu endereco")) {
            val res = integrationManager.readCurrentLocationSummary()
            return OfflineMatchResult.Handled(res.message, res.toolName)
        }

        // 8. Generic app launching ("abrir whatsapp", "abra o spotify", "abrir câmera", "abrir wifi")
        val openRegex = Regex("^(?:abrir|abra|iniciar|execute|executar)\\s+(?:o\\s+|a\\s+|os\\s+|as\\s+)?(.+)$")
        if (openRegex.containsMatchIn(clean) && !clean.contains("aba ")) {
            val appTarget = rawInput
                .replace(Regex("^(?:abrir|abra|iniciar|execute|executar)\\s+(?:o\\s+|a\\s+|os\\s+|as\\s+)?", RegexOption.IGNORE_CASE), "")
                .trim()
            if (appTarget.isNotBlank()) {
                val res = integrationManager.openAppByName(appTarget)
                if (res.success || clean.contains("whatsapp") || clean.contains("spotify") ||
                    clean.contains("youtube") || clean.contains("maps") || clean.contains("waze") ||
                    clean.contains("chrome") || clean.contains("gmail") || clean.contains("instagram") ||
                    clean.contains("telegram") || clean.contains("camera") || clean.contains("wifi") ||
                    clean.contains("bluetooth") || clean.contains("configurac")
                ) {
                    return OfflineMatchResult.Handled(res.message, res.toolName)
                }
            }
        }

        return null
    }

    private fun extractSegmentAfter(raw: String, markers: List<String>): String {
        val lower = raw.lowercase(Locale("pt", "BR"))
        for (marker in markers) {
            val idx = lower.indexOf(marker)
            if (idx >= 0) {
                return raw.substring(idx + marker.length).trim()
            }
        }
        return ""
    }

    private fun describeNetwork(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Desconhecida"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline (sem rede)"
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi / LAN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Rede Móvel")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
        }
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return if (transports.isEmpty()) "Offline"
        else "${transports.joinToString(" + ")} (${if (hasInternet) "online" else "somente rede local"})"
    }

    private fun tryEvaluateOfflineMath(clean: String): String? {
        val normalizedExpr = clean
            .removePrefix("quanto e ")
            .removePrefix("calcule ")
            .removePrefix("calcular ")
            .replace(" vezes ", " * ")
            .replace(" x ", " * ")
            .replace(" dividido por ", " / ")
            .replace(" mais ", " + ")
            .replace(" menos ", " - ")
            .replace(",", ".")
            .trim()

        val match = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)$").find(normalizedExpr)
            ?: return null

        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) return "divisão por zero indefinida" else a / b
            else -> return null
        }

        return if (result % 1.0 == 0.0) result.toLong().toString()
        else String.format(Locale("pt", "BR"), "%.2f", result)
    }

    companion object {
        val builtInExamples: List<Pair<String, String>> = listOf(
            "Me guie pelo app" to "Auto-tour guiado por voz pelas telas do OpenJarvis",
            "Ir para Motores" to "Auto-navegação instantânea dentro do próprio app",
            "Abrir WhatsApp" to "Abre apps instalados ou envia mensagens via Contatos",
            "Navegar para Avenida Paulista" to "Traça rota diretamente no Google Maps",
            "Tocar lo-fi no Spotify" to "Reproduz busca de música no Spotify / mídia",
            "Minha agenda hoje" to "Lê seus compromissos reais do calendário do Android",
            "Onde estou?" to "Consulta localização GPS/rede e endereço aproximado",
            "Ligar para Maria" to "Busca o contato na agenda e abre o discador",
            "Definir alarme para 07:30" to "Dispara o AlarmClock nativo do sistema",
            "Status do sistema" to "Lê bateria, Wi-Fi/LAN, memórias e permissões ativas"
        )
    }
}
