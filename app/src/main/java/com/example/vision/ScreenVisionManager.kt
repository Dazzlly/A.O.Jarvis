package com.example.vision

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicReference

data class ScreenShareState(
    val isSharing: Boolean = false,
    val hasFrame: Boolean = false,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val lastCaptureTimestamp: Long = 0L,
    val statusText: String = "Compartilhamento de tela inativo",
    val errorMessage: String? = null
)

/**
 * Gerenciador central de visão de tela em tempo real do OpenJarvis.
 * Mantém o último quadro capturado via MediaProjection (ou captura pontual enviada pelo usuário)
 * pronto para envio multimodal ao Gemini via Firebase AI Logic.
 */
class ScreenVisionManager {

    private val _state = MutableStateFlow(ScreenShareState())
    val state: StateFlow<ScreenShareState> = _state.asStateFlow()

    private val latestBitmapRef = AtomicReference<Bitmap?>(null)

    fun createScreenCaptureIntent(context: Context): Intent {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
    }

    fun startScreenShareService(context: Context, resultCode: Int, data: Intent) {
        val serviceIntent = Intent(context, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_START
            putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenShareService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopScreenShareService(context: Context) {
        val serviceIntent = Intent(context, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun updateFrame(bitmap: Bitmap) {
        latestBitmapRef.set(bitmap)
        _state.update {
            it.copy(
                isSharing = true,
                hasFrame = true,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                lastCaptureTimestamp = System.currentTimeMillis(),
                statusText = "Tela compartilhada ao vivo (${bitmap.width}×${bitmap.height})",
                errorMessage = null
            )
        }
    }

    fun attachManualScreenshot(bitmap: Bitmap) {
        val scaled = scaleBitmapForVision(bitmap, maxDimension = 1080)
        latestBitmapRef.set(scaled)
        _state.update {
            it.copy(
                hasFrame = true,
                frameWidth = scaled.width,
                frameHeight = scaled.height,
                lastCaptureTimestamp = System.currentTimeMillis(),
                statusText = if (it.isSharing) it.statusText else "Captura anexada para análise visual",
                errorMessage = null
            )
        }
    }

    fun getLatestFrameCopy(): Bitmap? {
        val current = latestBitmapRef.get() ?: return null
        if (current.isRecycled) return null
        return runCatching {
            current.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
    }

    fun markServiceStarted() {
        _state.update {
            it.copy(
                isSharing = true,
                statusText = "Aguardando primeiro quadro da tela…",
                errorMessage = null
            )
        }
    }

    fun markServiceStopped(error: String? = null) {
        latestBitmapRef.set(null)
        _state.value = ScreenShareState(
            isSharing = false,
            hasFrame = false,
            statusText = if (error != null) "Erro na captura de tela" else "Compartilhamento de tela encerrado",
            errorMessage = error
        )
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun scaleBitmapForVision(source: Bitmap, maxDimension: Int = 900): Bitmap {
            val w = source.width
            val h = source.height
            if (w <= maxDimension && h <= maxDimension) return source
            val ratio = maxDimension.toFloat() / maxOf(w, h).toFloat()
            val targetW = (w * ratio).toInt().coerceAtLeast(1)
            val targetH = (h * ratio).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }
    }
}
