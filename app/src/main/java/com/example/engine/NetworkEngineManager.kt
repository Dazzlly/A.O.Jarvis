package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.JarvisSettings
import com.example.data.MemoryEntity
import com.example.data.RoutingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI

private const val TAG = "NetworkEngineManager"
private const val OLLAMA_PROBE_CACHE_MS = 15_000L

data class NetworkEngineState(
    val isOnline: Boolean = false,
    val isWifiConnected: Boolean = false,
    val transportLabel: String = "Verificando rede…",
    val localIpAddress: String? = null,
    val activeEngine: EngineType = EngineType.GEMINI,
    val lastRoutingReason: String = "Pronto para roteamento",
    val ollamaHealth: OllamaHealthStatus? = null
)

/**
 * Gerenciador de rede e alternância entre a API do Gemini (Firebase AI Logic)
 * e um servidor Ollama local configurável via Wi-Fi.
 */
class NetworkEngineManager(
    context: Context,
    val geminiEngine: GeminiInferenceEngine,
    val ollamaEngine: OllamaInferenceEngine
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(readCurrentNetworkSnapshot())
    val state: StateFlow<NetworkEngineState> = _state.asStateFlow()

    @Volatile
    private var lastCheckedUrl: String = ""

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshNetworkState()
        }

        override fun onLost(network: Network) {
            refreshNetworkState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshNetworkState()
        }
    }

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }.onFailure { err ->
            Log.w(TAG, "Unable to register default network callback: ${err.message}")
        }
        refreshNetworkState()
    }

    fun refreshNetworkState() {
        val snapshot = readCurrentNetworkSnapshot()
        _state.update { current ->
            current.copy(
                isOnline = snapshot.isOnline,
                isWifiConnected = snapshot.isWifiConnected,
                transportLabel = snapshot.transportLabel,
                localIpAddress = snapshot.localIpAddress
            )
        }
    }

    fun validateOllamaUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Informe o endereço do servidor Ollama."))
        }
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return runCatching {
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") {
                "Use apenas http:// ou https:// para o servidor Ollama."
            }
            require(!uri.host.isNullOrBlank()) {
                "Endereço de host inválido para o servidor Ollama."
            }
            if (uri.port != -1) {
                require(uri.port in 1..65535) { "Porta inválida (${uri.port})." }
            }
            normalized
        }
    }

    suspend fun probeOllama(rawBaseUrl: String): OllamaHealthStatus {
        refreshNetworkState()
        val validated = validateOllamaUrl(rawBaseUrl).getOrElse { err ->
            val invalidStatus = OllamaHealthStatus(
                isReachable = false,
                errorMessage = err.message ?: "URL do Ollama inválida",
                checkedAt = System.currentTimeMillis()
            )
            _state.update { it.copy(ollamaHealth = invalidStatus) }
            return invalidStatus
        }

        val health = ollamaEngine.probeHealthAndModels(validated)
        lastCheckedUrl = validated
        _state.update { current ->
            current.copy(
                ollamaHealth = health,
                lastRoutingReason = if (health.isReachable) {
                    "Ollama alcançável via LAN (${health.latencyMs}ms)"
                } else {
                    "Ollama indisponível (${health.errorMessage ?: "sem resposta"})"
                }
            )
        }
        return health
    }

    /**
     * Decide entre Ollama local (Wi-Fi/LAN) e Gemini Cloud (Firebase AI Logic),
     * com fallback automático para Gemini no modo AUTO caso o servidor local esteja offline.
     */
    fun streamInference(
        prompt: String,
        history: List<ConversationTurn>,
        memories: List<MemoryEntity>,
        settings: JarvisSettings,
        routingOverride: RoutingMode? = null,
        selfAwarenessContext: String = ""
    ): Flow<InferenceStreamEvent> = flow {
        refreshNetworkState()
        val effectiveMode = routingOverride ?: settings.routingMode
        val networkSnapshot = _state.value

        val targetEngine: EngineType = when (effectiveMode) {
            RoutingMode.OLLAMA -> EngineType.OLLAMA
            RoutingMode.GEMINI -> EngineType.GEMINI
            RoutingMode.AUTO -> resolveAutoEngine(settings, networkSnapshot)
            RoutingMode.OFFLINE -> EngineType.OFFLINE
        }

        if (targetEngine == EngineType.OLLAMA) {
            val normalizedUrl = validateOllamaUrl(settings.ollamaBaseUrl).getOrNull()
            if (normalizedUrl == null) {
                val msg = "Endereço do servidor Ollama inválido: ${settings.ollamaBaseUrl}"
                if (effectiveMode == RoutingMode.AUTO) {
                    updateActiveEngine(EngineType.GEMINI, "Fallback para Gemini (URL Ollama inválida)")
                    geminiEngine.streamResponse(prompt, history, memories, settings, selfAwarenessContext)
                        .collect { emit(it) }
                } else {
                    emit(InferenceStreamEvent.Error(msg, EngineType.OLLAMA, settings.ollamaModel, 0L))
                }
                return@flow
            }

            if (!networkSnapshot.isWifiConnected && !isLoopbackOrEmulatorHost(normalizedUrl)) {
                if (effectiveMode == RoutingMode.AUTO) {
                    updateActiveEngine(EngineType.GEMINI, "Wi-Fi desconectado — roteando para Gemini Cloud")
                    geminiEngine.streamResponse(prompt, history, memories, settings, selfAwarenessContext)
                        .collect { emit(it) }
                    return@flow
                } else {
                    updateActiveEngine(EngineType.OLLAMA, "Tentando Ollama sem Wi-Fi ativo")
                }
            } else {
                updateActiveEngine(EngineType.OLLAMA, "Ollama LAN via ${networkSnapshot.transportLabel}")
            }

            var emittedChunk = false
            var shouldFallbackToGemini = false

            ollamaEngine.streamChat(
                prompt = prompt,
                history = history,
                memories = memories,
                settings = settings.copy(ollamaBaseUrl = normalizedUrl),
                selfAwarenessContext = selfAwarenessContext
            ).collect { event ->
                when (event) {
                    is InferenceStreamEvent.Chunk -> {
                        emittedChunk = true
                        emit(event)
                    }

                    is InferenceStreamEvent.Completed -> emit(event)

                    is InferenceStreamEvent.Error -> {
                        if (effectiveMode == RoutingMode.AUTO && !emittedChunk) {
                            shouldFallbackToGemini = true
                            _state.update {
                                it.copy(
                                    ollamaHealth = OllamaHealthStatus(
                                        isReachable = false,
                                        errorMessage = event.message,
                                        checkedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        } else {
                            emit(event)
                        }
                    }
                }
            }

            if (shouldFallbackToGemini) {
                updateActiveEngine(EngineType.GEMINI, "Fallback automático: Ollama falhou → Gemini Cloud")
                geminiEngine.streamResponse(prompt, history, memories, settings, selfAwarenessContext)
                    .collect { emit(it) }
            }
        } else {
            val reason = if (effectiveMode == RoutingMode.GEMINI) {
                "Modo Gemini Cloud selecionado"
            } else {
                "Auto: Gemini Cloud (${networkSnapshot.transportLabel})"
            }
            updateActiveEngine(EngineType.GEMINI, reason)
            geminiEngine.streamResponse(prompt, history, memories, settings, selfAwarenessContext)
                .collect { emit(it) }
        }
    }

    private suspend fun resolveAutoEngine(
        settings: JarvisSettings,
        networkSnapshot: NetworkEngineState
    ): EngineType {
        val validatedUrl = validateOllamaUrl(settings.ollamaBaseUrl).getOrNull() ?: return EngineType.GEMINI
        val canReachLan = networkSnapshot.isWifiConnected || isLoopbackOrEmulatorHost(validatedUrl)
        if (!canReachLan) {
            return EngineType.GEMINI
        }

        val cached = networkSnapshot.ollamaHealth
        val now = System.currentTimeMillis()
        val isCacheFresh = cached != null &&
            lastCheckedUrl == validatedUrl &&
            (now - cached.checkedAt) < OLLAMA_PROBE_CACHE_MS

        val health = if (isCacheFresh) cached else probeOllama(validatedUrl)
        return if (health.isReachable) EngineType.OLLAMA else EngineType.GEMINI
    }

    private fun updateActiveEngine(engine: EngineType, reason: String) {
        _state.update {
            it.copy(activeEngine = engine, lastRoutingReason = reason)
        }
    }

    private fun isLoopbackOrEmulatorHost(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        return host == "127.0.0.1" || host == "localhost" || host == "10.0.2.2"
    }

    private fun readCurrentNetworkSnapshot(): NetworkEngineState {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val label = when {
            hasWifi -> "Wi-Fi"
            hasEthernet -> "Ethernet"
            hasCellular -> "Dados Móveis"
            hasInternet -> "Rede Ativa"
            else -> "Offline"
        }

        return NetworkEngineState(
            isOnline = hasInternet || hasWifi || hasEthernet,
            isWifiConnected = hasWifi || hasEthernet,
            transportLabel = label,
            localIpAddress = resolveLocalIpv4Address()
        )
    }

    fun resolveLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<Pair<String, String>>()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("169.254.")) {
                            candidates.add(iface.name.lowercase() to ip)
                        }
                    }
                }
            }
            candidates.firstOrNull { (name, _) -> name.startsWith("wlan") || name.startsWith("wifi") }?.second
                ?: candidates.firstOrNull { (name, _) -> name.startsWith("eth") }?.second
                ?: candidates.firstOrNull()?.second
        }.getOrNull()
    }
}
