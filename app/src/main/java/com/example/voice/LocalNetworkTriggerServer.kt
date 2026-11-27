package com.example.voice

import android.util.Log
import com.example.data.RoutingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val TAG = "LocalNetworkTriggerSrv"
private const val SOCKET_TIMEOUT_MS = 5_000
private const val MAX_HEADER_BYTES = 4_096
private const val MAX_BODY_BYTES = 8_192
private const val MAX_PROMPT_LENGTH = 1_000

data class LanServerState(
    val isRunning: Boolean = false,
    val port: Int = 8765,
    val localIp: String? = null,
    val triggerCount: Int = 0,
    val lastTriggerPreview: String? = null,
    val lastTriggerAt: Long = 0L,
    val errorMessage: String? = null
) {
    val endpointUrl: String
        get() = "http://${localIp ?: "<ip-do-android>"}:$port/trigger"
}

data class LanTriggerRequest(
    val action: String, // "prompt", "pause", "resume", "new_session"
    val prompt: String = "",
    val speak: Boolean = true,
    val routingOverride: RoutingMode? = null
)

data class LanTriggerResponse(
    val statusCode: Int,
    val body: JSONObject
)

/**
 * Servidor HTTP local leve para recebimento de gatilhos na rede Wi-Fi/LAN.
 *
 * Controles de segurança implementados:
 * - Autenticação obrigatória por token aleatório (Authorization: Bearer ou X-Jarvis-Token)
 *   com comparação em tempo constante (MessageDigest.isEqual).
 * - Timeout rígido de socket (5s), limite de cabeçalhos (4 KB) e corpo (8 KB).
 * - Allowlist explícita de rotas (/health, /status, /trigger) sem CORS curinga.
 * - Validação estrita de campos JSON e tamanho máximo do prompt (1000 caracteres).
 */
