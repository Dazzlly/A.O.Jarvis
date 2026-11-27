package com.example.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import java.util.Locale

data class BatteryStatusSnapshot(
    val levelPercent: Int = -1,
    val isCharging: Boolean = false,
    val chargingSource: String = "Bateria",
    val temperatureCelsius: Float = 0f,
    val isPowerSaveMode: Boolean = false,
    val isLowBattery: Boolean = false,
    val healthLabel: String = "Normal",
    val updatedAt: Long = 0L
) {
    val isKnown: Boolean get() = levelPercent in 0..100
    val isCritical: Boolean get() = isKnown && levelPercent <= 15 && !isCharging
    val needsPowerSaving: Boolean get() = isKnown && !isCharging && (levelPercent <= 25 || isLowBattery)
}

/**
 * Monitor real-time battery telemetry (level, charging state, source, temperature, Android Power Save mode)
 * for the Device Control Foreground Service and AI Engine context reporting.
 */
object BatteryStatusMonitor {

    const val LOW_BATTERY_THRESHOLD_PERCENT = 25

    fun readSnapshot(context: Context, batteryIntent: Intent? = null): BatteryStatusSnapshot {
        val appContext = context.applicationContext
        val intent = batteryIntent ?: runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val rawScale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val computedPct = if (rawLevel >= 0 && rawScale > 0) {
            ((rawLevel * 100f) / rawScale).toInt().coerceIn(0, 100)
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: -1
        }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            (status == -1 && batteryManager?.isCharging == true)

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Tomada (AC)"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sem fio"
            else -> if (isCharging) "Carregador" else "Bateria"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            ?: BatteryManager.BATTERY_HEALTH_GOOD
        val healthLabel = when (health) {
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Crítica"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensão"
            else -> "Normal"
        }

        val isPowerSave = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        val isLow = computedPct in 0..LOW_BATTERY_THRESHOLD_PERCENT && !isCharging

        return BatteryStatusSnapshot(
            levelPercent = computedPct,
            isCharging = isCharging,
            chargingSource = chargingSource,
            temperatureCelsius = tempC,
            isPowerSaveMode = isPowerSave,
            isLowBattery = isLow,
            healthLabel = healthLabel,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun buildPowerSavingRecommendation(
        battery: BatteryStatusSnapshot,
        settings: DeviceSettingsSnapshot? = null
    ): String {
        if (!battery.isKnown) {
            return "Telemetria de bateria indisponível no momento."
        }
        if (battery.isCharging) {
            return "Bateria em ${battery.levelPercent}% carregando via ${battery.chargingSource}. Nenhuma economia urgente necessária."
        }

        val suggestions = buildList {
            if (settings == null || settings.brightnessPercent > 35) {
                add("reduzir brilho da tela para 20%")
            }
            if (settings?.isTorchOn == true) {
                add("desligar a lanterna")
            }
            if (settings?.isBluetoothEnabled == true) {
                add("desativar Bluetooth se não estiver em uso")
            }
            if (!battery.isPowerSaveMode) {
                add("ativar Economia de Bateria do Android")
            }
        }

        return when {
            battery.isCritical -> {
                "Alerta de bateria crítica (${battery.levelPercent}%). Recomendo imediatamente: ${suggestions.joinToString(", ")}."
            }
            battery.needsPowerSaving -> {
                "Bateria baixa (${battery.levelPercent}%). Sugestão de economia: ${suggestions.joinToString(", ")}."
            }
            else -> {
                val tempNote = if (battery.temperatureCelsius >= 41f) {
                    " Temperatura elevada (${String.format(Locale.US, "%.1f", battery.temperatureCelsius)}°C)."
                } else {
                    ""
                }
                "Bateria em ${battery.levelPercent}% (${battery.chargingSource}).$tempNote"
            }
        }
    }

    fun formatForAiPrompt(
        battery: BatteryStatusSnapshot,
        settings: DeviceSettingsSnapshot? = null
    ): String {
        if (!battery.isKnown) {
            return "Telemetria de bateria (Foreground Service): indisponível."
        }
        val stateText = if (battery.isCharging) {
            "carregando via ${battery.chargingSource}"
        } else {
            "descarregando"
        }
        val tempText = if (battery.temperatureCelsius > 0f) {
            ", temperatura=${String.format(Locale.US, "%.1f", battery.temperatureCelsius)}°C"
        } else {
            ""
        }
        val saveModeText = if (battery.isPowerSaveMode) "ATIVO" else "desativado"
        val recommendation = buildPowerSavingRecommendation(battery, settings)

        return buildString {
            append("Telemetria de bateria (Foreground Service): nível=${battery.levelPercent}%, estado=$stateText$tempText, ")
            append("saúde=${battery.healthLabel}, economia do Android=$saveModeText. ")
            if (battery.needsPowerSaving) {
                append("ATENÇÃO: Bateria baixa! Sugira proativamente ações de economia de energia ao usuário ($recommendation) e ofereça executar [[ACTION:DEVICE_SETTING:power_save=on]] quando fizer sentido.")
            } else {
                append("Resumo energético: $recommendation")
            }
        }
    }
}
