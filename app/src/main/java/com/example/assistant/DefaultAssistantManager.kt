package com.example.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.core.IntegrationResult

data class DefaultAssistantStatus(
    val isDefaultAssistant: Boolean = false,
    val isRolePickerAvailable: Boolean = false,
    val currentSystemAssistant: String = "Não verificado",
    val statusDescription: String = "A.O.Jarvis não está definido como Assistente Padrão."
)

/**
 * Gerencia o registro e a configuração do A.O.Jarvis como Assistente Digital Padrão
 * do Android (RoleManager.ROLE_ASSISTANT no Android 10+ e Settings.ACTION_VOICE_INPUT_SETTINGS).
 */
class DefaultAssistantManager(private val context: Context) {

    fun readStatus(): DefaultAssistantStatus {
        val pkg = context.packageName
        var isRoleHeld = false
        var isRoleAvailable = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null) {
                isRoleAvailable = runCatching {
                    roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)
                }.getOrDefault(false)
                isRoleHeld = runCatching {
                    roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                }.getOrDefault(false)
            }
        }

        val secureAssistant = runCatching {
            Settings.Secure.getString(context.contentResolver, "assistant").orEmpty()
        }.getOrDefault("")
        val secureVoiceService = runCatching {
            Settings.Secure.getString(context.contentResolver, "voice_interaction_service").orEmpty()
        }.getOrDefault("")

        val isSecureMatch = secureAssistant.contains(pkg) || secureVoiceService.contains(pkg)
        val isDefault = isRoleHeld || isSecureMatch

        val currentLabel = when {
            isDefault -> "A.O.Jarvis (Ativo)"
            secureAssistant.isNotBlank() -> secureAssistant.substringBefore("/")
            secureVoiceService.isNotBlank() -> secureVoiceService.substringBefore("/")
            else -> "Assistente do sistema"
        }

        val description = if (isDefault) {
            "A.O.Jarvis é o Assistente Digital Padrão! Segure o botão Home/Energia ou deslize dos cantos inferiores para acionar por voz."
        } else {
            "Defina o A.O.Jarvis como app assistente digital padrão para acioná-lo em qualquer tela pelo gesto do sistema."
        }

        return DefaultAssistantStatus(
            isDefaultAssistant = isDefault,
            isRolePickerAvailable = isRoleAvailable,
            currentSystemAssistant = currentLabel,
            statusDescription = description
        )
    }

    /**
     * Cria o Intent oficial do Android RoleManager para definir o app como Assistente Padrão
     * diretamente pelo diálogo do sistema (Android 10+). Retorna null em versões anteriores
     * ou quando o papel não está disponível para diálogo direto.
     */
    fun createRequestDefaultAssistantRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                runCatching { roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)
            ) {
                return runCatching {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                }.getOrNull()
            }
        }
        return null
    }

    /**
     * Abre as configurações oficiais de App Assistente Digital do Android (funciona em todos os OEMs).
     */
    fun openSystemAssistantSettings(): IntegrationResult {
        val candidates = buildList {
            add(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            add(Intent(Settings.ACTION_SETTINGS))
        }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) {
                return IntegrationResult(
                    success = true,
                    message = "Abrindo configurações do App Assistente Digital. Selecione A.O.Jarvis.",
                    toolName = "DEFAULT_ASSISTANT"
                )
            }
        }
        return IntegrationResult(
            success = false,
            message = "Não foi possível abrir as configurações do Assistente Digital.",
            toolName = "DEFAULT_ASSISTANT"
        )
    }
}