class LocalNetworkTriggerServer(
    private val onStatusRequested: suspend () -> JSONObject,
    private val onTriggerReceived: suspend (LanTriggerRequest) -> LanTriggerResponse
) {
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var activeToken: String = ""

    private val _state = MutableStateFlow(LanServerState())
    val state: StateFlow<LanServerState> = _state.asStateFlow()

    fun start(port: Int, token: String, localIp: String?) {
        val safePort = port.coerceIn(1024, 65535)
        if (token.isBlank()) {
            _state.update {
                it.copy(
                    isRunning = false,
                    port = safePort,
                    localIp = localIp,
                    errorMessage = "Token LAN não configurado. Regenere o token em Motores."
                )
            }
            return
        }

        // If already running on the same port, just refresh token & IP
        if (_state.value.isRunning && _state.value.port == safePort && serverSocket?.isClosed == false) {
            activeToken = token
            _state.update { it.copy(localIp = localIp, errorMessage = null) }
            return
        }

        stop()
        activeToken = token

        acceptJob = serverScope.launch {
            try {
                val socket = ServerSocket(safePort, 8, InetAddress.getByName("0.0.0.0"))
                serverSocket = socket
                _state.update {
                    it.copy(
                        isRunning = true,
                        port = safePort,
                        localIp = localIp,
                        errorMessage = null
                    )
                }
                Log.i(TAG, "LAN trigger server listening on port $safePort")

                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: SocketException) {
                        break
                    }
                    launch { handleClient(client) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "LAN trigger server error on port $safePort: ${e.message}")
                _state.update {
                    it.copy(
                        isRunning = false,
                        port = safePort,
                        localIp = localIp,
                        errorMessage = "Erro ao abrir porta LAN $safePort: ${e.localizedMessage ?: "porta ocupada"}"
                    )
                }
            }
        }
    }

    fun updateLocalIp(localIp: String?) {
        _state.update { it.copy(localIp = localIp) }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _state.update { it.copy(isRunning = false) }
    }

    fun destroy() {
        stop()
        serverScope.cancel()
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = SOCKET_TIMEOUT_MS
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()

            val headerBytes = readUntilHeaderEnd(input) ?: run {
                writeJsonResponse(output, 400, JSONObject().put("error", "Requisição HTTP inválida"))
                return
            }

            val headerText = String(headerBytes, StandardCharsets.UTF_8)
            val lines = headerText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty().trim()
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeJsonResponse(output, 400, JSONObject().put("error", "Linha de requisição malformada"))
                return
            }

            val method = parts[0].uppercase()
            val path = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size.coerceAtMost(33)) {
                val line = lines[i]
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            // Public minimal health endpoint (no sensitive info)
            if (method == "GET" && path == "/health") {
                writeJsonResponse(
                    output,
                    200,
                    JSONObject()
                        .put("status", "ok")
                        .put("service", "openjarvis")
                )
                return
            }

            // All operational endpoints require valid constant-time token authentication
            if (!isAuthorized(headers)) {
                writeJsonResponse(
                    output,
                    401,
                    JSONObject().put("error", "Não autorizado. Envie X-Jarvis-Token ou Authorization: Bearer <token>.")
                )
                return
            }

            when {
                method == "GET" && path == "/status" -> {
                    val statusJson = onStatusRequested()
                    writeJsonResponse(output, 200, statusJson)
                }

                method == "POST" && path == "/trigger" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                        writeJsonResponse(
                            output,
                            413,
                            JSONObject().put("error", "Payload excede o limite de $MAX_BODY_BYTES bytes.")
                        )
                        return
                    }

                    val bodyBytes = readExactBytes(input, contentLength)
                    val bodyStr = String(bodyBytes, StandardCharsets.UTF_8).trim()
                    val parsed = parseTriggerJson(bodyStr)
                    if (parsed == null) {
                        writeJsonResponse(
                            output,
                            400,
                            JSONObject().put("error", "JSON inválido. Envie {\"prompt\":\"...\"} ou {\"action\":\"pause|resume|new_session\"}.")
                        )
                        return
                    }

                    _state.update {
                        it.copy(
                            triggerCount = it.triggerCount + 1,
                            lastTriggerPreview = if (parsed.action == "prompt") {
                                parsed.prompt.take(64)
                            } else {
                                "Ação: ${parsed.action}"
                            },
                            lastTriggerAt = System.currentTimeMillis()
                        )
                    }

                    val response = onTriggerReceived(parsed)
                    writeJsonResponse(output, response.statusCode, response.body)
                }

                else -> {
                    writeJsonResponse(
                        output,
                        404,
                        JSONObject().put("error", "Rota não encontrada. Use GET /health, GET /status ou POST /trigger.")
                    )
                }
            }
        }
    }

    private fun isAuthorized(headers: Map<String, String>): Boolean {
        val expected = activeToken
        if (expected.isBlank()) return false

        val headerToken = headers["x-jarvis-token"]?.trim()
        val authHeader = headers["authorization"]?.trim()
        val bearerToken = if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7).trim()
        } else {
            null
        }

        val candidate = headerToken ?: bearerToken ?: return false
        val expectedBytes = expected.toByteArray(StandardCharsets.UTF_8)
        val candidateBytes = candidate.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, candidateBytes)
    }

    private fun parseTriggerJson(bodyStr: String): LanTriggerRequest? {
        if (bodyStr.isBlank()) return null
        return runCatching {
            val json = JSONObject(bodyStr)
            val rawAction = json.optString("action", "prompt").trim().lowercase()
            val allowedActions = setOf("prompt", "pause", "resume", "new_session")
            if (rawAction !in allowedActions) return null

            val prompt = json.optString("prompt", "").trim().take(MAX_PROMPT_LENGTH)
            if (rawAction == "prompt" && prompt.isBlank()) return null

            val speak = json.optBoolean("speak", true)
            val engineStr = json.optString("engine", "").trim().uppercase()
            val overrideMode = if (engineStr.isNotEmpty()) {
                runCatching { RoutingMode.valueOf(engineStr) }.getOrNull()
            } else {
                null
            }

            LanTriggerRequest(
                action = rawAction,
                prompt = prompt,
                speak = speak,
                routingOverride = overrideMode
            )
        }.getOrNull()
    }

    private fun readUntilHeaderEnd(input: BufferedInputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (buffer.size() < MAX_HEADER_BYTES) {
            val byteRead = input.read()
            if (byteRead == -1) return null
            buffer.write(byteRead)
            matched = when {
                matched == 0 && byteRead == '\r'.code -> 1
                matched == 1 && byteRead == '\n'.code -> 2
                matched == 2 && byteRead == '\r'.code -> 3
                matched == 3 && byteRead == '\n'.code -> 4
                byteRead == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) {
                return buffer.toByteArray()
            }
        }
        return null
    }

    private fun readExactBytes(input: BufferedInputStream, length: Int): ByteArray {
        if (length <= 0) return ByteArray(0)
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(data, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) data else data.copyOf(offset)
    }

    private fun writeJsonResponse(output: OutputStream, statusCode: Int, json: JSONObject) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Internal Server Error"
        }
        val bodyBytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        runCatching {
            output.write(headers)
            output.write(bodyBytes)
            output.flush()
        }
    }
}
