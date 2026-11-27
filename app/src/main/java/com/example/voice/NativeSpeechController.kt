package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "NativeSpeechController"
private const val MAX_TTS_CHUNK_CHARS = 650

sealed interface TtsStatus {
    data object Initializing : TtsStatus
    data class Ready(val localeLabel: String) : TtsStatus
    data class Error(val reason: String) : TtsStatus
}

private data class PendingSpeech(
    val text: String,
    val localeTag: String,
    val onComplete: (() -> Unit)?
)

/**
 * Controlador unificado de Speech-to-Text (SpeechRecognizer nativo) e Text-to-Speech (TTS).
 * Corrige race conditions de inicialização, fallback de idioma pt-BR, quebra de respostas longas
 * em múltiplos chunks com callback confiável e atributos de áudio de assistente.
 */
class NativeSpeechController(private val context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var activeLocale: Locale = Locale.forLanguageTag("pt-BR")

    private var pendingSpeech: PendingSpeech? = null
    private var finalUtteranceId: String? = null
    private var onTtsComplete: (() -> Unit)? = null

    private val _ttsStatus = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
    val ttsStatus: StateFlow<TtsStatus> = _ttsStatus.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        initTtsEngine()
    }

    fun reinitializeTts() {
        mainHandler.post {
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
            _ttsStatus.value = TtsStatus.Initializing
            initTtsEngine()
        }
    }

    private fun initTtsEngine() {
        tts = TextToSpeech(appContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@post
                    configureTtsAudioAndLocale(engine, "pt-BR")
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == null || utteranceId == finalUtteranceId) {
                                finishSpeakingCallback()
                            }
                        }

                        @Deprecated("Deprecated in Android API")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == null || utteranceId == finalUtteranceId) {
                                finishSpeakingCallback()
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(TAG, "TTS utterance error code=$errorCode")
                            if (utteranceId == null || utteranceId == finalUtteranceId) {
                                finishSpeakingCallback()
                            }
                        }
                    })
                    ttsReady = true
                    _ttsStatus.value = TtsStatus.Ready(activeLocale.displayName)

                    // Flush any speech queued while TTS was booting
                    val queued = pendingSpeech
                    pendingSpeech = null
                    if (queued != null) {
                        speakInternal(queued.text, queued.localeTag, queued.onComplete)
                    }
                } else {
                    ttsReady = false
                    _ttsStatus.value = TtsStatus.Error("Motor TTS do sistema indisponível (código $status)")
                    Log.w(TAG, "TextToSpeech init failed with status=$status")
                    val queued = pendingSpeech
                    pendingSpeech = null
                    queued?.onComplete?.invoke()
                }
            }
        }
    }

    private fun configureTtsAudioAndLocale(engine: TextToSpeech, preferredTag: String) {
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        runCatching { engine.setAudioAttributes(audioAttrs) }
        runCatching {
            engine.setSpeechRate(1.04f)
            engine.setPitch(1.0f)
        }

        val candidates = listOf(
            Locale.forLanguageTag(preferredTag),
            Locale("pt", "BR"),
            Locale("pt"),
            Locale.getDefault(),
            Locale.US
        )

        for (candidate in candidates) {
            val result = runCatching { engine.setLanguage(candidate) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                activeLocale = candidate
                return
            }
        }
        activeLocale = Locale.getDefault()
    }

    private fun finishSpeakingCallback() {
        mainHandler.post {
            _isSpeaking.value = false
            finalUtteranceId = null
            val cb = onTtsComplete
            onTtsComplete = null
            cb?.invoke()
        }
    }

    fun startListening(
        preferOffline: Boolean = false,
        localeTag: String = "pt-BR",
        onReady: () -> Unit = {},
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit,
        onRms: (Float) -> Unit = {},
        onError: (Int, String) -> Unit = { _, _ -> }
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                onError(
                    SpeechRecognizer.ERROR_SERVER,
                    "Reconhecimento de voz nativo indisponível neste dispositivo."
                )
                return@post
            }

            stopSpeakingInternal()
            destroyRecognizerInternal()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onReady()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    onRms(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    onRms(0f)
                }

                override fun onError(error: Int) {
                    onRms(0f)
                    onError(error, mapSpeechError(error))
                }

                override fun onResults(results: Bundle?) {
                    onRms(0f)
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (transcript.isNotBlank()) {
                        onFinal(transcript)
                    } else {
                        onError(SpeechRecognizer.ERROR_NO_MATCH, "Nenhuma fala reconhecida.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        onPartial(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }

            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                onError(
                    SpeechRecognizer.ERROR_CLIENT,
                    e.localizedMessage ?: "Erro ao iniciar o microfone nativo."
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
        }
    }

    fun speak(
        text: String,
        localeTag: String = "pt-BR",
        onComplete: (() -> Unit)? = null
    ) {
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) {
            onComplete?.let { mainHandler.post(it) }
            return
        }

        mainHandler.post {
            if (!ttsReady) {
                if (_ttsStatus.value is TtsStatus.Error) {
                    // Attempt one automatic re-init and queue the speech
                    pendingSpeech = PendingSpeech(cleanText, localeTag, onComplete)
                    reinitializeTts()
                } else {
                    // Still initializing — queue so first response is never silently dropped
                    pendingSpeech = PendingSpeech(cleanText, localeTag, onComplete)
                }
                return@post
            }
            speakInternal(cleanText, localeTag, onComplete)
        }
    }

    private fun speakInternal(
        cleanText: String,
        localeTag: String,
        onComplete: (() -> Unit)?
    ) {
        val engine = tts
        if (engine == null) {
            _isSpeaking.value = false
            onComplete?.invoke()
            return
        }

        // Pause recognizer if active so microphone does not capture TTS output
        runCatching { speechRecognizer?.cancel() }

        configureTtsAudioAndLocale(engine, localeTag)
        val chunks = splitIntoSpeechChunks(cleanText)
        if (chunks.isEmpty()) {
            _isSpeaking.value = false
            onComplete?.invoke()
            return
        }

        val lastId = "jarvis_tts_${UUID.randomUUID()}"
        finalUtteranceId = lastId
        onTtsComplete = onComplete
        _isSpeaking.value = true

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        var enqueuedAny = false
        chunks.forEachIndexed { idx, chunk ->
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (idx == chunks.lastIndex) lastId else "jarvis_chunk_${idx}_${UUID.randomUUID()}"
            val result = runCatching {
                engine.speak(chunk, queueMode, params, utteranceId)
            }.getOrDefault(TextToSpeech.ERROR)
            if (result == TextToSpeech.SUCCESS) {
                enqueuedAny = true
            } else {
                Log.w(TAG, "TTS speak failed on chunk $idx with code=$result")
            }
        }

        if (!enqueuedAny) {
            finishSpeakingCallback()
        }
    }

    fun stopSpeaking() {
        mainHandler.post { stopSpeakingInternal() }
    }

    fun destroy() {
        mainHandler.post {
            destroyRecognizerInternal()
            stopSpeakingInternal()
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
        }
    }

    private fun stopSpeakingInternal() {
        pendingSpeech = null
        finalUtteranceId = null
        onTtsComplete = null
        _isSpeaking.value = false
        runCatching { tts?.stop() }
    }

    private fun destroyRecognizerInternal() {
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\[\\[ACTION:[^\\]]+\\]\\]"), " ")
            .replace(Regex("```[\\s\\S]*?```"), " bloco de código ")
            .replace(Regex("https?://\\S+"), " link ")
            .replace(Regex("[*_`#>•|\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitIntoSpeechChunks(text: String): List<String> {
        if (text.length <= MAX_TTS_CHUNK_CHARS) return listOf(text)
        val sentences = text.split(Regex("(?<=[.!?;:\\n])\\s+"))
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            val clean = sentence.trim()
            if (clean.isEmpty()) continue
            if (clean.length > MAX_TTS_CHUNK_CHARS) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                clean.chunked(MAX_TTS_CHUNK_CHARS).forEach { part -> chunks.add(part.trim()) }
            } else if (current.length + clean.length + 1 > MAX_TTS_CHUNK_CHARS) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(clean)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(clean)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks
    }

    private fun mapSpeechError(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_AUDIO -> "Erro ao capturar áudio."
        SpeechRecognizer.ERROR_CLIENT -> "Escuta cancelada."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone necessária."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Sem rede para STT online; habilite idioma offline nas configurações do Android."
        SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala reconhecida."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor de voz ocupado."
        SpeechRecognizer.ERROR_SERVER -> "Serviço de reconhecimento indisponível."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma entrada de fala detectada."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Pacote de idioma offline não instalado."
        else -> "Erro STT ($errorCode)."
    }
}
