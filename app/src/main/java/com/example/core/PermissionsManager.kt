package com.example.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class AutonomyPermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val permissions: List<String>,
    val isGranted: Boolean
)

/**
 * Gerencia todas as permissões de autonomia do OpenJarvis de forma transparente
 * e compatível com as políticas do Google Play (sem permissões proibidas ou excessivas).
 */
object PermissionsManager {

    fun allRuntimePermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return list.toTypedArray()
    }

    fun missingPermissions(context: Context): Array<String> {
        return allRuntimePermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionSnapshot(context: Context): List<AutonomyPermissionItem> {
        val items = mutableListOf(
            AutonomyPermissionItem(
                id = "mic",
                title = "Microfone (Voz Nativa & Live)",
                description = "Escuta de voz STT, modo contínuo e Gemini Live",
                permissions = listOf(Manifest.permission.RECORD_AUDIO),
                isGranted = isGranted(context, Manifest.permission.RECORD_AUDIO)
            ),
            AutonomyPermissionItem(
                id = "contacts",
                title = "Contatos",
                description = "Encontrar números/emails para ligações, WhatsApp e mensagens",
                permissions = listOf(Manifest.permission.READ_CONTACTS),
                isGranted = isGranted(context, Manifest.permission.READ_CONTACTS)
            ),
            AutonomyPermissionItem(
                id = "calendar",
                title = "Agenda e Eventos",
                description = "Ler compromissos do dia e criar reuniões por comando",
                permissions = listOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                ),
                isGranted = isGranted(context, Manifest.permission.READ_CALENDAR) &&
                    isGranted(context, Manifest.permission.WRITE_CALENDAR)
            ),
            AutonomyPermissionItem(
                id = "location",
                title = "Localização",
                description = "Contexto local, endereço atual e rotas em mapas",
                permissions = listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                isGranted = isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
            ),
            AutonomyPermissionItem(
                id = "camera",
                title = "Câmera",
                description = "Abrir captura de foto e recursos visuais sob demanda",
                permissions = listOf(Manifest.permission.CAMERA),
                isGranted = isGranted(context, Manifest.permission.CAMERA)
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                AutonomyPermissionItem(
                    id = "notifications",
                    title = "Notificações",
                    description = "Serviço em segundo plano contínuo e alertas do Jarvis",
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    isGranted = isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(
                AutonomyPermissionItem(
                    id = "bluetooth",
                    title = "Dispositivos Bluetooth",
                    description = "Integração com fones e áudio viva-voz",
                    permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
                    isGranted = isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
                )
            )
        }

        return items
    }

    fun summarizePermissionsForPrompt(context: Context): String {
        val snapshot = getPermissionSnapshot(context)
        val granted = snapshot.filter { it.isGranted }.map { it.title }
        val pending = snapshot.filter { !it.isGranted }.map { it.title }
        return buildString {
            append("Permissões concedidas (${granted.size}/${snapshot.size}): ")
            append(if (granted.isEmpty()) "nenhuma" else granted.joinToString(", "))
            if (pending.isNotEmpty()) {
                append(". Pendentes: ${pending.joinToString(", ")}")
            }
        }
    }
}
