package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

data class BatteryOptimizationStatus(
    val isIgnoringOptimizations: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val canRequestDirectExemption: Boolean = true,
    val statusLabel: String = "Verificando otimização de bateria…",
    val detailMessage: String = ""
)

/**
 * Verifica e solicita isenção de otimização de bateria (Doze / App Standby)
 * para manter o Foreground Service de voz do A.O.Jarvis ativo em segundo plano.
 */
class BatteryOptimizationManager(private val context: Context) {

    private val appContext = context.applicationContext

    fun readStatus(): BatteryOptimizationStatus {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = runCatching {
            powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        }.getOrDefault(false)
        val powerSave = runCatching {
            powerManager?.isPowerSaveMode == true
        }.getOrDefault(false)

        val directIntent = createDirectRequestIntent()
        val canDirect = runCatching {
            directIntent.resolveActivity(appContext.packageManager) != null
        }.getOrDefault(false)

        val statusLabel = if (isIgnoring) {
            "Otimização ignorada (Ativo em 2º plano)"
        } else {
            "Otimizado pelo Android (Pode pausar em 2º plano)"
        }

        val detailMessage = when {
            isIgnoring && powerSave ->
                "O A.O.Jarvis está isento de restrições de bateria, mas o modo Economia de Bateria do sistema está ligado."
            isIgnoring ->
                "O Android permite que o serviço em segundo plano do A.O.Jarvis continue ouvindo sem ser encerrado pelo modo Doze."
            else ->
                "O gerenciamento de bateria do Android pode suspender o serviço de voz em segundo plano. Recomenda-se permitir execução irrestrita."
        }

        return BatteryOptimizationStatus(
            isIgnoringOptimizations = isIgnoring,
            isPowerSaveMode = powerSave,
            canRequestDirectExemption = canDirect,
            statusLabel = statusLabel,
            detailMessage = detailMessage
        )
    }

    @SuppressLint("BatteryLife")
    fun createDirectRequestIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    fun createBestExemptionIntent(): Intent {
        val direct = createDirectRequestIntent()
        val canDirect = runCatching {
            direct.resolveActivity(appContext.packageManager) != null
        }.getOrDefault(false)
        if (canDirect) return direct

        val settingsList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val canList = runCatching {
            settingsList.resolveActivity(appContext.packageManager) != null
        }.getOrDefault(false)
        if (canList) return settingsList

        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    fun openBatteryOptimizationSettings(): IntegrationResult {
        return runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(appContext.packageManager) != null) {
                appContext.startActivity(intent)
                IntegrationResult(
                    success = true,
                    message = "Configurações de otimização de bateria abertas.",
                    toolName = "battery_optimization_settings"
                )
            } else {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallback)
                IntegrationResult(
                    success = true,
                    message = "Detalhes do aplicativo abertos para ajuste de bateria.",
                    toolName = "battery_optimization_settings"
                )
            }
        }.getOrElse { err ->
            IntegrationResult(
                success = false,
                message = err.localizedMessage ?: "Não foi possível abrir as configurações de bateria.",
                toolName = "battery_optimization_settings"
            )
        }
    }
}
