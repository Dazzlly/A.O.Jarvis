package com.example.vision

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.voice.JarvisRuntimeCoordinator

private const val TAG = "ScreenShareService"
private const val CHANNEL_ID = "openjarvis_screen_share_channel"
private const val NOTIFICATION_ID = 4092
private const val FRAME_THROTTLE_MS = 1_100L

/**
 * Foreground Service dedicado com tipo mediaProjection (obrigatório no Android 14+)
 * que captura a tela em tempo real de forma leve (~1 FPS) para que o OpenJarvis
 * identifique o que o usuário está visualizando com Gemini multimodal.
 */
class ScreenShareService : Service() {

    private lateinit var coordinator: JarvisRuntimeCoordinator
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var lastFrameMillis: Long = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection stopped by system or user")
            tearDownProjection()
            coordinator.screenVisionManager.markServiceStopped()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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
                tearDownProjection()
                coordinator.screenVisionManager.markServiceStopped()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    coordinator.screenVisionManager.markServiceStopped("Permissão de captura de tela não concedida.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Android 14+: MUST call startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                // BEFORE calling MediaProjectionManager.getMediaProjection().
                startForegroundCompat()
                startProjection(resultCode, resultData)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        tearDownProjection()
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                coordinator.screenVisionManager.markServiceStopped("Falha ao inicializar MediaProjection.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            val thread = HandlerThread("OpenJarvisScreenCapture").apply { start() }
            val handler = Handler(thread.looper)
            captureThread = thread
            captureHandler = handler

            projection.registerCallback(projectionCallback, handler)
            mediaProjection = projection

            val (captureWidth, captureHeight, densityDpi) = computeScaledDisplayDimensions()
            val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
            reader.setOnImageAvailableListener({ r ->
                val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrameMillis < FRAME_THROTTLE_MS) return@setOnImageAvailableListener
                    lastFrameMillis = now

                    val plane = image.planes.firstOrNull() ?: return@setOnImageAvailableListener
                    val buffer = plane.buffer ?: return@setOnImageAvailableListener
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth
                    val paddedWidth = captureWidth + rowPadding / pixelStride

                    val rawBitmap = Bitmap.createBitmap(paddedWidth, captureHeight, Bitmap.Config.ARGB_8888)
                    rawBitmap.copyPixelsFromBuffer(buffer)
                    val cropped = if (paddedWidth != captureWidth) {
                        Bitmap.createBitmap(rawBitmap, 0, 0, captureWidth, captureHeight).also {
                            rawBitmap.recycle()
                        }
                    } else {
                        rawBitmap
                    }
                    coordinator.screenVisionManager.updateFrame(cropped)
                } catch (e: Exception) {
                    Log.w(TAG, "Frame capture skipped: ${e.message}")
                } finally {
                    image.close()
                }
            }, handler)
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "OpenJarvisScreenVision",
                captureWidth,
                captureHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
            coordinator.screenVisionManager.markServiceStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen projection", e)
            tearDownProjection()
            coordinator.screenVisionManager.markServiceStopped(
                e.localizedMessage ?: "Erro ao iniciar compartilhamento de tela."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun computeScaledDisplayDimensions(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.densityDpi
        val (rawW, rawH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }

        val maxSide = 720
        val scale = if (maxOf(rawW, rawH) > maxSide) {
            maxSide.toFloat() / maxOf(rawW, rawH).toFloat()
        } else {
            1f
        }
        val w = (rawW * scale).toInt().coerceAtLeast(320)
        val h = (rawH * scale).toInt().coerceAtLeast(320)
        return Triple(w, h, density)
    }

    private fun tearDownProjection() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null
        runCatching { captureThread?.quitSafely() }
        captureThread = null
        captureHandler = null
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, ScreenShareService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OpenJarvis visualizando sua tela")
            .setContentText("Compartilhamento de tela ativo. Pergunte ao Jarvis sobre o que está na tela.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, "Parar compartilhamento", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenJarvis Visão de Tela",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indica quando o OpenJarvis está recebendo quadros da sua tela para análise visual."
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        tearDownProjection()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.vision.ACTION_START_SCREEN_SHARE"
        const val ACTION_STOP = "com.example.vision.ACTION_STOP_SCREEN_SHARE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
