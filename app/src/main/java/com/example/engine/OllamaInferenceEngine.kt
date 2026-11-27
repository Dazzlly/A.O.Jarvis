package com.example.engine

import android.util.Log
import com.example.data.JarvisSettings
import com.example.data.MemoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val TAG = "OllamaInferenceEngine"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class OllamaInferenceEngine {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return "http://192.168.1.100:11434"
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    suspend fun probeHealthAndModels(rawBaseUrl: String): OllamaHealthStatus =
        withContext(Dispatchers.IO) {
            val baseUrl = normalizeBaseUrl(rawBaseUrl)
            val startMs = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/tags")
                    .get()
                    .build()

                probeClient.newCall(request).execute().use { response ->
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    if (!response.isSuccessful) {
                        return@withContext OllamaHealthStatus(
                            isReachable = false,
                            latencyMs = elapsed,
                            errorMessage = "Servidor respondeu HTTP ${response.code}",
                            checkedAt = System.currentTimeMillis()
                        )
                    }

                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    val parsedModels = buildList {
                        for (i in 0 until modelsArray.length()) {
                            val item = modelsArray.optJSONObject(i) ?: continue
                            val details = item.optJSONObject("details")
                            add(
                                OllamaModelInfo(
                                    name = item.optString("name", ""),
                                    sizeBytes = item.optLong("size", 0L),
                                    parameterSize = details?.optString("parameter_size", "").orEmpty(),
                                    quantization = details?.optString("quantization_level", "").orEmpty()
                                )
                            )
                        }
                    }.filter { it.name.isNotBlank() }

                    OllamaHealthStatus(
                        isReachable = true,
                        latencyMs = elapsed,
                        models = parsedModels,
                        errorMessage = null,
                        checkedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Ollama probe failed for $baseUrl: ${e.message}")
                OllamaHealthStatus(
                    isReachable = false,
                    latencyMs = System.currentTimeMillis() - startMs,
                    errorMessage = e.localizedMessage ?: "Servidor Ollama inacessível na rede local",
                    checkedAt = System.currentTimeMillis()
                )
            }
        }

    fun streamChat(
        prompt: String,
        history: List<ConversationTurn>,
        memories: List<MemoryEntity>,
        settings: JarvisSettings,
        selfAwarenessContext: String = ""
    ): Flow<InferenceStreamEvent> = flow {
        val baseUrl = normalizeBaseUrl(settings.ollamaBaseUrl)
        val modelName = settings.ollamaModel.ifBlank { "llama3.2" }
        val startMs = System.currentTimeMillis()
        val accumulated = StringBuilder()

        try {
            val memoryContext = if (memories.isNotEmpty()) {
                "\n\nMemórias salvas localmente:\n" +
                    memories.take(12).joinToString("\n") { "- [${it.category}] ${it.key}: ${it.content}" }
            } else ""

            val messages = JSONArray().apply {
                put(
                    JSONObject().apply {
                        put("role", "system")
                        put("content", settings.systemPrompt + selfAwarenessContext + memoryContext)
                    }
                )
                history.takeLast(10).forEach { turn ->
                    put(
                        JSONObject().apply {
                            put("role", if (turn.role == "user") "user" else "assistant")
                            put("content", turn.content)
                        }
                    )
                }
                put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            }

            val payload = JSONObject().apply {
                put("model", modelName)
                put("stream", true)
                put("messages", messages)
                put(
                    "options",
                    JSONObject().apply {
                        put("temperature", settings.temperature.toDouble())
                    }
                )
            }

            val request = Request.Builder()
                .url("$baseUrl/api/chat")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            var tokensPerSec = 0.0

            streamClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errText = response.body?.string()?.take(240).orEmpty()
                    val elapsed = System.currentTimeMillis() - startMs
                    emit(
                        InferenceStreamEvent.Error(
                            message = "Ollama retornou HTTP ${response.code}: ${errText.ifBlank { response.message }}",
                            engineType = EngineType.OLLAMA,
                            modelName = modelName,
                            latencyMs = elapsed
                        )
                    )
                    return@flow
                }

                val bodyStream = response.body?.byteStream()
                    ?: throw IllegalStateException("Corpo de resposta vazio do Ollama.")

                BufferedReader(InputStreamReader(bodyStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty()) {
                            val obj = JSONObject(trimmedLine)
                            val msgObj = obj.optJSONObject("message")
                            val delta = msgObj?.optString("content", "").orEmpty()
                            if (delta.isNotEmpty()) {
                                accumulated.append(delta)
                                val visiblePreview = accumulated.toString()
                                    .substringBefore("[[ACTION")
                                    .trimEnd()
                                emit(
                                    InferenceStreamEvent.Chunk(
                                        accumulatedText = visiblePreview,
                                        engineType = EngineType.OLLAMA,
                                        modelName = modelName
                                    )
                                )
                            }
                            if (obj.optBoolean("done", false)) {
                                val evalCount = obj.optLong("eval_count", 0L)
                                val evalDurationNs = obj.optLong("eval_duration", 0L)
                                if (evalCount > 0L && evalDurationNs > 0L) {
                                    tokensPerSec = evalCount / (evalDurationNs / 1_000_000_000.0)
                                }
                                break
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }

            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val finalText = accumulated.toString().trim()
            if (finalText.isNotEmpty()) {
                if (tokensPerSec <= 0.0) {
                    tokensPerSec = (finalText.length / 4.0) / (elapsed / 1000.0)
                }
                emit(
                    InferenceStreamEvent.Completed(
                        fullText = finalText,
                        engineType = EngineType.OLLAMA,
                        modelName = modelName,
                        latencyMs = elapsed,
                        tokensPerSec = tokensPerSec
                    )
                )
            } else {
                emit(
                    InferenceStreamEvent.Error(
                        message = "O modelo local '$modelName' não retornou conteúdo.",
                        engineType = EngineType.OLLAMA,
                        modelName = modelName,
                        latencyMs = elapsed
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startMs
            Log.e(TAG, "Ollama chat failed for $baseUrl", e)
            emit(
                InferenceStreamEvent.Error(
                    message = "Erro ao conectar no Ollama ($baseUrl): ${e.localizedMessage ?: "Verifique se OLLAMA_HOST=0.0.0.0 está ativo no PC."}",
                    engineType = EngineType.OLLAMA,
                    modelName = modelName,
                    latencyMs = elapsed
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun executeLocalWebhook(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = rawUrl.trim()
            require(url.startsWith("http://") || url.startsWith("https://")) {
                "URL inválida. Use http:// ou https://"
            }
            val request = Request.Builder().url(url).get().build()
            probeClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty().take(320)
                if (response.isSuccessful) {
                    Result.success("HTTP ${response.code} OK${if (body.isNotBlank()) " — $body" else ""}")
                } else {
                    Result.failure(IllegalStateException("HTTP ${response.code} (${response.message})"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
