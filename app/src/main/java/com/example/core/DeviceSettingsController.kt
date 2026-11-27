package com.example.core

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

data class DeviceSettingsSnapshot(
    val canWriteSystemSettings: Boolean = false,
    val hasDndAccess: Boolean = false,
    val hasBluetoothPermission: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val brightnessPercent: Int = 50,
    val isAutoBrightness: Boolean = true,
    val isAutoRotate: Boolean = true,
    val isTorchOn: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val ringerModeLabel: String = "Normal",
    val isDndEnabled: Boolean = false
)

/**
 * Controlador real de configurações do dispositivo Android para o A.O.Jarvis.
 * Gerencia permissões e controles de brilho, brilho automático, volume, modo silencioso/vibrar,
 * Wi-Fi, Bluetooth, lanterna, rotação automática, tempo de tela, Não Perturbe e painéis nativos.
 */
class DeviceSettingsController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    @Volatile
    private var torchEnabled: Boolean = false

    fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(appContext)

    fun hasDndPolicyAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isWifiCurrentlyEnabled(): Boolean {
        return runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
    }

    fun isBluetoothCurrentlyEnabled(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
                false
            } else {
                bluetoothManager?.adapter?.isEnabled == true
            }
        }.getOrDefault(false)
    }

    fun readSnapshot(): DeviceSettingsSnapshot {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPct = ((curVol.toFloat() / maxVol) * 100f).roundToInt().coerceIn(0, 100)

        val rawBright = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128)
        val brightPct = ((rawBright / 255f) * 100f).roundToInt().coerceIn(0, 100)

        val autoBright = runCatching {
            Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrDefault(true)

        val autoRotate = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        }.getOrDefault(true)

        val ringerLabel = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silencioso"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrar"
            else -> "Normal"
        }

        val dndOn = runCatching {
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)

        return DeviceSettingsSnapshot(
            canWriteSystemSettings = canWriteSystemSettings(),
            hasDndAccess = hasDndPolicyAccess(),
            hasBluetoothPermission = hasBluetoothPermission(),
            mediaVolumePercent = volPct,
            brightnessPercent = brightPct,
            isAutoBrightness = autoBright,
            isAutoRotate = autoRotate,
            isTorchOn = torchEnabled,
            isWifiEnabled = isWifiCurrentlyEnabled(),
            isBluetoothEnabled = isBluetoothCurrentlyEnabled(),
            ringerModeLabel = ringerLabel,
            isDndEnabled = dndOn
        )
    }

    fun openWriteSettingsPermission(): IntegrationResult {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) {
                "Abri a tela 'Modificar configurações do sistema'. Ative a permissão para o A.O.Jarvis ajustar brilho, rotação e tempo de tela."
            } else {
                "Não foi possível abrir a tela de permissão de configurações do sistema."
            },
            toolName = "device:permissao-config"
        )
    }

    fun openDndAccessPermission(): IntegrationResult {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) {
                "Abri o acesso ao modo Não Perturbe. Autorize o A.O.Jarvis para alternar o silencioso e o Não Perturbe."
            } else {
                "Não foi possível abrir as configurações do modo Não Perturbe."
            },
            toolName = "device:permissao-dnd"
        )
    }

    fun toggleWifi(enabled: Boolean): IntegrationResult {
        val wm = wifiManager ?: return openSystemPanelOrSettings("wifi")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val applied = runCatching { wm.setWifiEnabled(enabled) }.getOrDefault(false)
            if (applied) {
                return IntegrationResult(
                    success = true,
                    message = if (enabled) "Wi-Fi ativado." else "Wi-Fi desativado.",
                    toolName = "device:wifi"
                )
            }
        }
        // Android 10+ (API 29+) restricts silent Wi-Fi toggling for third-party apps;
        // launch the official floating Settings.Panel quick toggle sheet.
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        val launched = runCatching {
            appContext.startActivity(Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            true
        }.getOrDefault(false)
        val verb = if (enabled) "ativar" else "desativar"
        return IntegrationResult(
            success = launched,
            message = if (launched) {
                "Abri o painel rápido de Wi-Fi do Android para você $verb o Wi-Fi em um toque."
            } else {
                "Não foi possível abrir o controle de Wi-Fi."
            },
            toolName = "device:wifi"
        )
    }

    fun toggleBluetooth(enabled: Boolean): IntegrationResult {
        val adapter = bluetoothManager?.adapter ?: return IntegrationResult(
            success = false,
            message = "Este dispositivo não possui adaptador Bluetooth disponível.",
            toolName = "device:bluetooth"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            val panel = openSystemPanelOrSettings("bluetooth")
            return panel.copy(
                message = "Conceda a permissão Bluetooth nas permissões de autonomia ou alterne diretamente no painel Bluetooth que abri.",
                toolName = "device:bluetooth"
            )
        }

        // On Android 12 and below, direct adapter toggle is permitted when BLUETOOTH_CONNECT / BLUETOOTH_ADMIN is held.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val directApplied = runCatching {
                @Suppress("DEPRECATION", "MissingPermission")
                if (enabled) adapter.enable() else adapter.disable()
            }.getOrDefault(false)
            if (directApplied) {
                return IntegrationResult(
                    success = true,
                    message = if (enabled) "Bluetooth ativado." else "Bluetooth desativado.",
                    toolName = "device:bluetooth"
                )
            }
        }

        // Android 13+ (API 33+): use official ACTION_REQUEST_ENABLE dialog for turning ON,
        // and native Bluetooth settings panel for turning OFF.
        val intent = if (enabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } else {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = when {
                launched && enabled -> "Abri a confirmação nativa do Android para ligar o Bluetooth."
                launched && !enabled -> "Abri o painel de Bluetooth do Android para desligar o Bluetooth com segurança."
                else -> "Não foi possível abrir o controle de Bluetooth."
            },
            toolName = "device:bluetooth"
        )
    }

    fun setMediaVolumePercent(percent: Int): IntegrationResult {
        val clamped = percent.coerceIn(0, 100)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((clamped / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
        return runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            IntegrationResult(true, "Volume de mídia ajustado para $clamped%.", "device:volume")
        }.getOrElse { err ->
            IntegrationResult(false, "Não foi possível alterar o volume: ${err.localizedMessage}", "device:volume")
        }
    }

    fun adjustMediaVolume(deltaSteps: Int): IntegrationResult {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val next = (curVol + deltaSteps).coerceIn(0, maxVol)
        val pct = ((next.toFloat() / maxVol) * 100f).roundToInt()
        return runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI)
            val verb = if (deltaSteps >= 0) "aumentado" else "diminuído"
            IntegrationResult(true, "Volume de mídia $verb para $pct%.", "device:volume")
        }.getOrElse { err ->
            IntegrationResult(false, "Falha ao ajustar o volume: ${err.localizedMessage}", "device:volume")
        }
    }

    fun setRingerMode(mode: String): IntegrationResult {
        val normalized = mode.trim().lowercase()
        val targetMode = when {
            normalized.contains("silenc") || normalized == "mute" -> AudioManager.RINGER_MODE_SILENT
            normalized.contains("vibr") -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        if (targetMode == AudioManager.RINGER_MODE_SILENT && !hasDndPolicyAccess()) {
            return openDndAccessPermission()
        }
        return runCatching {
            audioManager.ringerMode = targetMode
            val label = when (targetMode) {
                AudioManager.RINGER_MODE_SILENT -> "Silencioso"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrar"
                else -> "Normal com som"
            }
            IntegrationResult(true, "Modo de toque alterado para $label.", "device:toque")
        }.getOrElse {
            openDndAccessPermission()
        }
    }

    fun setDoNotDisturb(enabled: Boolean): IntegrationResult {
        if (!hasDndPolicyAccess()) {
            return openDndAccessPermission()
        }
        return runCatching {
            notificationManager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            IntegrationResult(
                true,
                if (enabled) "Modo Não Perturbe ativado." else "Modo Não Perturbe desativado.",
                "device:nao-perturbe"
            )
        }.getOrElse { err ->
            IntegrationResult(false, "Falha ao alterar Não Perturbe: ${err.localizedMessage}", "device:nao-perturbe")
        }
    }

    fun setTorchEnabled(enabled: Boolean): IntegrationResult {
        val cm = cameraManager ?: return IntegrationResult(
            false,
            "Este dispositivo não expõe controle de lanterna.",
            "device:lanterna"
        )
        return runCatching {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return IntegrationResult(false, "Nenhuma câmera com flash encontrada.", "device:lanterna")
            cm.setTorchMode(cameraId, enabled)
            torchEnabled = enabled
            IntegrationResult(
                true,
                if (enabled) "Lanterna ligada." else "Lanterna desligada.",
                "device:lanterna"
            )
        }.getOrElse { err ->
            IntegrationResult(
                false,
                "Não foi possível alternar a lanterna: ${err.localizedMessage ?: "recurso ocupado"}",
                "device:lanterna"
            )
        }
    }

    fun setBrightnessPercent(percent: Int): IntegrationResult {
        if (!canWriteSystemSettings()) {
            return openWriteSettingsPermission()
        }
        val clamped = percent.coerceIn(5, 100)
        val systemValue = ((clamped / 100f) * 255f).roundToInt().coerceIn(10, 255)
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                systemValue
            )
            IntegrationResult(true, "Brilho da tela ajustado para $clamped%.", "device:brilho")
        }.getOrElse { err ->
            IntegrationResult(false, "Erro ao ajustar brilho: ${err.localizedMessage}", "device:brilho")
        }
    }

    fun adjustBrightness(deltaPercent: Int): IntegrationResult {
        val current = readSnapshot().brightnessPercent
        return setBrightnessPercent((current + deltaPercent).coerceIn(5, 100))
    }

    fun setAutoBrightness(enabled: Boolean): IntegrationResult {
        if (!canWriteSystemSettings()) {
            return openWriteSettingsPermission()
        }
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            IntegrationResult(
                true,
                if (enabled) "Brilho automático ativado." else "Brilho automático desativado (modo manual).",
                "device:brilho"
            )
        }.getOrElse { err ->
            IntegrationResult(false, "Erro ao alterar brilho automático: ${err.localizedMessage}", "device:brilho")
        }
    }

    fun setAutoRotate(enabled: Boolean): IntegrationResult {
        if (!canWriteSystemSettings()) {
            return openWriteSettingsPermission()
        }
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            IntegrationResult(
                true,
                if (enabled) "Rotação automática da tela ativada." else "Rotação automática da tela travada.",
                "device:rotacao"
            )
        }.getOrElse { err ->
            IntegrationResult(false, "Erro ao alterar rotação: ${err.localizedMessage}", "device:rotacao")
        }
    }

    fun setScreenTimeoutSeconds(seconds: Int): IntegrationResult {
        if (!canWriteSystemSettings()) {
            return openWriteSettingsPermission()
        }
        val safeSeconds = seconds.coerceIn(15, 1800)
        return runCatching {
            Settings.System.putInt(
                appContext.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                safeSeconds * 1000
            )
            val label = if (safeSeconds >= 60) "${safeSeconds / 60} minuto(s)" else "$safeSeconds segundos"
            IntegrationResult(true, "Tempo limite de tela definido para $label.", "device:tempo-tela")
        }.getOrElse { err ->
            IntegrationResult(false, "Erro ao ajustar tempo de tela: ${err.localizedMessage}", "device:tempo-tela")
        }
    }

    fun openSystemPanelOrSettings(target: String): IntegrationResult {
        val clean = target.trim().lowercase()
        val (action, label) = when {
            clean.contains("wifi") || clean.contains("wi-fi") || clean.contains("internet") -> {
                val act = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                act to "Painel de Internet e Wi-Fi"
            }

            clean.contains("volume") || clean.contains("som") || clean.contains("audio") -> {
                val act = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_VOLUME
                } else {
                    Settings.ACTION_SOUND_SETTINGS
                }
                act to "Painel de Volume e Som"
            }

            clean.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS to "Configurações de Bluetooth"
            clean.contains("bateria") || clean.contains("economia") -> Settings.ACTION_BATTERY_SAVER_SETTINGS to "Economia de Bateria"
            clean.contains("localiz") || clean.contains("gps") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Configurações de Localização"
            clean.contains("aviao") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS to "Modo Avião"
            clean.contains("tela") || clean.contains("brilho") || clean.contains("display") -> Settings.ACTION_DISPLAY_SETTINGS to "Configurações de Tela"
            clean.contains("nfc") -> {
                val act = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS
                act to "Configurações de NFC"
            }
            else -> Settings.ACTION_SETTINGS to "Configurações do Android"
        }

        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val launched = runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
        return IntegrationResult(
            success = launched,
            message = if (launched) "Abri $label." else "Não foi possível abrir $label.",
            toolName = "device:painel"
        )
    }

    /**
     * Aplica ações imediatas de economia de bateria disponíveis sem root:
     * reduz o brilho da tela para 20%, desliga a lanterna se acesa e abre o painel
     * de Economia de Bateria do Android caso o modo ainda não esteja ativo.
     */
    fun applyPowerSavingActions(openBatteryPanelIfNeeded: Boolean = true): IntegrationResult {
        val actionsTaken = mutableListOf<String>()
        val snapshot = readSnapshot()

        if (snapshot.isTorchOn) {
            val torchRes = setTorchEnabled(false)
            if (torchRes.success) actionsTaken.add("lanterna desligada")
        }

        if (canWriteSystemSettings()) {
            if (snapshot.brightnessPercent > 25) {
                val brightRes = setBrightnessPercent(20)
                if (brightRes.success) actionsTaken.add("brilho reduzido para 20%")
            }
        } else {
            actionsTaken.add("conceda 'Alterar Sistema' para reduzir brilho automaticamente")
        }

        val battery = BatteryStatusMonitor.readSnapshot(appContext)
        if (openBatteryPanelIfNeeded && !battery.isPowerSaveMode) {
            val panelRes = openSystemPanelOrSettings("bateria")
            if (panelRes.success) actionsTaken.add("painel Economia de Bateria aberto")
        }

        val summary = if (actionsTaken.isEmpty()) {
            "Perfil de economia de energia verificado (${battery.levelPercent}%)."
        } else {
            "Economia de energia aplicada: ${actionsTaken.joinToString(", ")}."
        }
        return IntegrationResult(
            success = true,
            message = summary,
            toolName = "device:economia-bateria"
        )
    }

    /**
     * Executor unificado para comandos de voz e tags [[ACTION:DEVICE_SETTING:chave=valor]].
     */
    fun executeSettingAction(spec: String): IntegrationResult {
        val key = spec.substringBefore("=").trim().lowercase()
        val value = spec.substringAfter("=", "").trim().lowercase()
        return when (key) {
            "power_save", "battery_saver", "economia_bateria" -> {
                if (value == "panel" || value == "open") {
                    openSystemPanelOrSettings("bateria")
                } else {
                    applyPowerSavingActions(openBatteryPanelIfNeeded = value != "silent")
                }
            }

            "brightness", "brilho" -> when (value) {
                "up", "mais", "+" -> adjustBrightness(20)
                "down", "menos", "-" -> adjustBrightness(-20)
                "auto" -> setAutoBrightness(true)
                "manual" -> setAutoBrightness(false)
                else -> setBrightnessPercent(value.filter { it.isDigit() }.toIntOrNull() ?: 70)
            }

            "volume" -> when (value) {
                "up", "mais", "+" -> adjustMediaVolume(2)
                "down", "menos", "-" -> adjustMediaVolume(-2)
                "mute", "mudo", "0" -> setMediaVolumePercent(0)
                "max", "maximo", "100" -> setMediaVolumePercent(100)
                else -> setMediaVolumePercent(value.filter { it.isDigit() }.toIntOrNull() ?: 60)
            }

            "wifi", "wi-fi" -> {
                val enable = when (value) {
                    "toggle" -> !isWifiCurrentlyEnabled()
                    "off", "false", "desligar", "desativar" -> false
                    else -> true
                }
                toggleWifi(enable)
            }

            "bluetooth", "bt" -> {
                val enable = when (value) {
                    "toggle" -> !isBluetoothCurrentlyEnabled()
                    "off", "false", "desligar", "desativar" -> false
                    else -> true
                }
                toggleBluetooth(enable)
            }

            "torch", "lanterna", "flashlight" -> setTorchEnabled(value != "off" && value != "false" && value != "desligar")
            "rotate", "rotacao" -> setAutoRotate(value != "off" && value != "false" && value != "desligar")
            "dnd", "nao_perturbe" -> setDoNotDisturb(value != "off" && value != "false" && value != "desligar")
            "ringer", "toque" -> setRingerMode(value)
            "timeout", "tempo_tela" -> setScreenTimeoutSeconds(value.filter { it.isDigit() }.toIntOrNull() ?: 60)
            "panel", "painel", "open" -> openSystemPanelOrSettings(value)
            else -> openSystemPanelOrSettings(spec)
        }
    }
}
