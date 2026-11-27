package com.example.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.voice.JarvisRuntimeCoordinator

private const val TAG = "DeviceControlFGS"
private const val CHANNEL_ID = "openjarvis_device_control_channel"
private const val NOTIFICATION_ID = 4094
private const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

data class DeviceControlServiceState(
    val isRunning: Boolean = false,
    val snapshot: DeviceSettingsSnapshot = DeviceSettingsSnapshot(),
    val batteryStatus: BatteryStatusSnapshot = BatteryStatusSnapshot(),
    val lastActionMessage: String = "Serviço pronto para comandos de voz de dispositivo e telemetria de bateria",
    val lastUpdatedAt: Long = 0L
) {
    val missingPermissionsList: List<String>
        get() = buildList {
            if (!snapshot.canWriteSystemSettings) add("Modificar Sistema (Brilho)")
            if (!snapshot.hasDndAccess) add("Não Perturbe")
            if (!snapshot.hasBluetoothPermission) add("Bluetooth")
        }
}

/**
 * Foreground Service dedicado para gerenciar permissões de configurações do dispositivo
 * e executar alterações de brilho, volume, Wi-Fi e Bluetooth sob demanda (inclusive por voz
 * enquanto o app está em segundo plano), mantendo observação contínua do estado do sistema.
 */
class DeviceControlForegroundService : Service() {

    private lateinit var coordinator: JarvisRuntimeCoordinator
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReceiverRegistered = false
    private var isObserverRegistered = false

    private val systemSettingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            syncStateAndNotification()
        }
    }

    private val hardwareStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val batteryIntent = if (intent?.action == Intent.ACTION_BATTERY_CHANGED) intent else null
            val statusMessage = when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> "Bateria baixa detectada — telemetria enviada ao motor de IA para economia."
                Intent.ACTION_POWER_CONNECTED -> "Carregador conectado — telemetria de energia atualizada."
                Intent.ACTION_POWER_DISCONNECTED -> "Carregador desconectado — monitorando consumo de bateria."
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> "Estado da Economia de Bateria do Android atualizado."
                else -> null
            }
            syncStateAndNotification(lastMessage = statusMessage, batteryIntent = batteryIntent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        coordinator = JarvisRuntimeCoordinator.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                tearDownObservers()
                coordinator.updateDeviceControlServiceState(
                    isRunning = false,
                    lastActionMessage = "Serviço de controle do dispositivo encerrado."
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_EXECUTE_SETTING -> {
                ensureForegroundAndObservers()
                val spec = intent.getStringExtra(EXTRA_SETTING_SPEC).orEmpty()
                if (spec.isNotBlank()) {
                    val result = coordinator.deviceSettingsController.executeSettingAction(spec)
                    syncStateAndNotification(lastMessage = result.message)
                } else {
                    syncStateAndNotification()
                }
            }

            ACTION_REFRESH -> {
                ensureForegroundAndObservers()
                syncStateAndNotification(lastMessage = "Permissões e estados do dispositivo atualizados.")
            }

            else -> {
                ensureForegroundAndObservers()
                syncStateAndNotification(lastMessage = "Monitoramento de brilho, volume, Wi-Fi e Bluetooth ativo.")
            }
        }
        return START_STICKY
    }

    private fun ensureForegroundAndObservers() {
        val snapshot = coordinator.deviceSettingsController.readSnapshot()
        val battery = BatteryStatusMonitor.readSnapshot(this)
        val notification = buildNotification(snapshot, battery)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isObserverRegistered) {
            runCatching {
                contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    systemSettingsObserver
                )
                isObserverRegistered = true
            }.onFailure { Log.w(TAG, "Could not register Settings.System observer: ${it.message}") }
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
                addAction(ACTION_VOLUME_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    hardwareStateReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
            }.onFailure { Log.w(TAG, "Could not register hardware state receiver: ${it.message}") }
        }
    }

    private fun syncStateAndNotification(
        lastMessage: String? = null,
        batteryIntent: Intent? = null
    ) {
        val snapshot = coordinator.deviceSettingsController.readSnapshot()
        val battery = BatteryStatusMonitor.readSnapshot(this, batteryIntent)
        val effectiveMessage = lastMessage ?: if (battery.needsPowerSaving) {
            BatteryStatusMonitor.buildPowerSavingRecommendation(battery, snapshot)
        } else {
            null
        }
        coordinator.updateDeviceControlServiceState(
            isRunning = true,
            snapshot = snapshot,
            batteryStatus = battery,
            lastActionMessage = effectiveMessage
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(snapshot, battery))
    }

    private fun buildNotification(
        snapshot: DeviceSettingsSnapshot,
        battery: BatteryStatusSnapshot
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            30,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val powerSaveIntent = PendingIntent.getService(
            this,
            31,
            Intent(this, DeviceControlForegroundService::class.java).apply {
                action = ACTION_EXECUTE_SETTING
                putExtra(EXTRA_SETTING_SPEC, "power_save=on")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val brightnessDownIntent = PendingIntent.getService(
            this,
            32,
            Intent(this, DeviceControlForegroundService::class.java).apply {
                action = ACTION_EXECUTE_SETTING
                putExtra(EXTRA_SETTING_SPEC, "brightness=down")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            33,
            Intent(this, DeviceControlForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wifiLabel = if (snapshot.isWifiEnabled) "Wi-Fi ON" else "Wi-Fi OFF"
        val btLabel = if (snapshot.isBluetoothEnabled) "BT ON" else "BT OFF"
        val batteryLabel = if (battery.isKnown) {
            val suffix = if (battery.isCharging) "⚡" else if (battery.needsPowerSaving) "⚠" else ""
            "Bat ${battery.levelPercent}%$suffix"
        } else {
            "Bat --"
        }
        val lowBatteryNotice = if (battery.needsPowerSaving) {
            " · Economia recomendada"
        } else {
            ""
        }
        val permNotice = if (!snapshot.canWriteSystemSettings) {
            " · Falta permissão de Brilho"
        } else {
            ""
        }
        val summary = "$batteryLabel · Brilho ${snapshot.brightnessPercent}% · Vol ${snapshot.mediaVolumePercent}% · $wifiLabel · $btLabel$lowBatteryNotice$permNotice"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("A.O.Jarvis · Controle & Monitor de Bateria")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, "Economizar", powerSaveIntent)
            .addAction(0, "Brilho -", brightnessDownIntent)
            .addAction(0, "Parar", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "A.O.Jarvis Controle do Dispositivo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Gerencia permissões e controles por voz de brilho, volume, Wi-Fi e Bluetooth."
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun tearDownObservers() {
        if (isObserverRegistered) {
            runCatching { contentResolver.unregisterContentObserver(systemSettingsObserver) }
            isObserverRegistered = false
        }
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(hardwareStateReceiver) }
            isReceiverRegistered = false
        }
    }

    override fun onDestroy() {
        tearDownObservers()
        coordinator.updateDeviceControlServiceState(isRunning = false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.core.ACTION_START_DEVICE_CONTROL"
        const val ACTION_STOP = "com.example.core.ACTION_STOP_DEVICE_CONTROL"
        const val ACTION_REFRESH = "com.example.core.ACTION_REFRESH_DEVICE_CONTROL"
        const val ACTION_EXECUTE_SETTING = "com.example.core.ACTION_EXECUTE_DEVICE_SETTING"
        const val EXTRA_SETTING_SPEC = "extra_setting_spec"
    }
}
