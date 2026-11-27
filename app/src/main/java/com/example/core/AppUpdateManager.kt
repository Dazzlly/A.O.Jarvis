package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

private const val TAG = "AppUpdateManager"
private const val MAX_RESPONSE_BYTES = 262_144 // 256 KB cap
private val GITHUB_REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

data class RemoteReleaseInfo(
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val hasApkAsset: Boolean,
    val apkAssetName: String? = null
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(
        val currentVersion: String,
        val latestTag: String,
        val checkedAt: Long
    ) : AppUpdateState

    data class UpdateAvailable(
        val currentVersion: String,
        val release: RemoteReleaseInfo,
        val checkedAt: Long
    ) : AppUpdateState

    data class Error(val message: String) : AppUpdateState
}

sealed interface FdroidRepoCheckState {
    data object Idle : FdroidRepoCheckState
    data object Checking : FdroidRepoCheckState
    data class Valid(
        val indexFile: String,
        val sizeBytes: Int,
        val verifiedUrl: String
    ) : FdroidRepoCheckState

    data class Invalid(val message: String) : FdroidRepoCheckState
}

/**
 * Gerenciador de atualizações remotas compatível com GitHub Releases e F-Droid.
 *
 * Verifica novas versões pela API pública do GitHub Releases (sem exigir API keys)
 * e direciona a instalação de forma segura e compatível com as políticas do Android/Google Play
 * via F-Droid (esquema fdroidrepos:// / HTTPS) ou página oficial da Release no GitHub.
 */
class AppUpdateManager(context: Context) {

    private val appContext = context.applicationContext

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _fdroidStatus = MutableStateFlow<FdroidRepoCheckState>(FdroidRepoCheckState.Idle)
    val fdroidStatus: StateFlow<FdroidRepoCheckState> = _fdroidStatus.asStateFlow()

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    fun normalizeGitHubRepo(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        return cleaned
    }

    fun isValidGitHubRepo(raw: String): Boolean {
        val normalized = normalizeGitHubRepo(raw)
        return GITHUB_REPO_REGEX.matches(normalized) && !normalized.contains("..")
    }

