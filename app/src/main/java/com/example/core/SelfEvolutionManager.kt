package com.example.core

import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "SelfEvolutionManager"
private const val MAX_GITHUB_RESPONSE_BYTES = 512_000
private const val MAX_FILE_READ_CHARS = 18_000
private val SAFE_REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
private val SAFE_PATH_REGEX = Regex("^[A-Za-z0-9_./-]+$")
private val ALLOWED_EXTENSIONS = setOf(".kt", ".xml", ".md", ".txt", ".json", ".yaml", ".yml", ".kts")
private val BLOCKED_PATH_SEGMENTS = listOf(
    "debug.keystore",
    ".jks",
    ".keystore",
    "google-services.json",
    ".env",
    "local.properties",
    "signingConfigs"
)

data class ProposedFileChange(
    val path: String,
    val changeSummary: String,
    val updatedContent: String
)

data class EvolutionProposal(
    val repoSlug: String,
    val baseBranch: String,
    val featureBranch: String,
    val prTitle: String,
    val commitMessage: String,
    val summary: String,
    val fileChanges: List<ProposedFileChange>,
    val suggestedNextTag: String? = null
)

sealed interface SelfEvolutionState {
    data object Idle : SelfEvolutionState
    data class Working(val step: String) : SelfEvolutionState
    data class ProposalReady(val proposal: EvolutionProposal) : SelfEvolutionState
    data class PullRequestCreated(
        val prUrl: String,
        val prNumber: Int,
        val branchName: String,
        val title: String
    ) : SelfEvolutionState

    data class Error(val message: String) : SelfEvolutionState
}

/**
 * Motor seguro de Auto-Evolução (Auto-Desenvolvimento) do OpenJarvis.
 *
 * Em vez de executar código dinâmico inseguro no dispositivo (proibido no Android/Google Play),
 * o assistente lê o código-fonte real do seu repositório GitHub, usa Gemini (Firebase AI Logic)
 * para gerar alterações estruturadas, exibe tudo para sua revisão explícita e — somente após
 * aprovação — cria uma branch, faz commit e abre um Pull Request real no GitHub.
 * Ao fazer merge/tag no GitHub, o workflow compila o novo APK e publica no seu F-Droid.
 */
