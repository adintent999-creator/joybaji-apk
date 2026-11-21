package com.bypass.studio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VoiceEngine(private val context: Context, private val onLog: (String) -> Unit) {
    private var tts: TextToSpeech? = null
    private val ttsLock = Any()
    var ready = false
        private set

    val personas: Map<String, Pair<Float, Float>> = mapOf(
        "Emma" to (1.15f to 1.00f),
        "Swara" to (1.10f to 1.05f),
        "Anaya" to (1.20f to 1.10f),
        "Aarav" to (0.88f to 0.96f),
        "Rohan" to (0.92f to 1.00f)
    )
    val personaNames: List<String> get() = personas.keys.toList()

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setLanguage(localeFor("bn"))
                onLog("TTS ready")
            } else {
                onLog("TTS init failed: $status")
            }
        }
    }

    private fun localeFor(lang: String): Locale = when (lang) {
        "hi" -> Locale("hi", "IN")
        "en" -> Locale.ENGLISH
        else -> Locale("bn", "BD")
    }

    private fun applyVoice(lang: String, persona: String, speed: Float): String {
        val result = tts?.setLanguage(localeFor(lang)) ?: TextToSpeech.LANG_NOT_SUPPORTED
        var used = lang
        if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
            tts?.setLanguage(Locale.ENGLISH)
            used = "en"
        }
        val (pitch, baseRate) = personas[persona] ?: (1.0f to 1.0f)
        tts?.setPitch(pitch)
        tts?.setSpeechRate((baseRate * speed).coerceIn(0.5f, 2.0f))
        return used
    }

    fun speak(text: String, lang: String, persona: String, speed: Float) {
        if (!ready) {
            onLog("TTS not ready")
            return
        }
        val used = applyVoice(lang, persona, speed)
        if (used != lang) onLog("Locale $lang unavailable, using English")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bypass-${System.currentTimeMillis()}")
    }

    fun synthesizeToFile(text: String, file: File, onDone: (Int) -> Unit) =
        synthesizeLineToFile(text, file, "bn", "Emma", 1.0f, onDone)

    fun synthesizeLineToFile(
        text: String,
        file: File,
        lang: String,
        persona: String,
        speed: Float,
        onDone: (Int) -> Unit
    ) {
        if (!ready) {
            onDone(TextToSpeech.ERROR)
            return
        }
        file.parentFile?.mkdirs()
        val utteranceId = "bypass-export-${System.nanoTime()}"
        val latch = CountDownLatch(1)
        val result = intArrayOf(TextToSpeech.SUCCESS)
        synchronized(ttsLock) {
            applyVoice(lang, persona, speed)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        result[0] = TextToSpeech.SUCCESS
                        latch.countDown()
                    }
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        result[0] = TextToSpeech.ERROR
                        latch.countDown()
                    }
                }
            })
            val started = tts?.synthesizeToFile(text, null, file, utteranceId)
                ?: TextToSpeech.ERROR
            if (started != TextToSpeech.SUCCESS) {
                result[0] = started
                onDone(started)
                return
            }
            if (!latch.await(90, TimeUnit.SECONDS)) {
                result[0] = TextToSpeech.ERROR
                onLog("TTS timeout: ${file.name}")
            }
        }
        onDone(result[0])
    }

    fun startListening(
        recognizer: SpeechRecognizer,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        lang: String = "bn-BD"
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
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