    fun validateHttpsUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("URL vazia."))
        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || scheme == "fdroidrepos") {
                "Use apenas URLs seguras https:// ou fdroidrepos://"
            }
            require(!uri.host.isNullOrBlank()) { "Host inválido na URL." }
            trimmed
        }
    }

    suspend fun checkForUpdates(rawGitHubRepo: String): AppUpdateState = withContext(Dispatchers.IO) {
        val repoSlug = normalizeGitHubRepo(rawGitHubRepo)
        if (!isValidGitHubRepo(repoSlug)) {
            val err = AppUpdateState.Error(
                "Configure o repositório GitHub no formato usuario/repositorio na aba Motores."
            )
            _state.value = err
            return@withContext err
        }

        _state.value = AppUpdateState.Checking
        val apiUrl = "https://api.github.com/repos/$repoSlug/releases/latest"

        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenJarvis-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> {
                        val err = AppUpdateState.Error(
                            "Nenhuma Release encontrada em $repoSlug. Crie uma tag (ex: v1.0.1) no GitHub para publicar."
                        )
                        _state.value = err
                        return@withContext err
                    }

                    403 -> {
                        val err = AppUpdateState.Error(
                            "Limite temporário da API do GitHub atingido ou repositório privado."
                        )
                        _state.value = err
                        return@withContext err
                    }
                }

                if (!response.isSuccessful) {
                    val err = AppUpdateState.Error("GitHub respondeu HTTP ${response.code}.")
                    _state.value = err
                    return@withContext err
                }

                val rawBytes = response.body?.byteStream()?.use { stream ->
                    val buffer = ByteArray(MAX_RESPONSE_BYTES + 1)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read == -1) break
                        total += read
                    }
                    if (total > MAX_RESPONSE_BYTES) {
                        throw IllegalStateException("Resposta da Release excedeu o limite seguro.")
                    }
                    buffer.copyOf(total)
                } ?: ByteArray(0)

                val json = JSONObject(String(rawBytes, Charsets.UTF_8))
                val tagName = json.optString("tag_name", "").trim()
                val title = json.optString("name", tagName).trim().ifBlank { tagName }
                val notes = json.optString("body", "").trim().take(1_200)
                val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases").trim()
                val publishedAt = json.optString("published_at", "").take(10)

                val safeHtmlUrl = if (htmlUrl.startsWith("https://github.com/")) {
                    htmlUrl
                } else {
                    "https://github.com/$repoSlug/releases"
                }

                val assets = json.optJSONArray("assets") ?: JSONArray()
                var apkName: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        break
                    }
                }

                val release = RemoteReleaseInfo(
                    tagName = tagName,
                    title = title,
                    releaseNotes = notes.ifBlank { "Nova versão publicada via GitHub Releases." },
                    htmlUrl = safeHtmlUrl,
                    publishedAt = publishedAt,
                    hasApkAsset = apkName != null,
                    apkAssetName = apkName
                )

                val now = System.currentTimeMillis()
                val resultState = if (tagName.isNotBlank() && isNewerVersion(tagName, currentVersionName)) {
                    AppUpdateState.UpdateAvailable(
                        currentVersion = currentVersionName,
                        release = release,
                        checkedAt = now
                    )
                } else {
                    AppUpdateState.UpToDate(
                        currentVersion = currentVersionName,
                        latestTag = tagName.ifBlank { "v$currentVersionName" },
                        checkedAt = now
                    )
                }
                _state.value = resultState
                resultState
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check GitHub updates for $repoSlug: ${e.message}")
            val err = AppUpdateState.Error(
                e.localizedMessage ?: "Não foi possível consultar atualizações no GitHub."
            )
            _state.value = err
            err
        }
    }

    fun openGitHubRelease(url: String): Result<String> {
        val clean = url.trim()
        if (!clean.startsWith("https://github.com/")) {
            return Result.failure(IllegalArgumentException("URL de Release do GitHub inválida."))
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return if (launched) {
            Result.success("Abrindo Release no GitHub.")
        } else {
            Result.failure(IllegalStateException("Nenhum navegador respondeu para abrir o GitHub."))
        }
    }

    /**
     * Verifica previamente se a URL informada realmente serve um índice F-Droid válido
     * (entry.jar ou index-v1.jar com assinatura ZIP/JAR não vazia), evitando o erro
     * "ClosedByteChannelException: required 10 bytes but only 0 available" no app F-Droid.
     */
    suspend fun verifyFdroidRepo(rawRepoUrl: String): FdroidRepoCheckState = withContext(Dispatchers.IO) {
        val validated = validateHttpsUrl(rawRepoUrl).getOrElse { err ->
            val invalid = FdroidRepoCheckState.Invalid(err.message ?: "URL do repositório F-Droid inválida.")
            _fdroidStatus.value = invalid
            return@withContext invalid
        }

        val httpsBase = validated
            .replaceFirst("fdroidrepos://", "https://")
            .substringBefore("?")
            .trimEnd('/')

        if (httpsBase.startsWith("https://github.com/")) {
            val invalid = FdroidRepoCheckState.Invalid(
                "Não use a URL da página do código (github.com). Use o botão 'URL Raw (Sem Pages)' ou 'URL GitHub Pages' que termina em /fdroid/repo."
            )
            _fdroidStatus.value = invalid
            return@withContext invalid
        }

        _fdroidStatus.value = FdroidRepoCheckState.Checking
        val candidates = listOf("entry.jar", "index-v1.jar")
        var lastHttpCode = 0

        for (indexFile in candidates) {
            val probeUrl = "$httpsBase/$indexFile"
            val req = Request.Builder()
                .url(probeUrl)
                .header("User-Agent", "OpenJarvis-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    lastHttpCode = resp.code
                    if (resp.isSuccessful) {
                        val headerBytes = resp.body?.byteStream()?.use { stream ->
                            val buf = ByteArray(64)
                            var readTotal = 0
                            while (readTotal < buf.size) {
                                val r = stream.read(buf, readTotal, buf.size - readTotal)
                                if (r == -1) break
                                readTotal += r
                            }
                            buf.copyOf(readTotal)
                        } ?: ByteArray(0)

                        val contentLen = resp.body?.contentLength()?.toInt() ?: headerBytes.size
                        // Valid JAR/ZIP starts with 'P' (0x50) 'K' (0x4B) and is non-empty
                        val isZipMagic = headerBytes.size >= 4 &&
                            headerBytes[0] == 0x50.toByte() &&
                            headerBytes[1] == 0x4B.toByte()
                        if (isZipMagic) {
                            val ok = FdroidRepoCheckState.Valid(
                                indexFile = indexFile,
                                sizeBytes = if (contentLen > 0) contentLen else headerBytes.size,
                                verifiedUrl = httpsBase
                            )
                            _fdroidStatus.value = ok
                            return@withContext ok
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "F-Droid probe failed for $probeUrl: ${e.message}")
            }
        }

        val reason = when (lastHttpCode) {
            404 -> "Índice F-Droid não encontrado nesta URL (HTTP 404). Confirme que o repositório GitHub é público e que o workflow 'Build APK, GitHub Release & F-Droid Repo' na aba Actions concluiu com sucesso."
            403 -> "Acesso negado (HTTP 403). O repositório GitHub precisa ser Público para o F-Droid baixar o índice."
            0 -> "Sem resposta do servidor. Verifique sua conexão ou se a URL termina em /fdroid/repo."
            else -> "A URL respondeu HTTP $lastHttpCode sem um índice F-Droid válido (entry.jar / index-v1.jar). Rode o workflow no GitHub Actions primeiro."
        }
        val invalid = FdroidRepoCheckState.Invalid(reason)
        _fdroidStatus.value = invalid
        invalid
    }

    fun openInFdroid(rawRepoUrl: String): Result<String> {
        val validated = validateHttpsUrl(rawRepoUrl).getOrElse { return Result.failure(it) }
        val normalized = validated.trimEnd('/')
        if (normalized.startsWith("https://github.com/") || normalized.startsWith("fdroidrepos://github.com/")) {
            return Result.failure(
                IllegalArgumentException(
                    "Use a URL /fdroid/repo (Raw GitHub ou GitHub Pages), não a URL principal github.com."
                )
            )
        }
        val fdroidDeepLink = if (normalized.startsWith("fdroidrepos://")) {
            normalized
        } else {
            normalized.replaceFirst("https://", "fdroidrepos://")
        }

        // 1. Try opening directly in F-Droid client app
        val fdroidIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fdroidDeepLink)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedFdroid = runCatching { appContext.startActivity(fdroidIntent); true }.getOrDefault(false)
        if (launchedFdroid) {
            return Result.success("Repositório aberto no F-Droid para sincronizar atualizações.")
        }

        // 2. Fallback to HTTPS repository link in browser
        val httpsFallback = normalized.replaceFirst("fdroidrepos://", "https://")
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(httpsFallback)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedWeb = runCatching { appContext.startActivity(webIntent); true }.getOrDefault(false)
        return if (launchedWeb) {
            Result.success("F-Droid não detectado; abrindo URL do repositório no navegador.")
        } else {
            Result.failure(IllegalStateException("Não foi possível abrir o repositório F-Droid."))
        }
    }

    fun buildRawGitHubFdroidUrl(rawGitHubRepo: String): String {
        val slug = normalizeGitHubRepo(rawGitHubRepo)
        if (!isValidGitHubRepo(slug)) return ""
        return "https://raw.githubusercontent.com/$slug/gh-pages/fdroid/repo"
    }

    fun buildDefaultFdroidPagesUrl(rawGitHubRepo: String): String {
        val slug = normalizeGitHubRepo(rawGitHubRepo)
        if (!isValidGitHubRepo(slug)) return ""
        val owner = slug.substringBefore("/").lowercase()
        val repo = slug.substringAfter("/")
        return "https://$owner.github.io/$repo/fdroid/repo"
    }

    fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteTag)
        val localParts = parseVersionParts(localVersion)
        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun parseVersionParts(version: String): List<Int> {
        val cleaned = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
        return cleaned.split(".").mapNotNull { it.trim().toIntOrNull() }
    }
}
