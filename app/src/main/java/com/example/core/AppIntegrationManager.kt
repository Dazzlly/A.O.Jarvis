package com.example.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class IntegrationResult(
    val success: Boolean,
    val message: String,
    val toolName: String
)

private data class KnownAppEntry(
    val displayName: String,
    val aliases: List<String>,
    val packages: List<String>,
    val fallbackUrl: String? = null
)

/**
 * Motor de integrações reais do OpenJarvis com aplicativos e serviços nativos do Android:
 * WhatsApp, Spotify, YouTube, Google Maps, Waze, Chrome, Gmail, Telegram, Instagram,
 * Contatos, Agenda, Discador, SMS, Câmera, Configurações e Localização.
 */
class AppIntegrationManager(private val context: Context) {

    private val appContext = context.applicationContext

    private val knownApps = listOf(
        KnownAppEntry("WhatsApp", listOf("whatsapp", "zap", "whats"), listOf("com.whatsapp", "com.whatsapp.w4b")),
        KnownAppEntry("YouTube", listOf("youtube", "yt"), listOf("com.google.android.youtube"), "https://m.youtube.com"),
        KnownAppEntry("Spotify", listOf("spotify"), listOf("com.spotify.music"), "https://open.spotify.com"),
        KnownAppEntry("Google Maps", listOf("maps", "google maps", "mapa", "mapas"), listOf("com.google.android.apps.maps")),
        KnownAppEntry("Waze", listOf("waze"), listOf("com.waze")),
        KnownAppEntry("Chrome", listOf("chrome", "navegador", "browser"), listOf("com.android.chrome"), "https://www.google.com"),
        KnownAppEntry("Gmail", listOf("gmail", "email", "e-mail"), listOf("com.google.android.gm")),
        KnownAppEntry("Instagram", listOf("instagram", "insta"), listOf("com.instagram.android"), "https://www.instagram.com"),
        KnownAppEntry("Telegram", listOf("telegram"), listOf("org.telegram.messenger")),
        KnownAppEntry("Agenda", listOf("agenda", "calendario", "google agenda"), listOf("com.google.android.calendar")),
        KnownAppEntry("Relógio", listOf("relogio", "despertador", "clock"), listOf("com.google.android.deskclock")),
        KnownAppEntry("Calculadora", listOf("calculadora", "calculator"), listOf("com.google.android.calculator")),
        KnownAppEntry("Google Keep", listOf("keep", "notas", "google keep"), listOf("com.google.android.keep")),
        KnownAppEntry("Configurações", listOf("configuracoes", "ajustes", "settings"), listOf("com.android.settings"))
    )

    fun openAppByName(rawName: String): IntegrationResult {
        val clean = normalize(rawName)
        if (clean.contains("camera") || clean.contains("foto")) {
            return openCamera()
        }
        if (clean.contains("wifi") || clean.contains("wi-fi")) {
            return openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "Configurações de Wi-Fi")
        }
        if (clean.contains("bluetooth")) {
            return openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Configurações de Bluetooth")
        }
        if (clean.contains("configurac") || clean.contains("ajustes")) {
            return openSystemSettings(Settings.ACTION_SETTINGS, "Configurações do Android")
        }

        val entry = knownApps.firstOrNull { app ->
            app.aliases.any { alias -> clean == alias || clean.contains(alias) }
        } ?: return IntegrationResult(
            success = false,
            message = "Não encontrei o aplicativo \"$rawName\" na lista de integrações conhecidas. Tente WhatsApp, YouTube, Spotify, Maps, Waze, Chrome, Gmail, Telegram, Instagram, Agenda, Câmera ou Configurações.",
            toolName = "integracao:abrir-app"
        )

