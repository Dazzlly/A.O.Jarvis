package com.example.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "AudioRecordWakeWord"
private const val SAMPLE_RATE_HZ = 16_000
private const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
private const val MIN_SPEECH_MS = 220L
private const val MAX_SPEECH_WINDOW_MS = 1_400L
private const val END_SILENCE_MS = 240L
private const val TRIGGER_COOLDOWN_MS = 1_500L

data class WakeWordDetectorState(
    val isMonitoring: Boolean = false,
    val isVerifyingPhrase: Boolean = false,
    val wakePhrase: String = "hey jarvis",
    val normalizedLevel: Float = 0f,
    val noiseFloorDb: Float = -55f,
    val triggerCount: Int = 0,
    val lastDetectedText: String = "",
    val statusText: String = "Standby AudioRecord inativo",
    val errorMessage: String? = null
)

data class WakeWordMatchResult(
    val matched: Boolean,
    val inlineCommand: String = ""
)

/**
 * Detector leve de palavra-chave ("Hey Jarvis") baseado na API AudioRecord do Android.
 *
 * Estágio 1 (sempre leve, 100% local via AudioRecord PCM 16 kHz):
 *  - Calcula energia RMS adaptativa sobre o piso de ruído ambiente, taxa de cruzamento por zero (ZCR)
 *    e razão de energia na banda vocal para detectar uma rajada de fala compatível com a cadência
 *    de "Hey Jarvis" sem manter o SpeechRecognizer em loop constante.
 *
 * Estágio 2 (confirmação acústica):
 *  - Libera o AudioRecord imediatamente para evitar conflito de microfone e dispara o callback
 *    de verificação/ativação para confirmar "Hey Jarvis" e abrir a escuta do assistente sem toque manual.
 */
class AudioRecordWakeWordDetector(private val context: Context) {

    private val appContext = context.applicationContext
    private val detectorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(WakeWordDetectorState())
    val state: StateFlow<WakeWordDetectorState> = _state.asStateFlow()

    private val lock = Any()
    private var monitorJob: Job? = null
    private var activeRecorder: AudioRecord? = null

    @Volatile
    private var sensitivity: Float = 0.65f

    @Volatile
    private var wakePhrase: String = "hey jarvis"

    @Volatile
    private var lastTriggerTimestampMs: Long = 0L

    fun updateParameters(phrase: String, sensitivityLevel: Float) {
        wakePhrase = phrase.trim().ifBlank { "hey jarvis" }
        sensitivity = sensitivityLevel.coerceIn(0.2f, 1.0f)
        _state.update { it.copy(wakePhrase = wakePhrase) }
    }

    fun setVerifyingPhrase(verifying: Boolean, label: String? = null) {
        _state.update {
            it.copy(
                isVerifyingPhrase = verifying,
                statusText = label ?: if (verifying) {
                    "Verificando \"$wakePhrase\"…"
                } else if (it.isMonitoring) {
                    "AudioRecord · Aguardando \"$wakePhrase\""
                } else {
                    "Standby AudioRecord inativo"
                }
            )
        }
    }

    fun recordSuccessfulDetection(matchedTranscript: String) {
        lastTriggerTimestampMs = System.currentTimeMillis()
        _state.update {
            it.copy(
                isVerifyingPhrase = false,
                triggerCount = it.triggerCount + 1,
                lastDetectedText = matchedTranscript,
                statusText = "Ativado por \"$wakePhrase\""
            )
        }
    }

    fun startMonitoring(
        phrase: String = wakePhrase,
        sensitivityLevel: Float = sensitivity,
        onVoiceEnergyLevel: (Float) -> Unit = {},
        onCandidateDetected: () -> Unit
    ) {
        updateParameters(phrase, sensitivityLevel)

        if (!hasAudioPermission()) {
            _state.update {
                it.copy(
                    isMonitoring = false,
                    normalizedLevel = 0f,
                    statusText = "Permissão de microfone necessária",
                    errorMessage = "Conceda permissão de microfone para usar a detecção \"Hey Jarvis\"."
                )
            }
            return
        }

        synchronized(lock) {
            if (monitorJob?.isActive == true) {
                return
            }
            _state.update {
                it.copy(
                    isMonitoring = true,
                    isVerifyingPhrase = false,
                    wakePhrase = wakePhrase,
                    errorMessage = null,
                    statusText = "AudioRecord · Aguardando \"$wakePhrase\""
                )
            }

            monitorJob = detectorScope.launch {
                runPcmDetectionLoop(
                    onVoiceEnergyLevel = onVoiceEnergyLevel,
                    onCandidateDetected = onCandidateDetected
                )
            }
        }
    }

