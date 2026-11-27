package com.example.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.util.Log
import com.example.MainActivity
import com.example.voice.JarvisRuntimeCoordinator

private const val TAG = "JarvisDefaultAssist"

const val EXTRA_TRIGGERED_AS_ASSISTANT = "com.example.EXTRA_TRIGGERED_AS_ASSISTANT"
const val EXTRA_ASSIST_CONTEXT_HINT = "com.example.EXTRA_ASSIST_CONTEXT_HINT"

/**
 * Serviço oficial VoiceInteractionService que registra o A.O.Jarvis como
 * Assistente Digital Padrão do Android (Configurações > Apps padrão > App assistente digital).
 */
class JarvisVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i(TAG, "A.O.Jarvis VoiceInteractionService pronto como Assistente Padrão.")
    }
}

/**
 * Cria sessões de interação quando o usuário aciona o gesto de Assistente Padrão
 * (segurar Home/Power ou deslizar a partir dos cantos inferiores).
 */
class JarvisVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JarvisVoiceInteractionSession(this)
    }
}

/**
 * Sessão de assistência do sistema: captura o contexto/screenshot opcional fornecido
 * pelo Android, armazena no ScreenVisionManager para consciência visual e abre o
 * A.O.Jarvis já pronto para escutar por voz.
 */
class JarvisVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private var hasLaunchedAssistantUi = false

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        hasLaunchedAssistantUi = false
        launchJarvisAssistantActivity(contextHint = null)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            runCatching {
                val coordinator = JarvisRuntimeCoordinator.getInstance(context.applicationContext)
                coordinator.screenVisionManager.attachManualScreenshot(screenshot)
                Log.d(TAG, "Screenshot de contexto do Assistente Padrão anexado ao ScreenVisionManager.")
            }
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val webUri = state.assistContent?.webUri?.toString()
        val title = state.assistStructure?.getWindowNodeAt(0)?.title?.toString()
        val hint = listOfNotNull(
            title?.takeIf { it.isNotBlank() },
            webUri?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").takeIf { it.isNotBlank() }

        if (!hasLaunchedAssistantUi) {
            launchJarvisAssistantActivity(contextHint = hint)
        }
    }

    private fun launchJarvisAssistantActivity(contextHint: String?) {
        hasLaunchedAssistantUi = true
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_TRIGGERED_AS_ASSISTANT, true)
            if (!contextHint.isNullOrBlank()) {
                putExtra(EXTRA_ASSIST_CONTEXT_HINT, contextHint)
            }
        }
        runCatching {
            context.startActivity(launchIntent)
        }.onFailure {
            Log.e(TAG, "Falha ao abrir MainActivity via Assistente Padrão", it)
        }
        finish()
    }
}

/**
 * Serviço mínimo de reconhecimento declarado no voice_interaction_service.xml
 * para compatibilidade total com todos os fabricantes Android (Samsung One UI, Pixel, Xiaomi).
 */
class JarvisRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
