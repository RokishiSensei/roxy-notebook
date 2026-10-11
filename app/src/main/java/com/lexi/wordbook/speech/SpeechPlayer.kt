package com.lexi.wordbook.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Thin wrapper around the platform TextToSpeech engine used for pronunciation playback.
 * The engine is created lazily on first use and shut down with the Activity.
 */
class SpeechPlayer(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var pending: Pair<String, Float>? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile
    private var speakingText: String? = null

    private val _speakingText = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speakingText.asStateFlow()

    fun ensureInitialized() {
        if (engine == null) {
            runCatching {
                engine = TextToSpeech(appContext, this)
            }.onFailure {
                _available.value = false
                _ready.value = false
            }
        }
    }

    override fun onInit(status: Int) {
        val ok = status == TextToSpeech.SUCCESS
        _ready.value = ok
        _available.value = ok
        if (!ok) {
            engine = null
            return
        }
        engine?.let { tts ->
            val locale = Locale.US
            val result = runCatching { tts.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                runCatching { tts.setLanguage(Locale.ENGLISH) }
            }
            runCatching { tts.setSpeechRate(0.95f) }
            runCatching { tts.setPitch(1.0f) }
            runCatching {
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speakingText.value = utteranceId
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_speakingText.value == utteranceId) _speakingText.value = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (_speakingText.value == utteranceId) _speakingText.value = null
                    }
                })
            }
            pending?.let { (text, rate) ->
                pending = null
                speak(text, rate)
            }
        }
    }

    /** Speaks [text] with an optional slower [rate] for dictation. */
    fun speak(text: String, rate: Float = SpeechPlayer.RATE_NORMAL) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        ensureInitialized()
        val tts = engine
        if (tts == null || !_ready.value) {
            pending = clean to rate
            return
        }
        runCatching {
            tts.setSpeechRate(rate)
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, clean)
        }.onFailure { _available.value = false }
    }

    fun stop() {
        runCatching { engine?.stop() }
        _speakingText.value = null
    }

    fun shutdown() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        _ready.value = false
        _speakingText.value = null
    }

    companion object {
        const val RATE_NORMAL = 0.95f
        const val RATE_SLOW = 0.6f
    }
}