    fun stopMonitoring(statusOverride: String? = null) {
        synchronized(lock) {
            monitorJob?.cancel()
            monitorJob = null
            releaseRecorderLocked()
        }
        _state.update {
            it.copy(
                isMonitoring = false,
                normalizedLevel = 0f,
                statusText = statusOverride ?: if (it.isVerifyingPhrase) {
                    "Verificando \"$wakePhrase\"…"
                } else {
                    "Standby AudioRecord pausado"
                }
            )
        }
    }

    fun destroy() {
        stopMonitoring("Standby AudioRecord encerrado")
    }

    @SuppressLint("MissingPermission")
    private suspend fun runPcmDetectionLoop(
        onVoiceEnergyLevel: (Float) -> Unit,
        onCandidateDetected: () -> Unit
    ) {
        val recorder = createAudioRecord()
        if (recorder == null) {
            _state.update {
                it.copy(
                    isMonitoring = false,
                    normalizedLevel = 0f,
                    statusText = "AudioRecord indisponível",
                    errorMessage = "Não foi possível inicializar o AudioRecord neste dispositivo."
                )
            }
            return
        }

        synchronized(lock) {
            activeRecorder = recorder
        }

        val frameBuffer = ShortArray(FRAME_SAMPLES)
        var noiseFloorRms = 140.0
        var inSpeechBurst = false
        var speechStartMs = 0L
        var lastActiveSpeechMs = 0L
        var voicedFrameCount = 0
        var syllablePeaks = 0
        var previousHighEnergy = false
        var lastUiEmitMs = 0L

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord não entrou em estado de gravação.")
            }