class SelfEvolutionManager(
    private val tokenStore: SecureTokenStore,
    private val appUpdateManager: AppUpdateManager
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val _state = MutableStateFlow<SelfEvolutionState>(SelfEvolutionState.Idle)
    val state: StateFlow<SelfEvolutionState> = _state.asStateFlow()

    private val _hasToken = MutableStateFlow(tokenStore.hasGitHubToken())
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    private val evolutionSchema = Schema.obj(
        properties = mapOf(
            "prTitle" to Schema.string(description = "Título curto do Pull Request em português"),
            "commitMessage" to Schema.string(description = "Mensagem de commit objetiva"),
            "summary" to Schema.string(description = "Resumo claro em português das mudanças propostas"),
            "suggestedNextTag" to Schema.string(description = "Próxima tag sugerida, ex: v1.0.2", nullable = true),
            "files" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "path" to Schema.string(description = "Caminho relativo do arquivo no repositório"),
                        "changeSummary" to Schema.string(description = "Explicação curta da alteração neste arquivo"),
                        "updatedContent" to Schema.string(description = "Conteúdo completo atualizado do arquivo")
                    )
                ),
                description = "Lista de 1 a 3 arquivos modificados ou criados"
            )
        ),
        optionalProperties = listOf("suggestedNextTag")
    )

    private val devModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = evolutionSchema
            }
        )
    }

    fun saveGitHubToken(token: String): Boolean {
        val ok = tokenStore.saveGitHubToken(token)
        _hasToken.value = tokenStore.hasGitHubToken()
        return ok
    }

    fun clearGitHubToken() {
        tokenStore.clearGitHubToken()
        _hasToken.value = false
    }

    fun resetState() {
        _state.value = SelfEvolutionState.Idle
    }

    fun isSafeSourcePath(path: String): Boolean {
        val clean = path.trim()
        if (clean.isEmpty() || clean.startsWith("/") || clean.contains("..") || clean.contains("\\")) {
            return false
        }
        if (!SAFE_PATH_REGEX.matches(clean)) return false
        val lower = clean.lowercase()
        if (BLOCKED_PATH_SEGMENTS.any { lower.contains(it.lowercase()) }) return false
        if (lower == "app/build.gradle.kts" || lower == "build.gradle.kts") return false
        return ALLOWED_EXTENSIONS.any { lower.endsWith(it) }
    }

    /**
     * Etapa 1: Lê a árvore e arquivos relevantes do repositório GitHub do usuário
     * e solicita ao Gemini uma proposta estruturada de alteração de código.
     */
    suspend fun generateEvolutionProposal(
        rawGitHubRepo: String,
        userRequest: String
    ): SelfEvolutionState = withContext(Dispatchers.IO) {
        val requestText = userRequest.trim()
        if (requestText.isBlank()) {
            val err = SelfEvolutionState.Error("Descreva qual melhoria ou recurso deseja que o OpenJarvis desenvolva.")
            _state.value = err
            return@withContext err
        }

        val repoSlug = appUpdateManager.normalizeGitHubRepo(rawGitHubRepo)
        if (!SAFE_REPO_REGEX.matches(repoSlug) || repoSlug.contains("..")) {
            val err = SelfEvolutionState.Error(
                "Configure primeiro seu repositório GitHub (usuario/repositorio) em Motores → Atualizações Remotas."
            )
            _state.value = err
            return@withContext err
        }

        val token = tokenStore.getGitHubToken()
        if (token.isNullOrBlank()) {
            val err = SelfEvolutionState.Error(
                "Informe seu GitHub Fine-Grained Token (armazenado criptografado no Keystore) para ler e propor Pull Requests no repositório."
            )
            _state.value = err
            return@withContext err
        }

        try {
            _state.value = SelfEvolutionState.Working("Conectando ao repositório $repoSlug…")
            val repoMeta = githubGetJson("https://api.github.com/repos/$repoSlug", token)
            val defaultBranch = repoMeta.optString("default_branch", "main").ifBlank { "main" }

            _state.value = SelfEvolutionState.Working("Mapeando arquivos do projeto ($defaultBranch)…")
            val treeJson = githubGetJson(
                "https://api.github.com/repos/$repoSlug/git/trees/$defaultBranch?recursive=1",
                token
            )
            val treeArray = treeJson.optJSONArray("tree") ?: JSONArray()
            val repoFiles = mutableListOf<String>()
            for (i in 0 until treeArray.length()) {
                val item = treeArray.optJSONObject(i) ?: continue
                if (item.optString("type") == "blob") {
                    val p = item.optString("path")
                    if (isSafeSourcePath(p)) {
                        repoFiles.add(p)
                    }
                }
            }

            if (repoFiles.isEmpty()) {
                val err = SelfEvolutionState.Error("Nenhum arquivo fonte encontrado em $repoSlug ($defaultBranch).")
                _state.value = err
                return@withContext err
            }

            // Pick up to 3 most relevant files based on keywords in the user's request
            val candidatePaths = selectRelevantFiles(repoFiles, requestText).take(3)
            _state.value = SelfEvolutionState.Working("Lendo ${candidatePaths.size} arquivo(s) fonte do GitHub…")

            val fileContents = mutableMapOf<String, String>()
            for (path in candidatePaths) {
                val contentObj = runCatching {
                    githubGetJson("https://api.github.com/repos/$repoSlug/contents/$path?ref=$defaultBranch", token)
                }.getOrNull() ?: continue
                val base64 = contentObj.optString("content", "").replace("\n", "")
                if (base64.isNotBlank()) {
                    val decoded = String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
                    fileContents[path] = decoded.take(MAX_FILE_READ_CHARS)
                }
            }

            _state.value = SelfEvolutionState.Working("Gemini analisando código e gerando proposta de PR…")
            val prompt = buildEvolutionPrompt(
                repoSlug = repoSlug,
                userRequest = requestText,
                allPaths = repoFiles.take(60),
                loadedFiles = fileContents
            )

            val response = withTimeout(60_000L) {
                devModel.generateContent(prompt)
            }
            val rawJson = response.text?.trim()
                ?: throw IllegalStateException("O Gemini retornou uma resposta vazia.")

            val parsed = JSONObject(rawJson)
            val prTitle = parsed.optString("prTitle", "OpenJarvis Auto-Evolução").trim().take(90)
            val commitMsg = parsed.optString("commitMessage", "feat: auto-evolução solicitada pelo usuário").trim().take(120)
            val summary = parsed.optString("summary", "Alterações propostas pelo OpenJarvis.").trim()
            val suggestedTag = parsed.optString("suggestedNextTag", "").trim().ifBlank { null }

            val filesArray = parsed.optJSONArray("files") ?: JSONArray()
            val changes = mutableListOf<ProposedFileChange>()
            for (i in 0 until filesArray.length().coerceAtMost(3)) {
                val item = filesArray.optJSONObject(i) ?: continue
                val p = item.optString("path", "").trim()
                val cs = item.optString("changeSummary", "").trim()
                val updated = item.optString("updatedContent", "")
                if (isSafeSourcePath(p) && updated.isNotBlank()) {
                    changes.add(ProposedFileChange(path = p, changeSummary = cs, updatedContent = updated))
                }
            }

            if (changes.isEmpty()) {
                val err = SelfEvolutionState.Error(
                    "Nenhuma alteração válida e segura foi proposta. Tente detalhar melhor o recurso desejado."
                )
                _state.value = err
                return@withContext err
            }

            val branchName = "jarvis/evolve-${System.currentTimeMillis() / 1000}"
            val proposal = EvolutionProposal(
                repoSlug = repoSlug,
                baseBranch = defaultBranch,
                featureBranch = branchName,
                prTitle = prTitle,
                commitMessage = commitMsg,
                summary = summary,
                fileChanges = changes,
                suggestedNextTag = suggestedTag
            )
            val ready = SelfEvolutionState.ProposalReady(proposal)
            _state.value = ready
            ready
        } catch (e: TimeoutCancellationException) {
            val err = SelfEvolutionState.Error("Tempo limite (60s) excedido ao gerar proposta de código.")
            _state.value = err
            err
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Self-evolution proposal failed", e)
            val err = SelfEvolutionState.Error(
                e.localizedMessage ?: "Falha ao gerar proposta de auto-evolução."
            )
            _state.value = err
            err
        }
    }

    /**
     * Etapa 2 (somente após aprovação explícita do usuário):
     * Cria uma nova branch, faz commit dos arquivos aprovados e abre o Pull Request no GitHub.
     */
    suspend fun approveAndCreatePullRequest(proposal: EvolutionProposal): SelfEvolutionState =
        withContext(Dispatchers.IO) {
            val token = tokenStore.getGitHubToken()
            if (token.isNullOrBlank()) {
                val err = SelfEvolutionState.Error("Token GitHub ausente. Salve o token antes de criar o PR.")
                _state.value = err
                return@withContext err
            }
            if (!SAFE_REPO_REGEX.matches(proposal.repoSlug) || proposal.repoSlug.contains("..")) {
                val err = SelfEvolutionState.Error("Repositório GitHub inválido.")
                _state.value = err
                return@withContext err
            }

            try {
                _state.value = SelfEvolutionState.Working("Criando branch ${proposal.featureBranch}…")
                val refJson = githubGetJson(
                    "https://api.github.com/repos/${proposal.repoSlug}/git/ref/heads/${proposal.baseBranch}",
                    token
                )
                val baseSha = refJson.getJSONObject("object").getString("sha")

                val createRefBody = JSONObject()
                    .put("ref", "refs/heads/${proposal.featureBranch}")
                    .put("sha", baseSha)
                githubRequestJson(
                    method = "POST",
                    url = "https://api.github.com/repos/${proposal.repoSlug}/git/refs",
                    token = token,
                    body = createRefBody
                )

                for ((idx, change) in proposal.fileChanges.withIndex()) {
                    if (!isSafeSourcePath(change.path)) continue
                    _state.value = SelfEvolutionState.Working(
                        "Enviando arquivo ${idx + 1}/${proposal.fileChanges.size}: ${change.path.substringAfterLast('/')}…"
                    )
                    // Check existing file SHA if updating an existing file
                    val existingSha = runCatching {
                        githubGetJson(
                            "https://api.github.com/repos/${proposal.repoSlug}/contents/${change.path}?ref=${proposal.featureBranch}",
                            token
                        ).optString("sha", "").ifBlank { null }
                    }.getOrNull()

                    val encodedContent = Base64.encodeToString(
                        change.updatedContent.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )
                    val putBody = JSONObject()
                        .put("message", proposal.commitMessage)
                        .put("content", encodedContent)
                        .put("branch", proposal.featureBranch)
                    if (existingSha != null) {
                        putBody.put("sha", existingSha)
                    }

                    githubRequestJson(
                        method = "PUT",
                        url = "https://api.github.com/repos/${proposal.repoSlug}/contents/${change.path}",
                        token = token,
                        body = putBody
                    )
                }

                _state.value = SelfEvolutionState.Working("Abrindo Pull Request no GitHub…")
                val prBodyText = buildString {
                    append("## Auto-Evolução gerada pelo OpenJarvis\n\n")
                    append(proposal.summary)
                    append("\n\n### Arquivos alterados\n")
                    proposal.fileChanges.forEach { fc ->
                        append("- `${fc.path}`: ${fc.changeSummary}\n")
                    }
                    if (!proposal.suggestedNextTag.isNullOrBlank()) {
                        append("\nApós revisar e aprovar este PR, crie a tag `${proposal.suggestedNextTag}` para disparar o build automático do APK e atualizar seu repositório F-Droid.")
                    }
                }
                val prPayload = JSONObject()
                    .put("title", proposal.prTitle)
                    .put("head", proposal.featureBranch)
                    .put("base", proposal.baseBranch)
                    .put("body", prBodyText)

                val prResponse = githubRequestJson(
                    method = "POST",
                    url = "https://api.github.com/repos/${proposal.repoSlug}/pulls",
                    token = token,
                    body = prPayload
                )

                val prUrl = prResponse.optString("html_url", "https://github.com/${proposal.repoSlug}/pulls")
                val prNumber = prResponse.optInt("number", 0)
                val created = SelfEvolutionState.PullRequestCreated(
                    prUrl = if (prUrl.startsWith("https://github.com/")) prUrl else "https://github.com/${proposal.repoSlug}/pulls",
                    prNumber = prNumber,
                    branchName = proposal.featureBranch,
                    title = proposal.prTitle
                )
                _state.value = created
                created
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create self-evolution Pull Request", e)
                val err = SelfEvolutionState.Error(
                    e.localizedMessage ?: "Erro ao publicar branch/PR no GitHub. Verifique se o token tem permissão de Contents e Pull Requests."
                )
                _state.value = err
                err
            }
        }

    private fun selectRelevantFiles(allPaths: List<String>, request: String): List<String> {
        val tokens = request.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

        val scored = allPaths.map { path ->
            val lower = path.lowercase()
            var score = 0
            if (lower.endsWith(".kt")) score += 4
            if (lower.contains("offlinecommand")) score += 3
            if (lower.contains("appselfcontroller")) score += 3
            if (lower.contains("consolescreen") || lower.contains("enginesscreen")) score += 2
            tokens.forEach { t ->
                if (lower.contains(t)) score += 6
            }
            path to score
        }.sortedByDescending { it.second }

        return scored.map { it.first }
    }

    private fun buildEvolutionPrompt(
        repoSlug: String,
        userRequest: String,
        allPaths: List<String>,
        loadedFiles: Map<String, String>
    ): String = buildString {
        append("Você é o engenheiro sênior Android (Kotlin + Jetpack Compose + Material 3) responsável pela auto-evolução do aplicativo OpenJarvis ($repoSlug).\n")
        append("Pedido do usuário para evoluir o código do app:\n\"$userRequest\"\n\n")
        append("Regras obrigatórias:\n")
        append("1. Retorne apenas alterações válidas, compiláveis em Kotlin/Compose, preservando todos os imports e código existente.\n")
        append("2. Nunca modifique build.gradle.kts, keystores, .env ou google-services.json.\n")
        append("3. Modifique no máximo 2 ou 3 arquivos existentes (ou crie um arquivo Kotlin modular se necessário) com o conteúdo COMPLETO de cada arquivo alterado.\n\n")
        append("Arquivos disponíveis no repositório:\n")
        allPaths.forEach { append("- $it\n") }
        append("\nConteúdo atual dos arquivos mais relevantes:\n")
        loadedFiles.forEach { (path, content) ->
            append("\n--- ARQUIVO: $path ---\n")
            append(content)
            append("\n--- FIM: $path ---\n")
        }
    }

    private fun githubGetJson(url: String, token: String): JSONObject {
        require(url.startsWith("https://api.github.com/")) { "Endpoint GitHub inválido." }
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "OpenJarvis-Android")
            .get()
            .build()
        return executeJsonRequest(req)
    }

    private fun githubRequestJson(
        method: String,
        url: String,
        token: String,
        body: JSONObject
    ): JSONObject {
        require(url.startsWith("https://api.github.com/")) { "Endpoint GitHub inválido." }
        val requestBody = body.toString().toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "OpenJarvis-Android")

        when (method.uppercase()) {
            "POST" -> builder.post(requestBody)
            "PUT" -> builder.put(requestBody)
            else -> throw IllegalArgumentException("Método HTTP não suportado: $method")
        }
        return executeJsonRequest(builder.build())
    }

    private fun executeJsonRequest(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val rawBytes = response.body?.byteStream()?.use { stream ->
                val buffer = ByteArray(MAX_GITHUB_RESPONSE_BYTES + 1)
                var total = 0
                while (total < buffer.size) {
                    val read = stream.read(buffer, total, buffer.size - total)
                    if (read == -1) break
                    total += read
                }
                if (total > MAX_GITHUB_RESPONSE_BYTES) {
                    throw IllegalStateException("Resposta da API do GitHub excedeu o limite seguro.")
                }
                buffer.copyOf(total)
            } ?: ByteArray(0)

            val text = String(rawBytes, Charsets.UTF_8)
            if (!response.isSuccessful) {
                val apiMsg = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw IllegalStateException(
                    "GitHub HTTP ${response.code}${if (!apiMsg.isNullOrBlank()) ": $apiMsg" else ""}"
                )
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
}
