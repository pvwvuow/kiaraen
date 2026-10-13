package com.kiaraen.app.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Tiny TextToSpeech wrapper for the «بلندگو» buttons and listening mode.
 * Initialized lazily on first use; a missing/failed engine is silently ignored
 * (every call is guarded) so learning never breaks on TTS-less devices.
 * Persian device locale is kept for Persian strings, English (US) for words.
 */
object TtsPlayer {

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    fun speakWord(context: Context, word: String, rate: String = "normal") {
        speak(context, word, Locale.US, rate)
    }

    fun speakPersian(context: Context, text: String, rate: String = "normal") {
        speak(context, text, Locale("fa", "IR"), rate)
    }

    private fun speak(context: Context, text: String, locale: Locale, rate: String) {
        if (text.isBlank()) return
        val engine = obtain(context) ?: return
        runCatching {
            engine.language = locale
            engine.setSpeechRate(
                when (rate) {
                    "slow" -> 0.65f
                    "fast" -> 1.35f
                    else -> 0.95f
                },
            )
            engine.setPitch(1.05f)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kiara_$text")
        }
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun obtain(context: Context): TextToSpeech? {
        if (tts != null) return if (ready) tts else null
        synchronized(this) {
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                }
            }
        }
        return if (ready) tts else null
        // first call may return null while the engine warms up — the tap
        // simply does nothing, the second tap speaks (matches Web Speech feel)
    }
}
