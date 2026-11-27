package com.example.data

import kotlinx.coroutines.flow.Flow
import java.text.Normalizer
import java.util.Locale

/**
 * Local Room Repository for essential offline voice commands (flashlight, brightness, volume,
 * Wi-Fi/Bluetooth toggles, battery status & power-saving profile) that execute without internet access.
 */
class OfflineVoiceCommandRepository(
    private val dao: OfflineVoiceCommandDao
) {
    fun observeCommands(): Flow<List<OfflineVoiceCommandEntity>> = dao.observeAll()

    suspend fun ensureDefaultCommandsSeeded() {
        if (dao.count() == 0) {
            dao.insertAll(defaultEssentialCommands())
        }
    }

    suspend fun findBestMatch(rawOrNormalizedInput: String): OfflineVoiceCommandEntity? {
        ensureDefaultCommandsSeeded()
        val clean = normalize(rawOrNormalizedInput)
        if (clean.isBlank()) return null

        val enabled = dao.getEnabledCommands()
        var bestMatch: OfflineVoiceCommandEntity? = null
        var bestScore = 0

        for (cmd in enabled) {
            val candidates = buildList {
                add(normalize(cmd.triggerPhrase))
                cmd.aliases.split("|", ",", ";")
                    .map { normalize(it) }
                    .filter { it.isNotBlank() }
                    .forEach { add(it) }
            }.filter { it.isNotBlank() }

            for (phrase in candidates) {
                val score = when {
                    clean == phrase -> 1000 + phrase.length
                    clean.contains(phrase) -> 500 + phrase.length
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = cmd
                }
            }
        }
        return bestMatch
    }

    suspend fun recordUsage(commandId: Long) {
        dao.incrementUsage(commandId)
    }

    suspend fun addCommand(
        triggerPhrase: String,
        aliases: String,
        actionKey: String,
        actionArgument: String,
        description: String
    ): Long {
        return dao.insert(
            OfflineVoiceCommandEntity(
                triggerPhrase = triggerPhrase.trim(),
                aliases = aliases.trim(),
                actionKey = actionKey.trim().uppercase(Locale.US),
                actionArgument = actionArgument.trim(),
                description = description.trim(),
                enabled = true
            )
        )
    }

    suspend fun toggleCommand(command: OfflineVoiceCommandEntity) {
        dao.update(command.copy(enabled = !command.enabled))
    }

    suspend fun deleteCommand(id: Long) {
        dao.deleteById(id)
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").trim()
    }

    companion object {
        fun defaultEssentialCommands(): List<OfflineVoiceCommandEntity> = listOf(
            OfflineVoiceCommandEntity(
                triggerPhrase = "turn on flashlight",
                aliases = "ligar lanterna|acender lanterna|ativar lanterna|flashlight on|torch on|turn on torch",
                actionKey = "TORCH_ON",
                actionArgument = "on",
                description = "Liga o flash LED da câmera imediatamente sem internet"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "turn off flashlight",
                aliases = "desligar lanterna|apagar lanterna|desativar lanterna|flashlight off|torch off|turn off torch",
                actionKey = "TORCH_OFF",
                actionArgument = "off",
                description = "Desliga o flash LED da câmera offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "power saving mode",
                aliases = "economizar bateria|ativar economia de bateria|modo economia de energia|save battery|enable power saving",
                actionKey = "POWER_SAVE",
                actionArgument = "on",
                description = "Reduz brilho para 20%, apaga a lanterna e abre o painel de Economia de Bateria"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "battery status",
                aliases = "nivel da bateria|como esta a bateria|status da bateria|check battery|battery level",
                actionKey = "BATTERY_STATUS",
                actionArgument = "",
                description = "Lê o nível atual de bateria, temperatura e sugere ações de economia offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "lower brightness",
                aliases = "diminuir brilho|baixar brilho|reduzir brilho|dim screen",
                actionKey = "DEVICE_SETTING",
                actionArgument = "brightness=down",
                description = "Reduz o brilho da tela em 20% offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "increase brightness",
                aliases = "aumentar brilho|subir brilho|mais brilho|brightness up",
                actionKey = "DEVICE_SETTING",
                actionArgument = "brightness=up",
                description = "Aumenta o brilho da tela em 20% offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "mute volume",
                aliases = "mutar volume|silenciar volume|volume zero|mute audio",
                actionKey = "DEVICE_SETTING",
                actionArgument = "volume=mute",
                description = "Silencia o volume de mídia instantaneamente offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "volume up",
                aliases = "aumentar volume|subir volume|mais volume",
                actionKey = "DEVICE_SETTING",
                actionArgument = "volume=up",
                description = "Aumenta o volume de mídia offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "volume down",
                aliases = "diminuir volume|baixar volume|menos volume",
                actionKey = "DEVICE_SETTING",
                actionArgument = "volume=down",
                description = "Diminui o volume de mídia offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "toggle wifi",
                aliases = "alternar wifi|alternar wi-fi|turn off wifi|turn on wifi",
                actionKey = "DEVICE_SETTING",
                actionArgument = "wifi=toggle",
                description = "Alterna Wi-Fi ou abre o painel rápido de Internet do Android offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "toggle bluetooth",
                aliases = "alternar bluetooth|turn on bluetooth|turn off bluetooth",
                actionKey = "DEVICE_SETTING",
                actionArgument = "bluetooth=toggle",
                description = "Alterna Bluetooth ou abre a confirmação nativa do Android offline"
            ),
            OfflineVoiceCommandEntity(
                triggerPhrase = "do not disturb",
                aliases = "ativar nao perturbe|nao perturbe|enable dnd|silence notifications",
                actionKey = "DEVICE_SETTING",
                actionArgument = "dnd=on",
                description = "Ativa o modo Não Perturbe do dispositivo offline"
            )
        )
    }
}