        val pm = appContext.packageManager
        for (pkg in entry.packages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val launched = runCatching { appContext.startActivity(launchIntent); true }.getOrDefault(false)
                if (launched) {
                    return IntegrationResult(
                        success = true,
                        message = "Abrindo ${entry.displayName}.",
                        toolName = "integracao:${entry.displayName.lowercase()}"
                    )
                }
            }
        }

        if (entry.fallbackUrl != null) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { appContext.startActivity(webIntent); true }.getOrDefault(false)
            if (launched) {
                return IntegrationResult(
                    success = true,
                    message = "${entry.displayName} não está instalado; abrindo versão web no navegador.",
                    toolName = "integracao:web-${entry.displayName.lowercase()}"
                )
            }
        }

        return IntegrationResult(
            success = false,
            message = "${entry.displayName} não está instalado neste dispositivo.",
            toolName = "integracao:abrir-app"
        )
    }

    fun openCamera(): IntegrationResult {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) "Câmera aberta." else "Nenhum aplicativo de câmera respondeu.",
            toolName = "integracao:camera"
        )
    }

    fun openSystemSettings(action: String, label: String): IntegrationResult {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) "Abrindo $label." else "Não foi possível abrir $label.",
            toolName = "integracao:configuracoes"
        )
    }

    suspend fun dialContactOrNumber(target: String): IntegrationResult = withContext(Dispatchers.IO) {
        val trimmed = target.trim()
        val digitsOnly = trimmed.filter { it.isDigit() || it == '+' }
        val resolved: Pair<String, String>? = if (digitsOnly.length >= 6) {
            trimmed to digitsOnly
        } else {
            findContactPhone(trimmed)
        }

        if (resolved == null) {
            val needsPerm = !PermissionsManager.isGranted(appContext, Manifest.permission.READ_CONTACTS)
            return@withContext IntegrationResult(
                success = false,
                message = if (needsPerm) {
                    "Conceda a permissão de Contatos para que eu encontre \"$trimmed\" automaticamente."
                } else {
                    "Não encontrei nenhum contato com o nome \"$trimmed\" na sua agenda."
                },
                toolName = "integracao:telefone"
            )
        }

        val (displayName, phone) = resolved
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(dialIntent); true }.getOrDefault(false)
        IntegrationResult(
            success = launched,
            message = if (launched) "Discador aberto para $displayName ($phone)."
            else "Não foi possível abrir o discador do sistema.",
            toolName = "integracao:telefone"
        )
    }

    suspend fun sendWhatsAppMessage(target: String, messageText: String): IntegrationResult =
        withContext(Dispatchers.IO) {
            val trimmed = target.trim()
            val digitsOnly = trimmed.filter { it.isDigit() }
            val resolved: Pair<String, String>? = if (digitsOnly.length >= 8) {
                trimmed to digitsOnly
            } else {
                findContactPhone(trimmed)?.let { (name, phone) ->
                    name to phone.filter { it.isDigit() }
                }
            }

            if (resolved == null) {
                val needsPerm = !PermissionsManager.isGranted(appContext, Manifest.permission.READ_CONTACTS)
                return@withContext IntegrationResult(
                    success = false,
                    message = if (needsPerm) {
                        "Preciso da permissão de Contatos para buscar o WhatsApp de \"$trimmed\", ou diga o número com DDD."
                    } else {
                        "Não encontrei o contato \"$trimmed\" na agenda para enviar WhatsApp."
                    },
                    toolName = "integracao:whatsapp"
                )
            }

            val (displayName, cleanDigits) = resolved
            val encodedMsg = URLEncoder.encode(messageText.trim(), StandardCharsets.UTF_8.name())
            val waUri = Uri.parse("https://wa.me/$cleanDigits?text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
            IntegrationResult(
                success = launched,
                message = if (launched) "WhatsApp aberto com a mensagem pronta para $displayName."
                else "Não foi possível abrir o WhatsApp neste dispositivo.",
                toolName = "integracao:whatsapp"
            )
        }

    suspend fun sendSmsMessage(target: String, messageText: String): IntegrationResult =
        withContext(Dispatchers.IO) {
            val trimmed = target.trim()
            val digitsOnly = trimmed.filter { it.isDigit() || it == '+' }
            val resolved = if (digitsOnly.length >= 6) trimmed to digitsOnly else findContactPhone(trimmed)
            if (resolved == null) {
                return@withContext IntegrationResult(
                    success = false,
                    message = "Não encontrei o contato \"$trimmed\" para enviar SMS.",
                    toolName = "integracao:sms"
                )
            }
            val (displayName, phone) = resolved
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).apply {
                putExtra("sms_body", messageText.trim())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
            IntegrationResult(
                success = launched,
                message = if (launched) "Aplicativo de SMS aberto para $displayName."
                else "Não foi possível abrir o aplicativo de mensagens.",
                toolName = "integracao:sms"
            )
        }

    fun navigateWithMaps(destination: String): IntegrationResult {
        val cleanDest = destination.trim()
        if (cleanDest.isBlank()) {
            return IntegrationResult(false, "Informe um destino para iniciar a rota.", "integracao:mapas")
        }
        val encoded = Uri.encode(cleanDest)
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedMaps = runCatching { appContext.startActivity(navIntent); true }.getOrDefault(false)
        if (launchedMaps) {
            return IntegrationResult(
                success = true,
                message = "Iniciando navegação no Google Maps para \"$cleanDest\".",
                toolName = "integracao:maps"
            )
        }
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launchedGeo = runCatching { appContext.startActivity(geoIntent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launchedGeo,
            message = if (launchedGeo) "Abrindo rota para \"$cleanDest\" no mapa."
            else "Nenhum aplicativo de mapas encontrado para abrir \"$cleanDest\".",
            toolName = "integracao:maps"
        )
    }

    fun searchYouTube(query: String): IntegrationResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) "Buscando \"$query\" no YouTube."
            else "Não foi possível abrir a busca no YouTube.",
            toolName = "integracao:youtube"
        )
    }

    fun playMediaSearch(query: String, preferSpotify: Boolean = false): IntegrationResult {
        val cleanQuery = query.trim()
        if (preferSpotify) {
            val spotifyUri = Uri.parse("spotify:search:${Uri.encode(cleanQuery)}")
            val spotifyIntent = Intent(Intent.ACTION_VIEW, spotifyUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launchedSpotify = runCatching { appContext.startActivity(spotifyIntent); true }.getOrDefault(false)
            if (launchedSpotify) {
                return IntegrationResult(
                    success = true,
                    message = "Buscando \"$cleanQuery\" no Spotify.",
                    toolName = "integracao:spotify"
                )
            }
        }

        val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, cleanQuery)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(mediaIntent); true }.getOrDefault(false)
        if (launched) {
            return IntegrationResult(
                success = true,
                message = "Reproduzindo busca de mídia para \"$cleanQuery\".",
                toolName = "integracao:midia"
            )
        }
        return searchYouTube(cleanQuery)
    }

    fun webSearch(query: String): IntegrationResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) "Pesquisando \"$query\" na web."
            else "Não foi possível abrir o navegador para pesquisa.",
            toolName = "integracao:web"
        )
    }

    fun createCalendarEvent(title: String, startMillis: Long? = null): IntegrationResult {
        val start = startMillis ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        val end = start + 60 * 60 * 1000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.trim().ifBlank { "Compromisso OpenJarvis" })
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        val timeFmt = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")).format(Date(start))
        return IntegrationResult(
            success = launched,
            message = if (launched) "Abrindo Agenda para confirmar \"$title\" em $timeFmt."
            else "Nenhum aplicativo de calendário respondeu.",
            toolName = "integracao:agenda"
        )
    }

    suspend fun readTodayCalendarEvents(): IntegrationResult = withContext(Dispatchers.IO) {
        if (!PermissionsManager.isGranted(appContext, Manifest.permission.READ_CALENDAR)) {
            return@withContext IntegrationResult(
                success = false,
                message = "Conceda a permissão de Agenda para que eu possa ler seus compromissos de hoje.",
                toolName = "integracao:agenda"
            )
        }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startDay = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val endDay = cal.timeInMillis

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startDay)
        ContentUris.appendId(builder, endDay)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )

        val events = mutableListOf<String>()
        val timeFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        runCatching {
            appContext.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && events.size < 8) {
                    val title = cursor.getString(0)?.trim().orEmpty()
                    val begin = cursor.getLong(1)
                    if (title.isNotBlank()) {
                        events.add("• ${timeFmt.format(Date(begin))} — $title")
                    }
                }
            }
        }

        val reply = if (events.isEmpty()) {
            "Sua agenda de hoje está livre — nenhum compromisso encontrado."
        } else {
            "Seus compromissos de hoje:\n${events.joinToString("\n")}"
        }
        IntegrationResult(success = true, message = reply, toolName = "integracao:agenda")
    }

    @SuppressLint("MissingPermission")
    suspend fun readCurrentLocationSummary(): IntegrationResult = withContext(Dispatchers.IO) {
        val hasFine = PermissionsManager.isGranted(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = PermissionsManager.isGranted(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) {
            return@withContext IntegrationResult(
                success = false,
                message = "Conceda a permissão de Localização para que eu possa consultar onde você está.",
                toolName = "integracao:localizacao"
            )
        }

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext IntegrationResult(false, "Serviço de localização indisponível.", "integracao:localizacao")

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val location = providers
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (location == null) {
            return@withContext IntegrationResult(
                success = false,
                message = "Localização ainda não disponível no GPS/rede. Verifique se o GPS do aparelho está ativado.",
                toolName = "integracao:localizacao"
            )
        }

        val lat = String.format(Locale.US, "%.5f", location.latitude)
        val lon = String.format(Locale.US, "%.5f", location.longitude)

        @Suppress("DEPRECATION")
        val addressLine = runCatching {
            val geocoder = Geocoder(appContext, Locale("pt", "BR"))
            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull()

        val msg = if (!addressLine.isNullOrBlank()) {
            "Sua localização atual aproximada é: $addressLine (lat $lat, lon $lon)."
        } else {
            "Suas coordenadas atuais aproximadas são latitude $lat e longitude $lon."
        }
        IntegrationResult(success = true, message = msg, toolName = "integracao:localizacao")
    }

    private fun findContactPhone(queryName: String): Pair<String, String>? {
        if (!PermissionsManager.isGranted(appContext, Manifest.permission.READ_CONTACTS)) {
            return null
        }
        val normalizedQuery = normalize(queryName)
        if (normalizedQuery.isBlank()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return runCatching {
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                var partialMatch: Pair<String, String>? = null
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)?.trim().orEmpty()
                    val number = cursor.getString(1)?.trim().orEmpty()
                    if (name.isBlank() || number.isBlank()) continue
                    val normName = normalize(name)
                    if (normName == normalizedQuery) {
                        return@use name to number
                    }
                    if (partialMatch == null && (normName.contains(normalizedQuery) || normalizedQuery.contains(normName))) {
                        partialMatch = name to number
                    }
                }
                partialMatch
            }
        }.getOrNull()
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }
}
