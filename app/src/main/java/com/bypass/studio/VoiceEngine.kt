package com.bypass.studio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale

class VoiceEngine(private val context: Context, private val onLog: (String) -> Unit) {
    private var tts: TextToSpeech? = null
    var ready = false
        private set

    private val personas = mapOf(
        "Emma" to (1.15f to 1.00f),
        "Swara" to (1.10f to 1.05f),
        "Anaya" to (1.20f to 1.10f),
        "Aarav" to (0.88f to 0.96f),
        "Rohan" to (0.92f to 1.00f)
    )

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setLanguage(Locale("bn", "BD"))
                onLog("TTS ready")
            } else {
                onLog("TTS init failed: $status")
            }
        }
    }

    fun speak(text: String, lang: String, persona: String, speed: Float) {
        if (!ready) {
            onLog("TTS not ready")
            return
        }
        val locale = when (lang) {
            "hi" -> Locale("hi", "IN")
            "en" -> Locale.ENGLISH
            else -> Locale("bn", "BD")
        }
        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
            tts?.setLanguage(Locale.ENGLISH)
            onLog("Locale $lang unavailable, using English")
        }
        val (pitch, baseRate) = personas[persona] ?: (1.0f to 1.0f)
        tts?.setPitch(pitch)
        tts?.setSpeechRate((baseRate * speed).coerceIn(0.5f, 2.5f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bypass-${System.currentTimeMillis()}")
    }

    fun synthesizeToFile(text: String, file: File, onDone: (Int) -> Unit) {
        file.parentFile?.mkdirs()
        val result = tts?.synthesizeToFile(text, null, file, "bypass-export-${System.currentTimeMillis()}")
            ?: TextToSpeech.ERROR
        onDone(result)
    }

    fun startListening(recognizer: SpeechRecognizer, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onLog("Mic ready")
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) = onError("STT error $error")
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) onResult(list[0])
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) onResult(list[0])
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