            while (detectorScope.isActive && monitorJob?.isActive == true) {
                val readCount = recorder.read(frameBuffer, 0, frameBuffer.size)
                if (readCount <= 0) {
                    delay(20L)
                    continue
                }

                val nowMs = System.currentTimeMillis()
                val features = extractFrameFeatures(frameBuffer, readCount)

                // Adaptive speech threshold controlled by user sensitivity (0.2 = strict .. 1.0 = very responsive)
                val snrMultiplier = 3.4 - (sensitivity * 1.9) // ~3.0x down to ~1.5x over noise floor
                val minAbsRms = 420.0 - (sensitivity * 260.0) // ~368 down to ~160
                val triggerRms = maxOf(noiseFloorRms * snrMultiplier, minAbsRms)

                // Speech typically exhibits moderate ZCR and balanced vocal-band energy
                val looksLikeVoiceBand = features.zcr in 0.015f..0.42f && features.voiceBandRatio >= 0.22f
                val isVoiceFrame = features.rms >= triggerRms && looksLikeVoiceBand

                // Slowly adapt ambient noise floor during quiet frames
                if (!isVoiceFrame) {
                    noiseFloorRms = (noiseFloorRms * 0.96) + (features.rms.coerceIn(45.0, 900.0) * 0.04)
                }

                // Publish lightweight energy level (~15 Hz max) for live UI indicator
                if (nowMs - lastUiEmitMs >= 65L) {
                    lastUiEmitMs = nowMs
                    val normalized = ((features.rms / (triggerRms * 1.6)).toFloat()).coerceIn(0.05f, 1f)
                    val floorDb = rmsToDb(noiseFloorRms)
                    _state.update {
                        it.copy(
                            normalizedLevel = normalized,
                            noiseFloorDb = floorDb
                        )
                    }
                    onVoiceEnergyLevel(normalized)
                }

                if (isVoiceFrame) {
                    if (!inSpeechBurst) {
                        inSpeechBurst = true
                        speechStartMs = nowMs
                        voicedFrameCount = 0
                        syllablePeaks = 0
                        previousHighEnergy = false
                    }
                    voicedFrameCount += 1
                    lastActiveSpeechMs = nowMs

                    val isPeakFrame = features.rms >= triggerRms * 1.18
                    if (isPeakFrame && !previousHighEnergy) {
                        syllablePeaks += 1
                    }
                    previousHighEnergy = isPeakFrame
                } else {
                    previousHighEnergy = false
                }

                if (inSpeechBurst) {
                    val elapsedBurstMs = nowMs - speechStartMs
                    val silenceGapMs = nowMs - lastActiveSpeechMs
                    val burstCompleted = silenceGapMs >= END_SILENCE_MS || elapsedBurstMs >= MAX_SPEECH_WINDOW_MS

                    if (burstCompleted) {
                        val validDuration = elapsedBurstMs >= MIN_SPEECH_MS && voicedFrameCount >= 7
                        val validCadence = syllablePeaks in 1..6
                        val outsideCooldown = (nowMs - lastTriggerTimestampMs) >= TRIGGER_COOLDOWN_MS

                        inSpeechBurst = false
                        voicedFrameCount = 0
                        syllablePeaks = 0

                        if (validDuration && validCadence && outsideCooldown) {
                            lastTriggerTimestampMs = nowMs
                            // Release AudioRecord BEFORE invoking Stage 2 so SpeechRecognizer gets exclusive mic access
                            synchronized(lock) {
                                releaseRecorderLocked()
                                monitorJob = null
                            }
                            _state.update {
                                it.copy(
                                    isMonitoring = false,
                                    isVerifyingPhrase = true,
                                    normalizedLevel = 0f,
                                    statusText = "Voz detectada — confirmando \"$wakePhrase\"…"
                                )
                            }
                            onCandidateDetected()
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (monitorJob?.isActive == true) {
                Log.w(TAG, "AudioRecord monitoring error: ${e.message}")
                _state.update {
                    it.copy(
                        isMonitoring = false,
                        normalizedLevel = 0f,
                        statusText = "AudioRecord pausado",
                        errorMessage = e.localizedMessage
                    )
                }
            }
        } finally {
            synchronized(lock) {
                if (activeRecorder === recorder) {
                    releaseRecorderLocked()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null
        val bufferSize = maxOf(minBuf * 2, FRAME_SAMPLES * 8)

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
        }
        return null
    }

    private fun releaseRecorderLocked() {
        val rec = activeRecorder
        activeRecorder = null
        if (rec != null) {
            runCatching {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            }
            runCatching { rec.release() }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class PcmFrameFeatures(
        val rms: Double,
        val zcr: Float,
        val voiceBandRatio: Float
    )

    private fun extractFrameFeatures(samples: ShortArray, count: Int): PcmFrameFeatures {
        var sumSquares = 0.0
        var zeroCrossings = 0
        var diffEnergy = 0.0
        var prev = samples[0].toInt()

        for (i in 0 until count) {
            val s = samples[i].toInt()
            sumSquares += s.toDouble() * s.toDouble()
            if (i > 0) {
                if ((s >= 0 && prev < 0) || (s < 0 && prev >= 0)) {
                    zeroCrossings += 1
                }
                val diff = s - prev
                diffEnergy += abs(diff).toDouble()
            }
            prev = s
        }
        val rms = sqrt(sumSquares / count.coerceAtLeast(1))
        val zcr = zeroCrossings.toFloat() / (count - 1).coerceAtLeast(1)
        val meanAbsDiff = diffEnergy / (count - 1).coerceAtLeast(1)
        val voiceBandRatio = if (rms > 1.0) {
            (meanAbsDiff / rms).toFloat().coerceIn(0f, 2f)
        } else {
            0f
        }
        return PcmFrameFeatures(rms = rms, zcr = zcr, voiceBandRatio = voiceBandRatio)
    }

    private fun rmsToDb(rms: Double): Float {
        val safe = (rms / 32768.0).coerceAtLeast(1e-6)
        return (20.0 * log10(safe)).toFloat().coerceIn(-90f, 0f)
    }

    companion object {
        private val DEFAULT_HEY_JARVIS_ALIASES = listOf(
            "hey jarvis",
            "ei jarvis",
            "oi jarvis",
            "hei jarvis",
            "e jarvis",
            "ok jarvis",
            "hey jarves",
            "ei jarves",
            "hey djarvis",
            "jarvis"
        )

        /**
         * Verifica se a transcrição contém "Hey Jarvis" (ou a palavra-chave configurada)
         * e extrai qualquer comando dito imediatamente na mesma frase.
         */
        fun matchWakePhrase(rawTranscript: String, configuredWakeWord: String): WakeWordMatchResult {
            val normalizedTranscript = normalizeForMatch(rawTranscript)
            if (normalizedTranscript.isBlank()) return WakeWordMatchResult(matched = false)

            val configuredNorm = normalizeForMatch(configuredWakeWord)
            val candidates = buildList {
                if (configuredNorm.isNotBlank()) add(configuredNorm)
                DEFAULT_HEY_JARVIS_ALIASES.forEach { alias ->
                    if (!contains(alias)) add(alias)
                }
            }.sortedByDescending { it.length }

            for (phrase in candidates) {
                val idx = normalizedTranscript.indexOf(phrase)
                if (idx >= 0) {
                    val endIdx = (idx + phrase.length).coerceAtMost(rawTranscript.length)
                    val trailing = if (endIdx < rawTranscript.length) {
                        rawTranscript.substring(endIdx)
                            .trimStart(',', '.', ':', ' ', '-', '!', '?')
                            .trim()
                    } else {
                        ""
                    }
                    return WakeWordMatchResult(matched = true, inlineCommand = trailing)
                }
            }
            return WakeWordMatchResult(matched = false)
        }

        private fun normalizeForMatch(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(Locale("pt", "BR")), Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
